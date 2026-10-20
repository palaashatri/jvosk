# AGENTS.md — jvosk

jvosk is a Java 17 Swing desktop app for offline speech-to-text with [Vosk](https://alphacephei.com/vosk/). It downloads and manages Vosk models, converts common audio formats with JAVE (bundled ffmpeg), and exports TXT, SRT, VTT, JSON and Markdown. UI uses FlatLaf.

## Current state (2026-10-20)

- Works as a single-window app; last code change was February 2026.
- **No automated tests and no CI.** `dependency-reduced-pom.xml` (a shade-plugin build artifact) is committed by mistake.
- The model catalogue is built by scraping the HTML of `alphacephei.com/vosk/models` with jsoup (`models/ModelRegistry.java`), so a page redesign breaks it. Downloaded model zips are not checksum-verified (zip-slip *is* guarded).

## Commands

Requirements: JDK 17+, Maven 3.8+.

```bash
mvn clean package                                          # builds target/jvosk-0.1.0-SNAPSHOT.jar (shaded, includes JAVE's ffmpeg binaries)
mvn exec:java                                              # run from source (main class atri.palaash.jvosk.App)
java -jar target/jvosk-0.1.0-SNAPSHOT.jar
mvn test                                                   # no tests yet — add them under src/test/java
```

Models are stored in `~/.jvosk/models/<model-name>/`.

## Layout (`src/main/java/atri/palaash/jvosk/`)

| Path | What it does |
|---|---|
| `App.java` | Entry point: look-and-feel, model manager, background update check |
| `models/` | `ModelRegistry` (catalogue scrape), `ModelManager` (install/delete/scan, unzip), `DownloadManager`, `VoskModel` |
| `stt/VoskTranscriber.java` | Audio → 16 kHz mono PCM via JAVE, Vosk recognition, timestamps |
| `ui/` | `MainFrame` (941 lines: drop zone, transcription, export, shortcuts), `ModelManagerDialog` |
| `util/` | Preferences, audio info, network checks, `TranscriptExporter` |

## Rules

- Keep transcription, model download and catalogue fetching off the Swing EDT (`SwingWorker` or background threads, results via `invokeLater`).
- Download to a temp file, verify size (and checksum when one is available), then move into place. Keep the zip-slip check in `ModelManager` when touching extraction.
- Exporters are pure functions of the transcript; give each format a golden-file test.
- Don't commit models, `target/` or `dependency-reduced-pom.xml`.
- New UI goes in its own panel class rather than growing `MainFrame`.

## Suggested first tasks

1. Delete `dependency-reduced-pom.xml` and add it to `.gitignore`.
2. Add JUnit 5 tests for `TranscriptExporter`, `VoskModel` parsing and zip extraction, then a GitHub Actions workflow running `mvn -B verify` on Linux, macOS and Windows.
3. Make the catalogue robust: prefer a machine-readable model list if Vosk publishes one, and fall back to a bundled snapshot when the fetch or parse fails.

## Git workflow

Never push to `main`; branch, run `mvn -B package`, and open a PR describing what you tested (including a manual transcription of a short WAV if you touched `stt/`).
