# AGENTS.md — jvosk

jvosk is a Java 17 Swing desktop app for offline speech-to-text with [Vosk](https://alphacephei.com/vosk/). It downloads and manages Vosk models, converts common audio formats with JAVE (bundled ffmpeg), and exports TXT, SRT, VTT, JSON and Markdown. UI uses FlatLaf.

## Current state (2026-10-24)

- Works as a single-window app; last code change was October 2026.
- **JUnit 5 suite and GitHub Actions CI.** Unit tests run with `mvn -B verify`; CI also runs `mvn -B -Pintegration-tests verify` on Linux, macOS and Windows, then retains each platform's shaded jar.
- The model catalogue comes from Vosk's official `model-list.json`, parsed with Jackson. A bundled snapshot is used when fetching/parsing fails and can be refreshed with `python scripts/update-model-catalogue.py`.
- New installs persist catalogue version, archive size and checksum for real update checks. Downloads resume via HTTP Range, can be queued in the Model Manager, and are verified before staged extraction/rollback-safe installation. Zip-slip protection remains enabled.
- **Known limitations:** installations created before metadata persistence must be reinstalled once for update tracking; snapshot refreshes are manual; there are no Swing UI tests or `jpackage` installers.

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
```

Models are stored in `~/.jvosk/models/<model-name>/`.

## Layout (`src/main/java/atri/palaash/jvosk/`)

| Path | What it does |
|---|---|
| `App.java` | Entry point: look-and-feel, model manager, background update check |
| `models/` | `ModelRegistry` (JSON catalogue + offline snapshot), `ModelManager` (metadata, resume/queue, verified staged install/delete/scan), `DownloadManager`, `VoskModel` |
| `stt/VoskTranscriber.java` | Audio → 16 kHz mono PCM via JAVE, Vosk recognition, timestamps |
| `ui/` | `MainFrame` (drop zone, transcription, export, shortcuts), `ModelManagerDialog` |
| `util/` | Preferences, audio info, network checks, `TranscriptExporter` |

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

## Suggested next tasks

1. Add `jpackage` installers and publish release assets from signed version tags.
2. Add Swing UI tests for update/reinstall, queue cancellation and partial-download status.
3. Add a model/catalogue migration utility for legacy installs instead of requiring a reinstall to seed metadata.
4. Consider resumable queue persistence across application restarts.

## Git workflow

Never push to `main`; branch, run `mvn -B package`, and open a PR describing what you tested (including a manual transcription of a short WAV if you touched `stt/`).
