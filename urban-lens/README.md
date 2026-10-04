# Urban Lens

An Android map that shows how a city feels right now: **crowd density**, **air quality** and **construction**, with routes that steer you around all three.

Built with Kotlin, Jetpack Compose and MapLibre. Uses only free, keyless open-data APIs.

## Features

| Feature | What it does |
|---|---|
| Crowd layer | Hexagon grid from quiet (teal) to packed (red), re-estimated every minute |
| Air layer | AQI tiles from Open-Meteo, plus local bumps for construction dust and reported smoke |
| Construction layer | Road, building and metro works from OpenStreetMap, with impact notes |
| Place cards | Tap any place for live crowd, AQI, nearby works, an hourly "usually busy" chart and the quietest time left today |
| Spot inspector | Tap anywhere for crowd, AQI and works at that spot; long-press to save, report or route there |
| Smart routing | Walk, cycle or drive. Compares OSRM alternatives and detours, then tags the fastest, cleanest-air, least crowded and construction-free routes, with "% less pollution than fastest" |
| Community reports | Report construction, blocked roads, smoke or waterlogging, with an optional photo. Reports expire unless someone confirms them |
| AQI alerts | Hourly background checks of your saved places, with a general or sensitive health profile and a custom threshold |
| Weather widget | Temperature, wind and AQI right where you are |

## Data sources

| Layer | Source | Notes |
|---|---|---|
| Base map | [CARTO Dark Matter](https://github.com/CartoDB/basemap-styles) | © CARTO, © OpenStreetMap contributors |
| Air quality | [Open-Meteo Air Quality API](https://open-meteo.com/en/docs/air-quality-api) | CAMS model, ~10–40 km resolution |
| Weather | [Open-Meteo Forecast API](https://open-meteo.com/en/docs) | |
| Construction & places | [OpenStreetMap](https://www.openstreetmap.org/copyright) via [Overpass API](https://overpass-api.de) | Loaded when the view is under ~7 km wide |
| Routing | [OSRM on FOSSGIS servers](https://routing.openstreetmap.de) | Car, bike and foot profiles |
| Crowd density | Simulated | Estimated from real places and typical hourly patterns. There is no free real-time source for crowd counts |

Community reports and saved places are stored on the device (Room). Swapping in a backend later only touches `ReportRepository`.

## Project layout

```
core/   Pure Kotlin (JVM): geo math, hex grid, crowd model, AQI field, API clients/parsers,
        route scoring, report and alert policies. Unit-tested.
app/    Android: MapLibre map, Compose UI, Room, WorkManager alerts.
```

## Build

Requirements: JDK 17 and the Android SDK (API 35).

```bash
./gradlew :core:test          # unit tests
./gradlew :app:assembleDebug  # APK at app/build/outputs/apk/debug/
./gradlew :app:assembleRelease  # smaller, R8-optimized APK for ARM phones
```

The release build is signed with the debug key so it can be sideloaded for testing; set up a real keystore before publishing it anywhere.

CI (`.github/workflows/urban-lens-android.yml` at the repo root) runs both on every push that touches `urban-lens/`, uploads the debug and release APKs as artifacts, and publishes both on the `urban-lens-debug` pre-release.

## Defaults

- If location is off, the map starts on Chennai.
- Routes start from your location, or the map center if location is off.
- Air-quality alerts are off until you turn them on in Settings and save a place.
