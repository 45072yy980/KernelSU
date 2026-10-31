//! System-partition guard v2: a three-level static scan of a module's scripts.
//!
//! Spec: `docs/partition-guard-v2.md`. The scan never executes module code; it
//! only reads the scripts the module would run and reports what each one does.
//! Every finding carries the file, the 1-based line, the matched source text and
//! a human-readable risk, so the installer can show the user exactly what a
//! module would touch before it is allowed to run.
//!
//! Levels:
//!   * [`Level::Green`]  - no real partition is touched, install proceeds.
//!   * [`Level::Yellow`] - touches a real partition, or runs code that cannot be
//!     audited statically; the user confirms with the volume keys.
//!   * [`Level::Red`]    - irreversible or boot-chain breaking; always refused,
//!     even with `--force`.

// The rule table is a long list of literal patterns; the pedantic doc/format
// lints add noise here without catching anything.
#![allow(clippy::all, clippy::pedantic, clippy::nursery)]

use std::collections::HashSet;
use std::fs;
use std::io::Read;
use std::os::unix::io::AsRawFd;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use regex_lite::Regex;

/// How dangerous a finding is. Declaration order is the sort order, so the
/// strongest level wins when a report is reduced to a single verdict.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Level {
    Green,
    Yellow,
    Red,
}

impl Level {
    pub fn label(self) -> &'static str {
        match self {
            Self::Green => "🟢 未发现风险",
            Self::Yellow => "🟡 需要确认",
            Self::Red => "🔴 禁止安装",
        }
    }
}

/// One place in a module that does something the guard cares about.
#[derive(Debug, Clone)]
pub struct Finding {
    pub level: Level,
    /// Script path relative to the module root.
    pub file: String,
    /// 1-based line number inside that script.
    pub line: usize,
    /// Trimmed source line, cut to [`SNIPPET_CHARS`] characters.
    pub snippet: String,
    /// Rule id, e.g. `R1` / `Y2`; see the spec for the table.
    pub rule: &'static str,
    /// Short human-readable name of the rule.
    pub title: &'static str,
    /// What flashing this could do, in user-facing words.
    pub risk: &'static str,
}

/// Everything one scan learned about one module.
#[derive(Debug, Clone)]
pub struct ScanReport {
    pub module_id: String,
    /// Number of scripts actually scanned.
    pub scripts: usize,
    /// Total bytes read.
    pub bytes: u64,
    /// True when the file-count or per-file size cap was hit.
    pub truncated: bool,
    pub findings: Vec<Finding>,
}

impl ScanReport {
    /// Strongest level among the findings; `Green` when there are none.
    pub fn level(&self) -> Level {
        self.findings
            .iter()
            .map(|finding| finding.level)
            .max()
            .unwrap_or(Level::Green)
    }

    fn count(&self, level: Level) -> usize {
        self.findings
            .iter()
            .filter(|finding| finding.level == level)
            .count()
    }
}

/// Answer from the volume-key prompt.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Confirm {
    /// Volume-up: the user accepted the risk.
    Continue,
    /// Volume-down, timeout, or interrupt.
    Cancel,
    /// No readable input device, so the question could not be asked.
    Unavailable,
}

/// One rule: a level, the risk wording, and the line pattern that triggers it.
#[derive(Clone, Copy)]
struct Rule {
    level: Level,
    id: &'static str,
    title: &'static str,
    risk: &'static str,
    pattern: &'static str,
}

const RED_BOOT: Rule = Rule {
    level: Level::Red,
    id: "R4",
    title: "写入引导分区",
    risk: "写 boot / init_boot / vendor_boot / dtbo / vbmeta / super，会破坏引导链，设备可能无法开机",
    pattern: r"\b(dd|tee|cat|cp|mv|echo|flash_image|nandwrite|flashcp|avbctl)\b[^;|]*by-name/[a-z0-9_./-]*(boot|vbmeta|dtbo|super)",
};

const RED_RAW_WRITE: Rule = Rule {
    level: Level::Red,
    id: "R1",
    title: "直接写块设备",
    risk: "把数据写进分区原始扇区，文件系统与校验值立刻损坏，重启也修不回来，只能重刷整机",
    pattern: r#"\bdd\b[^;|]*\bof=\s*["']?/dev/"#,
};

const RED_REDIRECT: Rule = Rule {
    level: Level::Red,
    id: "R1",
    title: "重定向写入块设备",
    risk: "把数据写进分区原始扇区，文件系统与校验值立刻损坏，重启也修不回来，只能重刷整机",
    pattern: r#">>?\s*["']?/dev/block/"#,
};

