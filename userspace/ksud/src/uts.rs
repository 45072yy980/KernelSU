//! UTS 视图：按 uid 伪装 `uname(2)` 的返回值。
//!
//! 与 SuSFS 的 `set-uname` 不同：那个只能在打开 SuSFS 时用、且只有 release/version；
//! 这里是内核自带特性，六个字段都能改，还能按 uid 只对指定应用生效。

use std::ffi::c_char;

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

// uapi 的 char 数组要用 c_char 接收：bindgen 按目标平台的 char 有符号性生成类型，
// aarch64 是 [u8; 65]、x86_64 是 [i8; 65]，写死其中一种就只剩一个 ABI 能编过。
//
// 下面两个函数的 `as u8` / `as c_char` 在 aarch64 上是恒等转换，clippy 会报
// unnecessary_cast —— 但去掉就没法在 x86_64 上编译，所以只能按目标平台放行。
#[allow(clippy::unnecessary_cast)]
fn read_field(buf: &[c_char]) -> String {
    let bytes: Vec<u8> = buf
        .iter()
        .map(|value| *value as u8)
        .take_while(|value| *value != 0)
        .collect();
    String::from_utf8_lossy(&bytes).into_owned()
}

#[allow(clippy::unnecessary_cast)]
fn write_field(buf: &mut [c_char], value: &str) {
    let bytes = value.as_bytes();
    let len = bytes.len().min(NAME_LEN - 1);
    for (index, slot) in buf.iter_mut().enumerate().take(NAME_LEN) {
        *slot = if index < len {
            bytes[index] as c_char
        } else {
            0
        };
    }
}

/// 把文档转成内核结构体。
///
/// 刻意放在类型外面：`UtsConfig` 要 `derive(Deserialize)`，而 clippy 不允许在带 `unsafe`
/// 方法的类型上派生 Deserialize（`unsafe-derive`）。转换逻辑本来也不需要挂在类型上。
fn to_kernel(config: &UtsConfig) -> Result<ksu_uapi::ksu_uts_config> {
    if config.target_uids.len() > MAX_TARGETS {
        anyhow::bail!("目标应用过多：上限 {MAX_TARGETS} 个");
    }
    // SAFETY: 结构体是纯 POD，零初始化即可用作 ioctl 的 out 参数。
    let mut cmd: ksu_uapi::ksu_uts_config = unsafe { std::mem::zeroed() };
    cmd.mode = config.mode;
    cmd.field_mask = config.field_mask;
    cmd.target_count = config.target_uids.len() as u32;
    for (index, uid) in config.target_uids.iter().enumerate() {
        cmd.target_uids[index] = *uid;
    }
    write_field(&mut cmd.sysname, &config.sysname);
    write_field(&mut cmd.nodename, &config.nodename);
    write_field(&mut cmd.release, &config.release);
    write_field(&mut cmd.version, &config.version);
    write_field(&mut cmd.machine, &config.machine);
    write_field(&mut cmd.domainname, &config.domainname);
    Ok(cmd)
}

fn from_kernel(cmd: &ksu_uapi::ksu_uts_config) -> UtsConfig {
    let count = (cmd.target_count as usize).min(MAX_TARGETS);
    UtsConfig {
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

/// 读当前配置，返回 JSON。
pub fn get() -> Result<String> {
    let mut cmd: ksu_uapi::ksu_uts_config = unsafe { std::mem::zeroed() };
    ksucalls::get_uts_config(&mut cmd).context("读取 UTS 配置失败（内核可能不支持该特性）")?;
    let config = from_kernel(&cmd);
    serde_json::to_string_pretty(&config).context("序列化 UTS 配置失败")
}

/// 从 stdin 的 JSON 写配置。
pub fn set(text: &str) -> Result<String> {
    let config: UtsConfig = serde_json::from_str(text).context("解析 UTS 配置失败")?;
    let mut cmd = to_kernel(&config)?;
    ksucalls::set_uts_config(&mut cmd).context("写入 UTS 配置失败（参数不合法或内核不支持）")?;
    Ok("{\"ok\":true}".to_string())
}
