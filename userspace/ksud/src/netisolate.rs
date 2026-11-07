//! 网络隔离：按 uid 拒绝对外 `connect(2)`。
//!
//! 内核侧只拦 `AF_INET`/`AF_INET6` 的 connect，回环与内网可以按开关放行；`AF_UNIX`
//! 和 `AF_NETLINK` 永远不拦，否则应用连本地服务都连不上。`flags` 的两个位见
//! `kernel/include/uapi/netisolate.h`。

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

use crate::ksu_uapi;
use crate::ksucalls;

const MAX_TARGETS: usize = 128;

/// 配置文档：字段名与内核 `ksu_netisolate_config` 一一对应。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct NetisolateConfig {
    /// 0=关闭 1=仅目标应用
    #[serde(default)]
    pub mode: u32,
    /// 位 0 = 放行回环，位 1 = 放行私网
    #[serde(default)]
    pub flags: u32,
    #[serde(default)]
    pub target_uids: Vec<u32>,
    /// 内核输出：1 = 钩子已装上。写回时内核会忽略。
    #[serde(default)]
    pub supported: u32,
}

/// 把文档转成内核结构体（放在类型外，理由同 `uts.rs` 的 `unsafe-derive`）。
fn to_kernel(config: &NetisolateConfig) -> Result<ksu_uapi::ksu_netisolate_config> {
    if config.target_uids.len() > MAX_TARGETS {
        anyhow::bail!("目标应用过多：上限 {MAX_TARGETS} 个");
    }
    // SAFETY: 结构体是纯 POD，零初始化即可用作 ioctl 的 out 参数。
    let mut cmd: ksu_uapi::ksu_netisolate_config = unsafe { std::mem::zeroed() };
    cmd.mode = config.mode;
    cmd.flags = config.flags;
    cmd.target_count = config.target_uids.len() as u32;
    for (index, uid) in config.target_uids.iter().enumerate() {
        cmd.target_uids[index] = *uid;
    }
    Ok(cmd)
}

fn from_kernel(cmd: &ksu_uapi::ksu_netisolate_config) -> NetisolateConfig {
    let count = (cmd.target_count as usize).min(MAX_TARGETS);
    NetisolateConfig {
        mode: cmd.mode,
        flags: cmd.flags,
        target_uids: cmd.target_uids[..count].to_vec(),
        supported: cmd.supported,
    }
}

/// 读当前配置，返回 JSON。
pub fn get() -> Result<String> {
    let mut cmd: ksu_uapi::ksu_netisolate_config = unsafe { std::mem::zeroed() };
    ksucalls::get_netisolate(&mut cmd).context("读取网络隔离配置失败（内核可能不支持该特性）")?;
    let config = from_kernel(&cmd);
    serde_json::to_string_pretty(&config).context("序列化网络隔离配置失败")
}

/// 从 stdin 的 JSON 写配置。
pub fn set(text: &str) -> Result<String> {
    let config: NetisolateConfig = serde_json::from_str(text).context("解析网络隔离配置失败")?;
    let mut cmd = to_kernel(&config)?;
    ksucalls::set_netisolate(&mut cmd).context("写入网络隔离配置失败（参数不合法或内核不支持）")?;
    Ok("{\"ok\":true}".to_string())
}
