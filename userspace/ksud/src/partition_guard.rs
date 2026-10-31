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
//!
//! Before a line is matched the scanner
//!   1. strips unquoted `#` comments (a comment must start a shell word),
//!   2. joins `\`-continued lines (the finding keeps the first line's number),
//!   3. substitutes variables that were assigned a block-device path or a
//!      dangerous command name earlier in the same file, so `D=/dev/block` +
//!      `of=$D/sda1` is caught the same way as the literal spelling.
//!
//! Truncation is always escalated: a scan that could not read everything can
//! never be reported as green, because whatever was not read might write a real
//! partition.

// The rule table is a long list of literal patterns; the pedantic doc/format
// lints add noise here without catching anything.
#![allow(clippy::all, clippy::pedantic, clippy::nursery)]

use std::collections::{HashMap, HashSet};
use std::fs::{self, File};
use std::io::{BufRead, BufReader, Read};
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
    /// 1-based line number inside that script; `0` for module-wide notes.
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
    /// Number of scripts actually opened and scanned.
    pub scripts: usize,
    /// Total bytes read.
    pub bytes: u64,
    /// True when the script-count, directory-depth, byte or finding cap was hit.
    /// A truncated scan is never reported as green.
    pub truncated: bool,
    /// True when one script exceeded [`MAX_SCRIPT_BYTES`]. It was still scanned
    /// to the end, but an abnormal size asks for confirmation.
    pub oversized: bool,
    pub findings: Vec<Finding>,
    /// Strongest level seen while scanning. Tracked separately from `findings`
    /// so that capping the list can never downgrade a red module to yellow.
    max_level: Level,
    /// How many red findings were recorded (reds bypass the yellow cap).
    red_recorded: usize,
    /// How many yellow findings were recorded (they have their own budget).
    yellow_recorded: usize,
    /// Total matches per level, counted even when the finding list is capped,
    /// so the report can say how many were hidden.
    red_total: u64,
    yellow_total: u64,
}

impl ScanReport {
    /// Strongest level seen during the scan; `Green` when nothing matched.
    pub fn level(&self) -> Level {
        self.max_level
    }

    fn total(&self, level: Level) -> u64 {
        match level {
            Level::Red => self.red_total,
            Level::Yellow => self.yellow_total,
            Level::Green => 0,
        }
    }

    /// Remember one rule match, honouring the per-level finding budget.
    fn record(&mut self, rule: &Rule, file: &str, line: usize, text: &str) {
        self.max_level = self.max_level.max(rule.level);
        let record = match rule.level {
            Level::Red => {
                self.red_total += 1;
                self.red_recorded < MAX_FINDINGS
            }
            _ => {
                self.yellow_total += 1;
                self.yellow_recorded < MAX_FINDINGS
            }
        };
        if !record {
            self.truncated = true;
            return;
        }
        match rule.level {
            Level::Red => self.red_recorded += 1,
            _ => self.yellow_recorded += 1,
        }
        self.findings.push(Finding {
            level: rule.level,
            file: file.to_string(),
            line,
            snippet: truncate_chars(text, SNIPPET_CHARS),
            rule: rule.id,
            title: rule.title,
            risk: rule.risk,
        });
    }

