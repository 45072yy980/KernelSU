**English** | [Español](README_ES.md) | [简体中文](README_CN.md) | [繁體中文](README_TW.md) | [日本語](README_JP.md) | [한국어](README_KR.md) | [Polski](README_PL.md) | [Português (Brasil)](README_PT-BR.md) | [Türkçe](README_TR.md) | [Русский](README_RU.md) | [Tiếng Việt](README_VI.md) | [Indonesia](README_ID.md) | [עברית](README_IW.md) | [हिंदी](README_IN.md) | [Italiano](README_IT.md)

# DikSU

<img src="https://kernelsu.org/logo.png" style="width: 96px;" alt="logo">

**DikSU** is a kernel-based root solution for Android (a customized fork of KernelSU) that
ships a built-in **Jailbreak (Magica) mode**: on devices that are **locked (no unlocked
bootloader) and must not touch any partition**, it late-loads KernelSU as a kernel module
into the running system using already-obtained temporary privileges.

[![Latest release](https://img.shields.io/github/v/release/45072yy980/KernelSU?label=Release&logo=github)](https://github.com/45072yy980/KernelSU/releases/latest)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/KernelSU)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![GitHub License](https://img.shields.io/github/license/tiann/KernelSU?logo=gnu)](/LICENSE)

## Features

### Core (inherited from KernelSU)

- Kernel-based `su` and root access management.
- Module system based on [metamodules](https://kernelsu.org/guide/metamodule.html): a pluggable architecture supporting OverlayFS and friends.
- [App Profile](https://kernelsu.org/guide/app-profile.html): lock up the root power in a cage.

### 🌟 Jailbreak (Magica) mode — the flagship feature

For devices that are **locked** but where temporary privileges were obtained via a
vulnerability (e.g. `adb root`):

| Aspect | Detail |
|---|---|
| **Never touches a partition** | No `boot` flashing, no `system`/`vendor`/`product` writes; everything lives in `/data` |
| **No bootloader unlock** | For users who can't / won't unlock the bootloader |
| **In-memory late load** | `late-load` injects `kernelsu.ko` into the running kernel straight from memory |
| **Reverts on reboot** | No persistent writes; a reboot returns the device to stock, leaving no trace |

Jailbreak mode requires **SELinux to be Permissive** and some form of temporary root
(typically `adb root`).

```text
Manager UI ──▶ AppZygotePreload (JNI) ──▶ ksud late-load --magica <port>
                                              │
                                              ├─ 1. enable_adb_root(port)
                                              ├─ 2. connect back over adb
                                              ├─ 3. load kernelsu.ko from memory
                                              ├─ 4. run post-fs-data / metamodule stages
                                              └─ 5. service / boot-completed cleanup
```

### 🚀 One-tap jailbreak (built-in exploit)

When the device has **no root at all yet** (kernel not loaded), tapping the
"not installed" card on the home screen offers two choices:

- **Jailbreak (exploit)** — run the bundled [GhostLock](https://github.com/45072yy980/ghostlock-app)
  (CVE-2026-43499) exploit to gain uid 0, then automatically call this Manager's
  own `ksud late-load` to enter jailbreak mode on the spot. **No partition is touched.**
- **Manual install** — the ordinary installation flow.

The exploit ships inside the APK as `libghostlock.so` (arm64-v8a only) and matches
its kernel offset table against `uname -r`; unsupported kernels are refused. It
follows the same never-touch-a-partition rule.

### 🛡️ Jailbreak Partition Guard

Jailbreak mode runs from memory, so it **cannot revert a partition that was really written
the way a reboot would**. Therefore, while in jailbreak mode, DikSU automatically protects
module installation:

- **Blocked** — script commands that actually reach a block device / real partition:
  `dd ... of=/dev/...`, `> /dev/block/...`, `tee /dev/block/...`,
  `mkfs`/`mke2fs`/`tune2fs`/`e2fsck`/`resize2fs`/`nandwrite`/`sgdisk`/`parted` against `/dev/...`,
  `mount -o remount,rw /dev/...`, `blockdev --setrw /dev/...`, `fastboot flash`, and so on.
- **Allowed** — ordinary modules that merely ship `system/` trees (that is a **temporary**
  OverlayFS overlay, reverted on reboot), `/sys` writes, `/data` writes, bind mounts,
  comments mentioning "flash", etc.

> ℹ️ The guard is **active only in jailbreak mode**; a normally booted KernelSU is untouched.
> To force an install anyway, use `ksud module install --force <zip>`.

### Other customizations

- **Manager renamed to DikSU**, package name `me.diksu.kernelsu`, with a dedicated signing chain that strictly matches the certificate hash embedded in the kernel.
- **Miuix theme**: switch freely between Material and Miuix UI styles.

## Compatibility state

DikSU officially supports GKI 2.0 devices (kernel 5.10+). Older kernels (4.14+) are also
supported, but the kernel will need to be built manually.

WSA, ChromeOS, and container-based Android are all supported.

Currently, `arm64-v8a` and `x86_64` are supported.

**Extra requirements for jailbreak mode**: SELinux Permissive plus obtainable temporary root
(e.g. `adb root`).

> [!CAUTION]
> Recent kernel versions have implemented a breaking change causing KernelSU to fail and potentially trigger a kernel panic on `x86_64`! Check the website for more info!

## Usage

- [Installation](https://kernelsu.org/guide/installation.html) (DikSU supports both the LKM and jailbreak paths)
- [How to build](https://kernelsu.org/guide/how-to-build.html)
- [Official website](https://kernelsu.org/)
- **Jailbreak mode**: when SELinux is Permissive, the Manager home shows a "Jailbreak" entry — follow the on-screen prompt.

## Differences from upstream KernelSU

1. **Package / name**: `me.diksu.kernelsu`, display name `DikSU`.
2. **Jailbreak (Magica) mode**: full kernel late-load chain (Manager → JNI → ksud → kernel).
3. **Jailbreak guard**: blocks modules that really write partitions, protecting a device that cannot be reverted.
4. **Built-in exploit**: with no root yet, the "not installed" card can escalate and enter jailbreak (GhostLock / CVE-2026-43499).
4. **Signing chain**: a dedicated key and certificate hash, strictly matched between kernel and Manager.
5. **UI**: Miuix / Material dual themes, with several interface refinements.

## Translation

Chinese (Simplified / Traditional) is the primary maintained language. New language support
PRs are welcome.

## Discussion

- Telegram: [@KernelSU](https://t.me/KernelSU)

## Security

For information on reporting security vulnerabilities, see [SECURITY.md](/SECURITY.md).

## License

- Files under the `kernel` directory are [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- All other parts except the `kernel` directory are [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html).

## Credits

- [KernelSU](https://github.com/tiann/KernelSU): the upstream project this fork is based on.
- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/): The KernelSU idea.
- [Magisk](https://github.com/topjohnwu/Magisk): The powerful root tool.
- [genuine](https://github.com/brevent/genuine/): apk v2 signature verification.
- [Diamorphine](https://github.com/m0nad/Diamorphine): some rootkit tricks.