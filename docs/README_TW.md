[English](README.md) | [Español](README_ES.md) | [简体中文](README_CN.md) | **繁體中文** | [日本語](README_JP.md) | [한국어](README_KR.md) | [Polski](README_PL.md) | [Português (Brasil)](README_PT-BR.md) | [Türkçe](README_TR.md) | [Русский](README_RU.md) | [Tiếng Việt](README_VI.md) | [Indonesia](README_ID.md) | [עברית](README_IW.md) | [हिंदी](README_IN.md) | [Italiano](README_IT.md)

# DikSU

<img src="https://kernelsu.org/logo.png" style="width: 96px;" alt="標誌">

**DikSU** 是一套面向 Android、基於核心的 root 方案（KernelSU 的客製化分支），
內建**越獄模式（Jailbreak / Magica）**：在裝置**未解鎖 Bootloader、不修改任何分區**的前提下，
藉由已取得的臨時權限，把 KernelSU 以核心模組的形式**晚期載入**進正在運行的系統。

[![最新版本](https://img.shields.io/github/v/release/45072yy980/KernelSU?label=%e7%99%bc%e8%a1%8c%e7%89%88%e6%9c%ac&logo=github)](https://github.com/45072yy980/KernelSU/releases/latest)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/KernelSU)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![GitHub License](https://img.shields.io/github/license/tiann/KernelSU?logo=gnu)](/LICENSE)

## 特性

### 核心能力（繼承自 KernelSU）

- 基於核心的 `su` 與權限管理，比使用者態方案更穩定、更隱蔽。
- 基於 [metamodules](https://kernelsu.org/zh_TW/guide/metamodule.html) 的模組系統：可插拔的模組架構，支援 OverlayFS 等主流元模組。
- [App Profile](https://kernelsu.org/zh_TW/guide/app-profile.html)：把 Root 權限關進籠子裡。

### 🌟 越獄模式（Jailbreak / Magica）—— 核心特色

面向**沒有解鎖 Bootloader**、但透過漏洞取得了臨時權限（例如 `adb root`）的裝置：

| 關鍵點 | 說明 |
|---|---|
| **全程不修改分區** | 不刷 `boot`、不動 `system`/`vendor`/`product`，所有資料都落在 `/data` |
| **無需解鎖 BL** | 適合無法 / 不願解鎖 Bootloader 的使用者 |
| **記憶體晚期載入** | 透過 `late-load` 把 `kernelsu.ko` 直接從記憶體注入正在運行的核心 |
| **重啟即還原** | 不做任何持久化寫入，重啟後回到原廠狀態，無痕可查 |

越獄模式要求 **SELinux 為 Permissive**，並且裝置已取得臨時 root（典型路徑是 `adb root`）。

```text
Manager UI ──▶ AppZygotePreload (JNI) ──▶ ksud late-load --magica <port>
                                              │
                                              ├─ 1. enable_adb_root(port)
                                              ├─ 2. 透過 adb 連回本機
                                              ├─ 3. 從記憶體載入 kernelsu.ko
                                              ├─ 4. 執行 post-fs-data / 元模組掛載等階段
                                              └─ 5. service / boot-completed 收尾
```

### 🚀 一鍵越獄啟用（內建漏洞利用）

裝置**完全還沒有 root** 時（核心尚未載入），主介面的「尚未安裝」卡片點擊後會彈出選擇：

- **越獄啟用（漏洞提權）** —— 直接使用內建的 [GhostLock](https://github.com/45072yy980/ghostlock-app)
  （CVE-2026-43499）漏洞利用取得 uid 0，隨後自動呼叫本管理器自己的 `ksud late-load`，
  就地進入越獄模式。**全程不修改任何分區。**
- **手動安裝** —— 走原本的手動安裝流程。

內建的漏洞利用以 `libghostlock.so` 形式隨 APK 分發（僅 `arm64-v8a`），並會依 `uname -r`
比對核心偏移表；比對不到的核心會拒絕執行。它同樣遵循「不動分區」的紅線。

### 🛡️ 系統分區保護（System Partition Guard）

越獄模式跑在記憶體裡，**無法像重啟那樣還原一個被真實寫入的分區**。因此在越獄模式下，
安裝模組時 DikSU 會自動啟用防護；非越獄模式下則由你自行決定是否開啟：

- **越獄模式**：強制開啟、**不可關閉** —— 晚期載入的會話無法撤銷一次真實的分區寫入。
- **非越獄模式**：可在 設定 → **其他功能** → **系統分區保護** 中自由開關。
  開啟後，安裝模組時 Manager 會向 ksud 傳入 `KSU_PARTITION_GUARD=1`。

攔截規則（ksud 側）：

- **會攔截**：腳本中真正觸達區塊裝置 / 真實分區的操作 ——
  `dd ... of=/dev/...`、`> /dev/block/...`、`tee /dev/block/...`、
  `mkfs`/`mke2fs`/`tune2fs`/`e2fsck`/`resize2fs`/`nandwrite`/`sgdisk`/`parted` 操作 `/dev/...`、
  `mount -o remount,rw /dev/...`、`blockdev --setrw /dev/...`、`fastboot flash` 等。
- **不會誤傷**：僅包含 `system/` 等分區目錄的普通模組（那是 OverlayFS 的**臨時**覆蓋，重啟即還原）、
  寫 `/sys`、寫 `/data`、bind mount、註解裡提到 flash 等，都會正常放行。

> ℹ️ 預設情況下該防護**只在越獄模式自動啟用**；正常開機進入的 KernelSU 不受影響，
> 除非你在設定裡主動打開它。
> 若確需強制安裝，可使用 `ksud module install --force <zip>` 繞過。

### 其它客製化

- **Manager 更名為 DikSU**，套件名 `me.diksu.kernelsu`，獨立簽名鏈，與核心內的憑證雜湊嚴格對應。
- **全新桌面圖示**：自適應圖示，已依安全區適配，圓形 / 方形遮罩下均完整顯示。
- **Miuix 主題**：Material 與 Miuix 雙 UI 風格，可自由切換。

## 相容狀態

DikSU 官方支援 GKI 2.0 的裝置（核心版本 5.10 以上）；舊核心也是相容的（最低 4.14+），不過需要自己編譯核心。

WSA、ChromeOS 和運行在容器上的 Android 也可以與 DikSU 一起工作。

目前支援 `arm64-v8a` 和 `x86_64` 架構。

**越獄模式額外要求**：SELinux 為 Permissive，且可取得臨時 root（如 `adb root`）。

> [!CAUTION]
> 最近的核心版本引入了一項破壞性變更，導致 KernelSU 在 `x86_64` 上運行失敗，甚至可能引發核心恐慌 (kernel panic)！請查看網站獲取更多資訊！

## 使用方法

- [安裝教學](https://kernelsu.org/zh_TW/guide/installation.html)（DikSU 支援其中的 LKM / 越獄模式兩種路徑）
- [如何建置？](https://kernelsu.org/zh_TW/guide/how-to-build.html)
- [官方網站](https://kernelsu.org/zh_TW/)
- **越獄模式**：在 Manager 主介面，當偵測到 SELinux 為 Permissive 時會出現「越獄」入口，依提示操作即可。

## 與上游 KernelSU 的差異

1. **套件 / 名稱**：`me.diksu.kernelsu`，顯示名 `DikSU`。
2. **越獄模式（Magica）**：完整的核心晚期載入鏈路（Manager → JNI → ksud → 核心）。
3. **系統分區保護**：越獄模式強制開啟、非越獄模式可開關，攔截會真實寫入分區的模組。
4. **內建漏洞利用**：未 root 時可由「尚未安裝」卡片直接提權並進入越獄（GhostLock / CVE-2026-43499）。
4. **簽名鏈**：使用自建金鑰與憑證雜湊，核心與 Manager 嚴格匹配。
5. **UI**：Miuix / Material 雙主題，含若干介面優化。

## 參與翻譯

中文（簡體 / 繁體）為本專案的主要維護語言。如需新增語言支援，歡迎提交 PR。

## 討論

- Telegram: [@KernelSU](https://t.me/KernelSU)

## 安全性

有關回報 KernelSU 安全漏洞的資訊，請參閱 [SECURITY.md](/SECURITY.md)。

## 授權條款

- 目錄 `kernel` 下所有檔案為 [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)。
- 除 `kernel` 目錄的其他部分均為 [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html)。

## 誌謝

- [KernelSU](https://github.com/tiann/KernelSU)：本專案的上游與基礎。
- [kernel-assisted-superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/)：KernelSU 的靈感。
- [Magisk](https://github.com/topjohnwu/Magisk)：強大的 root 工具箱。
- [genuine](https://github.com/brevent/genuine/)：apk v2 簽名驗證。
- [Diamorphine](https://github.com/m0nad/Diamorphine)：一些 rootkit 技巧。