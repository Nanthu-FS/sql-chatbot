# Smart Notes (Android)

An AI-native notes app for Android: Kotlin, Jetpack Compose, Room and the Claude API. It ships with five switchable themes.

## Themes

You can switch themes in **Settings**. Each option there is previewed in its own style.

| Theme | Look |
|---|---|
| Mono Swiss | White and black with one red accent; dense lists and mono labels |
| Neo-Brutalist | Yellow dotted background, thick borders, hard shadows |
| Retro OS | Notes appear as windows on a 90s desktop, with a menu bar and sticky notes |
| Rolodex | Index cards on lined paper in a dark green drawer |
| Terminal | Amber text on black; notes listed as `.md` files at a prompt |

The themes live in `app/.../ui/theme/`. `Skins.kt` holds each theme's colours and fonts, and `SkinComponents.kt` holds the shared building blocks (scaffold, card, button, text field, chip, banner), which each theme draws differently.

## Features

| Feature | Where |
|---|---|
| **Ask your notes**: answers come only from your notes and cite them; tap a citation to open the note | `AskScreen`, `core/AskNotes`, `core/ClaudeService.ask` |
| **Auto-linking**: related notes are suggested as you type; one tap inserts a `[[link]]` | `core/AutoLinker`, editor |
| **Voice to structured note**: dictate, then get headings, todos and dates pulled out | `VoiceScreen`, `SpeechCapture`, `ClaudeService.structureTranscript` |
| **Smart resurfacing**: notes older than 3 days come back when they match an upcoming calendar event | `core/Resurfacer`, home banner |
| **Photo or whiteboard to note**: get the text plus a Mermaid diagram, rendered in the note | `ClaudeService.photoToNote`, `NoteRenderer` |
| **Board mode**: each paragraph becomes a draggable card; the layout is saved per note | `BoardScreen` |
| **Gesture commands** (Draw mode): ✓ makes a line a todo, ○ searches for the circled text, — ticks off a todo | `core/GestureRecognizer`, `GestureLayer` |
| **Live widgets inside notes**: `{{timer 25}}` and `{{weather Bengaluru}}` (Open-Meteo, no API key) | `NoteRenderer`, `Weather` |
| **Home-screen checklist widget**: stays in sync with the note's todos | `ChecklistWidget` (Glance) |
| **Lock-screen quick capture**: a Quick Settings tile opens a capture-only screen over the keyguard | `QuickCaptureTileService`, `QuickCaptureActivity` |
| **Place reminders**: "Remind me when I'm here", using geofences | `features/places` |
| **Meeting mode**: detects the current calendar event, transcribes, and Claude labels speakers and writes summary, decisions and action items | `MeetingScreen` |
| **Clipboard inbox**: copied text is kept for 24 hours, plus a "Save to Smart Notes" action in any app's text-selection menu | `ClipboardCapture`, `ProcessTextActivity` |
| **Time travel**: scrub or play a note's edit history with line diffs, and restore any version | `TimeTravelScreen`, `core/TimeTravel` |

## Project layout

- `core/`: a plain Kotlin (JVM) module holding the app's logic and the Claude client. It has no Android dependencies and is unit-tested.
- `app/`: the Android app (Compose UI, Room, Glance widget, services).

## Build

You need Android Studio (Ladybug or newer) or the Android SDK with API 35.

```bash
./gradlew :core:test          # unit tests for the logic
./gradlew :app:assembleDebug  # build the APK
```

Open the app, go to **Settings**, paste an Anthropic API key and grant the permissions you want. The key is stored with `EncryptedSharedPreferences`.

## Notes and limits

- AI calls use `claude-opus-5-5` with server-side refusal fallbacks enabled (`fallbacks: "default"`).
- Android 10 and later only let the app that is on screen read the clipboard. Copies are captured when you open Smart Notes; the text-selection action covers copying from other apps.
- Speaker labels in meeting mode are inferred by Claude from pauses and context; there's no audio speaker detection.
- The themes use system font families. Bundling the mockup fonts (Space Grotesk, IBM Plex, VT323 and others) is a next step.
- A future step is a backend proxy, so the API key isn't stored on the phone.
