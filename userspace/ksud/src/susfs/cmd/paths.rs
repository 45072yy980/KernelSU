//! SuSFS path / map / redirect operations.

use crate::susfs::abi::consts::{
    CMD_SUSFS_ADD_OPEN_REDIRECT, CMD_SUSFS_ADD_SUS_MAP, CMD_SUSFS_ADD_SUS_PATH,
    CMD_SUSFS_ADD_SUS_PATH_LOOP, CMD_SUSFS_SET_PRIORITY_UID_FILTER, ERR_CMD_NOT_SUPPORTED,
    SUSFS_APP_STR_BUF_SIZE, SUSFS_MAX_PATHNAME,
};
use crate::susfs::abi::{
    SusfsMap, SusfsOpenRedirect, SusfsPriorityUidFilter, SusfsSusPath, send,
};
use crate::susfs::util::copy_path_into;
/// Mark `path` as a SuSFS hidden path.
pub fn add_sus_path(path: &str) -> anyhow::Result<()> {
    let mut cmd = SusfsSusPath {
        target_pathname: [0; SUSFS_MAX_PATHNAME],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    copy_path_into(&mut cmd.target_pathname, path)?;
    send(CMD_SUSFS_ADD_SUS_PATH, &mut cmd, "add_sus_path")
}
/// Like [`add_sus_path`] but also follows the path across bind mounts.
pub fn add_sus_path_loop(path: &str) -> anyhow::Result<()> {
    let mut cmd = SusfsSusPath {
        target_pathname: [0; SUSFS_MAX_PATHNAME],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    copy_path_into(&mut cmd.target_pathname, path)?;
    send(CMD_SUSFS_ADD_SUS_PATH_LOOP, &mut cmd, "add_sus_path_loop")
}
/// Add an `open()`-time redirect: `target` paths will be silently opened
/// as `redirected` for processes selected by `uid_scheme`.
pub fn add_open_redirect(target: &str, redirected: &str, uid_scheme: u32) -> anyhow::Result<()> {
    let mut cmd = SusfsOpenRedirect {
        target_pathname: [0; SUSFS_MAX_PATHNAME],
        redirected_pathname: [0; SUSFS_MAX_PATHNAME],
        uid_scheme,
        err: ERR_CMD_NOT_SUPPORTED,
    };
    copy_path_into(&mut cmd.target_pathname, target)?;
    copy_path_into(&mut cmd.redirected_pathname, redirected)?;
    send(CMD_SUSFS_ADD_OPEN_REDIRECT, &mut cmd, "add_open_redirect")
}
/// Add `path` to the SuSFS map of overlay / mount roots.
pub fn add_sus_map(path: &str) -> anyhow::Result<()> {
    let mut cmd = SusfsMap {
        target_pathname: [0; SUSFS_MAX_PATHNAME],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    copy_path_into(&mut cmd.target_pathname, path)?;
    send(CMD_SUSFS_ADD_SUS_MAP, &mut cmd, "add_sus_map")
}

/// 设置「优先按 uid 过滤」的应用名单（独立 LKM 的
/// `CMD_SUSFS_SET_PRIORITY_UID_FILTER`）。
///
/// `app_strings` 是内核直接解析的一段文本：内核只要求它非空、且缓冲区里
/// 有 NUL 终止符，具体格式（每行一个包名 / uid）由 LKM 的
/// `susfs_priority_uid_set()` 决定，所以这里原样透传、不做拆分或校验。
///
/// 内建实现没有这条命令，会回 `ERR_CMD_NOT_SUPPORTED`；`send()` 把 126
/// 当成「不支持」而不是错误，调用方按返回值判断即可。
pub fn set_priority_uid_filter(app_strings: &str) -> anyhow::Result<()> {
    let mut cmd = SusfsPriorityUidFilter {
        app_strings: [0; SUSFS_APP_STR_BUF_SIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    };
    let bytes = app_strings.as_bytes();
    // 必须留出结尾的 NUL，且首字节不能是 NUL（内核两处都会拒）。
    if bytes.is_empty() {
        anyhow::bail!("priority uid filter list is empty");
    }
    if bytes.len() >= SUSFS_APP_STR_BUF_SIZE {
        anyhow::bail!(
            "priority uid filter list too long ({} >= {})",
            bytes.len(),
            SUSFS_APP_STR_BUF_SIZE
        );
    }
    cmd.app_strings[..bytes.len()].copy_from_slice(bytes);
    send(
        CMD_SUSFS_SET_PRIORITY_UID_FILTER,
        &mut cmd,
        "set_priority_uid_filter",
    )
}
