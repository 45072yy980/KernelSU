#![allow(dead_code)]
// ---------------------------------------------------------------------------
// Paths the kernel module loader and the WebUI both need.
//
// These used to live only inside the android-only `android` module, but the
// WebUI is built for every target (the ksud-extra cross builds produce the
// desktop binaries too), and it reads WORKING_DIR and friends unconditionally.
// On a non-Android host the tree is rooted at /data/adb/ksu as well, which is
// harmless: those builds only ever drive the WebUI against a device.
// ---------------------------------------------------------------------------

#[cfg(not(target_os = "android"))]
mod non_android {
    pub const ADB_DIR: &str = "/data/adb/";
    pub const WORKING_DIR: &str = "/data/adb/ksu/";
    pub const BINARY_DIR: &str = "/data/adb/ksu/bin/";
    pub const LIBRARY_DIR: &str = "/data/adb/ksu/lib/";
    pub const LOG_DIR: &str = "/data/adb/ksu/log/";
    pub const WEBUI_PORT_PATH: &str = "/data/adb/ksu/webui.port";
    pub const WEBUI_TOKEN_PATH: &str = "/data/adb/ksu/webui.token";
    pub const MODULE_DIR: &str = "/data/adb/modules/";
    pub const MODULE_UPDATE_DIR: &str = "/data/adb/modules_update/";
    pub const PROFILE_DIR: &str = "/data/adb/ksu/profile/";
}
#[cfg(not(target_os = "android"))]
pub use non_android::*;

/// 越狱模式下传给 kernelsu.ko 的模块黑名单（block_modules= 参数）。
/// 逗号或空白分隔；boot 补丁模式走 ksu_config，不需要它。
pub const BLACKLIST_PATH: &str = "/data/adb/ksu/block_modules";

/// The panel's three own state files.
pub const WEBUI_JUMPS_PATH: &str = "/data/adb/ksu/webui_jumps.json";
pub const WEBUI_QUICK_RUN_PATH: &str = "/data/adb/ksu/webui_quickrun.json";
pub const WEBUI_THEME_PATH: &str = "/data/adb/ksu/webui_theme.json";

/// The `sh` to hand a child.
pub const SHELL_PATH: &str = if cfg!(target_os = "android") {
    "/system/bin/sh"
} else {
    "sh"
};

#[cfg(target_os = "android")]
mod android {
    use const_format::concatcp;
    use std::time::Duration;

    pub const BOOT_STAGE_TIMEOUT: Duration = Duration::from_secs(35);
    pub const HOT_INSTALL_SCRIPT_TIMEOUT: Duration = Duration::from_secs(30);
    pub const EMULATED_SOFT_REBOOT_TIMEOUT: Duration = Duration::from_secs(5);
    pub const WAITSYS_READY_TIMEOUT: Duration = Duration::from_secs(2);
    pub const WAITSYS_STOP_TIMEOUT: Duration = Duration::from_secs(5);
    pub const BOOTLOG_TIMEOUT: &str = "30s";

    pub const ADB_DIR: &str = "/data/adb/";
    pub const WORKING_DIR: &str = concatcp!(ADB_DIR, "ksu/");
    pub const BINARY_DIR: &str = concatcp!(WORKING_DIR, "bin/");
    pub const LIBRARY_DIR: &str = concatcp!(WORKING_DIR, "lib/");
    pub const LOG_DIR: &str = concatcp!(WORKING_DIR, "log/");
    pub const SULOGD_LOCK_PATH: &str = concatcp!(WORKING_DIR, "sulogd.lock");

    pub const PROFILE_DIR: &str = concatcp!(WORKING_DIR, "profile/");
    pub const PROFILE_SELINUX_DIR: &str = concatcp!(PROFILE_DIR, "selinux/");
    pub const PROFILE_TEMPLATE_DIR: &str = concatcp!(PROFILE_DIR, "templates/");

    pub const KSURC_PATH: &str = concatcp!(WORKING_DIR, ".ksurc");
    pub const WEBUI_PORT_PATH: &str = concatcp!(WORKING_DIR, "webui.port");
    pub const WEBUI_TOKEN_PATH: &str = concatcp!(WORKING_DIR, "webui.token");
    pub const DAEMON_PATH: &str = concatcp!(ADB_DIR, "ksud");
    pub const LIBADBROOT_PATH: &str = concatcp!(LIBRARY_DIR, "libadbroot.so");