    /// Add a module-wide note (no source line), used for scan-scope problems
    /// such as a truncated scan or a symlink pointing outside the module.
    fn note(
        &mut self,
        level: Level,
        rule: &'static str,
        title: &'static str,
        risk: &'static str,
        file: &str,
        snippet: &str,
    ) {
        self.max_level = self.max_level.max(level);
        match level {
            Level::Red => self.red_total += 1,
            Level::Yellow => self.yellow_total += 1,
            Level::Green => {}
        }
        self.findings.push(Finding {
            level,
            file: file.to_string(),
            line: 0,
            snippet: snippet.to_string(),
            rule,
            title,
            risk,
        });
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

/// Write targets only: `of=` and `>` redirection (and `tee` below). `if=` is
/// deliberately absent, so reading a boot partition for a backup is not red.
const RED_BOOT: Rule = Rule {
    level: Level::Red,
    id: "R4",
    title: "写入引导分区",
    risk: "写 boot / init_boot / vendor_boot / dtbo / vbmeta / super，会破坏引导链，设备可能无法开机",
    pattern: r#"(\bof=|\>)\s*["']?/dev/block/[a-z0-9_./-]*by-name/[a-z0-9_./-]*(boot|init_boot|vendor_boot|dtbo|vbmeta|super)\b"#,
};

/// Any `of=` that points at a block device. This also catches a write whose
/// command name sits in a variable (`C=dd; $C ... of=/dev/block/sda1`).
const RED_RAW_WRITE: Rule = Rule {
    level: Level::Red,
    id: "R1",
    title: "直接写块设备",
    risk: "把数据写进分区原始扇区，文件系统与校验值立刻损坏，重启也修不回来，只能重刷整机",
    pattern: r#"\bof=\s*["']?/dev/block/"#,
};

const RED_REDIRECT: Rule = Rule {
    level: Level::Red,
    id: "R1",
    title: "重定向写入块设备",
    risk: "把数据写进分区原始扇区，文件系统与校验值立刻损坏，重启也修不回来，只能重刷整机",
    pattern: r#">>?\s*["']?/dev/block/"#,
};

/// `tee` writes every plain argument, but `< /dev/block/...` is an input
/// redirect, so the pattern stops at `<`.
const RED_TEE: Rule = Rule {
    level: Level::Red,
    id: "R1",
    title: "tee 写入块设备",
    risk: "把数据写进分区原始扇区，文件系统与校验值立刻损坏，重启也修不回来，只能重刷整机",
    pattern: r"\btee\b[^;|<]*/dev/block/",
};

/// `cp`/`mv`/`install` only write their **last** argument. Counting the source
/// too (`cp /dev/block/sda1 /data/backup`) would block every read-only backup.
const RED_COPY: Rule = Rule {
    level: Level::Red,
    id: "R1",
    title: "复制文件到块设备",
    risk: "把文件内容灌进分区原始扇区，分区数据会被破坏且无法回滚",
    pattern: r#"\b(cp|mv|install)\b[^;|]*\s["']?/dev/block/[^\s"';|]*["']?\s*(\d?>>?\s*\S+\s*)?($|[;&|])"#,
};

const RED_FORMAT: Rule = Rule {
    level: Level::Red,
    id: "R2",
    title: "格式化或抹除分区",
    risk: "抹掉分区元数据，整分区数据不可恢复",
    pattern: r"\b(mkfs(\.[a-z0-9]+)?|mke2fs|wipefs|blkdiscard|sgdisk|parted|sfdisk)\b[^;|]*/dev/",
};

/// `e2fsck -y` on a device is destructive: it "repairs" whatever it finds.
const RED_FSCK: Rule = Rule {
    level: Level::Red,
    id: "R2",
    title: "强行修复或抹除分区",
    risk: "e2fsck -y / mkswap / tune2fs 会直接改写分区元数据，整分区数据可能不可恢复",
    pattern: r"\b(e2fsck|fsck(\.[a-z0-9]+)?|mkswap|tune2fs)\b[^;|]*(-y|--yes)\b[^;|]*/dev/",
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
    pattern: r"\bavbctl\b[^;|]*\b(disable|enable|set|erase|write|flash|resize)\b|\bdisable-verity\b|\bset-avb\b",
};

const RED_DMVERITY: Rule = Rule {
    level: Level::Red,
    id: "R5",
    title: "关闭 dm-verity",
    risk: "破坏 verified boot；OTA 会失败，部分机型直接拒绝启动",
    pattern: r"\bdm-verity\b[^;|]*\bdisable\b",
};

/// `remount` alone keeps the current flags, so only an explicit `rw` is red.
const RED_MOUNT_RAW: Rule = Rule {
    level: Level::Red,
    id: "R6",
    title: "以可写方式挂载裸块设备",
    risk: "绕过文件系统层直接改分区，等同于把分区交给脚本任意写",
    pattern: r"\bmount\b[^;|]*\brw\b[^;|]*/dev/block/",
};

/// Same as [`RED_MOUNT_RAW`] with the option after the device
/// (`mount /dev/block/... /mnt -o rw`).
const RED_MOUNT_RAW_LATE: Rule = Rule {
    level: Level::Red,
    id: "R6",
    title: "以可写方式挂载裸块设备",
    risk: "绕过文件系统层直接改分区，等同于把分区交给脚本任意写",
    pattern: r"\bmount\b[^;|]*/dev/block/[^;|]*\brw\b",
};

/// A block-device path assigned to a variable is treated as the worst case,
/// but only in a real assignment position: `dd if=/dev/block/sda1 of=/data/x`
/// must not look like an assignment of `if`.
const RED_INDIRECT: Rule = Rule {
    level: Level::Red,
    id: "R7",
    title: "变量间接引用块设备",
    risk: "块设备路径藏在变量里，无法静态确定写入目标，按最坏情况处理",
    pattern: r#"(^|[;]|&&|\|\||\(|\{)\s*(export\s+)?[A-Za-z_][A-Za-z0-9_]*=["']?/dev/block($|[/"'\s])"#,
};

const YELLOW_MOUNT_RAW: Rule = Rule {
    level: Level::Yellow,
    id: "Y6",
    title: "挂载裸块设备",
    risk: "裸设备挂载本身只读时不能写入，但如果脚本随后重新挂成可写、或把 fd 交给别的工具，就会直接改分区",
    pattern: r"\bmount\b[^;|]*/dev/block/",
};

const YELLOW_REMOUNT_RW: Rule = Rule {
    level: Level::Yellow,
    id: "Y1",
    title: "把系统分区改成可写",
    risk: "只读系统分区被 remount 成可写；此后对它的修改会真实落到分区，重启不会还原",
    pattern: r#"\bmount\b[^;|]*\brw\b[^;|]*\s/(system|system_ext|system_dlkm|system_root|vendor|vendor_dlkm|product|odm|oem|my_[a-z0-9_]+)(/|\s|["']|$|[;&|])"#,
};

/// Options after the partition path (`mount /system -o rw`). `remount,rw` is
/// already covered by [`YELLOW_REMOUNT_RW`], and `remount` alone stays green.
const YELLOW_REMOUNT_RW_LATE: Rule = Rule {
    level: Level::Yellow,
    id: "Y1",
    title: "把系统分区改成可写",
    risk: "只读系统分区被 remount 成可写；此后对它的修改会真实落到分区，重启不会还原",
    pattern: r#"\bmount\b[^;|]*\s/(system|system_ext|system_dlkm|system_root|vendor|vendor_dlkm|product|odm|oem|my_[a-z0-9_]+)(/|\s|["']|$|[;&|])[^;|]*\brw\b"#,
};

/// Re-mounting `/` read-write touches every real partition at once.
const YELLOW_ROOT_RW: Rule = Rule {
    level: Level::Yellow,
    id: "Y1",
    title: "把根目录改成可写",
    risk: "把根挂载点重新挂成可写，之后对 /system 等路径的写入会真实落到分区，重启不还原",
    pattern: r#"\bmount\b[^;|]*\brw\b[^;|]*\s/\s*($|[;&|])"#,
};

/// `cp`/`mv`/`ln`/`install` only write their destination; reading `/system`
/// while writing `$MODPATH/system` is the normal overlay pattern.
const YELLOW_SYS_TARGET: Rule = Rule {
    level: Level::Yellow,
    id: "Y2",
    title: "修改系统分区文件",
    risk: "直接改系统分区里的文件；写坏会导致无法开机或 OTA 失败，通常只能重刷",
    pattern: r#"\b(cp|mv|ln|install)\b[^;|]*\s["']?/(system|system_ext|system_dlkm|system_root|vendor|vendor_dlkm|product|odm|oem|my_[a-z0-9_]+)(/[^\s"';|]*)?["']?\s*(\d?>>?\s*\S+\s*)?($|[;&|])"#,
};

/// These commands change whatever path they are given, so any argument counts.
/// The gap allows `|` because sed's `s|a|b|` delimiter is quoted text, not a pipe.
const YELLOW_SYS_ANY: Rule = Rule {
    level: Level::Yellow,
    id: "Y2",
    title: "修改系统分区文件",
    risk: "直接改系统分区里的文件；写坏会导致无法开机或 OTA 失败，通常只能重刷",
    pattern: r#"\b(sed\s+(-i|--in-place)|rm|chmod|chown|mkdir|touch|truncate|tee)\b[^;]*\s["']?/(system|system_ext|system_dlkm|system_root|vendor|vendor_dlkm|product|odm|oem|my_[a-z0-9_]+)(/|\s|["']|$|[;&|])"#,
};

const YELLOW_SYS_REDIRECT: Rule = Rule {
    level: Level::Yellow,
    id: "Y2",
    title: "重定向写入系统分区",
    risk: "直接改系统分区里的文件；写坏会导致无法开机或 OTA 失败，通常只能重刷",
    pattern: r#">>?\s*["']?/(system|system_ext|system_dlkm|system_root|vendor|vendor_dlkm|product|odm|oem|my_[a-z0-9_]+)(/|\s|["']|$|[;&|])"#,
};

/// `dd of=` into a file on a system partition. Without this the write is green
/// because the target is not a block device.
const YELLOW_SYS_DD: Rule = Rule {
    level: Level::Yellow,
    id: "Y2",
    title: "用 dd 写系统分区文件",
    risk: "直接改系统分区里的文件；写坏会导致无法开机或 OTA 失败，通常只能重刷",
    pattern: r#"\bdd\b[^;|]*\bof=\s*["']?/(system|system_ext|system_dlkm|system_root|vendor|vendor_dlkm|product|odm|oem|my_[a-z0-9_]+)(/|\s|["']|$|[;&|])"#,
};

/// Installing a file *as* a module-root script. The content needs no shebang
/// and no `.sh` suffix, so it would escape the scan entirely.
const YELLOW_SCRIPT_WRITE: Rule = Rule {
    level: Level::Yellow,
    id: "Y7",
    title: "安装期生成可执行脚本",
    risk: "把文件复制/改名成模块根脚本，脚本内容在扫描之后才出现，无法静态审计",
    pattern: r#"\b(cp|mv|ln|install)\b[^;|]*\s[^\s;|]*(service\.sh|post-fs-data\.sh|post-mount\.sh|boot-completed\.sh|late-load\.sh|metainstall\.sh|metamount\.sh|customize\.sh|install\.sh|uninstall\.sh)["']?\s*(\d?>>?\s*\S+\s*)?($|[;&|])"#,
};

/// Making a module-root script executable at install time is the second half
/// of the same trick.
const YELLOW_SCRIPT_CHMOD: Rule = Rule {
    level: Level::Yellow,
    id: "Y7",
    title: "安装期赋可执行权限",
    risk: "把模块根脚本改成可执行，脚本内容可能在扫描之后才生成，无法静态审计",
    pattern: r"\bchmod\b[^;|]*(\+x|[0-7]{3,4})[^;|]*\b(service\.sh|post-fs-data\.sh|post-mount\.sh|boot-completed\.sh|late-load\.sh|metainstall\.sh|metamount\.sh|customize\.sh|install\.sh|uninstall\.sh)\b",
};

/// `chmod +x` on a file inside the module: after the install it becomes an
/// executable entry point whose content was never in the scanned script list.
/// Plain `chmod 644` is a normal overlay step and stays green.
const YELLOW_CHMOD_MODULE: Rule = Rule {
    level: Level::Yellow,
    id: "Y7",
    title: "安装期赋可执行权限",
    risk: "把模块里的文件改成可执行，内容可能在扫描之后才生成，无法静态审计",
    pattern: r"\bchmod\b[^;|]*\+[a-z]*x\b[^;|]*(\$\{?MODPATH\}?/|\./)",
};

/// Redirect / heredoc writing a module-root script name (`printf … >
/// $MODPATH/service.sh`, `cat <<EOF > $MODPATH/service.sh`).
const YELLOW_SCRIPT_REDIRECT: Rule = Rule {
    level: Level::Yellow,
    id: "Y7",
    title: "安装期生成可执行脚本",
    risk: "把内容重定向成模块根脚本，脚本内容在扫描之后才出现，无法静态审计",
    pattern: r#">>?\s*["']?[^\s;|]*(service\.sh|post-fs-data\.sh|post-mount\.sh|boot-completed\.sh|late-load\.sh|metainstall\.sh|metamount\.sh|customize\.sh|install\.sh|uninstall\.sh)\b"#,
};

const YELLOW_BLOCKDEV: Rule = Rule {
    level: Level::Yellow,
    id: "Y3",
    title: "强制块设备可写",
    risk: "让只读块设备接受写入，后续写入会直接落到分区",
    pattern: r"\bblockdev\b[^;|]*--setrw",
};

/// `of=` pointing at a variable we could not resolve; `of="$1"` is a function
/// parameter and is left alone (see the `dd of="$1"` regression).
const YELLOW_DYNAMIC_OF: Rule = Rule {
    level: Level::Yellow,
    id: "Y10",
    title: "写入目标无法静态确定",
    risk: "写入目标是变量，静态无法确定落点，可能指向真实分区",
    pattern: r#"\bof=\s*["']?\$\{?[A-Za-z_]"#,
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
    pattern: r"\bprintf\b[^;|]*\\x[0-9a-fA-F]{2}",
};

const YELLOW_SUBSHELL: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "子 shell 动态执行",
    risk: "命令由子 shell 现场生成再执行，无法静态审计",
    pattern: r#"\b(ba)?sh\b[^;|]*-c\s*["']?\$\("#,
};

/// `sh -c "$VAR"` / `bash -c "$VAR"`: the command is only known at run time.
const YELLOW_SHELL_VAR: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "动态命令执行",
    risk: "命令由变量给出，静态无法审计，可能藏着任意写分区逻辑",
    pattern: r#"\b(ba)?sh\b[^;|]*-c\s*["']?\$\{?[A-Za-z_]"#,
};

