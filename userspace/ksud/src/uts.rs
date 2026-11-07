//! UTS 视图：按 uid 伪装 `uname(2)` 的返回值。
//!
//! 与 SuSFS 的 `set-uname` 不同：那个只能在打开 SuSFS 时用、且只有 release/version；
//! 这里是内核自带特性，六个字段都能改，还能按 uid 只对指定应用生效。

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

use crate::ksu_uapi;
use crate::ksucalls;

const NAME_LEN: usize = 65;
const MAX_TARGETS: usize = 64;

/// 配置文档。字段名与内核结构一一对应，界面直接读写这份 JSON。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct UtsConfig {
    /// 0=关闭 1=全局 2=仅目标应用
    #[serde(default)]
    pub mode: u32,
    #[serde(default)]
    pub field_mask: u32,
    #[serde(default)]
    pub target_uids: Vec<u32>,
    #[serde(default)]
    pub sysname: String,
    #[serde(default)]
    pub nodename: String,
    #[serde(default)]
    pub release: String,
    #[serde(default)]
    pub version: String,
    #[serde(default)]
    pub machine: String,
    #[serde(default)]
    pub domainname: String,
}

fn read_field(buf: &[u8]) -> String {
    let bytes: Vec<u8> = buf.iter().copied().take_while(|value| *value != 0).collect();
    String::from_utf8_lossy(&bytes).into_owned()
}

fn write_field(buf: &mut [u8], value: &str) {
    let bytes = value.as_bytes();
    let len = bytes.len().min(NAME_LEN - 1);
    for (index, slot) in buf.iter_mut().enumerate().take(NAME_LEN) {
        *slot = if index < len { bytes[index] } else { 0 };
    }
}

impl UtsConfig {
    fn to_kernel(&self) -> Result<ksu_uapi::ksu_uts_config> {
        if self.target_uids.len() > MAX_TARGETS {
            anyhow::bail!("目标应用过多：上限 {MAX_TARGETS} 个");
        }
        // SAFETY: 结构体是纯 POD，零初始化即可用作 out 参数。
        let mut cmd: ksu_uapi::ksu_uts_config = unsafe { std::mem::zeroed() };
        cmd.mode = self.mode;
        cmd.field_mask = self.field_mask;
        cmd.target_count = self.target_uids.len() as u32;
        for (index, uid) in self.target_uids.iter().enumerate() {
            cmd.target_uids[index] = *uid;
        }
        write_field(&mut cmd.sysname, &self.sysname);
        write_field(&mut cmd.nodename, &self.nodename);
        write_field(&mut cmd.release, &self.release);
        write_field(&mut cmd.version, &self.version);
        write_field(&mut cmd.machine, &self.machine);
        write_field(&mut cmd.domainname, &self.domainname);
        Ok(cmd)
    }

    fn from_kernel(cmd: &ksu_uapi::ksu_uts_config) -> Self {
        let count = (cmd.target_count as usize).min(MAX_TARGETS);
        Self {
            mode: cmd.mode,
            field_mask: cmd.field_mask,
            target_uids: cmd.target_uids[..count].to_vec(),
            sysname: read_field(&cmd.sysname),
            nodename: read_field(&cmd.nodename),
            release: read_field(&cmd.release),
            version: read_field(&cmd.version),
            machine: read_field(&cmd.machine),
            domainname: read_field(&cmd.domainname),
        }
    }
}

/// 读当前配置，返回 JSON。
pub fn get() -> Result<String> {
    let mut cmd: ksu_uapi::ksu_uts_config = unsafe { std::mem::zeroed() };
    ksucalls::get_uts_config(&mut cmd).context("读取 UTS 配置失败（内核可能不支持该特性）")?;
    let config = UtsConfig::from_kernel(&cmd);
    serde_json::to_string_pretty(&config).context("序列化 UTS 配置失败")
}

/// 从 stdin 的 JSON 写配置。
pub fn set(text: &str) -> Result<String> {
    let config: UtsConfig = serde_json::from_str(text).context("解析 UTS 配置失败")?;
    let mut cmd = config.to_kernel()?;
    ksucalls::set_uts_config(&mut cmd).context("写入 UTS 配置失败（参数不合法或内核不支持）")?;
    Ok("{\"ok\":true}".to_string())
}
