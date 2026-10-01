//! Built-in equivalent of the standalone `soft_restart_fix` module.
//!
//! Why this exists: a jailbroken device can only load the kernel module by
//! *soft* rebooting (killing zygote), and a soft reboot never resets the
//! kernel's PID counter the way a real reboot does. The counter keeps climbing
//! from wherever it was, so after a while every process carries a PID far larger
//! than the device's uptime justifies -- and "uptime says hours, PIDs say days"
//! is a fingerprint ordinary users never produce. Detectors flag it outright.
//!
//! The fix is to roll the allocator *back* to a small value just before the
//! restart, so the framework that comes back up looks like it took the low PIDs
//! a fresh boot would have handed out.
//!
//! How the rollback works, and why it is a loop:
//!
//! The obvious way is to write the desired value into
//! `/proc/sys/kernel/ns_last_pid`. That file only exists when the kernel was
//! built with `CONFIG_CHECKPOINT_RESTORE`, and plenty of production kernels are
//! not -- on those the whole sysctl is missing. The only remaining lever is the
//! allocator itself: fork a child, it consumes the next PID. Forking *forward*
//! cannot move the counter down, so we wait for it to come back around on its
//! own: the allocator hands out PIDs up to `/proc/sys/kernel/pid_max` (usually
//! 32768) and then wraps to a small value. We keep forking until we see the
//! wrap, then keep going until the freshly handed-out PID reaches `TARGET_PID`.
//!
//! That is exactly what the trusted module's helper binary does, and it is why
//! a small attempt cap is useless here: from a counter in the tens of thousands
//! the wrap alone can take ~30k forks. The budget is time, not attempts.
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
const PID_MAX: &str = "/proc/sys/kernel/pid_max";

/// Where the allocator should sit after we are done. Well above the low,
/// "fresh boot" range but far below `/proc/sys/kernel/pid_max` (usually 32768),
/// so there is plenty of room left for real work.
const TARGET_PID: i32 = 1700;

/// How long the fork loop may run before we give up and let the reboot proceed.
///
/// Matches the module's `MAX_WAIT_SEC`. Reaching the wrap from a high counter
/// genuinely takes tens of thousands of forks, so this is not a generous
/// ceiling -- it is the amount of time the job actually needs.
const FORK_LOOP_TIMEOUT: Duration = Duration::from_secs(10);

/// Fallback attempt cap, only used if `pid_max` cannot be read. One wrap plus
/// the distance to `TARGET_PID` will never exceed this on a normal kernel.
const FORK_LOOP_MAX_ATTEMPTS: u32 = 100_000;

/// Read a small non-negative integer out of a procfs file.
fn read_i32(path: &str) -> Option<i32> {
    fs::read_to_string(path)
        .ok()
        .and_then(|s| s.trim().parse::<i32>().ok())
}

fn read_pid_max() -> Option<i32> {
    read_i32(PID_MAX).filter(|v| *v > 0)
}

/// One fork, returning the PID the kernel handed the child (or `None` on
/// failure). The child leaves immediately.
///
/// The reap is non-blocking on purpose. This runs inside ksud's daemon, where
/// SIGCHLD may be ignored: a blocking `waitpid` would then never return and the
/// soft reboot would hang before it ever reached `stop`. We poll instead, and if
/// the child is not collectable within a moment we simply walk away -- init
/// adopts and reaps it anyway.
fn allocate_one_pid() -> Option<i32> {
    // SAFETY: the child path only calls `_exit`, which is async-signal-safe.
    let pid = unsafe { libc::fork() };
    match pid {
        0 => unsafe { libc::_exit(0) },
        p if p > 0 => {
            let deadline = Instant::now() + Duration::from_millis(200);
            let mut status = 0;
            loop {
                // SAFETY: WNOHANG makes this a question, never a wait.
                let reaped = unsafe { libc::waitpid(p, &mut status, libc::WNOHANG) };
                if reaped == p || reaped == -1 || Instant::now() >= deadline {
                    break;
                }
                std::thread::sleep(Duration::from_millis(2));
            }
            Some(p)
        }
        _ => None,
    }
}

