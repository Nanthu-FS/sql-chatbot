# Tonnage — workout tracker

A mobile-first workout log in a dark theme: true-black background, rounded dark cards, and sage, white and tan accents. It tracks daily workouts, sets and total weight lifted (tonnage). Your own photo is used as the home-screen cover and as your avatar throughout the app.

No build step and no dependencies. It is plain HTML, CSS and JavaScript.

## Features

- **Daily workouts**: a week strip on the home screen, with one workout per day. You can open any past day to backfill it.
- **Sets**: for each set you enter weight × reps and tick it off when done. A new exercise pre-fills the sets you did last time. An optional rest timer starts after each set.
- **Total weight**: volume per set, per exercise, per workout, per week and all time. Comparisons are week-to-date against last week.
- **Progress**: a radial chart for each workout and by muscle group, a 12-week volume chart, personal records, estimated 1RM and a history for each exercise.
- **Your photo**: upload it from the home screen or from Profile. It is resized on the device and stored locally.
- kg or lb, a weekly goal, custom exercises, and JSON backup export/import.
- Works offline and installs to the home screen as a PWA.

The app opens with clearly marked **example workouts** so you can see how it works. Tap **Clear** to remove them.

## Run it

Open `index.html` in a browser, or serve the folder:

```bash
python3 -m http.server 8000 --directory workout-app
```

Then visit <http://localhost:8000>. To use it on your phone, open `http://<your-computer-ip>:8000` on the same Wi-Fi network and choose **Add to Home Screen**. Offline mode needs `localhost` or HTTPS.

## Android app (APK)

`android/` wraps the same files in a small native WebView app (minimum Android 7, package `io.github.nanthufs.tonnage`). In the Android app:

- Data is saved to the app's own storage, and Android's backup service includes it.
- **Export backup** opens Android's save dialog.
- The screen stays on while today's workout is open.
- The system back gesture closes sheets and steps back through screens.

The build uses Ubuntu/Debian's packaged Android tools, so it needs no Android Studio or Gradle:

```bash
sudo apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 default-jdk-headless
workout-app/android/build.sh   # → workout-app/android/build/tonnage.apk
```

The script creates a signing key at `~/.android/tonnage.keystore` on first run. Android installs an update over the existing app only when both are signed with the same key. With a different key, export a backup, uninstall, install the new APK, then import the backup.

## Data

Everything is saved on the device: workouts, settings and the photo. The browser version uses local storage, and the Android app uses its own app storage. Use **Profile → Export backup** to move your data to another device or browser.

## Tests

```bash
node --test 'workout-app/tests/*.test.js'
```

## Files

| File | Purpose |
| --- | --- |
| `index.html` | Page shell and PWA metadata |
| `styles.css` | Theme tokens and components |
| `logic.js` | Pure training logic: dates, units, volume, records, chart ticks, example data |
| `app.js` | Screens, storage, photo handling and interactions |
| `sw.js`, `manifest.webmanifest`, `icons/` | Offline support and install icons |
| `tests/` | Unit tests for `logic.js` |
| `android/` | Android wrapper: manifest, `MainActivity.java`, launcher icons, `build.sh` |
