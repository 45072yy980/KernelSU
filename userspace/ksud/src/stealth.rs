//! `ksud stealth` -- turn the manager's disguise on and off.
//!
//! The state lives in the kernel (see `kernel/manager/stealth.c`). With it on,
//! GET_INFO stops reporting KSU_GET_INFO_FLAG_MANAGER and
//! KSU_GET_INFO_FLAG_LATE_LOAD, so a caller cannot tell that a manager is
//! installed or that the module arrived after boot. The manager keeps its
//! privileges; only what the kernel admits to changes.
//!
//! `get` prints a bare `0` or `1` so a shell can test it directly:
//!
//!     if [ "$(ksud stealth get)" = "1" ]; then ...
//!
//! On a kernel that predates the call, every subcommand fails rather than
//! pretending the switch exists and is off.

use anyhow::{Result, bail};
use crate::ksucalls;

/// Fail early on a kernel that does not know the call.
///
/// Without this, `set` would look like it worked on an old kernel: the ioctl
/// fails, `is_stealth_enabled()` stays false, and the only symptom is that
/// nothing happened.
fn require_supported() -> Result<()> {
    if !ksucalls::is_stealth_supported() {
        bail!("stealth: this kernel does not support stealth mode");
    }
    Ok(())
}

/// Print the current state as a bare `0` or `1`.
pub fn get() -> Result<()> {
    require_supported()?;
    println!("{}", u8::from(ksucalls::is_stealth_enabled()));
    Ok(())
}

/// Turn it on or off, and report what it ended up as.
pub fn set(enabled: bool) -> Result<()> {
    require_supported()?;

    ksucalls::set_stealth(enabled)?;

    // Read back rather than trusting the request: if the kernel accepted the
    // call but did not store the state, the caller needs to hear about it.
    let now = ksucalls::is_stealth_enabled();
    println!("{}", u8::from(now));

    if now != enabled {
        bail!(
            "stealth: asked for {}, kernel reports {}",
            u8::from(enabled),
            u8::from(now)
        );
    }
    Ok(())
}

/// Flip it and report the new state.
///
/// `set` runs the support check itself, so an old kernel fails here too.
pub fn toggle() -> Result<()> {
    set(!ksucalls::is_stealth_enabled())
}