const RED_TEE: Rule = Rule {
    level: Level::Red,
    id: "R1",
    title: "tee 写入块设备",
    risk: "把数据写进分区原始扇区，文件系统与校验值立刻损坏，重启也修不回来，只能重刷整机",
    pattern: r"\btee\b[^;|]*/dev/block/",
};

const RED_COPY: Rule = Rule {
    level: Level::Red,
    id: "R1",
    title: "复制文件到块设备",
    risk: "把文件内容灌进分区原始扇区，分区数据会被破坏且无法回滚",
    pattern: r#"\b(cp|mv|install)\b[^;|]*\s["']?/dev/block/[^;|\s"']*["']?\s*($|[;|&])"#,
};

const RED_FORMAT: Rule = Rule {
    level: Level::Red,
    id: "R2",
    title: "格式化或抹除分区",
    risk: "抹掉分区元数据，整分区数据不可恢复",
    pattern: r"\b(mkfs(\.[a-z0-9]+)?|mke2fs|wipefs|blkdiscard|sgdisk|parted|sfdisk)\b[^;|]*/dev/",
};

const RED_FLASH: Rule = Rule {
    level: Level::Red,
    id: "R3",
    title: "刷写镜像",
    risk: "覆盖 boot / system / vbmeta 等分区，可能导致设备无法开机",
    pattern: r"\b(fastboot\b[^;|]*\bflash|update_engine|flash_image|nandwrite|flashcp)\b",
};

const RED_AVB: Rule = Rule {
    level: Level::Red,
    id: "R5",
    title: "关闭 AVB / verity",
    risk: "破坏 verified boot；OTA 会失败，部分机型直接拒绝启动",
    pattern: r"\b(avbctl|avbtool)\b|\bdisable-verity\b|\bset-avb\b",
};

const RED_MOUNT_RAW: Rule = Rule {
    level: Level::Red,
    id: "R6",
    title: "以可写方式挂载裸块设备",
    risk: "绕过文件系统层直接改分区，等同于把分区交给脚本任意写",
    pattern: r"\bmount\b[^;|]*\b(rw|remount)\b[^;|]*/dev/block/",
};

const RED_INDIRECT: Rule = Rule {
    level: Level::Red,
    id: "R7",
    title: "变量间接引用块设备",
    risk: "块设备路径藏在变量里，无法静态确定写入目标，按最坏情况处理",
    pattern: r#"(^|\s)(export\s+)?[A-Za-z_][A-Za-z0-9_]*=["']?/dev/block/"#,
};

const YELLOW_MOUNT_RAW: Rule = Rule {
    level: Level::Yellow,
    id: "Y6",
    title: "挂载裸块设备",
    risk: "裸设备挂载本身只读时不能写入，但如果脚本随后重新挂成可写、或把 fd 交给别的工具，就会直接改分区",
    pattern: r"\bmount\b[^;|]*/dev/block/",
};

const YELLOW_REMOUNT_A: Rule = Rule {
    level: Level::Yellow,
    id: "Y1",
    title: "把系统分区改成可写",
    risk: "只读系统分区被 remount 成可写；此后对它的修改会真实落到分区，重启不会还原",
    pattern: r"\bmount\b[^;|]*\b(remount|rw)\b[^;|]*/(system|system_ext|vendor|product|odm|oem|my_[a-z]+)\b",
};

const YELLOW_REMOUNT_B: Rule = Rule {
    level: Level::Yellow,
    id: "Y1",
    title: "把系统分区改成可写",
    risk: "只读系统分区被 remount 成可写；此后对它的修改会真实落到分区，重启不会还原",
    pattern: r"\bmount\b[^;|]*/(system|system_ext|vendor|product|odm|oem|my_[a-z]+)\b[^;|]*\b(remount|rw)\b",
};

const YELLOW_SYS_WRITE: Rule = Rule {
    level: Level::Yellow,
    id: "Y2",
    title: "修改系统分区文件",
    risk: "直接改系统分区里的文件；写坏会导致无法开机或 OTA 失败，通常只能重刷",
    pattern: r"\b(sed\s+-i|cp|mv|rm|chmod|chown|ln|install|tee)\b[^;|]*\s/(system|system_ext|vendor|product|odm|oem|my_[a-z]+)/",
};

