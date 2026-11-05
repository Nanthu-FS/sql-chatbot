# Pulse — food, water & daily essentials (Android)

A native Android app (Kotlin + Jetpack Compose) for tracking calories, macros,
water, fasting, sleep, steps, weight and supplements. Data stays on the phone.

## Themes

Six switchable themes, each with its own colours, fonts, shapes and layouts
(change them in **Today → palette button → Theme**):

| Theme  | Look |
|--------|------|
| Volt   | Near-black, electric lime, segmented calorie ring (Space Grotesk + Inter) |
| Paper  | Warm editorial page, huge serif numbers, hairline rules (Instrument Serif + DM Sans) |
| Brutal | Cream, thick outlines, hard shadows, tilted stickers (Bricolage Grotesque) |
| Neon   | Synth terminal, glowing LED ring, ASCII macro bars (JetBrains Mono) |
| Matcha | Soft sage, rounded shapes, macro "jars" (Nunito) |
| Swiss  | Stark white/black/red grid, giant numerals (Inter Tight) |

## Features

- **Today** – calorie budget with macro split, week strip to look back, macro
  progress, water and steps at a glance, meals list.
- **Log food** – meal tabs, offline search of ~100 common foods, recents,
  barcode scanning (Google Play services scanner + Open Food Facts lookup),
  Quick add for anything else, portion picker with live macros.
- **Water** – animated bottle, 150/250/500 ml quick add, undo, streaks.
- **Insights** – week / month / year calorie bars vs goal, weight trend,
  7-day macro split, averages, 35-day consistency grid.
- **Habits** – intermittent fasting timer (12–20 h), sleep, steps, mindful
  minutes, weight and a daily supplements checklist.
- **Automatic steps** – connect Health Connect (Settings → Health Connect, or tap
  Steps) and Pulse reads your daily step totals from the phone's counter, Google
  Fit, Samsung Health, Fitbit or a watch. It syncs the last 5 weeks on open and
  every 2 minutes while the app is on screen; read-only, nothing leaves the phone.
  Without it, steps can still be entered by hand.
- **Settings** – theme picker, name, all daily goals, add-burned-calories toggle.

## Build

Requirements: Android Studio (or JDK 17 + Android SDK 35).

```bash
cd pulse-android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Every push that touches `pulse-android/` also builds the APKs in GitHub Actions
(workflow **Pulse Android APK**); download them from the run's **pulse-apk** artifact.

The release build is signed with the debug key so it installs directly; set up
your own signing config before publishing to Google Play.

## Fonts

Bundled from Google Fonts under the SIL Open Font License 1.1 — see
`FONTS-LICENSE.md`.
