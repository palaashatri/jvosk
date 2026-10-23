# AGENTS.md — jvosk

jvosk is a Java 17 Swing desktop app for offline speech-to-text with [Vosk](https://alphacephei.com/vosk/). It downloads and manages Vosk models, converts common audio formats with JAVE (bundled ffmpeg), and exports TXT, SRT, VTT, JSON and Markdown. UI uses FlatLaf.

## Current state (2026-10-24)

- Works as a single-window app; last code change was October 2026.
- **JUnit 5 suite and GitHub Actions CI.** `mvn -B verify` runs the tests and builds the shaded jar on Linux, macOS and Windows.
- The model catalogue comes from Vosk's official `model-list.json`, parsed with Jackson. A snapshot is bundled at `src/main/resources/atri/palaash/jvosk/models/model-list.json` and used when the remote fetch or parse fails. Downloaded zips are verified against the exact catalogue size and MD5; zip-slip extraction is still guarded and installs are staged with rollback.
- **Known limitations:** "Check for Updates" only compares model names and therefore reports no real updates yet; the catalogue snapshot must be refreshed manually; there are no UI or per-platform integration tests.

## Commands

Requirements: JDK 17+, Maven 3.8+.

```bash
mvn clean package                                          # builds target/jvosk-0.1.0-SNAPSHOT.jar (shaded, includes JAVE's ffmpeg binaries)
mvn exec:java                                              # run from source (main class atri.palaash.jvosk.App)
java -jar target/jvosk-0.1.0-SNAPSHOT.jar
mvn -B verify                                              # tests + package; what CI runs
mvn test                                                   # unit/golden-file tests only
```

Models are stored in `~/.jvosk/models/<model-name>/`.

## Layout (`src/main/java/atri/palaash/jvosk/`)

| Path | What it does |
|---|---|
| `App.java` | Entry point: look-and-feel, model manager, background update check |
| `models/` | `ModelRegistry` (JSON catalogue + offline snapshot), `ModelManager` (verified install/delete/scan, staged unzip), `DownloadManager`, `VoskModel` |
| `stt/VoskTranscriber.java` | Audio → 16 kHz mono PCM via JAVE, Vosk recognition, timestamps |
| `ui/` | `MainFrame` (drop zone, transcription, export, shortcuts), `ModelManagerDialog` |
| `util/` | Preferences, audio info, network checks, `TranscriptExporter` |

Catalogue snapshot: `src/main/resources/atri/palaash/jvosk/models/model-list.json`.
Tests: `src/test/java/...`; golden exports in `src/test/resources/golden/`.

## Rules

- Keep transcription, model download and catalogue fetching off the Swing EDT (`SwingWorker` or background threads, results via `invokeLater`).
- Download to a temp file, verify size (and checksum when one is available), then move into place. Keep the zip-slip check in `ModelManager` when touching extraction.
- Exporters are pure functions of the transcript; give each format a golden-file test.
- Run `mvn -B verify` before pushing; keep the Linux/macOS/Windows CI matrix green.
- When refreshing the bundled catalogue snapshot, update the snapshot tests and mention the date in `README.md`.
- Don't commit models, `target/` or `dependency-reduced-pom.xml`.
- New UI goes in its own panel class rather than growing `MainFrame`.

## Suggested next tasks

1. Implement persistent install metadata (catalogue version, checksum, size) and use it for real update detection.
2. Add a small script or Maven profile that regenerates the bundled catalogue snapshot from the official URL, so refreshing it is a reviewable one-command change.
3. Add per-platform integration tests for model extraction and loading, plus a short manual WAV transcription check in CI where feasible.
4. Slim the distribution: per-OS builds or `jpackage` installers instead of shipping every JAVE ffmpeg binary.
5. Add a resume/queue for interrupted downloads and surface partial files in the Model Manager.

## Git workflow

Never push to `main`; branch, run `mvn -B package`, and open a PR describing what you tested (including a manual transcription of a short WAV if you touched `stt/`).