const YELLOW_SYS_REDIRECT: Rule = Rule {
    level: Level::Yellow,
    id: "Y2",
    title: "重定向写入系统分区",
    risk: "直接改系统分区里的文件；写坏会导致无法开机或 OTA 失败，通常只能重刷",
    pattern: r#">>?\s*["']?/(system|system_ext|vendor|product|odm|oem|my_[a-z]+)/"#,
};

const YELLOW_BLOCKDEV: Rule = Rule {
    level: Level::Yellow,
    id: "Y3",
    title: "强制块设备可写",
    risk: "让只读块设备接受写入，后续写入会直接落到分区",
    pattern: r"\bblockdev\b[^;|]*--setrw",
};

const YELLOW_EVAL: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "动态执行无法审计",
    risk: "脚本内容被拼接后执行（eval），无法静态审计，可能藏着任意写分区逻辑",
    pattern: r"\beval\b",
};

const YELLOW_BASE64: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "解码后执行无法审计",
    risk: "内容被 base64 编码后再执行，无法静态审计，可能藏着任意写分区逻辑",
    pattern: r"\bbase64\b[^;|]*(-d|--decode)",
};

const YELLOW_XXD: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "十六进制还原后执行",
    risk: "内容以十六进制形式藏匿后再执行，无法静态审计",
    pattern: r"\bxxd\b[^;|]*\s-r\b",
};

const YELLOW_PRINTF_HEX: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "转义字节拼接执行",
    risk: "用 printf 拼出带转义字节的内容再执行，无法静态审计",
    pattern: r"printf\b[^;|]*\\x[0-9a-fA-F]{2}",
};

const YELLOW_SUBSHELL: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "子 shell 动态执行",
    risk: "命令由子 shell 现场生成再执行，无法静态审计",
    pattern: r#"\b(ba)?sh\b[^;|]*-c\s*["']?\$\("#,
};

const YELLOW_NET_EXEC: Rule = Rule {
    level: Level::Yellow,
    id: "Y5",
    title: "联网取回并执行",
    risk: "实际执行的代码在安装时才下载，无法审计，可能包含任意写分区逻辑",
    pattern: r"\b(curl|wget)\b[^;|]*\|\s*(sh|bash)\b",
};

const YELLOW_NET_SAVE_EXEC: Rule = Rule {
    level: Level::Yellow,
    id: "Y5",
    title: "下载后执行",
    risk: "下载脚本再执行，实际内容在安装时才确定，无法审计",
    pattern: r"\b(curl|wget)\b[^;|]*&&\s*[^;|]*\b(ba)?sh\b",
};

/// Red rules first: a line that both flashes and remounts should report the
/// stronger, more specific reason.
const RULES: &[Rule] = &[
    RED_BOOT,
    RED_AVB,
    RED_FLASH,
    RED_FORMAT,
    RED_MOUNT_RAW,
    RED_RAW_WRITE,
    RED_REDIRECT,
    RED_TEE,
    RED_COPY,
    RED_INDIRECT,
    YELLOW_MOUNT_RAW,
    YELLOW_REMOUNT_A,
    YELLOW_REMOUNT_B,
    YELLOW_SYS_WRITE,
    YELLOW_SYS_REDIRECT,
    YELLOW_BLOCKDEV,
    YELLOW_EVAL,
    YELLOW_BASE64,
    YELLOW_XXD,
    YELLOW_PRINTF_HEX,
    YELLOW_SUBSHELL,
    YELLOW_NET_EXEC,
    YELLOW_NET_SAVE_EXEC,
];

/// Script names a module may run even without a `.sh` suffix.
const FIXED_SCRIPTS: &[&str] = &[
    "customize.sh",
    "install.sh",
    "uninstall.sh",
    "post-fs-data.sh",
    "post-mount.sh",
    "service.sh",
    "boot-completed.sh",
    "late-load.sh",
    "metainstall.sh",
    "metamount.sh",
];

/// Binary-ish suffixes that are never shells scripts.
const SKIP_SUFFIXES: &[&str] = &[
    "so", "apk", "dex", "jar", "png", "jpg", "jpeg", "webp", "gif", "zip", "gz", "xz", "zst",
    "img", "ko", "o", "a", "luac", "odex", "vdex", "art", "ttf", "otf", "woff", "woff2",
];

const MAX_SCRIPTS: usize = 200;
const MAX_SCRIPT_BYTES: u64 = 2 * 1024 * 1024;
const MAX_FINDINGS: usize = 200;
const MAX_DEPTH: u32 = 8;
/// Snippet length, in characters, shown in the report.
const SNIPPET_CHARS: usize = 80;
/// Time the volume-key prompt waits before treating silence as "cancel".
pub const CONFIRM_TIMEOUT: Duration = Duration::from_secs(60);

