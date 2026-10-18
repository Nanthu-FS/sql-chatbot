# Dither Farm: owner decisions

Answers the owner gave while scoping the game. `GAME_DESIGN.md` must follow these.

## Platform and presentation
- Native Android (Kotlin, custom Canvas renderer). No Unity.
- Portrait, isometric view. Tap the farm directly (Hay Day style); there is no avatar.
- Pixel art: 32px, Endesga-32 palette, checker dithering, black outline, hard drop shadow.
- Font: Silkscreen (OFL).
- SFX only, no music. Haptics with a settings toggle.
- App name: **Dither Farm**. The APK is built by GitHub Actions.

## Time and world
- 1 in-game day = 20 real minutes. Progress continues while the app is closed (offline catch-up).
- Day/night lighting.
- Seasons are 14 days long (about 4.7 h real time). Crops wither outside their season; a greenhouse lets any crop grow in any season.
- Weather: rain auto-waters crops. Storms are risky: they can damage unprotected crops, and lightning rods / scarecrows protect them.

## Farm and economy
- Start with 6 plots and buy more land with coins.
- 12 crops: wheat, carrot, potato, tomato, corn, strawberry, pumpkin, sunflower, cabbage, eggplant, melon, grapes.
- Market prices swing daily (about ±30%), shown with arrows.
- Detailed leveling. The player starts with some resources.
- Storage is limited; barn/silo capacity can be upgraded.
- Goals: order board, achievements, collection book.
- Short guided tutorial tips.
- Local notifications when crops or machines are ready (toggle).

## Forest, mine, tools, machines
- Farm, Forest and Mine are separate screens, switched with buttons.
- Mine: a regrowing quarry near the entrance plus descending floors for rare ores.
- Energy bar: chopping and mining cost energy. Energy refills over time (including offline), by eating food, and on level up.
- Tool tiers: axe, pickaxe, watering can, hoe (plus others), going wood → stone → iron → gold.
- Machines: sprinkler, auto-harvester, seed planter, auto-seller, processors (mill, sawmill, furnace), and more.
- Coal fuel for some machines (furnace, drills); farm machines run free.

## Buildings, villagers, saves
- Machines and buildings go into fixed building slots (predefined spots), not free placement.
- Order board orders come from named villagers with pixel portraits; completing their orders earns friendship points.
- Saves are local with an automatic backup copy, plus export/import of the save file.
- Minimum Android version: 10 (API 29).

## Camera, quality, events
- Every screen pans with drag and zooms with pinch, in pixel-perfect integer zoom steps.
- Crops come in normal, silver and gold quality; fertilizer and care raise quality and sell price.
- Events: a travelling merchant (rare seeds and tools), one festival per season with a mini-goal, and a daily login gift.

## Balance (from simulation)
- The XP curve is shorter than the design table from level 4 on (`levels.json` is authoritative). A bot that checks in hourly, 8 times a day, reaches L5 on day 2, L10 by day 5 and about L18 after three weeks (`PacingTest`).
- `ProgressionTest` checks that every item a building, recipe, tool, zone or gate needs can be obtained by the level it unlocks.

## Playtest round 1 (owner feedback)
- **World:** the farm grows to about a 40x40 tile world with a dense forest border (no floating-island edge), a river with a bridge, signposted roads to the Forest and Mine, a village edge with cottages and the order board, and an empty fenced pasture kept for animals.
- **Fields:** land is bought as fenced field blocks (not single plots). Each block gets a name, a crop, a worker and a destination: Truck (sell) or Silo (keep).
- **Workers:** named farmhands with their own look, shown as animated pixel people. A worker assigned to a field harvests ripe crops, replants the field's crop (Silo seeds first, then buys), waters and clears dead plots. Workers level 1 to 25 with coins, getting faster each level. Each eats crops from the Silo daily (1 per day at L1-9, 2 at L10-19, 3 at L20-25, cheapest first) and rests if there is nothing to eat.
- **Harvester and truck:** workers stack the harvest in crates at the field edge. A harvester cart collects crates and loads the truck (Truck fields) or the Silo (Silo fields). The truck waits by the barn; tap it to send it off the map to sell its load, and it returns with the coins.
- **Skills:** 1 skill point per level across five branches (Green Thumb, Woodsman, Miner, Trader, Tinkerer), plus craftable one-shot tonics. Respec at the Workbench for coins (price grows with level).
- **Scale:** crops are redrawn smaller (about two thirds) so buildings tower over the fields.
- **World scale (round 1b):** the cottage (about 44px) is the yardstick. Farmhands are about 25px, vehicles about a farmhand tall, waist-high things (workbench, compost bin, beehive, anvil, kettle) at most 26px, people-sized machines at most 34px, sheds and big machines stay under the cottage; only the barn, silo, windmill and drill rig are taller. Buildings, machines and vehicles are re-rendered at their size by `Tools/ArtGen/scaled.py` (part of `generate.mjs`), and `ScaleTest` fails the build if a sprite leaves its band. The `farm_scale` screenshot shows the farmstead for a visual check.
- **Farmhand look:** style D3 "tall" (11x21 body, hand-placed pixels: collar, buttons, pocket, knee patches, boot soles), picked from five options (`Docs/farmhand_styles.png`).
- **Audio:** sound effects vary in pitch and have variants; ambient loops (birds, wind, crickets, forest, mine drips); weather ambience (rain, thunder, snowy wind); lo-fi, very calm background music (replaces the earlier "SFX only"), with toggles.

## Later (not v1)
- Farm animals.
