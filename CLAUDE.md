# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Kelpie (formerly "Herdr Signal") is an Android app (Kotlin / Compose) for supervising coding agents (Claude Code, Codex, Pi) that [herdr](https://herdr.dev) hosts on remote dev machines. It is not a mobile SSH terminal, and it is not an official herdr project. Docs, comments and UI copy are mostly in Chinese.

## Commands

Prerequisites:
- JDK 17. On this machine: `export JAVA_HOME=/usr/local/zulu17.56.15-ca-jdk17.0.14-linux_x64`.
- Android SDK with compileSdk 37; `sdk.dir` goes in `local.properties`.

```bash
./gradlew :app:assembleDebug          # io.github.luxiang0412.kelpie.debug
./gradlew :app:assembleMinified       # R8 build + debug provisioning hooks, package .min, debug-signed; for on-device checks
./gradlew :app:assembleRelease        # requires keystore.properties (see below)
./gradlew test                        # all unit tests (also runs Roborazzi screenshot verification)
./gradlew :app:verifyRoborazziDebug   # home-screen screenshot regression (Robolectric, no device)
./gradlew :app:recordRoborazziDebug   # re-record baselines in app/src/test/screenshots/ after intended UI changes
./gradlew :app:lintDebug
python3 tools/i18n_literals.py --check   # CI gate: Chinese string literals must be wrapped in t("中文", "English")
python3 tools/check_doc_links.py         # dangling path references in docs
```

Single test: `./gradlew :core:herdr:testDebugUnitTest --tests '*CodexTranscriptParserTest*'`. Use the variant task (`testDebugUnitTest`). Plain `:module:test --tests` fails.

Macrobenchmark: `ANDROID_SERIAL=emulator-5554 ./gradlew :benchmark:connectedMinifiedAndroidTest`. Run it on an emulator, because MIUI blocks launching the target Activity.

## Build structure

- **Versions**: all dependency, plugin and SDK versions live in `gradle/libs.versions.toml`. Modules reference them only through `libs.*` aliases.
- **Convention plugins** in `build-logic/convention/` hold the shared compileSdk / minSdk / Java 17 / Compose setup:
  - `kelpie.android.application`
  - `kelpie.android.library`
  - `kelpie.android.compose`

  Module build files only declare the plugin, the namespace and their dependencies.
- **Release signing**: `keystore.properties` and `kelpie-release.jks` sit in the repo root and are gitignored.
  - Without them, `assembleRelease` fails on purpose.
  - `-Pkelpie.allowDebugSigning=true` is only for test builds.
- **Release steps**: bump `versionCode` / `versionName` in `app/build.gradle.kts`, update `CHANGELOG.md`, run tests, lint and the release build, commit, then `git tag kelpie-v<version>`. Copy the APK to `dist/`, which is gitignored.
- `app/src/main/baseline-prof.txt` contains fully qualified class names. Update it when you rename packages or classes; `tools/profdump2baseline.py` regenerates it.

## Architecture

Dependency direction: `app` → `core:markdown` / `core:designsystem` / `core:data` → `core:herdr` → `core:ssh`. Packages are `io.github.luxiang0412.kelpie` for the app and `io.github.luxiang0412.kelpie.core.<module>` for the core modules.

### Talking to herdr

There is no server component. Everything goes over SSH (sshj, `core:ssh`) to each dev machine.
- `HerdrApi` opens a fresh exec channel per request, running `herdr --session <s> remote-api-bridge`. The bridge handles one request per connection and speaks NDJSON (`HerdrCodec`).
- `session.snapshot` gives the pane list; `events.subscribe` streams events.
- `ConnectionSupervisor` owns reconnect and backoff.

### Runtime (`core:data/runtime`)

`AppContainer` in `app/.../KelpieApp.kt` wires everything together.
- `Fleet` holds one `HostRuntime` per configured host. Its runtime map is replaced wholesale under a lock.
- Events are only invalidation hints. The truth is a periodic (~10 s) snapshot reconcile (`SessionReconciler`).
- Other runtime classes:
  - `Sender`: sends text and keys with a four-state receipt.
  - `Previews`: reads transcript snippets for panes that are waiting on the user, and detects Claude and Codex terminal prompts from screen text.
  - `ApprovalCenter`: handles phone approvals.

### Home ordering (`core:data/attention`)

`AttentionTracker` and `HomeDerivation` are pure Kotlin. They produce the attention-sorted home list (waiting → working → just finished → idle) and its durations, and are fully unit-tested without Android.

