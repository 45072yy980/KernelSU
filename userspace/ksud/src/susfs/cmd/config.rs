//! SuSFS 配置协议：存、验、应用。
//!
//! 契约照 FinaSU：配置是一份可导入导出的 JSON 文档，**先存后应用**。
//!
//! - `import`  校验 + 规范化 + 落盘，不碰内核
//! - `preview` 只校验与规范化，不落盘
//! - `apply`   把落盘的那份下发给内核，返回 applied / failed / skipped / pendingReboot
//! - `export`  吐回落盘的那份
//! - `status`  内核状态 + 配置摘要，一次调用拿全
//!
//! 于是管理器可以「改草稿 → 预览 → 保存 → 应用」分四步走，也可以每次改动后直接保存并应用；
//! 不论哪种，内核侧永远只有一份真值：`config.json`。
use std::collections::{BTreeMap, HashSet};
use std::path::Path;

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

use crate::susfs::{
    add_open_redirect, add_sus_map, add_sus_path, add_sus_path_loop, enable_avc_log_spoofing,
    enable_log, get_susfs_features, get_susfs_status, get_susfs_variant, get_susfs_version,
    hide_sus_mnts_for_non_su_procs, set_cmdline_or_bootconfig, set_uname, update_sus_kstat,
    update_sus_kstat_full_clone,
};

/// 与 FinaSU 一样，配置放在 `ksu` 工作目录下的独立子目录里。
const DIR: &str = "/data/adb/ksu/susfs";
const CONFIG: &str = "/data/adb/ksu/susfs/config.json";
/// 记录"上次真正下发成功的那份"，用来算 pendingReboot。
const STATE: &str = "/data/adb/ksu/susfs/state.json";

const MAX_RULES: usize = 256;
const MAX_BYTES: usize = 128 * 1024;

fn config_path() -> &'static Path {
    Path::new(CONFIG)
}

const fn default_version() -> u32 {
    1
}

/// 一份配置文档。字段形状与管理器侧的 `SusfsConfig` 一一对应。
#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ConfigDoc {
    #[serde(default = "default_version")]
    pub version: u32,
    #[serde(default)]
    pub enabled: BTreeMap<String, bool>,
    #[serde(default)]
    pub rules: Vec<Rule>,
}

impl Default for ConfigDoc {
    fn default() -> Self {
        Self {
            version: default_version(),
            enabled: BTreeMap::new(),
            rules: Vec::new(),
        }
    }
}

/// 一条规则。`type` 取值与管理器写出的 JSON 一致。
#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum Rule {
    Path {
        path: String,
    },
    Loop {
        path: String,
    },
    Map {
        path: String,
    },
    Kstat {
        path: String,
    },
    /// 完整克隆：把当前 stat 连同 nlink、size 一起写进伪装条目。
    #[serde(rename = "kstat_clone")]
    KstatClone {
        path: String,
    },
    Redirect {
        target: String,
        redirected: String,
        #[serde(rename = "uidScheme", default)]
        uid_scheme: u32,
    },
    Uname {
        release: String,
        #[serde(default)]
        version: String,
    },
    Cmdline {
        path: String,
    },
}

impl Rule {
    /// 与应用报告里使用的键一致（管理器侧同样以它去重）。
    pub fn key(&self) -> String {
        match self {
            Self::Path { path } => format!("path:{path}"),
            Self::Loop { path } => format!("loop:{path}"),
            Self::Map { path } => format!("map:{path}"),
            Self::Kstat { path } => format!("kstat:{path}"),
            Self::KstatClone { path } => format!("kstat_clone:{path}"),
            Self::Redirect {
                target,
                redirected,
                uid_scheme,
            } => format!("redirect:{target}:{redirected}:{uid_scheme}"),
            Self::Uname { .. } => "uname".to_string(),
            Self::Cmdline { .. } => "cmdline".to_string(),
        }
    }

