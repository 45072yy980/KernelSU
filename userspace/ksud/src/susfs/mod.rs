//! SuSFS userspace bindings.
//!
//! This module exposes a small, typed Rust API over the SuSFS kernel ABI,
//! which is reached through a single `reboot(2)` syscall with the magic
//! values `KSU_INSTALL_MAGIC1` and `SUSFS_MAGIC`.
//!
//! ## Layout
//!
//! ```text
//! susfs/
//! ├── mod.rs   ← you are here: public re-exports only
//! ├── util.rs  ← cross-command helpers (canonicalize / read_file / copy_*)
//! ├── abi/     ← SuSFS kernel ABI (consts + #[repr(C)] structs + syscall glue)
//! └── cmd/     ← one file per SuSFS command (status / spoof / paths / kstat)
//! ```
//!
//! The ABI is the same one the standalone `susfs_guard_lkm` kernel module
//! speaks, so this client works with either the built-in implementation or
//! the dynamically loaded module.
pub mod abi;
pub mod cmd;
pub mod util;
pub use cmd::kstat::*;
pub use cmd::paths::*;
pub use cmd::spoof::*;
pub use cmd::status::*;