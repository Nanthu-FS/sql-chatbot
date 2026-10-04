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

## Data

Everything is saved in the browser's local storage on that device: workouts, settings and the photo. Use **Profile → Export backup** to move your data to another device or browser.

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