    fn apply(&self) -> Result<()> {
        match self {
            Self::Path { path } => add_sus_path(path),
            Self::Loop { path } => add_sus_path_loop(path),
            Self::Map { path } => add_sus_map(path),
            // 只有 update 可以安全重放：add 是"开始跟踪"，必须紧挨着 bind mount。
            Self::Kstat { path } => update_sus_kstat(path),
            Self::KstatClone { path } => update_sus_kstat_full_clone(path),
            Self::Redirect {
                target,
                redirected,
                uid_scheme,
            } => add_open_redirect(target, redirected, *uid_scheme),
            Self::Uname { release, version } => set_uname(release, version),
            Self::Cmdline { path } => set_cmdline_or_bootconfig(path),
        }
    }
}

/// 一次应用的结果，序列化后交回管理器。
#[derive(Debug, Default, Serialize)]
pub struct ApplyReport {
    pub applied: Vec<String>,
    pub failed: BTreeMap<String, String>,
    pub skipped: BTreeMap<String, String>,
    #[serde(rename = "pendingReboot")]
    pub pending_reboot: Vec<String>,
}

/// `import` / `preview` 的返回：规范化后的文档 + 可显示的问题列表。
#[derive(Debug, Serialize)]
pub struct DocumentResponse {
    pub ok: bool,
    pub config: ConfigDoc,
    pub warnings: Vec<String>,
}

#[derive(Serialize)]
struct StatusDocument {
    loaded: bool,
    version: String,
    variant: String,
    features: Vec<String>,
    config: ConfigSummary,
}

#[derive(Serialize)]
struct ConfigSummary {
    rules: usize,
    switches: BTreeMap<String, bool>,
}

// ── 命令实现 ──────────────────────────────────────────────────────────────────

/// 一次拿全：内核状态 + 配置摘要。界面打开页面时只需要这一个调用。
pub fn status_document() -> Result<String> {
    let doc = load_config().unwrap_or_default();
    let loaded = get_susfs_status();
    let body = StatusDocument {
        loaded,
        version: if loaded {
            get_susfs_version()
        } else {
            String::new()
        },
        variant: if loaded {
            get_susfs_variant()
        } else {
            String::new()
        },
        features: split_features(&get_susfs_features()),
        config: ConfigSummary {
            rules: doc.rules.len(),
            switches: doc.enabled,
        },
    };
    serde_json::to_string(&body).context("序列化状态失败")
}

/// 校验 + 规范化 + 落盘。不碰内核。
pub fn import_document(text: &str) -> Result<String> {
    let response = normalize(text)?;
    write_config(&response.config)?;
    serde_json::to_string(&response).context("序列化导入结果失败")
}

/// 只校验与规范化，不写盘。
pub fn preview_document(text: &str) -> Result<String> {
    let response = normalize(text)?;
    serde_json::to_string(&response).context("序列化预览结果失败")
}

/// 导出落盘的配置；没有就是一份空文档。
pub fn export_document() -> Result<String> {
    std::fs::read_to_string(config_path()).map_or_else(
        |_| serde_json::to_string_pretty(&ConfigDoc::default()).context("序列化默认配置失败"),
        Ok,
    )
}

/// 把落盘的配置下发给内核，返回报告。
pub fn apply_saved() -> Result<String> {
    let doc = load_config().context("还没有保存过配置，先 import")?;
    let previous = read_state().unwrap_or_default();

    let mut report = ApplyReport::default();

    for (key, value) in &doc.enabled {
        let result = match key.as_str() {
            "log" => enable_log(*value),
            "avc_log_spoof" => enable_avc_log_spoofing(*value),
            "hide_mounts" => hide_sus_mnts_for_non_su_procs(*value),
            _ => continue,
        };
        match result {
            Ok(()) => report.applied.push(format!("switch:{key}")),
            Err(error) => {
                report
                    .failed
                    .insert(format!("switch:{key}"), error.to_string());
            }
        }
    }

    for rule in &doc.rules {
        let key = rule.key();
        match rule.apply() {
            Ok(()) => report.applied.push(key),
            Err(error) => {
                let message = error.to_string();
                // 重复添加不是故障：全量应用时每条都会再下发一次。
                if is_duplicate(&message) {
                    report.skipped.insert(key, message);
                } else {
                    report.failed.insert(key, message);
                }
            }
        }
    }

    // 上次有、这次没有的规则：内核没有删除命令，只能等重启后不再重放。
    let current: HashSet<String> = doc.rules.iter().map(Rule::key).collect();
    for old in previous.rules.iter().map(Rule::key) {
        if !current.contains(&old) {
            report.pending_reboot.push(old);
        }
    }

    write_state(&doc)?;
    serde_json::to_string(&report).context("序列化应用报告失败")
}

