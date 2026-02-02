# jvosk

Modern desktop application for speech-to-text transcription using Vosk offline speech recognition.

Built with Java Swing and FlatLaf for a polished, cross-platform UI.

<img width="1824" height="1424" alt="image" src="https://github.com/user-attachments/assets/83072e9f-bc86-4c8f-9e46-7fce1a2508ba" />

## Features

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
  - `Cmd/Ctrl+±` - Adjust font size
- **Statistics**: Word count, character count, WPM
- **Recent Files**: Quick access to previously transcribed files
- **Audio Info**: Display duration, format, sample rate
- **Progress Tracking**: Real-time transcription progress

### Quality of Life
- **Copy to Clipboard**: One-click copy of transcription
- **Cancel Anytime**: Stop long transcriptions mid-process
- **Unsaved Changes Warning**: Never lose work accidentally
- **Persistent Preferences**: Remembers your settings
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
2. Click "Browse Files..." or drag & drop an audio file
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
