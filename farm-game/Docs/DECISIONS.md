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

## Later (not v1)
- Farm animals.