// ── 内部 ──────────────────────────────────────────────────────────────────────

fn normalize(text: &str) -> Result<DocumentResponse> {
    if text.len() > MAX_BYTES {
        return Err(anyhow::anyhow!("配置过大：上限 {} KiB", MAX_BYTES / 1024));
    }
    let doc: ConfigDoc = serde_json::from_str(text).context("解析 SuSFS 配置失败")?;
    let mut warnings = Vec::new();
    let mut seen = HashSet::new();
    let mut rules = Vec::with_capacity(doc.rules.len());
    for rule in doc.rules {
        if !seen.insert(rule.key()) {
            warnings.push(format!("重复规则已合并：{}", rule.key()));
            continue;
        }
        rules.push(rule);
    }
    if rules.len() > MAX_RULES {
        warnings.push(format!("规则过多，仅保留前 {MAX_RULES} 条"));
        rules.truncate(MAX_RULES);
    }
    Ok(DocumentResponse {
        ok: true,
        config: ConfigDoc {
            version: doc.version,
            enabled: doc.enabled,
            rules,
        },
        warnings,
    })
}

fn split_features(raw: &str) -> Vec<String> {
    raw.split([',', '\n'])
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToString::to_string)
        .collect()
}

fn is_duplicate(message: &str) -> bool {
    let lower = message.to_lowercase();
    ["exist", "already", "duplicate", "eexist"]
        .iter()
        .any(|needle| lower.contains(needle))
}

fn load_config() -> Option<ConfigDoc> {
    let text = std::fs::read_to_string(config_path()).ok()?;
    serde_json::from_str(&text).ok()
}

fn read_state() -> Option<ConfigDoc> {
    let text = std::fs::read_to_string(STATE).ok()?;
    serde_json::from_str(&text).ok()
}

fn write_state(doc: &ConfigDoc) -> Result<()> {
    ensure_dir()?;
    let text = serde_json::to_string_pretty(doc).context("序列化状态失败")?;
    std::fs::write(STATE, text).context("写入状态失败")
}

fn write_config(doc: &ConfigDoc) -> Result<()> {
    ensure_dir()?;
    let text = serde_json::to_string_pretty(doc).context("序列化配置失败")?;
    std::fs::write(config_path(), text).context("写入配置失败")
}

fn ensure_dir() -> Result<()> {
    std::fs::create_dir_all(DIR).context("创建 SuSFS 目录失败")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rule_keys_match_manager_format() {
        assert_eq!(Rule::Path { path: "/a".into() }.key(), "path:/a");
        assert_eq!(
            Rule::Redirect {
                target: "/a".into(),
                redirected: "/b".into(),
                uid_scheme: 0
            }
            .key(),
            "redirect:/a:/b:0"
        );
    }

    #[test]
    fn normalize_dedupes_and_warns() {
        // 用 r## 定界：JSON 里本身有 `"#`（`"version": "#1"`）。
        let text = r##"{
            "version": 1,
            "enabled": {"log": true},
            "rules": [
                {"type": "path", "path": "/a"},
                {"type": "path", "path": "/a"},
                {"type": "uname", "release": "6.6.0", "version": "#1"}
            ]
        }"##;
        let response = normalize(text).unwrap();
        assert_eq!(response.config.rules.len(), 2);
        assert_eq!(response.warnings.len(), 1);
    }

    #[test]
    fn unknown_type_is_rejected() {
        let text = r#"{"rules":[{"type":"mystery","path":"/a"}]}"#;
        assert!(serde_json::from_str::<ConfigDoc>(text).is_err());
    }
}
