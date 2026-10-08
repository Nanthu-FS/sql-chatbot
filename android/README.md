# Mono Workspace (Android)

A local-only Notion-style workspace for Android: block editor, nested pages and typed databases with Table, Board, List, Gallery and Calendar views. Kotlin, Jetpack Compose, Material 3 restyled as Mono Swiss. There are no accounts and no network access; every byte stays on the device.

## Build

```
cd android
./gradlew :app:assembleRelease      # APK in app/build/outputs/apk/release
./gradlew :app:testDebugUnitTest    # formula, filter, sort, fractional index, markdown, templates
./gradlew :app:connectedDebugAndroidTest   # Room migration and block reorder (device needed)
```

CI (`.github/workflows/android.yml`) builds a signed APK on every push. It uploads the APK as a workflow artifact and publishes it to a rolling pre-release named `apk-<branch>`.

## Layout

- `model/`, `core/`, `engine/`: pure Kotlin. Rich text spans, fractional indexing, the formula language, filters, sorting, grouping, rollups, CSV and Markdown export.
- `data/`: Room entities and DAOs, DataStore settings, repositories, backup and export.
- `ui/`: Compose screens, theme tokens, the icon set and the component kit.

## Choices where the spec left room

- Recently visited pages live in Room (`RecentEntity`), not DataStore, so backups include them.
- `PageEntity` has extra columns: `isDatabase`, `favoriteOrder` and `trashRoot` (marks the item a trash entry restores).
- A database's title and icon live on its container page. Rows are pages whose `parentId` is that container page.
- The title property's value is the row page's title; it is never stored in `row_property_values`.
- Status options reuse the Select value shape; the group (to do, in progress, complete) is set on each option.
- Search indexing runs in the repositories on every write, not in SQL triggers.
- Page Markdown export writes a bare `.md`, or a `.zip` with `./media/` when the page has images or files. Database export is a `.zip` with the view's CSV, an `index.md` and one Markdown file per row that has content.
- Phone landscape, foldables and tablets use a permanent sidebar instead of the drawer. The sidebar can be resized by dragging its edge and collapsed to an icon rail. Columns stack below 600dp.
- Covers are generative black-and-white patterns or an image from the device; there are no colour covers.
- Formulas treat empty values as 0 in arithmetic and as "" in text, and `+` joins text when either side is text.
- Rollups of rollups are evaluated one level deep.
- Expressive motion is used for S Pen and mouse hover only: expanding action buttons, cards that focus on hover while the others blur, ink-bar rows, wipe buttons and parallax covers. The system "Remove animations" setting turns all of it off.
- Deleting from the trash, a database row selection or a view is confirmed by a two-step button (the first tap arms it, the second confirms). With an S Pen hovering, the label is already showing, so one tap confirms.
- The debug key in `app/debug.keystore` signs every build, so a new APK installs over the previous one. It is not a release key.
- WorkManager adds `ACCESS_NETWORK_STATE` through manifest merging. `INTERNET` is explicitly removed.
- Image import is capped at 20 MB, decoded with EXIF rotation, scaled to at most 2048px on the long edge and stored as JPEG (or PNG if the image has transparency).
