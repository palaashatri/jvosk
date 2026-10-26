# AGENTS.md — jvosk

jvosk is a Java 17 Swing desktop app for offline speech-to-text with [Vosk](https://alphacephei.com/vosk/). It downloads and manages Vosk models, converts common audio and video formats with JAVE (bundled ffmpeg), optionally filters music/noise before recognition, and exports TXT, SRT, VTT, JSON and Markdown. UI uses FlatLaf.

## Current state (2026-10-26)

- Works as a single-window app; last code change was October 2026.
- **JUnit 5 suite and GitHub Actions CI.** Unit tests run with `mvn -B verify`; CI also runs `mvn -B -Pintegration-tests verify` on Linux, macOS and Windows, then retains each platform's shaded jar.
- The model catalogue comes from Vosk's official `model-list.json`, parsed with Jackson. A bundled snapshot is used when fetching/parsing fails and can be refreshed with `python scripts/update-model-catalogue.py`.
- New installs persist catalogue version, archive size and checksum for real update checks. Downloads resume via HTTP Range, can be queued in the Model Manager, and are verified before staged extraction/rollback-safe installation. Zip-slip protection remains enabled.
- **Known limitations:** snapshot refreshes are manual; installs whose names are no longer in the catalogue still cannot get update metadata without a reinstall; native `jpackage` installers are unsigned.

## Commands

Requirements: JDK 17+, Maven 3.8+.

```bash
mvn clean package                                          # builds a shaded jar with the build host's JAVE FFmpeg binary
mvn exec:java                                              # run from source (main class atri.palaash.jvosk.App)
java -jar target/jvosk-0.1.0-SNAPSHOT.jar
mvn -B verify                                              # unit tests + package
mvn -B -Pintegration-tests verify                          # native model load + short WAV transcription smoke test
mvn test                                                   # unit/golden-file tests only
python scripts/update-model-catalogue.py                   # validate and refresh the bundled JSON snapshot
python scripts/package-release.py --jar target/jvosk-0.1.0-SNAPSHOT.jar --version 0.1.0 --dest dist
git tag -s v1.0.0 -m "jvosk 1.0.0" && git push origin v1.0.0   # signed tag; release workflow publishes jars + jpackage assets
```

Models are stored in `~/.jvosk/models/<model-name>/`.

## Layout (`src/main/java/atri/palaash/jvosk/`)

| Path | What it does |
|---|---|
| `App.java` | Entry point: look-and-feel, model manager, background update check |
| `models/` | `ModelRegistry` (JSON catalogue + offline snapshot), `ModelManager` (metadata, resume/queue, verified staged install/delete/scan/legacy migration), `DownloadManager`, `DownloadQueue`, `VoskModel` |
| `stt/VoskTranscriber.java` | Audio/video → 16 kHz mono PCM via JAVE, optional speech-band enhance, Vosk recognition, timestamps |
| `ui/` | `MainFrame` (drop zone, transcription, export, shortcuts), `ModelManagerDialog`, `ModelTableModel`, `ModelDownloadActions` |
| `util/` | Preferences, audio info, network checks, `TranscriptExporter`, `ModelMigration`, `MediaFormats`, `MediaConverter` |

Catalogue snapshot: `src/main/resources/atri/palaash/jvosk/models/model-list.json`.
Tests: `src/test/java/...`; golden exports in `src/test/resources/golden/`.

## Rules

- Keep transcription, model download and catalogue fetching off the Swing EDT (`SwingWorker` or background threads, results via `invokeLater`).
- Download to a temp file, verify size (and checksum when one is available), then move into place. Keep the zip-slip check in `ModelManager` when touching extraction.
- Preserve partial bytes only for interrupted/incomplete downloads; discard them after size/checksum failures. Keep Range resume and cleanup tests in sync.
- Exporters are pure functions of the transcript; give each format a golden-file test.
- Run `mvn -B verify` and `mvn -B -Pintegration-tests verify` before pushing; keep the Linux/macOS/Windows CI matrix green.
- When refreshing the bundled catalogue snapshot, run the script tests and mention the snapshot date in `README.md`.
- Don't commit models, `target/` or `dependency-reduced-pom.xml`.
- New UI goes in its own panel class rather than growing `MainFrame`.

## Completed quality milestones

1. Persist install metadata and compare model version, checksum and exact size for update detection.
2. Validate and atomically refresh the bundled catalogue through `scripts/update-model-catalogue.py`.
3. Test native model download/extraction/loading and transcribe an attributed short WAV in the Linux/macOS/Windows CI matrix.
4. Select a JAVE native binary per OS/architecture and retain platform-specific shaded jars.
5. Resume partial HTTP downloads, display them in the Model Manager, and queue multiple downloads.

6. Seed `.jvosk-install.json` for legacy installs from matching catalogue entries, without requiring a reinstall.
7. Persist the download queue across restarts and resume remaining names after cancellation.
8. Swing tests for update/reinstall labels, queue cancellation leftovers, and partial-download status.
9. `jpackage` installers published from annotated signed `vX.Y.Z` tags.

## Suggested next tasks

1. Code-sign macOS/Windows installers so Gatekeeper and SmartScreen do not warn.
2. Add a model/catalogue migration path for installs whose names are no longer in the catalogue.
3. Profile streaming recognition on a Raspberry Pi Zero 2 W (see SLOPOS/PARAMA notes below).

## Git workflow

Never push to `main`; branch, run `mvn -B package`, and open a PR describing what you tested (including a manual transcription of a short WAV if you touched `stt/`).

## Future SLOPOS/PARAMA integration and Pi Zero 2 W efficiency goals

**Status: PLANNED / NOT IMPLEMENTED / NOT A CURRENT RELEASE GATE.** This repository is an independently authored candidate for the **optional SLOPOS Dictation provider, complementary to JWhisper**. Preserve its current Java 17 Swing/FlatLaf, Vosk models, JAVE/media converter architecture, license, existing host support, current milestones and evidence requirements; do not rewrite the UI or core engine merely to target SLOPOS. Its GitHub non-fork status is not a substitute for an actual source/asset/license audit. SLOPOS first-party branding means curated, independently verified original software, not automatic default preinstallation.

- **Linux/SLOPOS first:** Provide optional SLOPOS dictation provider with model selection, real audio-device consent, file/transcript import-export, named keyboard controls, notifications/clipboard and offline operation. JWhisper and JVosk share common action/service semantics, not two competing always-on dictation daemons.
- **Raspberry Pi Zero 2 W efficiency (4× Cortex-A53, 512 MB shared RAM):** Prefer compact Vosk models verified on-device; stream microphone/audio and emit partial transcription without retaining entire decoded PCM in memory. Bound waveform previews, temp files and thread count. Preflight model download/decompression plus Java heap/native Vosk usage within 512 MB headroom and fail early if impossible. No idle microphone or model loading.
- **Native PARAMA/SURYA future target:** Future native PARAMA route requires JVM, Swing/AWT or headless adapter, native Vosk/JAVE/codec bindings, model storage, audio/input and SURYA accessibility; do not advertise native PARAMA until dependencies genuinely run.
- **Real device/VM acceptance (NOT_RUN):** Profile streaming 15–60 s speech fixtures and a minimal model on an actual Pi Zero 2 W at 720p: PSS/JVM heap/native memory, real-time factor (can be slow), input responsiveness, accuracy on fixed fixture, low-disk cancellation and no silent audio transfer.
- **Proof and governance:** Record target OS/ABI, library/runtime/model/codec versions, application SHA, actual Pi memory `MemAvailable`, process tree PSS/peak RSS, idle CPU, cold/warm first-usable latency, visible UI response p95, cancellation and correctness, missing features and failure cases. Aarch64 cross-compilation or QEMU is not physical hardware proof; PARAMA requires a separate native port proof, never implied by SLOPOS/Linux acceptance. Keep optional heavy runtimes and model downloads off the 512 MB core SLOPOS image; permit offloading only after informed user consent. Do not weaken correctness, privacy or project release gates to hit a memory target.
