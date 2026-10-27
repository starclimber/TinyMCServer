# 更新日志
# Changelog

本项目的版本变更记录。 / Release notes for this project.

---

## [1.3.0] — 2026-10-27

### 新增 / Added

- **frp 内网穿透**：内置 frp 客户端（frpc 0.71.1 arm64），把手机上运行的服务端映射到公网，朋友可以从外网直连。
  支持**三种配置方式**：图形表单、直接粘贴 TOML、直接粘贴老式 INI。
  **FRP tunneling**: bundles the frp client (frpc 0.71.1 arm64) so a phone-hosted server can be reached from the internet. Three ways to configure it: a form, raw TOML, or legacy INI.
- **关联启动**：隧道绑定服务器实例后，该实例进入 RUNNING 时自动拉起 frp，服务器停止或崩溃时自动收尾；未绑定时开关置灰。
  **Auto-start with the server**: once a tunnel is bound to an instance, frp launches when that instance reaches RUNNING and is torn down when it stops or crashes; the switch stays greyed out until then.
- **frp 控制台**：点隧道卡片进入，实时日志、启动/停止、清屏、编辑。首页右上角新增入口（设置左边）。
  **FRP console**: tap a tunnel card for live logs, start/stop, clear and edit. A new entry icon sits at the top-right of the home screen.

### 安全 / Security

- **只映射本机**：无论图形、TOML 还是 INI，本地目标地址一律限制为 `127.0.0.1`。图形模式锁定该值；TOML / INI 在**保存与启动时校验**，不是 `127.0.0.1` 直接拒绝并提示，不静默改写用户配置。
  **Loopback only**: in all three modes the local target is restricted to `127.0.0.1`. The form locks it; TOML / INI are validated on save and on start and rejected with a hint otherwise — user config is never silently rewritten.

### 修复 / Fixed

- **frp 日志里的颜色控制符**（`[0m[1;34m` 之类）已剥离，判断成功/失败也基于清理后的文本。
  ANSI color codes in frp logs (`[0m[1;34m` and friends) are stripped; success/failure detection runs on the cleaned text.
- **状态判断修正**：改为看到 `login to server success` / `start proxy success` 才标记为运行中，启动过程显示「连接中」，不再一启动就报成功。
  **Correct status detection**: the tunnel is marked running only after `login to server success` / `start proxy success`; the startup phase shows "connecting" instead of claiming success immediately.
- **点停止误报错误**：主动停止会打断读线程，原来会被当成故障并显示 `read interrupted by close() on another thread`。现在区分「用户主动停止」与「进程意外退出」，主动停止不再报错。
  **Stop no longer reports an error**: a user-initiated stop interrupts the reader thread, which used to surface as `read interrupted by close() on another thread`. User stops and unexpected exits are now distinguished.

### 变更 / Changed

- 版本号 `1.2.4` → `1.3.0`（versionCode 8）。
- NOTICE、README、README.en 增加 frp 相关说明与许可声明。

---

## [1.2.4] — 2026-10-22

### 安全 / Security

- **下载文件现在会校验摘要**：服务端 jar 与插件 jar 下载完成后，都会与上游公布的哈希比对，不一致立即删除并提示重试，避免半截或被篡改的文件被当成可用资源。
  各发行版公布的算法不同，按源使用对应算法：**Paper / Folia → SHA-256**、**Mojang Vanilla → SHA-1**、**Purpur → MD5**、**Modrinth 插件 → SHA-512（回退 SHA-1）**。
  **Downloads are now verified**: after a server jar or plugin jar finishes downloading, its digest is compared against the hash published upstream; on mismatch the file is deleted and a retry is suggested — no more half-written or tampered jars being treated as usable.
  Each publisher uses a different algorithm, so the matching one is applied: **Paper / Folia → SHA-256**, **Mojang Vanilla → SHA-1**, **Purpur → MD5**, **Modrinth plugins → SHA-512 (SHA-1 fallback)**.

### 变更 / Changed

- 版本号 `1.2.3` → `1.2.4`（versionCode 7）。

---

## [1.2.3] — 2026-10-12

### 优化 / Improved

