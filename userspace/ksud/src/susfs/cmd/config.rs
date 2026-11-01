//! 整份配置的应用与导出。
//!
//! 以前管理器一条规则一次 `ksud susfs add-…`：导入 60 条就是 60 次 su 往返，中途失败也
//! 不知道哪些落了地。这里把整份文档收进 ksud 内部执行，并把**上次应用的文档**记进
//! `/data/adb/ksu/susfs-applied.json`：
//!
//! - `apply` 一次调用完成全部下发，返回 applied / failed / skipped / pending_reboot；
//! - `export` 直接把上次的文档吐出来，不需要管理器再拼一遍；
//! - 配置里被删掉的规则没法从内核撤回（SuSFS 只有 ADD 类命令），只能如实列进
//!   `pending_reboot` —— 重启后不再重放，即自然失效。
use std::collections::{BTreeMap, HashSet};
use std::path::Path;

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

use crate::susfs::{
    add_open_redirect, add_sus_map, add_sus_path, add_sus_path_loop, enable_avc_log_spoofing,
    enable_log, hide_sus_mnts_for_non_su_procs, set_cmdline_or_bootconfig, set_uname,
    update_sus_kstat,
};

/// 上次应用成功（或部分成功）的配置落盘位置。
const STATE_PATH: &str = "/data/adb/ksu/susfs-applied.json";

fn default_version() -> u32 {
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
            Rule::Path { path } => format!("path:{path}"),
            Rule::Loop { path } => format!("loop:{path}"),
            Rule::Map { path } => format!("map:{path}"),
            Rule::Kstat { path } => format!("kstat:{path}"),
            Rule::Redirect {
                target,
                redirected,
                uid_scheme,
            } => format!("redirect:{target}:{redirected}:{uid_scheme}"),
            Rule::Uname { .. } => "uname".to_string(),
            Rule::Cmdline { .. } => "cmdline".to_string(),
        }
    }

    fn apply(&self) -> Result<()> {
        match self {
            Rule::Path { path } => add_sus_path(path),
            Rule::Loop { path } => add_sus_path_loop(path),
            Rule::Map { path } => add_sus_map(path),
            // 只有 update 可以安全重放：add 是"开始跟踪"，必须紧挨着 bind mount。
            Rule::Kstat { path } => update_sus_kstat(path),
            Rule::Redirect {
                target,
                redirected,
                uid_scheme,
            } => add_open_redirect(target, redirected, *uid_scheme),
            Rule::Uname { release, version } => set_uname(release, version),
            Rule::Cmdline { path } => set_cmdline_or_bootconfig(path),
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

/// 应用一份配置文档，返回报告 JSON。
pub fn apply_document(text: &str) -> Result<String> {
    let doc: ConfigDoc = serde_json::from_str(text).context("解析 SuSFS 配置失败")?;
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
                report.failed.insert(format!("switch:{key}"), error.to_string());
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

/// 导出上次应用的配置。没有记录时给一份空文档，而不是报错。
pub fn export_document() -> Result<String> {
    match read_state() {
        Some(doc) => serde_json::to_string_pretty(&doc).context("序列化配置失败"),
        None => serde_json::to_string_pretty(&ConfigDoc::default()).context("序列化配置失败"),
    }
}

fn is_duplicate(message: &str) -> bool {
    let lower = message.to_lowercase();
    ["exist", "already", "duplicate", "eexist"]
        .iter()
        .any(|needle| lower.contains(needle))
}

fn read_state() -> Option<ConfigDoc> {
    let text = std::fs::read_to_string(Path::new(STATE_PATH)).ok()?;
    serde_json::from_str(&text).ok()
}

fn write_state(doc: &ConfigDoc) -> Result<()> {
    if let Some(parent) = Path::new(STATE_PATH).parent() {
        std::fs::create_dir_all(parent).context("创建 ksu 工作目录失败")?;
    }
    let text = serde_json::to_string_pretty(doc).context("序列化配置失败")?;
    std::fs::write(STATE_PATH, text).context("写入 SuSFS 配置状态失败")
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
        assert_eq!(Rule::Uname { release: "r".into(), version: String::new() }.key(), "uname");
    }

    #[test]
    fn parses_manager_document() {
        let text = r#"{
            "version": 1,
            "enabled": {"log": true},
            "rules": [
                {"type": "path", "path": "/system/app/X"},
                {"type": "redirect", "target": "/a", "redirected": "/b", "uidScheme": 2},
                {"type": "uname", "release": "6.6.0", "version": "#1"}
            ]
        }"#;
        let doc: ConfigDoc = serde_json::from_str(text).unwrap();
        assert_eq!(doc.rules.len(), 3);
        assert_eq!(doc.rules[1].key(), "redirect:/a:/b:2");
    }

    #[test]
    fn unknown_type_is_rejected() {
        // 未知类型要报错而不是静默丢弃：多半是管理器比 ksud 新，让用户去升级。
        let text = r#"{"rules":[{"type":"mystery","path":"/a"}]}"#;
        assert!(serde_json::from_str::<ConfigDoc>(text).is_err());
    }
}
