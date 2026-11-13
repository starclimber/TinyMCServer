# [![Typing SVG](https://readme-typing-svg.demolab.com?font=Fira+Code&pause=1000&width=435&lines=%F0%9F%8E%AETinyMCserver)](https://git.io/typing-svg)

[![Typing SVG](https://readme-typing-svg.demolab.com?font=Fira+Code&pause=1000&color=2FF743&width=435&lines=Run+Minecraft+Java+servers+on+Android;No+Root+%C2%B7+No+Termux+%C2%B7+No+PC;Supports+Paper+%2F+Purpur+%2F+Folia)](https://git.io/typing-svg)

A Minecraft Java Edition **server launcher** that runs on an Android phone.

It bundles a trimmed OpenJDK runtime so the device can create and run Vanilla, Paper, Purpur and Folia servers directly — no root, no Termux, and no client-side rendering.

**English** | [简体中文](README.md)

> ## ⚠️ Official Notice
>
> **TinyMCserver's official repositories are:**
> - GitHub: <https://github.com/starclimber/TinyMCServer>
> - Codeberg: <https://codeberg.org/starclimber/TinyMCServer>
> - Gitea: <https://gitea.com/starclimber/TinyMCServer>
>
> Repositories with the same name on other platforms (e.g. GitCode) are **unauthorized automatic mirrors / re-uploads**. Code integrity cannot be guaranteed — please do not file issues or pull requests there.

---

## Features

- **One-tap instance creation**: pick a server flavor and game version; the app matches the runtime and downloads the corresponding server jar automatically.
- **Console**: live logs, command input, readiness checks, crash diagnostics.
- **Triggers ("Playground")**: the "Playground" entry in the console builds when / if / then / else automation rules for each instance; every action can carry its own "on success" / "on failure" branch, and every run is logged.
- **File management**: browse the server directory, edit `server.properties` (with localized labels) and text files, with binary-file detection.
- **Plugin manager**: search and install plugins from Modrinth, with download integrity checks, progress and cancel.
- **Backups & player management**: back up / restore instances, manage whitelist / OP / ban lists.
- **Skin Forge**: deterministically generate a 64×64 Minecraft Java skin from a "seed + style", exportable as PNG.
- **frp tunneling**: expose the phone-hosted server to the internet, configurable via form / TOML / INI, and auto-started together with the server.
- **Background reliability**: foreground service + WakeLock + WifiLock + battery-optimization whitelist guidance.
- **Multilingual**: the UI and `server.properties` labels support Chinese / English / Français / Español / Русский; auto-detected on first launch and switchable in Settings.
- **Hide from recents**: an opt-in setting (off by default) that removes the app from the system recents list.
- **Log-sharing sanitization** (on by default): copying a log automatically hides player IPs, the middle of UUIDs and player names, so it is safe to ask for help in public.
- **In-app update check**: a "Check for updates" screen under Settings shows the latest version and the full release notes, with a direct download link; the app also checks automatically on launch (can be turned off).

## Requirements

| Item | Requirement |
|---|---|
| OS version | Android 8.0 (API 26) or newer |
| CPU ABI | `arm64-v8a` |
| Storage | Depends on the server and runtime; typically 200 MB or more |

## Installation

1. Download the latest `TinyMCserver-<version>.apk` from the project's Releases page.
2. Allow installation from unknown sources on the device, then install the APK.

## Getting started

1. Open the app and tap **+** at the bottom right to create an instance.
2. Enter a name, choose a server flavor (Vanilla / Paper / Purpur / Folia) and a game version, set memory, and accept the EULA.
3. Tap **Create & download server**: the app extracts the runtime and downloads the server jar automatically (with progress), then opens the console.
4. Tap **Start** in the console to run the server. If a download is interrupted, the "Readiness check" panel lets you retry the runtime or the server individually.

## Supported servers

| Flavor | Source |
|---|---|
| Vanilla | Mojang `launchermeta` manifest |
| Paper / Folia | `fill.papermc.io` v3 |
| Purpur | `api.purpurmc.org` v2 |

This app **does not bundle any server software**; all server jars are downloaded on demand from the sources above when an instance is created.

A mirror or proxy prefix can be configured under "Settings → Advanced".

## Skin Forge

A built-in skin generator that produces 64×64 dual-layer skins for Minecraft Java Edition.

**Entry point**: top-right of the home screen (left of the settings button).

**Usage**:

1. Enter any **seed** (or tap "Reroll" to generate one at random);
2. Choose a **style** (or "Auto");
3. Preview the result live, and **export it as a PNG**.

The same "seed + style" always produces the same skin, making them easy to share and reproduce.

**Styles**: Adventurer / Hoodie / Knight / Mage / Ranger / Cyber / Ninja / Street, plus "Auto".

**Implementation**: constraint-based procedural generation — an xorshift32 PRNG drives the randomness, with hue-harmony relationships, saturation / lightness guards and lightness-contrast checks constraining the palette. Written purely in Kotlin, with no third-party code.

## Bundled runtime (JRE)

Three versions of the OpenJDK runtime (aarch64) are bundled and matched to game versions. They come from the official Termux repository and are distributed with the APK as mere aggregation; see [`NOTICE`](NOTICE) for copyright and origin.

| Game version | Runtime |
|---|---|
| 26.x and later | JRE 25 |
| 1.20.5 – 1.21.x | JRE 21 |
| 1.17 – 1.20.4 | JRE 17 |

Each runtime is stored as a single archive at `assets/jre/jre{17,21,25}/universal.tar.xz` and extracted into the app's private directory on first use (first into a staging directory, then atomically swapped in).

To reduce size and keep dependencies clean, the upstream runtime is trimmed and two of its native libraries are replaced with self-implemented versions:

- **`libandroid-shmem`** — provides SysV shared memory to the JVM (implemented on `memfd_create` + `mmap`).
- **`libandroid-spawn`** — provides `posix_spawn` to the JVM (uses the system implementation where available, falling back to a built-in one on older systems).

The sources of both libraries are in [`tools/jrelibs/`](tools/jrelibs/) (GNU Affero General Public License v3.0) and are cross-compiled with Zig.

## frp tunneling

Bundles the [frp](https://github.com/fatedier/frp) client (frpc 0.71.1, arm64) so a server running on this phone can be reached from the internet through a frps host with a public IP.

**Entry point**: the <kbd>router</kbd> icon at the top-right of the home screen (left of Settings).

**Three configuration modes**

| Mode | Description |
|---|---|
| Form | Enter server address, port, token, remote port and local port; TOML is generated automatically |
| TOML | Paste a complete `frpc.toml` |
| INI | Paste a legacy `frpc.ini` |

**Loopback only**: in every mode the local target is restricted to `127.0.0.1`. The form locks it; in TOML / INI modes it is **validated on save and on start** — if `localIP` / `local_ip` is anything else, the save is rejected with a hint. Your config is never silently rewritten.

**Auto-start together with the server**: bind a tunnel to a server instance and frp is launched when that instance reaches RUNNING, and torn down when it stops or crashes. The switch is greyed out until an instance is bound.

**Console**: tap a tunnel card to open it. Live frpc logs (ANSI color codes stripped), with start / stop / clear / edit.

## Triggers ("Playground")

> **Note**: triggers are an early experimental feature and may still have rough edges; please report any problem in [Issues](https://github.com/starclimber/TinyMCServer/issues).

The **Playground** button in the console button row configures automation for one instance: **when** something happens, **if** the conditions hold, **then** run these actions — otherwise run another set.

| Part | Contents |
|---|---|
| **When** (event) | Player joins, player leaves, player count, chat, death, advancement, server ready, timer (every N seconds / daily at HH:mm). Player events support three scopes: all players / specific player / name contains. |
| **If** (condition) | Name match, whitelist, OP, banned, online count, time range, weekday, random chance, time since last run, server state. Multiple conditions can require all or any; with no condition the rule always holds. |
| **Then** | Actions run when the conditions hold. |
| **Else** | Actions run when they don't; may be left empty. |

Actions: run any command, broadcast, private message, title / subtitle / actionbar, sound, particles, give items, teleport, teleport to player, gamemode, potion effect, heal, feed, kick, set time / weather, delay, set variable, add to variable, write log, stop the rest, jump to else.

**On success / on failure**: every action can carry two sub-branches. If the server answers within ~0.8 s with `Unknown command`, `Expected ...`, `That player does not exist` and the like, the action counts as failed and the "on failure" branch runs; otherwise "on success" runs. Branches can be nested.

**Variables**: `{player}` (triggering player), `{online}`, `{max}`, `{count}` (how many times this trigger has fired), `{time}`, `{date}`, `{var:name}`.

**List and log**: each trigger has its own switch; the three buttons at the bottom of a card open its log, open the editor or delete it. The log records, in order, the event that matched, the command actually sent and any failure reason, with a failures-only filter and a clear action. When a trigger acts, the console prints `[Trigger] <name>: <command>`.

**Storage**: triggers live in `triggers.json` inside the instance directory and travel with it when you back up or move the instance; the log is `trigger-logs.txt`, capped at 500 lines. Triggers only run while the server is running, and take effect as soon as you save — no restart needed.

**Example**: player joins → if online players ≥ 5 → `say The server is at 5 players`, else `title {player} actionbar {"text":"Welcome back"}`.

## Architecture

1. **UI layer** (Jetpack Compose): instance list, creation wizard, console, file editor, settings, player management, backup, plugins, Skin Forge.
2. **Server Manager layer**: instance model, version resolution, runtime matching, launch-argument assembly, lifecycle, log parsing, auto-restart.
3. **Runtime layer**: the bundled JRE, launching `java -jar <server.jar> nogui` via `ProcessBuilder` (headless).
4. **Native / OS layer**: foreground service, WakeLock / WifiLock, SAF export, network listeners, JRE native libraries.

## Data & storage

All data lives in the app's private directory; no storage permission is required:

```
/data/data/dev.tinymcserver.app/
  files/jre/jre{major}/     # extracted runtime
  files/instances/<id>/     # server.jar, eula.txt, server.properties, plugins/, world/, backups/
```

## Background & keep-alive

While a server runs, a foreground service is started (`foregroundServiceType="specialUse"`, `stopWithTask=false`).
The notification shows the flavor, version, runtime, port, player count and memory usage; a `PARTIAL_WAKE_LOCK` and a `WIFI_LOCK` are held while running.
Adding the app to the battery-optimization whitelist is recommended for more stable background operation.

## Known issues

The following issues come from the Android platform itself and **cannot be fixed inside the app** — changing these system settings requires Developer options or shell access. Follow the steps below manually.

### Android 12 and later: the server gets killed by the system (child-process limit)

**Symptom**: the server disappears after running for a while. The console shows no Java exception and the log just stops mid-line; the exit code is `9` (SIGKILL) or `137`.

**Cause**: Android 12 introduced the Phantom Process Killer, which caps how many child processes an app may spawn (32 by default, counted across all apps). The server is a child process of this app, so once the limit is exceeded — or the process is judged too CPU-hungry — the platform sends SIGKILL directly and the JVM never gets a chance to log anything. Running several instances at once, keeping frp up, or unpacking a JRE for the first time makes it more likely.

**Fix**: disable the limit.

1. Check Developer options for a switch named "Phantom process killing" or similar and turn it off — some ROMs do not expose it.
2. If there is no such switch, turn the limit off via ADB (enable USB debugging first). The exact commands differ slightly between Android versions — search for "disable phantom process killer Android" or check the Termux documentation.
3. No computer at hand? Use **Shizuku + aShell**: activate Shizuku first, then open aShell — aShell runs commands with ADB privileges, exactly the same as typing ADB on a computer — and run the command from step 2 there.

**Note**: the change usually takes effect immediately; some ROMs reset this setting when Developer options are turned off or after a major system update — repeat the steps if it comes back.

## Building

Requires **JDK 21**, **Android SDK** (platform 34 + build-tools 34.0.0) and **Gradle 8.7**.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

**Signing**: create `local.properties` in the project root with the following keys:

```properties
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=<password>
RELEASE_KEY_ALIAS=<alias>
RELEASE_KEY_PASSWORD=<password>
```

Both `local.properties` and the keystore file are listed in `.gitignore` and are never committed.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Subscribe

Want the latest updates? Subscribe via RSS: <https://rss.cdn.9k.beer/tinymcserver.rss>

## License

This project is licensed under the **GNU Affero General Public License v3.0**; see [`LICENSE`](LICENSE).

Third-party component origins and copyrights are listed in [`NOTICE`](NOTICE).
