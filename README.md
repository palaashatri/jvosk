# jvosk

Modern desktop application for speech-to-text transcription using Vosk offline speech recognition.

Built with Java Swing and FlatLaf for a polished, cross-platform UI with **multi-model support**.


[Download 0.1.0-SNAPSHOT](https://github.com/palaashatri/jvosk/releases/download/0.1.0-SNAPSHOT/jvosk-0.1.0-SNAPSHOT.jar)

>**Made for macOS 🍎** <img width="2048" height="1176" alt="image" src="https://github.com/user-attachments/assets/88412cbd-9350-4f34-9c9f-cf094977413b" />

> **Looks decent on Windows too :) 🪟** <img width="1011" height="752" alt="image" src="https://github.com/user-attachments/assets/da864c87-780a-4323-baec-e579c075b259" />


## Features

### Model Management
- **Multi-Model Support**: Download and manage multiple Vosk models
- **Machine-Readable Catalogue**: Uses Vosk's official `model-list.json`, with a bundled snapshot when offline
- **60+ Current Models**: Speech-to-text models across 35 languages; obsolete, speaker-ID and TTS entries are excluded
- **Verified Downloads**: Exact size and MD5 checks before a model is installed
- **Easy Switching**: Switch between models on the fly
- **Smart Downloads**: 
  - Small models for quick downloads
  - Big models with a confirmation prompt
  - Progress tracking for all downloads
- **35 Languages**: English, Chinese, Russian, French, German, Spanish, and many more
- **Model Manager UI**: 
  - View all available models with details (size, language, version)
  - Download new models with progress bar
  - Delete unused models
  - See installed/available status per model

### Audio Support
- **Multiple Formats**: WAV, MP3, M4A, FLAC, OGG, AAC, WMA, OPUS
- **Automatic Conversion**: Built-in audio conversion (no ffmpeg required!)
- **Drag & Drop**: Simply drag audio files into the app
- **File Browser**: Standard file picker with format filtering

### Transcription
- **Offline Processing**: No internet required, privacy-first
- **Real-time Progress**: Visual feedback during transcription
- **Accurate Results**: Powered by Vosk speech recognition
- **Optional Timestamps**: Add `[HH:MM:SS]` timestamps to each segment

### Export Options
- **Plain Text** (.txt)
- **Subtitle Formats** (SRT, VTT)
- **Structured Data** (JSON)
- **Markdown** (.md)

### User Interface
- **Modern Design**: Clean, professional interface with FlatLaf
- **Dark Mode**: System-aware dark/light theme toggle
- **Keyboard Shortcuts**: Streamlined workflow
  - `Cmd/Ctrl+O` - Open file
  - `Cmd/Ctrl+S` - Save transcript
  - `Cmd/Ctrl+N` - Clear/New
  - `Cmd/Ctrl+Shift+C` - Copy to clipboard
  - `Cmd/Ctrl+Shift+M` - Manage models
  - `Cmd/Ctrl+±` - Adjust font size
- **Statistics**: Word count, character count, WPM
- **Recent Files**: Quick access to previously transcribed files
- **Audio Info**: Display duration, format, sample rate
- **Progress Tracking**: Real-time transcription progress

### Quality of Life
- **Copy to Clipboard**: One-click copy of transcription
- **Cancel Anytime**: Stop long transcriptions mid-process
- **Unsaved Changes Warning**: Never lose work accidentally
- **Persistent Preferences**: Remembers your settings and selected model
- **Adjustable Font**: Customize text size for comfort

## Roadmap

In priority order — each is something a user would notice:

1. **Live transcription from the microphone.** Vosk is built for streaming recognition; show words as they are spoken.
2. **Word-level timing and confidence:** highlight low-confidence words in the transcript and use word times for tighter SRT/VTT cues (Vosk returns both).
3. **Batch transcription:** drop a folder, get a queue with per-file progress and one transcript per file.
4. **A real 1.0.0 release with smaller downloads:** the only release is `0.1.0-SNAPSHOT`, and the shaded jar bundles ffmpeg for every OS via `jave-all-deps`. Per-OS builds (or `jpackage` installers) would cut the size substantially.
5. **Checksum-based update detection:** store the catalogue version and checksum with each installed model, then surface real updates. The current "Check for Updates" action only compares names and cannot yet detect replacements.
6. **Per-platform integration tests:** verify zip extraction, model loading and exports on Linux, macOS and Windows in addition to the unit-test matrix.

### Recently completed

- Replaced fragile HTML scraping with Vosk's machine-readable catalogue and a bundled offline snapshot.
- Added exact download-size and MD5 verification, staged extraction and rollback-safe installation.
- Added JUnit 5 golden-file tests for every export format and a cross-platform CI workflow.

## Quick Start

### Requirements
- Java 17 or higher
- Maven 3.8+
- No additional dependencies needed!

### Build & Run

```bash
# Clone the repository
git clone https://github.com/palaashatri/jvosk.git
cd jvosk

# Build the project and run the test suite
mvn clean verify

# Run the application
mvn exec:java
```

### Tests and CI

```bash
mvn -B verify
```

