# Mono Workspace for Windows

Native Windows build of Mono Workspace (Compose Desktop): real `.exe` / `.msi` installers, Start menu entry, native window and file dialogs, bundled Java runtime, no network access.

## Download

Each push builds the rolling pre-release `desktop-<branch>` on GitHub Releases:

- `MonoWorkspace-Setup-1.0.0.exe` — per-user installer with Start menu and desktop shortcuts
- `MonoWorkspace-1.0.0.msi` — same, as an MSI
- `MonoWorkspace-Portable.zip` — unzip and run `Mono Workspace.exe`

The installers are unsigned, so SmartScreen shows "Windows protected your PC": choose **More info → Run anyway**.

## Screenshots

Rendered offscreen by CI from the real UI on every build (`./gradlew renderScreenshots`).

| | |
|---|---|
| ![Home](docs/screenshots/02-home.png) | ![Hover](docs/screenshots/03-home-hover.png) |
| ![Page](docs/screenshots/05-page.png) | ![Database](docs/screenshots/06-database.png) |
| ![Palette](docs/screenshots/08-palette.png) | ![Lock](docs/screenshots/11-lock.png) |
| ![Indigo Sashiko](docs/screenshots/theme-indigo-home.png) | ![Citrus Slice](docs/screenshots/theme-citrus-home.png) |
| ![Sumi Ink](docs/screenshots/theme-sumi-home.png) | ![Cyanotype](docs/screenshots/theme-cyanotype-home.png) |
| ![Velvet Rose](docs/screenshots/theme-velvet-home.png) | ![Observatory](docs/screenshots/theme-observatory-home.png) |
| ![Theme switch](docs/screenshots/theme-reveal-mid.png) | ![Appearance settings](docs/screenshots/theme-settings.png) |

## Themes

Seven themes, each with its own palette, type, corner radius and motion. Pick them in Settings › Appearance. "Follow Windows" switches between your chosen light and dark theme along with Windows' app mode.

| Theme | Kind | Character |
|---|---|---|
| Mono | Light | The original black-on-white Swiss look. |
| Indigo Sashiko | Light | Indigo cloth with white running stitches. |
| Citrus Slice | Light | Lemon, lime and blood orange; bouncy. |
| Sumi Ink | Light | Rice paper, ink wash, one vermilion seal. |
| Cyanotype | Dark | Prussian-blue sun prints with botanical light. |
| Velvet Rose | Dark | Aubergine velvet, rose-gold glow. |
| Observatory | Dark | Night sky, orbit rings, star gold. |

Each theme's motion pack (`ui/theme/fx/*Fx.kt`) supplies an ambient backdrop, a greeting ornament, the sidebar selection marker, celebrations for creating, starring, completing and trashing, screen transitions, the theme-switch reveal and an optional pointer trail. Ambient effects pause when the window is in the background and can be turned off in Settings; Reduce motion turns all of it off.

Fonts: Inter, Hanken Grotesk, Newsreader, Nunito, Cormorant Garamond and Manrope, bundled under the SIL Open Font License 1.1.

## Keyboard

Ctrl+P command palette · Ctrl+N new page · Ctrl+Shift+N create menu · Ctrl+Shift+F search · Ctrl+Shift+H home · Ctrl+\ sidebar · Ctrl+, settings · Ctrl+L lock · Alt+← / Alt+→ (or mouse back/forward) history · F11 full screen · Esc closes panels. Editor: Ctrl+B/I/U/E, Ctrl+Shift+S, Ctrl+K link, Ctrl+/ block menu, Tab / Shift+Tab indent.

## Choices

- **Shared engine.** `syncShared` copies the Android app's `model/`, `core/`, `engine/`, mappers and repositories into the build, so formulas, filters, rollups, export and data rules are one codebase. The Android unit tests run against the desktop build too.
- **Storage.** The workspace is held in memory and written atomically to `%APPDATA%\MonoWorkspace\db.json` half a second after each change, on focus loss and on exit. The file has the same shape as a backup's `db.json`; media sits next to it in `media\`. A corrupt file is set aside, never overwritten.
- **Backups.** `.monobackup` files are identical on Android and Windows: export on one, restore on the other.
- **Search.** In-memory token index with the same query syntax as the Android FTS table, rebuilt at start.
- **App lock.** A 4–12 digit PIN (salted, iterated SHA-256) instead of biometrics; locks on start, after 5 minutes in the background, or with Ctrl+L. Five wrong tries add a growing wait.
- **Single instance.** A second launch shows a message and exits, so two windows never overwrite each other's edits.
- **Sheets become panels.** Phone bottom sheets and dialogs render as floating panels over the app, which recedes and blurs behind them.
- **Motion.** Shared-axis page transitions with depth blur, a sidebar highlight that glides between items, cards that tilt toward the cursor with a following spotlight, magnetic icon buttons, directional button wipes, a kinetic headline and staggered entrances. Everything turns off with Settings → Reduce motion, and follows Windows' "Animation effects" setting.
- **Window.** Native title bar (snap layouts, Win+arrows work); size and maximized state are remembered; minimum size 720×520; under 1000dp wide the sidebar folds into a rail.