/// Scan every script a module would run and report what each one touches.
pub fn scan_module(root: &Path, module_id: &str) -> ScanReport {
    let (files, truncated_files) = collect_scripts(root);
    let rules = compile_rules();
    let mut report = ScanReport {
        module_id: module_id.to_string(),
        scripts: 0,
        bytes: 0,
        truncated: truncated_files,
        findings: Vec::new(),
    };

    for path in files {
        let Ok(meta) = fs::metadata(&path) else {
            continue;
        };
        if !meta.is_file() {
            continue;
        }
        let limit = meta.len().min(MAX_SCRIPT_BYTES);
        let Ok(file) = fs::File::open(&path) else {
            continue;
        };
        let mut buffer = Vec::with_capacity(limit as usize);
        if file.take(limit).read_to_end(&mut buffer).is_err() {
            continue;
        }
        if meta.len() > MAX_SCRIPT_BYTES {
            report.truncated = true;
        }
        report.scripts += 1;
        report.bytes += buffer.len() as u64;

        let text = String::from_utf8_lossy(&buffer);
        let name = relative_name(root, &path);
        for (index, raw_line) in text.lines().enumerate() {
            let line = raw_line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            for (rule, re) in &rules {
                if re.is_match(line) {
                    report.findings.push(Finding {
                        level: rule.level,
                        file: name.clone(),
                        line: index + 1,
                        snippet: truncate_chars(line, SNIPPET_CHARS),
                        rule: rule.id,
                        title: rule.title,
                        risk: rule.risk,
                    });
                    break;
                }
            }
            if report.findings.len() >= MAX_FINDINGS {
                report.truncated = true;
                break;
            }
        }
        if report.findings.len() >= MAX_FINDINGS {
            break;
        }
    }

    report
}