The test suite covers the catalogue parser and offline fallback, model archive validation and zip-slip protection, download size/MD5 checks, and golden-file exports for TXT, SRT, VTT, JSON and Markdown. GitHub Actions runs `mvn -B verify` on Linux, macOS and Windows with JDK 17.

### First Use

1. Launch the app
2. Open **Models → Manage Models...** (`Cmd/Ctrl+Shift+M`)
3. Download a model for your language:
   - For English: `vosk-model-small-en-us-0.15` (40MB) or `vosk-model-en-us-0.22` (1.8GB)
   - For other languages, browse the available models
4. Click "Download Model" and wait for completion
5. Select the downloaded model and click "Use This Model"
6. Click "Browse Files..." or drag & drop an audio file
7. Wait for transcription to complete
8. Save or copy your transcript!

## Model Management

### Accessing Model Manager

- **Menu**: Models → Manage Models...
- **Keyboard**: `Cmd/Ctrl+Shift+M`

### Available Models

The app reads Vosk's official machine-readable catalogue
([`model-list.json`](https://alphacephei.com/vosk/models/model-list.json)) and
falls back to a bundled snapshot when the catalogue cannot be fetched. The
current snapshot contains **63 current speech-to-text models across 35
languages**; obsolete models and archives this app cannot use (speaker-ID,
text-to-speech) are filtered out.

**Popular Languages:**
- **English**: US English, UK English and Indian English models
- **Chinese**: 2 models
- **Russian**: 2 models
- **French**: 2 models
- **German**: 2 models
- **Spanish**: 2 models
- **Japanese**: 2 models
- **And 25+ more languages!**

**Model Types:**
- **Small Models**: Size-optimised, good for mobile/desktop, lightweight
- **Big Models**: Higher accuracy, more memory and disk space

### Downloading Models

1. Open Model Manager
2. Browse available models in the table
3. Select a model
4. Click "Download Model"
5. Wait for download and extraction (progress shown)
6. Model is automatically installed and ready to use

Downloads are written to a temporary file and verified against the exact
archive size and MD5 checksum from the catalogue before extraction. Large
models will show a confirmation dialog before downloading.

### Switching Models

**Quick Switch:**
1. Menu: Models → Switch Model...
2. Select from installed models
3. Confirm selection

**Or via Model Manager:**
1. Open Model Manager
2. Select an installed model
3. Click "Use This Model"

### Model Updates

- Installed models are never modified or replaced automatically.
- The Model Manager has a **Check for Updates** action, but it currently only
  compares model names and therefore cannot detect every replacement.
  Checksum-based update detection is planned.
- To move to a newer model, download it and select **Use This Model**; the old
  model stays installed until you delete it.

### Deleting Models

1. Open Model Manager
2. Select an installed model
3. Click "Delete Model"
4. Confirm deletion

## Technical Details

### Architecture

**Core Components:**
- `VoskModel`: Immutable model metadata, including an exact byte size and optional MD5 checksum
- `ModelRegistry`: Loads and validates Vosk's JSON catalogue, filters unsupported/obsolete entries, and falls back to the bundled snapshot
- `ModelManager`: Downloads to a temporary file, verifies size and checksum, stages extraction, and installs with rollback
- `ModelManagerDialog`: UI for managing models (catalogue and download work runs off the Swing EDT)
- `VoskTranscriber`: Converts audio and runs recognition with the selected model

**Model Storage:**
- Models are stored in `~/.jvosk/models/` (`%USERPROFILE%\.jvosk\models` on Windows)
- Each model is in its own subdirectory named after the catalogue entry
- Models use the standard Vosk format and can be used with other Vosk tools

**Catalogue Fallback:**
- `src/main/resources/atri/palaash/jvosk/models/model-list.json` is a snapshot
  of the official catalogue (generated 2026-10-24)
- It is used only when the remote catalogue cannot be fetched or parsed
- The Model Manager shows when the bundled catalogue is in use

### Dependencies

```xml
<dependencies>
    <!-- Core speech recognition -->
    <dependency>
        <groupId>com.alphacephei</groupId>
        <artifactId>vosk</artifactId>
        <version>0.3.38</version>
    </dependency>
    
    <!-- UI framework -->
    <dependency>
        <groupId>com.formdev</groupId>
        <artifactId>flatlaf</artifactId>
        <version>3.4.1</version>
    </dependency>
    
    <!-- Audio conversion -->
    <dependency>
        <groupId>ws.schild</groupId>
        <artifactId>jave-all-deps</artifactId>
        <version>3.5.0</version>
    </dependency>
    
    <!-- JSON catalogue parsing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.1</version>
    </dependency>

    <!-- Tests -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.11.4</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Technology Stack

- **Speech Recognition**: [Vosk](https://alphacephei.com/vosk/)
- **Audio Processing**: [JAVE2](https://github.com/a-schild/jave2) (FFmpeg wrapper)
- **UI Framework**: Java Swing with [FlatLaf](https://www.formdev.com/flatlaf/)
- **Build Tool**: Maven

## License

MIT

## Contributing

Contributions welcome! Please open an issue or submit a pull request.