- **更新检查快多了**：改用自建镜像源，并按 **HTTPS → HTTP → GitHub 官方** 的顺序自动回退。
  同时支持 **ETag 条件请求** —— 内容没变时服务端直接返回 304，几乎零流量；实测检查耗时从数秒降到 1~2 秒。
  **Faster update check**: it now uses a self-hosted mirror, falling back automatically in the order **HTTPS → HTTP → GitHub official**.
  It also supports **ETag conditional requests** — when nothing changed the server returns 304 with almost no traffic; a check dropped from several seconds to 1–2 seconds.

### 变更 / Changed

- 镜像源请求超时收紧到 12 秒，避免某个源不可用时长时间卡住。
- 版本号 `1.2.2` → `1.2.3`（versionCode 6）。

---

## [1.2.2] — 2026-10-05

### 新增 / Added

- **应用内更新检查**：「设置 → 关于 → 检查更新」进入**独立页面**，显示当前版本、检查状态、最新版本与**完整更新日志**（落后的每个版本一次性列全），并可「前往下载」直达 APK。
  启动时也会静默检查一次（可在「设置 → 关于」关闭）；发现新版本会弹窗提示，点「前往下载」即可，无需前往仓库手动查看。
  **In-app update check**: "Settings → About → Check for updates" opens a **dedicated screen** showing the current version, the check status, the latest version and the **full release notes** (every version behind the current one, in one list), with a "Download" button that goes straight to the APK.
  The app also checks silently on launch (can be disabled under Settings → About); when a new version is found it shows a dialog, so there is no need to browse the repo yourself.

### 变更 / Changed

- 版本号 `1.2.1` → `1.2.2`（versionCode 5）。
- 启动时自动检查新版本，默认开启；静默检查失败不打扰用户。
- 只统计正式版（跳过 draft 与 prerelease）。

---

## [1.2.1] — 2026-10-03

### 新增 / Added

- **日志分享脱敏**（设置 → 通用，**默认开启**）：复制日志（诊断 / 错误摘要 / JRE 自检 / 深度探测）时自动处理隐私信息 ——
  玩家 **IP** → `x.x.x.x`；**UUID** 只保留前 8 位与后 4 位，中段以 `*` 覆盖；**玩家名** → `Player1` / `Player2`…（`/op`、`/ban` 等命令里的名字一并处理）。
  复制出来的内容可以直接贴进公开的 issue，不必手动删。可在设置中关闭。
  **Log-sharing sanitization** (Settings → General, **on by default**): when copying a log (diagnose / error summary / JRE self-test / deep probe), sensitive data is handled automatically —
  player **IPs** → `x.x.x.x`; **UUIDs** keep only the first 8 and last 4 characters with the middle masked by `*`; **player names** → `Player1` / `Player2`… (names inside `/op`, `/ban`, etc. are handled too).
  The copied output can be pasted straight into a public issue — no manual redaction. It can be turned off in Settings.

### 变更 / Changed

- 版本号 `1.2.0` → `1.2.1`（versionCode 4）。
- 更新日志的「修复」条目改为以**症状**描述（例如「灭屏后掉线（Android 14+）」而不是「WiFi 锁失效」），方便按现象检索。
  Changelog "Fixed" entries are now phrased as **symptoms** (e.g. "drops off after screen-off (Android 14+)" instead of "Wi-Fi lock broken"), matching how people search.

---

## [1.2.0] — 2026-10-03

### 界面 / Interface

- 状态栏改为**透明**并让内容延伸其后（edge-to-edge）；挖孔屏下标题不再被摄像头遮挡。
  The status bar is now **transparent** with content extending behind it (edge-to-edge); titles are no longer hidden by a punch-hole camera.
- 底部**手势导航条（小横条）**区域不再是一条黑带，内容自动避让。
  The bottom **gesture bar** area is no longer a black strip; content avoids it automatically.
- 关闭系统栏的自动对比度底衬。
  Disabled the system's automatic system-bar contrast scrim.

### 页面结构 / Page structure

- 「文件」与「配置」拆分为**两个独立页面**，实例卡片与控制台底部各有入口（原先是同一页面的多个 Tab）。
  Split **Files** and **Config** into two separate pages, reachable from both the instance card and the console toolbar (previously tabs of one page).
- 「插件」页整合为 **4 个 Tab**：本地导入 / 链接下载 / 在线搜索 / 已安装。
  Consolidated **Plugins** into **4 tabs**: local import / download from link / online search / installed.
