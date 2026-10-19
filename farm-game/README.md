# Dither Farm

A cozy isometric pixel-art farming game for Android: tap to plant, water and harvest, chop wood in the Forest, dig ore in the Mine, craft tools, and build machines that automate the farm. It's a personal project with no ads and no in-app purchases.

- **Native Android** (Kotlin, no engine): a custom Canvas renderer draws a small pixel buffer and scales it up by a whole number, so every art pixel stays a crisp square.
- **All art and sound are generated from code**, under `Tools/ArtGen`.
- Design: [`Docs/GAME_DESIGN.md`](Docs/GAME_DESIGN.md) · owner decisions: [`Docs/DECISIONS.md`](Docs/DECISIONS.md) · art rules: [`Docs/ART_SPEC.md`](Docs/ART_SPEC.md)

## What's in it
- **Farm:** a 44x44 world inside a forest wall: farmstead, river and bridge, village, pasture and eight fenced 4x4 field blocks bought one at a time. 12 crops with star quality, watering, fertilizer, seasons that wither crops, storms, a glass greenhouse that ignores seasons, sprinklers, scarecrows and lightning rods on the corners between plots.
- **Forest:** a 44x44 forest of five clearings along a winding trail, walled by deep woods and rock cliffs: trees to chop with axe tiers, stumps, saplings, foraging, fallen branches, bird nests and wild beehives.
- **Mine:** a quarry under stepped sandstone cliffs that grows back, and 40 floors below it walled in rock that changes colour every ten floors and fades to darkness: ladders, hollow knocks, dark floors and torches, cave-ins to brace, gates, an elevator every 5 floors and geodes to crack.
- **Machines:** about 20 buildings (Windmill, Bakery, Furnace, Sawmill, Drill Rig and more) that keep working while the app is closed, plus a Logging Hut, a Tree Nursery and a Market Cart that sells by your rules.
- **Farmhands:** five named farmhands (from level 5) each work one field: harvest into its crate, replant its crop, water and clear. They train up to level 25 and eat crops each morning. A harvester takes the crates to the truck or the Silo, and you tap the truck to sell its load in town.
- **Skills:** a point per level for a 20-skill tree in five branches (crops, wood, mine, trade, machines), reset at the Workbench; four craftable tonics (Energy Brew, Growth Tonic, Rain Totem, Lucky Charm).
- **Sound:** varied sound effects, calm lo-fi music per season and place, ambience and weather loops.
- **Story:** a smug bank officer refuses your loan because the village "gives nothing back"; you pick a farmer look and a name and vow to make it the best farming village. He visits after each rebuild, and approves the loan at the end.
- **Goals:** Grandpa's notebook of guided quests; restore the valley (four village tiers paid with bundles of goods); a goal chip that shows any goal you pin; three daily chores with a streak; a weekly truck contract; villager orders with friendship, achievements, a collection book with page bonuses, a daily gift streak, a traveling merchant and seasonal festivals.

## Get the APK
Every push that touches `farm-game/` runs **Dither Farm APK** in GitHub Actions, which runs the unit and screenshot tests and builds a debug APK.
1. Open the repository's **Actions** tab, then the latest **Dither Farm APK** run.
2. Download the `dither-farm-debug-apk` artifact and unzip it.
3. Copy `app-debug.apk` to your phone and open it. Allow "install unknown apps" for your file manager when Android asks.

Requires Android 10 or newer.

## Build locally
With Android Studio (or the Android SDK and JDK 17):
```sh
cd farm-game
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # rules tests + rendering smoke tests
```

Without the Android SDK, `Tools/jvm-check` compiles the app and runs its tests on a plain JVM, and can save screenshots of rendered screens (see its README). `Tools/jvm-check/play.sh <dir>` plays the real game one turn at a time (taps, slides, waits listed in `<dir>/actions.txt`) and saves a screenshot and a state summary, for playtesting without a phone.

## Project layout
```
app/src/main/kotlin/com/ditherfarm/
  engine/    renderer, iso math, camera, input, FX, lighting, weather, audio
  game/      rules and data: crops, forest, mine, machines, market, saves (pure Kotlin, unit tested)
  ui/        screens and the immediate-mode pixel UI
  platform/  save files, notifications
app/src/main/assets/
  art/       generated sprites + manifest.json (pivots)
  sfx/       generated chiptune WAVs
  fonts/     Silkscreen (OFL)
Tools/ArtGen/  sprite and sound generators
Tools/jvm-check/  SDK-free compile, test and screenshot harness
Docs/          design, decisions, art spec, contact sheets
Docs/store/    store listing: screenshots and the feature graphic
```

## Regenerating art and sound
```sh
cd farm-game/Tools/ArtGen
npm install
node generate.mjs            # all sprites (or: node generate.mjs tiles ui crops_a ...)
node sfx.mjs                 # all sound effects (+ variants)
node music.mjs               # lo-fi music and ambience loops (needs ffmpeg)
```
The launcher icon (every density plus the adaptive icon) comes from `python3 launcher.py`.

Contact sheets for review are written to `Docs/sheets/`. Buildings, machines and vehicles are re-rendered at world scale by `scaled.py` (run by `generate.mjs`); `ScaleTest` fails the build if a sprite leaves its size band, and the `farm_scale` screenshot shows the farmstead for a visual check.

## Store listing
`Docs/store/` holds six 1080x1920 screenshots (summer farm, brick village, forest grove, mine ores, the bank opening, daily goals) and the 1024x500 feature graphic. All are real frames of the game:
```sh
cd farm-game/Tools/jvm-check
/opt/gradle/bin/gradle test -Pshots --tests '*StoreShotsTest*'   # or your gradle; writes Docs/store/*.png
cd ../ArtGen && python3 feature_graphic.py                        # title over feature_scene.png
```

## Credits
See [`CREDITS.md`](CREDITS.md).
