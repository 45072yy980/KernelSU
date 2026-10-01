//! Built-in equivalent of the standalone `soft_restart_fix` module.
//!
//! Why this exists: after a soft reboot the process table is rebuilt from PID 1
//! upwards, so short-lived processes get small PIDs that look exactly like a
//! "device just restarted" fingerprint. Some root detectors watch for that.
//! Rolling the kernel's PID allocator forward before the restart hides it.
//!
//! Two ways to do it, tried in order:
//!
//! 1. Write `/proc/sys/kernel/ns_last_pid`. The kernel then hands out the next
//!    PID after that value. One small write, no processes spawned.
//! 2. If that path is not writable (older kernels, tightened permissions), fall
//!    back to a short `fork` loop: each child takes the next PID, so the counter
//!    walks forward on its own. The children exit at once, so the cost is a
//!    handful of forks.
//!
//! This used to ship as an installable module with a prebuilt static binary per
//! ABI. Doing it here means no module, no extra binary to keep in sync with each
//! KMI, and nothing to unpack at boot.

use std::{
    fs,
    io::Write,
    path::Path,
    time::{Duration, Instant},
};

use log::{info, warn};

const NS_LAST_PID: &str = "/proc/sys/kernel/ns_last_pid";
/// Where the allocator should sit after we are done. Well above the low,
/// "fresh boot" range but far below `/proc/sys/kernel/pid_max` (usually 32768),
/// so there is plenty of room left for real work.
const TARGET_PID: i32 = 1700;
/// Upper bound on the fork-loop fallback, so a stuck counter can never hang a
/// reboot. In practice the ns_last_pid write above succeeds, so this branch is
/// a safety net rather than the normal path.
const FORK_FALLBACK_TIMEOUT: Duration = Duration::from_secs(3);
/// Hard cap on how many children the fallback will spawn. Advancing the whole
/// way to TARGET_PID one fork at a time is never worth a long stall, so give up
/// early and leave the counter wherever it got to: a partial move still helps.
const FORK_FALLBACK_MAX_ATTEMPTS: u32 = 256;

fn read_current_last_pid() -> Option<i32> {
    fs::read_to_string(NS_LAST_PID)
        .ok()
        .and_then(|s| s.trim().parse::<i32>().ok())
}

/// Ask the kernel to award the next PID after `target` by writing ns_last_pid.
fn write_ns_last_pid(target: i32) -> bool {
    if !Path::new(NS_LAST_PID).exists() {
        return false;
    }
    let Ok(mut file) = fs::OpenOptions::new().write(true).open(NS_LAST_PID) else {
        return false;
    };
    if file.write_all(target.to_string().as_bytes()).is_err() {
        return false;
    }
    let _ = file.flush();
    // Read back: some kernels accept the write but clamp or ignore it.
    match read_current_last_pid() {
        Some(v) => v >= target,
        None => false,
    }
}

/// Fork children until the allocator hands out a PID at or past `target`, the
/// attempt cap is hit, or time runs out. Returns whether the target was reached,
/// plus how many children were spawned (for the log line).
fn fork_until_past(target: i32) -> (bool, u32) {
    let deadline = Instant::now() + FORK_FALLBACK_TIMEOUT;
    let mut last = 0;
    let mut attempts: u32 = 0;
    while attempts < FORK_FALLBACK_MAX_ATTEMPTS && Instant::now() < deadline {
        // SAFETY: the child path only calls `_exit`, which is async-signal-safe.
        let pid = unsafe { libc::fork() };
        attempts += 1;
        match pid {
            0 => {
                // Child: leave immediately, no shared state touched.
                unsafe { libc::_exit(0) };
            }
            p if p > 0 => {
                // Parent: reap the child so it does not linger as a zombie.
                let mut status = 0;
                unsafe { libc::waitpid(p, &mut status, 0) };
                last = p;
                if p >= target {
                    return (true, attempts);
                }
            }
            _ => {
                warn!("pid_reset: fork failed while advancing the PID counter");
                return (false, attempts);
            }
        }
    }
    warn!(
        "pid_reset: fork fallback stopped at pid {last} (target {target}, {attempts} forks)"
    );
    (false, attempts)
}

/// Advance the kernel's PID allocator so freshly started processes do not come
/// out with low PIDs. Best-effort: every failure path just logs and returns.
pub fn reset_pid_counter() {
    let before = read_current_last_pid();
    if let Some(cur) = before
        && cur >= TARGET_PID
    {
        info!("pid_reset: counter already at {cur}, nothing to do");
        return;
    }

    if write_ns_last_pid(TARGET_PID) {
        info!(
            "pid_reset: ns_last_pid set to {TARGET_PID} (was {})",
            before.map_or_else(|| "unknown".to_string(), |v| v.to_string())
        );
        return;
    }

    info!("pid_reset: ns_last_pid not usable, falling back to fork loop");
    let (reached, attempts) = fork_until_past(TARGET_PID);
    if reached {
        info!("pid_reset: PID counter reached {TARGET_PID} via fork ({attempts} forks)");
    } else {
        warn!(
            "pid_reset: PID counter not fully advanced ({attempts} forks); partial move kept"
        );
    }
}

/// `ksud pid-reset`: expose the routine so the Manager can trigger it directly.
///
/// Returns `Ok` even when the counter could not be moved: this is a best-effort
/// hide-the-restart step, never a reason to fail a command.
#[allow(clippy::unnecessary_wraps)]
pub fn pid_reset_command() -> anyhow::Result<()> {
    reset_pid_counter();
    Ok(())
}