### Transcripts

- **Parsers** in `core:herdr`:
  - `ClaudeTranscriptParser`, `CodexTranscriptParser` and `PiTranscriptParser`, behind `TranscriptParser`, which routes by path. Paths under `/.codex/sessions/` or named `rollout-*` go to the Codex parser.
  - Session locators find the file. For Claude and Codex, herdr's `agent_session` id is used first, then the cwd plus recency as a fallback.
  - `CodexSessionResolver` refuses to guess when several Codex panes share a cwd.
- **Reading**: `TranscriptTailer` reads files incrementally over SFTP. It keeps a byte offset that only advances at newlines, detects file replacement via a hash of the file head, resets on truncation, and caps the bytes read per sync.

### Writes are not idempotent

`WriteLedger` and `Sender` track every send.
- A send whose outcome is unknown becomes `Unknown` and is persisted (Room, `PendingWrites`), so it survives restarts. It is **never retried automatically**, and there is no retry button.
- The user dismisses it, or a new send to the same `host/pane` acknowledges it.

### Approvals

- `companion/hooks/kelpie_approve.py` runs on the dev machine:
  - Claude Code: as a PreToolUse hook.
  - Codex: as a PermissionRequest hook.
- The hook writes `<id>.req` (with a nonce) into the approvals dir and puts an `sa_*` token on its herdr pane. The app sees the token (or polls the dir over SFTP) and writes `<id>.answer`; the hook checks request id + nonce.
- If there is no answer, the answer fails validation or the hook errors, the agent falls back to its own terminal prompt. It never auto-allows.
- See `docs/approvals.md`.

### Pairing

- `companion/pair/kelpie_pair.py` shows a QR code with a `kelpie://pair?d=<base64url JSON>` link. The link contains the address, all host-key fingerprints and a per-phone ed25519 key.
- The app side is `core:data/Pairing.kt` plus `PairScreen`. Every entry point ends on a confirmation screen.
- Key comments start with `kelpie-`. Older keys start with `herdr-signal-`, and `--list` / `--revoke` still find them.

### Localization

UI copy is written inline as `t("中文", "English")` and `plural(n, "agent")` from `core/herdr/L10n.kt`. Pure-Kotlin modules use the same helpers. Don't use Android string resources for copy.

### Debug-only hooks

`app/src/debug` (also compiled into `minified`) holds two entry points:
- `DebugHooks`: imports `provision.json` plus key files pushed to the app's external files dir, then deletes them.
- `FixtureActivity`: a deterministic 2000-message conversation for scroll and jank tests.

`app/src/release` has a no-op `DebugHooks`.

## Invariants to preserve

- **Host keys**: always verify them by hand, with no TOFU. A pinned fingerprint that doesn't match is a refusal, never an overwrite.
- **Sends**: never auto-retry unknown writes.
- **Quick replies and approvals**:
  - Quick replies only fill the input box.
  - "Allow" requires a 600 ms press-and-hold; screen-reader users get a confirm dialog instead.
- **Home screen reads**: transcripts are read only for panes that are waiting on the user.
- **`herdr` in names**: `herdr` names the external tool (`core/herdr`, `HerdrApi` and so on). Only the app's own brand was renamed to Kelpie.
- **Comment references**: `Dn`, `I-n` and `RESULTS §x` in code comments point to old-client decisions and issues, excerpted in `docs/background.md`.

## Working conventions in this repo

- Commit directly on `main` and don't push. Commit messages end with a `Co-Authored-By` line.
- **Remote writes**: every write to a dev machine (sending to panes, creating tabs, changing files) is logged in `docs/history/run-log.md`. That file is append-only.
- **Test targets**: only use herdr sessions named `test`, on `ssh://luxiang@10.144.144.2` (Claude / Codex / Pi) and `ssh://luxiang@10.144.144.3` (Pi). Treat agents running in this repo's cwd as read-only.
- **Agent configs on the dev machines**: never modify the real `~/.claude` or `~/.codex` there, and never copy credentials such as `~/.claude/.credentials.json` or `~/.codex/auth.json`. Hook experiments use isolated config dirs under `~/spike-sandbox/`.
- **Test phone**: `adb -s 10.144.144.6:42407` (MIUI). The first adb install of a new package name needs approval on the phone. Restore any device settings you change.
- Docs live in `docs/`, indexed in `docs/README.md`. `docs/development.md` is the full developer guide.