/// `printf … | sh`: the command text is assembled and piped into a shell.
const YELLOW_PRINTF_PIPE: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "拼接命令后执行",
    risk: "命令由 printf 现场拼出来再执行，无法静态审计",
    pattern: r"\bprintf\b[^;|]*\|\s*(\S*/)?(ba)?sh\b",
};

/// `cat <file> | sh`: executing content that was not part of the scan.
const YELLOW_CAT_PIPE: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "执行未审计的文件",
    risk: "把安装期才生成/下载的文件内容交给 shell 执行，无法静态审计",
    pattern: r"\bcat\b[^;|]*\|\s*(\S*/)?(ba)?sh\b",
};

/// `sh /data/.../stage2.sh`: running a script that lives outside the scanned
/// module scripts. System paths are excluded to avoid flagging `sh /system/bin`.
const YELLOW_SH_FILE: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "执行模块外的脚本",
    risk: "执行的脚本不在模块内，内容没有被扫描，可能包含任意写分区逻辑",
    pattern: r#"(^|[\s;|&({])(ba)?sh\b\s+["']?/(data|tmp|sdcard|dev|mnt|cache|root)\b[^\s"';|]*"#,
};

/// `gunzip -c … | sh`, `openssl … -base64 … | sh`: the payload is decoded or
/// decompressed at install time, so its content is unknown to the scanner.
const YELLOW_DECODE_EXEC: Rule = Rule {
    level: Level::Yellow,
    id: "Y4",
    title: "解压或解码后执行",
    risk: "内容被解压/解码后再执行，无法静态审计，可能藏着任意写分区逻辑",
    pattern: r"\b(gunzip|zcat|unxz|lzma|bunzip2|openssl)\b[^;|]*\|\s*(\S*/)?(ba)?sh\b",
};