- 诊断相关入口精简为 **「诊断」+「错误摘要」**（JRE 自检与深度探测收进诊断对话框）。
  Reduced diagnostics to **Diagnose + Error summary** (JRE self-test and deep probe moved inside the diagnose dialog).

### 新增 / Added

- **导入插件**：从手机选择 `.jar`，或粘贴直链下载到 `plugins/`；可查看并删除已安装插件。
  **Import plugins**: pick a local `.jar`, or download from a direct link into `plugins/`; browse and delete installed plugins.
- 关于页新增项目仓库链接。
  Added a repository link on the About screen.

### 修复 / Fixed

- **手机灭屏几分钟后，服务器掉线 —— 同一局域网也连不上**（Android 14 及以上）。
  **A few minutes after the phone's screen turns off, the server drops off — unreachable even on the same LAN** (Android 14+).
- **放在后台跑约 6 小时后，服务器被系统停掉**（Android 15 及以上）。
  **After roughly 6 hours in the background, the server is stopped by the system** (Android 15+).
- **从控制台返回后偶发白屏**。
  **Occasional blank screen right after returning from the console.**
- **「下载服务端」的版本列表偶发空白**（Paper / Folia）：现已自动重试，失败会说明原因并可再试。
  **The version list under "Download server" sometimes came up empty** (Paper / Folia): it now retries automatically, and on failure shows the reason with a Retry button.
- **英文界面下 `server.properties` 标签的括号里又重复了一遍英文**。
  **In the English UI the raw key was repeated inside the parentheses of `server.properties` labels.**

### 变更 / Changed

- 版本号 `1.1.0` → `1.2.0`（versionCode 3）。

---

## [1.1.0] — 2026-09-30

### 新增 / Added

- **多语言支持**：界面与 `server.properties` 标签现支持 **中文 / English / Français / Español / Русский**（共 5 种）。
  首次启动按系统语言自动选择并记住；可在「设置 → 语言」中随时切换，切换即时生效、不重建界面，无闪烁。
  **Multilingual support**: the UI and `server.properties` labels now support **Chinese / English / Français / Español / Русский** (5 languages).
  The language is auto-detected on first launch and remembered; it can be changed anytime in Settings, taking effect instantly without recreating the UI.
- **隐藏最近任务**：设置中新增开关（默认关闭），开启后本应用不出现在系统「最近任务」列表中。
  **Hide from recents**: a new setting (off by default) that removes the app from the system's recents list.
- 多语言覆盖**全部文案**，包括运行日志、崩溃诊断与自检输出。
  The translation covers **all strings**, including runtime logs, crash diagnostics and self-test output.

### 修复 / Fixed

- 首页空状态提示文案换行后未居中（现按行居中）。
  The home empty-state text was not centered when wrapped across lines (now centered per line).

### 变更 / Changed

- 版本号 `1.0.0` → `1.1.0`（versionCode 2）。

---

## [1.0.0] — 2026-09-28

### 首发 / Initial release

- 创建并运行 **Vanilla / Paper / Purpur / Folia** 服务端
  Create and run **Vanilla / Paper / Purpur / Folia** servers on-device.
- 控制台：实时日志、发送命令、就绪检查、崩溃诊断
  Console: live logs, command input, readiness checks, crash diagnostics.
- 文件浏览与 `server.properties` 标签化编辑
  File browser and labeled editing of `server.properties`.
- 从 Modrinth 搜索并安装插件（带完整性校验）
  Search and install plugins from Modrinth (with integrity checks).
- 实例备份 / 还原、玩家管理（白名单 / OP / 封禁）
  Instance backup / restore and player management (whitelist / OP / ban).
- 皮肤工坊：以「种子 + 风格」确定性生成 64×64 皮肤
  Skin Forge: deterministically generate 64×64 skins from a seed + style.
- 前台服务保活（WakeLock / WifiLock）
  Background reliability via a foreground service (WakeLock / WifiLock).
- 内置精简 **JRE 17 / 21 / 25**，含自研 `libandroid-shmem`、`libandroid-spawn`
  Bundled trimmed **JRE 17 / 21 / 25**, including the self-implemented `libandroid-shmem` and `libandroid-spawn`.
