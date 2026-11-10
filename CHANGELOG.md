# 更新日志
# Changelog

本项目的版本变更记录。 / Release notes for this project.

---

## [1.3.6] — 2026-11-10

### 新增 / Added

- **玩家管理页改为在线玩家列表**：页面只列出当前在服务器的玩家，每人一行，右侧直接给出「白名单」「OP」「封禁」三个按钮 —— 已经在对应名单里的玩家，按钮是实心的，点一下移出；不在名单里的按钮是描边的，点一下加入。输入名字后点放大镜或键盘上的搜索键才过滤列表。服务端没有运行、或当前没人在线时，页面正中分别提示「请启动服务器后再试」「当前暂无玩家」。
  **Player management is now an online-player list**: only the players currently on the server are listed, one per row, with "Whitelist", "OP" and "Ban" buttons on the right — a filled button means the player is already in that list and tapping removes them, an outlined one adds them. Type a name and tap the magnifier or the keyboard search key to filter. When the server isn't running, or nobody is online, the page shows "Start the server first" / "No players online" in the centre.

### 修复 / Fixed

- **在线人数实时更新**：有人加入或退出时，控制台顶部显示的人数立刻跟着变，不用再手动执行 `list`。服务端启动时人数归零。
  **Live player count**: the counter in the console header changes the moment someone joins or leaves — no more running `list` by hand. It resets when the server starts.

- **原版不再要求勾选 EULA**：选择「Vanilla」或「Vanilla（预览与测试版）」时不再显示 EULA 勾选项，创建服务器时会自动写好启动所需的 `eula.txt`；Paper / Purpur / Folia 仍然需要勾选。
  **Vanilla no longer asks for the EULA checkbox**: choosing "Vanilla" or "Vanilla (preview and test builds)" skips the checkbox and writes the required `eula.txt` when the server is created; Paper / Purpur / Folia still ask.

### 变更 / Changed

- **TPS 快捷命令**：控制台快捷命令里的 `spark tps` 改为 `tps`。新版服务端没有内置 spark，`spark tps` 会报未知命令，而 `tps` 是 Paper / Purpur / Folia 自带的。
  **TPS shortcut**: the console shortcut `spark tps` is now `tps` — newer servers don't bundle spark, so `spark tps` failed with an unknown-command error, while `tps` is built into Paper / Purpur / Folia.

- 版本号 `1.3.5` → `1.3.6`（versionCode 14）。

---

## [1.3.5] — 2026-11-07

### 新增 / Added

- **一键重新生成地图**：控制台底部按钮区新增「重新生成地图」（在「备份」左边）。点击确认后会自动完成整套流程：服务端正在运行时先 `save-all` 再正常停止，删除 `level-name` 对应的主世界 / 下界 / 末地三个目录，随后重新启动，由服务端自己生成一张全新地图。只删世界数据，插件、配置与其他文件不动，也不自动备份。
  **One-tap world regeneration**: a new "Regenerate world" button in the console action row (left of "Backup"). After confirming, the app saves and stops the running server, deletes the three world folders that belong to `level-name` (overworld / Nether / End), and starts the server again so it generates a brand-new map. Only world data is removed — plugins, configs and other files are untouched, and no backup is created.

### 变更 / Changed

- 版本号 `1.3.4` → `1.3.5`（versionCode 13）。

---

## [1.3.4] — 2026-11-05

### 新增 / Added

- **Vanilla 预览与测试版**：服务端类型里新增一个独立选项「Vanilla（预览与测试版）」，选中后版本列表会包含除正式版以外的全部版本——快照（如 `26.4-snapshot-7`）、预发布 / RC（`-pre`、`-rc`），以及远古的 `old_beta` / `old_alpha`。默认的「Vanilla」仍然只列正式版；Paper / Purpur / Folia 没有对应的 MC 快照服务端，不受影响。
  **Vanilla previews and test builds**: a new standalone option "Vanilla (preview and test builds)" in the server type list. Selecting it makes the version list include everything except releases — snapshots such as `26.4-snapshot-7`, pre-releases / RCs (`-pre`, `-rc`), and the legacy `old_beta` / `old_alpha` builds. Plain "Vanilla" still lists releases only; Paper / Purpur / Folia are unaffected since no snapshot servers exist for them.

### 变更 / Changed

- 版本号 `1.3.3` → `1.3.4`（versionCode 12）。

---

## [1.3.3] — 2026-11-02

### 新增 / Added

- **服务器图标**：在「新建服务器」向导里就能选一张图，创建时自动居中裁成 64×64 的 PNG 写到实例根目录的 `server-icon.png` —— 玩家在客户端服务器列表里看到的就是它，不用再自己去文件管理里放图。已建好的实例可以在「配置」页顶部随时更换或移除图标；实例列表里每台服务器也会显示自己的图标。图标在服务端启动时读入，更换后需要重启服务端才会在客户端生效。
  **Server icon**: pick an image right in the "New server" wizard; it is centre-cropped to a 64×64 PNG and written to `server-icon.png` in the instance folder — exactly what players see in their client's server list, no manual file handling needed. Existing instances can change or remove the icon at the top of the "Config" screen, and the instance list now shows each server's own icon. The icon is read when the server starts, so restart it after a change for clients to pick it up.

### 变更 / Changed

- 版本号 `1.3.2` → `1.3.3`（versionCode 11）。

---

## [1.3.2] — 2026-11-01