/// Ask the kernel to award the next PID after `target` by writing ns_last_pid.
///
/// Only available on kernels built with `CONFIG_CHECKPOINT_RESTORE`; the caller
/// falls back to the fork loop when this returns `false`. The write is verified
/// by allocating a real PID and looking at the value, not by reading the file
/// back, because kernels differ in how they treat the written value.
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
    let Some(allocated) = allocate_one_pid() else {
        return false;
    };
    allocated >= target
}

/// Fork until the allocator wraps around and then reaches `target`.
///
/// Returns whether the counter was actually put at or past `target`, plus how
/// many children were spawned and the last PID observed (for the log line).
///
/// It is not enough for a single PID to be `>= target`: the counter may already
/// be far above `target`, in which case the *next* processes still carry the old
/// high PIDs. Only after the wrap does the allocator hand out small values
/// again, so `reached_target` is gated on having seen the wrap.
fn fork_until_wrapped_to(target: i32) -> (bool, u32, i32) {
    let deadline = Instant::now() + FORK_LOOP_TIMEOUT;
    // One full lap plus room to walk from the wrap point up to `target`.
    let max_attempts = read_pid_max()
        .and_then(|v| u32::try_from(v).ok())
        .and_then(|v| v.checked_add(4096))
        .unwrap_or(FORK_LOOP_MAX_ATTEMPTS);

    let mut prev = 0i32;
    let mut attempts: u32 = 0;
    let mut wrapped = false;

    while attempts < max_attempts && Instant::now() < deadline {
        attempts += 1;
        let Some(pid) = allocate_one_pid() else {
            warn!("pid_reset: fork failed while rolling the PID counter");
            return (false, attempts, prev);
        };
        // A PID smaller than the previous one can only mean the allocator came
        // back around.
        if pid < prev {
            wrapped = true;
        }
        if wrapped && pid >= target {
            return (true, attempts, pid);
        }
        prev = pid;
    }

    (false, attempts, prev)
}

/// Roll the kernel's PID allocator back so freshly started processes do not come
/// out with the large PIDs a long-running soft-rebooted device accumulates.
///
/// Best-effort: every failure path just logs and returns.
///
/// Runs on a worker thread with a hard deadline. The soft reboot calls this
/// right before it tears the framework down, and a hang here would leave the
/// user staring at a button that does nothing, so the reboot must never depend
/// on this finishing: if the deadline passes we abandon the thread and go on.
pub fn reset_pid_counter() {
    let (tx, rx) = std::sync::mpsc::channel();
    std::thread::spawn(move || {
        reset_pid_counter_inner();
        let _ = tx.send(());
    });
    if rx.recv_timeout(PID_RESET_DEADLINE).is_err() {
        warn!("pid_reset: gave up after {PID_RESET_DEADLINE:?}; reboot continues");
    }
}

/// Hard ceiling on the whole routine, comfortably above the loop's own timeout
/// so a normally-finishing loop is never cut short by this outer guard.
const PID_RESET_DEADLINE: Duration = Duration::from_secs(12);

fn reset_pid_counter_inner() {
    let before = read_i32(NS_LAST_PID);

    if write_ns_last_pid(TARGET_PID) {
        info!(
            "pid_reset: ns_last_pid set to {TARGET_PID} (was {})",
            before.map_or_else(|| "unknown".to_string(), |v| v.to_string())
        );
        return;
    }

    info!("pid_reset: ns_last_pid unavailable, rolling the PID counter via fork loop");
    let (reached, attempts, last) = fork_until_wrapped_to(TARGET_PID);
    if reached {
        info!(
            "pid_reset: PID counter wrapped and reached {TARGET_PID} \
             (last_pid={last}, {attempts} forks)"
        );
    } else {
        warn!(
            "pid_reset: PID counter not rolled back (last_pid={last}, \
             {attempts} forks); reboot continues with the old range"
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