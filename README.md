# AndroidWhisper — Speech to Text (English & Spanish) Proof of Concept

A minimal Android app that accesses the microphone and converts spoken audio to text.
It works in **both Spanish and English**, switchable at runtime with a single tap.

## How it works

The app uses Android's built-in **`SpeechRecognizer`** API (`android.speech`), which:

- captures audio from the microphone,
- performs speech recognition (on-device engine on most devices; otherwise via
  the system speech service), and
- returns transcribed text.

Bilingual support is achieved by setting the recognition language through
`RecognizerIntent.EXTRA_LANGUAGE`, toggling between `en-US` and `es-ES`.

No API keys, no third-party SDKs, no model downloads.

## Features

- 🎙️ Runtime microphone permission handling
- 🗣️ Live partial transcription as you speak
- 🌐 One-tap language switch: English ⇄ Spanish
- 📋 Selectable transcript output

## Requirements

- Android device or emulator running **API 23+** (Android 6.0+)
- A device/emulator with a speech recognition service available
  (most physical devices have Google's recognizer; emulators may not)
- A microphone (physical device recommended for real testing)

> ⚠️ Speech recognition availability varies by device/manufacturer. If the app
> reports "Speech recognition is not available", test on a physical phone or
> install a recognition engine. This is a platform limitation, not a bug.

## Build & run

### Option A — Android Studio (easiest)

1. Open this folder in **Android Studio** (Hedgehog 2023.1.1 or newer).
2. Let Gradle sync (the wrapper is generated automatically).
3. Connect a device with USB debugging enabled.
4. Click **Run ▶**.

### Option B — Command line

Generate the Gradle wrapper jar (not included as it is a binary), then build:

```bash
gradle wrapper --gradle-version 8.2          # one-time, needs a local Gradle 8.x
./gradlew assembleDebug
```

Install the APK on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Using the app

1. Tap **Start listening** and grant microphone permission when prompted.
2. Speak (the selected language is shown on the **language button**).
3. Watch the live transcript appear; final text is accumulated.
4. Tap the **language button** to switch between **English (US)** and **Español (ES)**.
5. Tap **Stop** to end a session.

## Project layout

```
app/src/main/
├── AndroidManifest.xml                         # permissions: RECORD_AUDIO, INTERNET
├── java/com/example/whisperdemo/
│   └── MainActivity.kt                         # core logic + RecognitionListener
└── res/
    ├── layout/activity_main.xml                # UI
    └── values/{strings,colors,themes}.xml
```

## Adding more languages

Edit `MainActivity.kt`:

```kotlin
private val languages = arrayOf("en-US", "es-ES", "fr-FR", "de-DE")
private val languageNames = arrayOf("English (US)", "Español (ES)", "Français", "Deutsch")
```

## Going further

This POC uses the system recognizer. For offline, on-device neural transcription
similar to OpenAI's Whisper, you would integrate a model such as
[whisper.cpp](https://github.com/ggerganov/whisper.cpp) via JNI/ONNX — heavier,
but fully offline and offline-language-accurate.
