# jvosk

Modern desktop application for speech-to-text transcription using Vosk offline speech recognition.

Built with Java Swing and FlatLaf for a polished, cross-platform UI with **multi-model support**.

<img width="2048" height="1176" alt="image" src="https://github.com/user-attachments/assets/88412cbd-9350-4f34-9c9f-cf094977413b" />


## Features

### Model Management
- **Multi-Model Support**: Download and manage multiple Vosk models
- **Automatic Updates**: Check for model updates at startup
- **150+ Models Available**: All models from [alphacephei.com/vosk/models](https://alphacephei.com/vosk/models)
- **Easy Switching**: Switch between models on the fly
- **Smart Downloads**: 
  - Small models (< 500MB) for quick downloads
  - Big models (> 500MB) with download confirmation
  - Progress tracking for all downloads
- **40+ Languages**: English, Chinese, Russian, French, German, Spanish, and many more
- **Model Manager UI**: 
  - View all available models with details (size, language, accuracy)
  - Download new models with progress bar
  - Delete unused models
  - Check for updates
  - Filter by installed/available status

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

## Quick Start

### Requirements
- Java 17 or higher
- Maven 3.6+
- No additional dependencies needed!

### Build & Run

```bash
# Clone the repository
git clone https://github.com/palaashatri/jvosk.git
cd jvosk

# Build the project
mvn clean package

# Run the application
mvn exec:java -Dexec.mainClass=atri.palaash.jvosk.App
```

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

The app provides access to 150+ models from the official Vosk repository:

**Popular Languages:**
- **English**: 10+ models (US, Indian accents)
- **Chinese**: 3 models
- **Russian**: 4 models
- **French**: 3 models
- **German**: 4 models
- **Spanish**: 2 models
- **Japanese**: 2 models
- **And 40+ more languages!**

**Model Types:**
- **Small Models** (< 100MB): Fast, good for mobile/desktop, lightweight
- **Big Models** (> 500MB): Higher accuracy, server-grade
- **Punctuation Models**: Add punctuation and capitalization
- **Speaker ID Models**: Identify different speakers

### Downloading Models

1. Open Model Manager
2. Browse available models in the table
3. Select a model
4. Click "Download Model"
5. Wait for download and extraction (progress shown)
6. Model is automatically installed and ready to use

**Note:** Large models will show a confirmation dialog before downloading.

### Switching Models

**Quick Switch:**
1. Menu: Models → Switch Model...
2. Select from installed models
3. Confirm selection

**Or via Model Manager:**
1. Open Model Manager
2. Select an installed model
3. Click "Use This Model"

### Automatic Updates

- On startup, the app checks for model updates
- If updates are available, you'll see a notification
- Updates can be downloaded through the Model Manager
- Models are never auto-updated without your confirmation

### Deleting Models

1. Open Model Manager
2. Select an installed model
3. Click "Delete Model"
4. Confirm deletion

## Technical Details

### Architecture

**New Components:**
- `VoskModel`: Data class for model metadata
- `ModelRegistry`: Parses Vosk models page and fetches model information
- `ModelManager`: Handles downloading, installing, version checking, and loading models
- `ModelManagerDialog`: UI for managing models
- Enhanced `VoskTranscriber`: Supports switching between models
- Updated `App`: Checks for model updates on startup

**Model Storage:**
- All models stored in `models/` directory
- Each model in its own subdirectory
- Models are standard Vosk format (can be used with other Vosk tools)

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
    
    <!-- Web scraping for model registry -->
    <dependency>
        <groupId>org.jsoup</groupId>
        <artifactId>jsoup</artifactId>
        <version>1.17.2</version>
    </dependency>
    
    <!-- JSON processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.1</version>
    </dependency>
</dependencies>
```
3. Wait for transcription to complete
4. Copy, save, or export your transcript

## Technology Stack

- **Speech Recognition**: [Vosk](https://alphacephei.com/vosk/)
- **Audio Processing**: [JAVE2](https://github.com/a-schild/jave2) (FFmpeg wrapper)
- **UI Framework**: Java Swing with [FlatLaf](https://www.formdev.com/flatlaf/)
- **Build Tool**: Maven

## License

MIT

## Contributing

Contributions welcome! Please open an issue or submit a pull request.