const YELLOW_NET_EXEC: Rule = Rule {
    level: Level::Yellow,
    id: "Y5",
    title: "联网取回并执行",
    risk: "实际执行的代码在安装时才下载，无法审计，可能包含任意写分区逻辑",
    pattern: r"\b(curl|wget)\b[^;|]*\|\s*(\S*/)?(ba)?sh\b",
};

const YELLOW_NET_SAVE_EXEC: Rule = Rule {
    level: Level::Yellow,
    id: "Y5",
    title: "下载后执行",
    risk: "下载脚本再执行，实际内容在安装时才确定，无法审计",
    pattern: r"\b(curl|wget)\b[^|]*[;&][^|]*\b(ba)?sh\b",
};

/// Red rules first: a line that both flashes and remounts should report the
/// stronger, more specific reason.
const RULES: &[Rule] = &[
    RED_BOOT,
    RED_AVB,
    RED_DMVERITY,
    RED_FLASH,
    RED_FORMAT,
    RED_FSCK,
    RED_MOUNT_RAW,
    RED_MOUNT_RAW_LATE,
    RED_RAW_WRITE,
    RED_REDIRECT,
    RED_TEE,
    RED_COPY,
    RED_INDIRECT,
    YELLOW_MOUNT_RAW,
    YELLOW_REMOUNT_RW,
    YELLOW_REMOUNT_RW_LATE,
    YELLOW_ROOT_RW,
    YELLOW_SYS_TARGET,
    YELLOW_SYS_ANY,
    YELLOW_SYS_REDIRECT,
    YELLOW_SYS_DD,
    YELLOW_SCRIPT_WRITE,
    YELLOW_SCRIPT_CHMOD,
    YELLOW_SCRIPT_REDIRECT,
    YELLOW_CHMOD_MODULE,
    YELLOW_BLOCKDEV,
    YELLOW_DYNAMIC_OF,
    YELLOW_EVAL,
    YELLOW_BASE64,
    YELLOW_XXD,
    YELLOW_PRINTF_HEX,
    YELLOW_SUBSHELL,
    YELLOW_SHELL_VAR,
    YELLOW_PRINTF_PIPE,
    YELLOW_CAT_PIPE,
    YELLOW_SH_FILE,
    YELLOW_DECODE_EXEC,
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

/// Command names a variable may stand for (`C=dd; $C of=/dev/block/sda1`).
const KNOWN_COMMANDS: &[&str] = &[
    "dd",
    "tee",
    "cp",
    "mv",
    "install",
    "cat",
    "mkfs",
    "mke2fs",
    "wipefs",
    "blkdiscard",
    "sgdisk",
    "parted",
    "sfdisk",
    "e2fsck",
    "mount",
    "umount",
    "blockdev",
    "sh",
    "bash",
];

/// Scripts are small in practice; 1000 files is enough for a real module while
/// still bounding the work when a zip ships tens of thousands of stubs. The old
/// 200-file cap was trivially bypassed by putting the payload in file 201.
const MAX_SCRIPTS: usize = 1000;
/// Total bytes read per scan. The old per-file 2 MiB *read* cap hid the tail of
/// a larger script; a total budget bounds the same work without creating an
/// unscanned region inside a normal script. Files larger than
/// [`MAX_SCRIPT_BYTES`] are still read to the end, but the unusual size itself
/// is reported so the user gets a confirmation.
const MAX_TOTAL_BYTES: u64 = 32 * 1024 * 1024;
/// A script this large is abnormal enough to ask the user to confirm.
const MAX_SCRIPT_BYTES: u64 = 2 * 1024 * 1024;
const MAX_FINDINGS: usize = 200;
const MAX_DEPTH: u32 = 8;
/// Snippet length, in characters, shown in the report.
const SNIPPET_CHARS: usize = 80;
/// Time the volume-key prompt waits before treating silence as "cancel".
pub const CONFIRM_TIMEOUT: Duration = Duration::from_secs(60);

/// Scan every script a module would run and report what each one touches.
pub fn scan_module(root: &Path, module_id: &str) -> ScanReport {
    let mut report = ScanReport {
        module_id: module_id.to_string(),
        scripts: 0,
        bytes: 0,
        truncated: false,
        oversized: false,
        findings: Vec::new(),
        max_level: Level::Green,
        red_recorded: 0,
        yellow_recorded: 0,
        red_total: 0,
        yellow_total: 0,
    };

    if !root.is_dir() {
        report.note(
            Level::Yellow,
            "Y8",
            "无法扫描模块目录",
            "模块目录不存在或不可读，无法确认它不会写真实分区",
            &root.display().to_string(),
            "",
        );
        return report;
    }

    let rules = compile_rules();
    let (files, outside_links, truncated_files) = collect_scripts(root);
    report.truncated = truncated_files;
    for name in outside_links {
        report.note(
            Level::Yellow,
            "Y9",
            "符号链接指向模块外",
            "链接目标不在模块目录内，实际执行的内容没有被扫描，无法静态审计",
            &name,
            "",
        );
    }

    let mut budget = MAX_TOTAL_BYTES;
    'scripts: for path in files {
        let Ok(file) = File::open(&path) else {
            report.truncated = true;
            continue;
        };
        report.scripts += 1;
        let name = relative_name(root, &path);
        let mut reader = BufReader::new(file);
        let mut file_bytes = 0u64;
        let mut vars: HashMap<String, String> = HashMap::new();
        let mut pending: Option<(usize, String)> = None;
        let mut number = 0usize;
        let mut buffer = Vec::new();
        loop {
            buffer.clear();
            let Ok(read) = reader.read_until(b'\n', &mut buffer) else {
                report.truncated = true;
                break;
            };
            if read == 0 {
                break;
            }
            report.bytes += read as u64;
            file_bytes += read as u64;
            if read as u64 > budget {
                report.truncated = true;
                break 'scripts;
            }
            budget -= read as u64;
            number += 1;
            let raw = String::from_utf8_lossy(&buffer);
            let raw = strip_comment(raw.trim_end_matches(['\n', '\r']));
            let continued = continues(raw);
            let piece = if continued {
                &raw[..raw.len() - 1]
            } else {
                raw
            };
            if let Some((start, mut logical)) = pending.take() {
                logical.push(' ');
                logical.push_str(piece);
                if continued {
                    pending = Some((start, logical));
                } else {
                    scan_text(&mut report, &rules, &name, &mut vars, start, &logical);
                }
                continue;
            }
            if continued {
                pending = Some((number, piece.to_string()));
                continue;
            }
            scan_text(&mut report, &rules, &name, &mut vars, number, piece);
        }
        if let Some((start, logical)) = pending.take() {
            scan_text(&mut report, &rules, &name, &mut vars, start, &logical);
        }
        if file_bytes > MAX_SCRIPT_BYTES {
            report.oversized = true;
        }
    }

    // A scan that could not read everything must never come out green, and an
    // abnormally large script is worth one confirmation even when fully read.
    if report.truncated && report.max_level < Level::Yellow {
        report.note(
            Level::Yellow,
            "Y8",
            "扫描范围不完整",
            "脚本数量、目录深度或读取总量超过上限，剩余内容没有被扫描，无法确认它不会写真实分区",
            "（扫描范围）",
            "",
        );
    }
    if report.oversized && report.max_level < Level::Yellow {
        report.note(
            Level::Yellow,
            "Y8",
            "脚本体积超过 2 MiB",
            "单个脚本超过 2 MiB（已完整扫描，但超常体积值得确认它不是混淆过的大脚本）",
            "（扫描范围）",
            "",
        );
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
        } else if report.oversized {
            "（含超大脚本）"
        } else {
            ""
        },
    );
    println!("结论：{}", report.level().label());

    for (level, heading) in [
        (Level::Red, "🔴 严重（不可逆，禁止安装）"),
        (Level::Yellow, "🟡 需要确认（会改真实分区）"),
    ] {
        let total = report.total(level);
        if total == 0 {
            continue;
        }
        println!();
        println!("{heading}");
        let mut shown = 0u64;
        for finding in report
            .findings
            .iter()
            .filter(|finding| finding.level == level)
        {
            if shown >= 20 {
                println!("  … 还有 {} 处未显示", total - 20);
                break;
            }
            shown += 1;
            if finding.line == 0 {
                println!("  {}. {}", shown, finding.file);
            } else {
                println!(
                    "  {}. {}:{}  [{}] {}",
                    shown, finding.file, finding.line, finding.rule, finding.title
                );
            }
            if !finding.snippet.is_empty() {
                println!("     {}", finding.snippet);
            }
            println!("     风险：{}", finding.risk);
        }
    }

    if report.level() == Level::Green {
        println!();
        println!("🟢 已检查");
        if report.scripts == 0 {
            println!("  ✓ 模块里没有可扫描的脚本");
        } else {
            println!(
                "  ✓ 未发现真实分区写入（脚本 {} 个，已全部扫描）",
                report.scripts
            );
        }
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
        if let Ok(file) = File::open(&path) {
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
        if Instant::now() >= deadline {
            return Confirm::Cancel;
        }
        let remaining = deadline.saturating_duration_since(Instant::now());
        let millis = remaining.as_millis().min(i32::MAX as u128) as i32;
        let ready =
            unsafe { libc::poll(pollfds.as_mut_ptr(), pollfds.len() as libc::nfds_t, millis) };
        if ready < 0 {
            // Interrupted by a signal: keep waiting. Anything else (EBADF,
            // EFAULT, …) would spin until the deadline, so give up instead.
            if std::io::Error::last_os_error().kind() == std::io::ErrorKind::Interrupted {
                continue;
            }
            return Confirm::Cancel;
        }
        if ready == 0 {
            // Timeout: let the deadline check above decide.
            continue;
        }

        let mut dead: Vec<usize> = Vec::new();
        for index in 0..pollfds.len() {
            let revents = pollfds[index].revents;
            if revents == 0 {
                continue;
            }
            if revents & (libc::POLLERR | libc::POLLHUP | libc::POLLNVAL) != 0 {
                dead.push(index);
                continue;
            }
            if revents & libc::POLLIN == 0 {
                continue;
            }
            let read = unsafe {
                libc::read(
                    pollfds[index].fd,
                    buffer.as_mut_ptr().cast::<libc::c_void>(),
                    buffer.len(),
                )
            };
            if read < 0 {
                if std::io::Error::last_os_error().kind() != std::io::ErrorKind::Interrupted {
                    dead.push(index);
                }
                continue;
            }
            if read == 0 {
                // Device went away; dropping it keeps poll from spinning.
                dead.push(index);
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

        if !dead.is_empty() {
            dead.sort_unstable();
            for index in dead.into_iter().rev() {
                // Remove the pollfd first so its raw fd is never polled again.
                pollfds.remove(index);
                files.remove(index);
            }
            if files.is_empty() {
                return Confirm::Unavailable;
            }
        }
    }
}

fn compile_rules() -> Vec<(&'static Rule, Regex)> {
    let rules: Vec<(&'static Rule, Regex)> = RULES
        .iter()
        .map(|rule| {
            let re = Regex::new(rule.pattern)
                .unwrap_or_else(|err| panic!("invalid partition-guard rule {}: {err}", rule.id));
            (rule, re)
        })
        .collect();
    assert_eq!(
        rules.len(),
        RULES.len(),
        "partition-guard rule table lost entries while compiling"
    );
    rules
}

/// True when `line` ends with an odd number of backslashes, i.e. the next line
/// continues the same shell command.
fn continues(line: &str) -> bool {
    let mut count = 0;
    for byte in line.bytes().rev() {
        if byte != b'\\' {
            break;
        }
        count += 1;
    }
    count % 2 == 1
}

/// Strip an unquoted `#` comment. A `#` only starts a comment at the beginning
/// of a word, so `echo foo#bar` and `'#'` are left alone.
fn strip_comment(line: &str) -> &str {
    let bytes = line.as_bytes();
    let mut quote: Option<u8> = None;
    let mut at_word_start = true;
    let mut index = 0;
    while index < bytes.len() {
        let byte = bytes[index];
        if let Some(active) = quote {
            if byte == b'\\' {
                index += 2;
                continue;
            }
            if byte == active {
                quote = None;
            }
            index += 1;
            continue;
        }
        match byte {
            b'\'' | b'"' => {
                quote = Some(byte);
                at_word_start = false;
            }
            b'\\' => {
                index += 2;
                at_word_start = false;
                continue;
            }
            b'#' if at_word_start => return &line[..index],
            _ => at_word_start = byte.is_ascii_whitespace(),
        }
        index += 1;
    }
    line
}

/// Match one logical line (comments already stripped, continuations joined).
fn scan_text(
    report: &mut ScanReport,
    rules: &[(&'static Rule, Regex)],
    name: &str,
    vars: &mut HashMap<String, String>,
    number: usize,
    text: &str,
) {
    let line = text.trim();
    if line.is_empty() || line.starts_with('#') {
        return;
    }
    // One pass so `E=$D/sda1` sees a variable assigned on an earlier line, then
    // a second pass so `D=/dev/block; dd of=$D/sda1` sees this very line.
    let expanded = expand_vars(line, vars);
    record_vars(&expanded, vars);
    let resolved = expand_vars(line, vars);
    for (rule, re) in rules {
        if re.is_match(&resolved) {
            report.record(rule, name, number, line);
            break;
        }
    }
}

/// Remember `NAME=/dev/block…` and `NAME=dd` style assignments so that later
/// `$NAME` uses can be expanded before matching. Only a word that starts a
/// statement (`;`, `&&`, `||`, `(`, `{` or the line start) can be an assignment,
/// which is what keeps `dd if=/dev/block/sda1` from looking like `if=…`.
fn record_vars(line: &str, vars: &mut HashMap<String, String>) {
    let bytes = line.as_bytes();
    let mut start = 0usize;
    let mut index = 0usize;
    while index < bytes.len() {
        let boundary = match bytes[index] {
            b';' | b'(' | b'{' | b')' | b'}' => true,
            b'&' => index + 1 < bytes.len() && bytes[index + 1] == b'&',
            b'|' => index + 1 < bytes.len() && bytes[index + 1] == b'|',
            _ => false,
        };
        if boundary {
            consider_assignment(&line[start..index], vars);
            index += if bytes[index] == b'&' || bytes[index] == b'|' {
                2
            } else {
                1
            };
            start = index;
            continue;
        }
        index += 1;
    }
    consider_assignment(&line[start..], vars);
}

fn consider_assignment(segment: &str, vars: &mut HashMap<String, String>) {
    let mut text = segment.trim_start();
    if let Some(rest) = text.strip_prefix("export ") {
        text = rest.trim_start();
    }
    let Some(eq) = text.find('=') else {
        return;
    };
    let (name, rest) = text.split_at(eq);
    if !is_var_name(name) {
        return;
    }
    let rest = &rest[1..];
    let value = if let Some(quote) = rest.chars().next().filter(|ch| *ch == '"' || *ch == '\'') {
        let Some(end) = rest[1..].find(quote) else {
            return;
        };
        &rest[1..1 + end]
    } else {
        rest.split(|ch: char| ch.is_whitespace() || matches!(ch, ';' | '&' | '|' | ')' | '}'))
            .next()
            .unwrap_or("")
    };
    if value.is_empty() || value.contains(char::is_whitespace) {
        return;
    }
    if value.starts_with("/dev/block") || KNOWN_COMMANDS.contains(&value) {
        vars.insert(name.to_string(), value.to_string());
    }
}

fn is_var_name(name: &str) -> bool {
    let mut chars = name.chars();
    let Some(first) = chars.next() else {
        return false;
    };
    if !(first.is_ascii_alphabetic() || first == '_') {
        return false;
    }
    chars.all(|ch| ch.is_ascii_alphanumeric() || ch == '_')
}

/// Replace `$NAME` / `${NAME}` with a value recorded by [`record_vars`].
fn expand_vars(line: &str, vars: &HashMap<String, String>) -> String {
    if vars.is_empty() {
        return line.to_string();
    }
    let mut out = String::with_capacity(line.len());
    let mut rest = line;
    while let Some(pos) = rest.find('$') {
        out.push_str(&rest[..pos]);
        let after = &rest[pos + 1..];
        if let Some(stripped) = after.strip_prefix('{') {
            let Some(end) = stripped.find('}') else {
                out.push('$');
                rest = &rest[pos + 1..];
                continue;
            };
            let name = &stripped[..end];
            if let Some(value) = vars.get(name) {
                out.push_str(value);
            } else {
                out.push_str(&rest[pos..pos + end + 3]);
            }
            rest = &rest[pos + end + 3..];
            continue;
        }
        let end = after
            .bytes()
            .take_while(|byte| byte.is_ascii_alphanumeric() || *byte == b'_')
            .count();
        if end == 0 {
            out.push('$');
            rest = &rest[pos + 1..];
            continue;
        }
        let name = &after[..end];
        if let Some(value) = vars.get(name) {
            out.push_str(value);
        } else {
            out.push_str(&rest[pos..pos + end + 1]);
        }
        rest = &rest[pos + end + 1..];
    }
    out.push_str(rest);
    out
}

/// Collect every file a module might execute. Symlinks are resolved one hop: a
/// `service.sh` symlink must have its target scanned, while a link escaping the
/// module is reported as un-auditable.
fn collect_scripts(root: &Path) -> (Vec<PathBuf>, Vec<String>, bool) {
    let root = fs::canonicalize(root).unwrap_or_else(|_| root.to_path_buf());
    let mut out = Vec::new();
    let mut outside = Vec::new();
    let mut seen = HashSet::new();
    let mut truncated = false;
    walk(
        &root,
        &root,
        0,
        &mut out,
        &mut outside,
        &mut seen,
        &mut truncated,
    );
    (out, outside, truncated)
}

fn walk(
    root: &Path,
    dir: &Path,
    depth: u32,
    out: &mut Vec<PathBuf>,
    outside: &mut Vec<String>,
    seen: &mut HashSet<PathBuf>,
    truncated: &mut bool,
) {
    if depth > MAX_DEPTH {
        // Only an actually populated subdirectory means something was skipped.
        if fs::read_dir(dir).is_ok_and(|mut entries| entries.next().is_some()) {
            *truncated = true;
        }
        return;
    }
    let Ok(entries) = fs::read_dir(dir) else {
        *truncated = true;
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        let Ok(file_type) = entry.file_type() else {
            *truncated = true;
            continue;
        };
        if file_type.is_symlink() {
            if !is_script_name(&path) {
                continue;
            }
            if out.len() >= MAX_SCRIPTS {
                *truncated = true;
                return;
            }
            match fs::canonicalize(&path) {
                Ok(target) if target.starts_with(root) => {
                    if target.is_file() && seen.insert(target.clone()) {
                        out.push(target);
                    }
                }
                _ => outside.push(relative_name(root, &path)),
            }
            continue;
        }
        if file_type.is_dir() {
            walk(root, &path, depth + 1, out, outside, seen, truncated);
            continue;
        }
        if !file_type.is_file() || !is_script(&path) {
            continue;
        }
        // Only a candidate that actually gets skipped means "truncated";
        // exactly MAX_SCRIPTS scripts is a complete scan.
        if out.len() >= MAX_SCRIPTS {
            *truncated = true;
            return;
        }
        if seen.insert(path.clone()) {
            out.push(path);
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

/// Name-based check used for symlinks: the link itself must look like a script
/// before its target is worth resolving.
fn is_script_name(path: &Path) -> bool {
    let Some(name) = path.file_name().and_then(|name| name.to_str()) else {
        return false;
    };
    if FIXED_SCRIPTS.contains(&name) {
        return true;
    }
    path.extension()
        .and_then(|ext| ext.to_str())
        .is_some_and(|ext| ext.eq_ignore_ascii_case("sh"))
}

fn has_shebang(path: &Path) -> bool {
    let Ok(mut file) = File::open(path) else {
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