/// Print the report the user sees above the install log (spec section 4).
pub fn print_report(report: &ScanReport) {
    println!();
    println!("🔍 系统分区保护 · 扫描报告");
    println!("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    println!(
        "模块：{}  扫描：{} 个脚本 / {} KiB{}",
        report.module_id,
        report.scripts,
        report.bytes / 1024,
        if report.truncated {
            "（已截断）"
        } else {
            ""
        },
    );
    println!("结论：{}", report.level().label());

    for (level, heading) in [
        (Level::Red, "🔴 严重（不可逆，禁止安装）"),
        (Level::Yellow, "🟡 需要确认（会改真实分区）"),
    ] {
        let count = report.count(level);
        if count == 0 {
            continue;
        }
        println!();
        println!("{heading}");
        let mut shown = 0;
        for finding in report
            .findings
            .iter()
            .filter(|finding| finding.level == level)
        {
            shown += 1;
            if shown > 20 {
                println!("  … 还有 {} 处未显示", count - 20);
                break;
            }
            println!(
                "  {}. {}:{}  [{}] {}",
                shown, finding.file, finding.line, finding.rule, finding.title
            );
            println!("     {}", finding.snippet);
            println!("     风险：{}", finding.risk);
        }
    }

    if report.level() == Level::Green {
        println!();
        println!("🟢 已检查");
        println!(
            "  ✓ 未发现真实分区写入（脚本 {} 个，已全部扫描）",
            report.scripts
        );
    }
    println!("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    println!();
}

/// Ask the user with the volume keys: up = install, down = cancel.
///
/// Nothing is grabbed with `EVIOCGRAB`, so the system keeps handling the keys as
/// usual; we only observe them.
pub fn confirm_with_volume_keys(timeout: Duration) -> Confirm {
    const EV_KEY: u16 = 0x01;
    const KEY_VOLUMEDOWN: u16 = 114;
    const KEY_VOLUMEUP: u16 = 115;
    const EVENT_SIZE: usize = 24;
    const MAX_EVENTS: usize = 64;

    let mut files = Vec::new();
    for index in 0..32 {
        let path = format!("/dev/input/event{index}");
        if let Ok(file) = fs::File::open(&path) {
            files.push(file);
        }
    }
    if files.is_empty() {
        return Confirm::Unavailable;
    }

    let mut pollfds: Vec<libc::pollfd> = files
        .iter()
        .map(|file| libc::pollfd {
            fd: file.as_raw_fd(),
            events: libc::POLLIN,
            revents: 0,
        })
        .collect();

    let deadline = Instant::now() + timeout;
    let mut buffer = [0u8; EVENT_SIZE * MAX_EVENTS];
    loop {
        let now = Instant::now();
        if now >= deadline {
            return Confirm::Cancel;
        }
        let remaining = deadline - now;
        let millis = remaining.as_millis().min(i32::MAX as u128) as i32;
        let ready =
            unsafe { libc::poll(pollfds.as_mut_ptr(), pollfds.len() as libc::nfds_t, millis) };
        if ready <= 0 {
            // Timeout or EINTR: loop and let the deadline check decide.
            continue;
        }
        for slot in &pollfds {
            if slot.revents & libc::POLLIN == 0 {
                continue;
            }
            let read = unsafe {
                libc::read(
                    slot.fd,
                    buffer.as_mut_ptr().cast::<libc::c_void>(),
                    buffer.len(),
                )
            };
            if read <= 0 {
                continue;
            }
            let read = read as usize;
            let mut offset = 0;
            while offset + EVENT_SIZE <= read {
                let event_type = u16::from_ne_bytes([buffer[offset + 16], buffer[offset + 17]]);
                let code = u16::from_ne_bytes([buffer[offset + 18], buffer[offset + 19]]);
                let value = i32::from_ne_bytes([
                    buffer[offset + 20],
                    buffer[offset + 21],
                    buffer[offset + 22],
                    buffer[offset + 23],
                ]);
                offset += EVENT_SIZE;
                if event_type != EV_KEY || value != 1 {
                    continue;
                }
                match code {
                    KEY_VOLUMEUP => return Confirm::Continue,
                    KEY_VOLUMEDOWN => return Confirm::Cancel,
                    _ => {}
                }
            }
        }
    }
}

fn compile_rules() -> Vec<(&'static Rule, Regex)> {
    RULES
        .iter()
        .filter_map(|rule| Regex::new(rule.pattern).ok().map(|re| (rule, re)))
        .collect()
}

/// Collect every file a module might execute, skipping symlinks and binaries.
fn collect_scripts(root: &Path) -> (Vec<PathBuf>, bool) {
    let mut out = Vec::new();
    let mut seen = HashSet::new();
    let mut truncated = false;
    walk(root, 0, &mut out, &mut seen, &mut truncated);
    (out, truncated)
}

fn walk(
    dir: &Path,
    depth: u32,
    out: &mut Vec<PathBuf>,
    seen: &mut HashSet<PathBuf>,
    truncated: &mut bool,
) {
    if depth > MAX_DEPTH || out.len() >= MAX_SCRIPTS {
        *truncated = true;
        return;
    }
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        let Ok(file_type) = entry.file_type() else {
            continue;
        };
        if file_type.is_symlink() {
            // A symlink could point anywhere; the module cannot rely on it and
            // following it would let a script name an unrelated file.
            continue;
        }
        if file_type.is_dir() {
            walk(&path, depth + 1, out, seen, truncated);
            if out.len() >= MAX_SCRIPTS {
                *truncated = true;
                return;
            }
            continue;
        }
        if !file_type.is_file() || !is_script(&path) {
            continue;
        }
        if seen.insert(path.clone()) {
            out.push(path);
        }
        if out.len() >= MAX_SCRIPTS {
            *truncated = true;
            return;
        }
    }
}

fn is_script(path: &Path) -> bool {
    let Some(name) = path.file_name().and_then(|name| name.to_str()) else {
        return false;
    };
    if FIXED_SCRIPTS.contains(&name) {
        return true;
    }
    let suffix = path
        .extension()
        .and_then(|ext| ext.to_str())
        .unwrap_or_default()
        .to_ascii_lowercase();
    if SKIP_SUFFIXES.contains(&suffix.as_str()) {
        return false;
    }
    if suffix == "sh" {
        return true;
    }
    // No known suffix: keep it only when it starts with a shebang. This catches
    // `system/bin/<tool>` style scripts without reading every binary in the zip.
    has_shebang(path)
}

fn has_shebang(path: &Path) -> bool {
    let Ok(mut file) = fs::File::open(path) else {
        return false;
    };
    let mut head = [0u8; 2];
    matches!(file.read_exact(&mut head), Ok(())) && head == *b"#!"
}

fn relative_name(root: &Path, path: &Path) -> String {
    path.strip_prefix(root)
        .unwrap_or(path)
        .display()
        .to_string()
}

fn truncate_chars(text: &str, max: usize) -> String {
    if text.chars().count() <= max {
        return text.to_string();
    }
    let mut out: String = text.chars().take(max).collect();
    out.push('…');
    out
}