### 修复 / Fixed

- **「配置 → 服务器属性」里的值改不动**：清空某一项或改掉原有内容时，输入框会立刻弹回原来的值，删不掉也写不进去，改完点保存等于没改。现在每一项的输入会即时生效，改完点「保存 server.properties」即写入文件。
  **Values in "Config → Server properties" could not be edited**: clearing an item or typing over the existing content made the field snap back to the old value — edits could not be made or saved. Typing now takes effect immediately, and "Save server.properties" writes the result to the file.

### 变更 / Changed

- 版本号 `1.3.1` → `1.3.2`（versionCode 10）。

---

## [1.3.1] — 2026-11-01

### 修复 / Fixed

- **启动时那串 JNA / oshi 红字不再出现**：Paper / Purpur / Folia 等服务端会通过 oshi 采集硬件信息，而它们自带的 JNA 原生库是桌面 Linux（glibc）版本，在 Android 上找不到 `libc.so.6`，控制台因此刷出 `Did not JNA classes…`、`dlopen failed: library "libc.so.6" not found`、`Could not initialize class com.sun.jna.Native` 等一大段红色报错。现在启动前会为实例准备好 Android 版本的 JNA 原生库，并让服务端优先加载它，oshi 能正常初始化，这段报错随之消失，硬件信息也恢复可用。
  **No more JNA / oshi error spam at startup**: Paper / Purpur / Folia collect hardware info through oshi, whose bundled JNA native library is built for desktop Linux (glibc) and fails to load on Android (`libc.so.6` not found) — producing a wall of red errors. The Android build of the JNA native library is now prepared for each instance and loaded first, so oshi initialises normally, the errors are gone and hardware info works again.
- **日志时间戳改为设备本地时区**：Android 上没有 `/etc/localtime`，服务端进程和 frp 客户端都取不到系统时区，日志时间一律按 UTC 打印——中国差 8 小时、欧美差 1~2 小时，看日志判断「什么时候出的问题」很容易看错。现在服务端控制台与 frp 控制台显示的都是设备本地时间，换到其他时区的设备、或遇到夏令时切换都自动跟随，不需要任何设置。
  **Log timestamps follow the device time zone**: Android has no `/etc/localtime`, so both the server process and the frp client fell back to UTC — 8 hours off in China, 1–2 hours off in Europe/US, making it easy to misread when something happened. The server console and the frp console now show the device's local time, following the local zone automatically on devices elsewhere and across daylight-saving switches, with no configuration needed.
- **其余环境告警同样不再刷屏**：Android 上还有一些绕不开的系统限制 —— oshi 读不到 `/proc/stat`、`/etc/os-release` 这类被 SELinux 限制或根本不存在的文件，jline 也会提示终端能力不可用。这些告警会和 JNA 相关的告警一起，在服务器就绪时汇总成一行提示，原文仍保存在实例目录的 `logs/android-noise.log` 里，不再用红色盖住真正需要关注的错误。
  **Other environment warnings no longer flood the console**: some limits cannot be worked around on Android — oshi cannot read files such as `/proc/stat` or `/etc/os-release` (blocked by SELinux or simply absent), and jline reports that advanced terminal features are unavailable. These warnings, along with the JNA ones, are summarised into a single line once the server is ready, while the original text is kept in `logs/android-noise.log`, so real errors are no longer buried in red.

### 变更 / Changed

- 版本号 `1.3.0` → `1.3.1`（versionCode 9）。

---

## [1.3.0] — 2026-10-27

### 新增 / Added

- **frp 内网穿透**：内置 frp 客户端（frpc 0.71.1 arm64），把手机上运行的服务端映射到公网，朋友可以从外网直连。
  支持**三种配置方式**：图形表单、直接粘贴 TOML、直接粘贴老式 INI。
  **FRP tunneling**: bundles the frp client (frpc 0.71.1 arm64) so a phone-hosted server can be reached from the internet. Three ways to configure it: a form, raw TOML, or legacy INI.
- **关联启动**：隧道绑定服务器实例后，该实例进入 RUNNING 时自动拉起 frp，服务器停止或崩溃时自动收尾；未绑定时开关置灰不可用。
  **Auto-start with the server**: once a tunnel is bound to an instance, frp launches when that instance reaches RUNNING and is torn down when it stops or crashes; the switch stays greyed out until then.
- **frp 控制台**：点隧道卡片进入，实时显示 frpc 日志（已剥离 ANSI 颜色码），支持启动 / 停止 / 清屏 / 编辑。首页右上角新增入口（设置左边）。
  **FRP console**: tap a tunnel card for live frpc logs (ANSI color codes stripped), with start / stop / clear / edit. A new entry icon sits at the top-right of the home screen.
- **只映射本机**：三种配置方式的本地目标地址一律限制为 `127.0.0.1`。图形模式锁定该值；TOML / INI 在保存与启动时校验，不是 `127.0.0.1` 直接拒绝并提示，不静默改写用户配置。
  **Loopback only**: in all three modes the local target is restricted to `127.0.0.1`. The form locks it; TOML / INI are validated on save and on start and rejected with a hint otherwise — user config is never silently rewritten.

### 变更 / Changed

- 版本号 `1.2.4` → `1.3.0`（versionCode 8）。
- NOTICE、README、README.en 补充 frp 组件说明与 MIT 许可声明。
- 官网首页新增官方仓库声明，版本信息同步至 1.3.0。

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