    pub const DAEMON_LINK_PATH: &str = concatcp!(BINARY_DIR, "ksud");

    pub const MODULE_DIR: &str = concatcp!(ADB_DIR, "modules/");
    pub const MODULE_UPDATE_DIR: &str = concatcp!(ADB_DIR, "modules_update/");
    pub const METAMODULE_DIR: &str = concatcp!(ADB_DIR, "metamodule/");
    // Replaced module dirs are parked here until the next boot
    pub const HOT_INSTALL_TRASH_DIR: &str = concatcp!(WORKING_DIR, "hot_install_trash/");

    // Preferred first: /metadata/vold (root:root 0700) hides best and does not
    // bump /metadata's nlink; else /metadata/watchdog (like Magisk); else /metadata.
    pub const PREINIT_DIR_VOLD: &str = "/metadata/vold/ksu/";
    pub const PREINIT_DIR_WATCHDOG: &str = "/metadata/watchdog/ksu/";
    pub const PREINIT_DIR_DEFAULT: &str = "/metadata/ksu/";
    pub const METADATA_FILE_CON: &str = "u:object_r:metadata_file:s0";
    pub const VOLD_METADATA_FILE_CON: &str = "u:object_r:vold_metadata_file:s0";
    pub const MODULES_RC_FILE: &str = "modules.rc";
    pub const MODULES_RC_TMP_FILE: &str = ".modules.rc.tmp";
    // Module preinit dirs are synced to PREINIT_DIR_*/preinit/<id>/ and run by the kernel's runner
    pub const PREINIT_SUBDIR: &str = "preinit";
    pub const PREINIT_MANIFEST_FILE: &str = ".manifest";
    pub const PREINIT_BOOTCOUNT_FILE: &str = ".preinit_bootcount";
    pub const PREINIT_STATUS_PATH: &str = concatcp!(WORKING_DIR, "preinit_status");

    pub const MODULE_WEB_DIR: &str = "webroot";
    pub const MODULE_ACTION_SH: &str = "action.sh";
    pub const DISABLE_FILE_NAME: &str = "disable";
    pub const UPDATE_FILE_NAME: &str = "update";
    pub const REMOVE_FILE_NAME: &str = "remove";
    pub const MODULE_INIT_RC_DIR: &str = "initrc";
    pub const MODULE_PREINIT_DIR: &str = "preinit";
    pub const PREINIT_CONF: &str = "preinit.conf";
    // Written by installer.sh when customize.sh requests a hot install
    pub const HOT_INSTALL_MARKER: &str = ".ksu_hot_install";
    // Written by ksud while a module is being staged; a leftover marker means
    // the install was interrupted and the directory must not be promoted
    pub const INSTALLING_MARKER: &str = ".ksu-installing";

    // Module config system
    pub const MODULE_CONFIG_DIR: &str = concatcp!(WORKING_DIR, "module_configs/");
    pub const PERSIST_CONFIG_NAME: &str = "persist.config";
    pub const TEMP_CONFIG_NAME: &str = "tmp.config";

    // Metamodule support
    pub const METAMODULE_MOUNT_SCRIPT: &str = "metamount.sh";
    pub const METAMODULE_METAINSTALL_SCRIPT: &str = "metainstall.sh";
    pub const METAMODULE_METAUNINSTALL_SCRIPT: &str = "metauninstall.sh";

    pub const KSU_BACKUP_DIR: &str = WORKING_DIR;
    pub const KSU_BACKUP_FILE_PREFIX: &str = "ksu_backup_";
    pub const BACKUP_FILENAME: &str = "stock_image.sha1";
    pub const KSU_TEMP_BACKUP_DIR_NAME: &str = "boot_backup";

    pub const DEFAULT_PACKAGE_NAME: &str = env!("KSU_PACKAGE_NAME");
}

#[allow(unused)]
pub const VERSION_CODE: &str = env!("VERSION_CODE");
pub const VERSION_NAME: &str = env!("VERSION_NAME");
#[cfg(target_os = "android")]
pub const FULL_VERSION: &str = const_format::formatcp!(
    "{VERSION_NAME} (uapi: {})",
    crate::ksu_uapi::KERNEL_SU_UAPI_VERSION
);

#[cfg(target_os = "android")]
pub use android::*;
