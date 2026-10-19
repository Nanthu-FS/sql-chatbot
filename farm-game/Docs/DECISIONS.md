# Dither Farm: owner decisions

Answers the owner gave while scoping the game. `GAME_DESIGN.md` must follow these.

## Platform and presentation
- Native Android (Kotlin, custom Canvas renderer). No Unity.
- Portrait, isometric view. Tap the farm directly (Hay Day style); there is no avatar.
- Pixel art: 32px, Endesga-32 palette, checker dithering, black outline, hard drop shadow. Art round 3 (sharp and clean, inspired by a lush reference but without its blur): a wider palette with hue shifting (shadows cooler, highlights warmer), no dithering (clean steps), coloured outlines, crisp see-through cast shadows to the lower right with sun spots under trees, leaf-cluster foliage, and posterised ground patches with grass clumps. World sprites and items get this from Tools/ArtGen/polish.py; UI icons keep the old style. Rocks (picked from three styles): ore-coloured rocks about a farmhand wide and knee high, three shape variants each; ore and stone item icons match. The Mine Mouth is a timber tunnel cut into a grass-topped block that joins the quarry cliffs.
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
  - Each branch has four skills that unlock in order (one rank of the previous skill); most take 3 ranks, Extra Slot 2. Effects per rank: Quick Sprout +4% growth, Bountiful 6% extra crop, Star Grower better quality, Deep Roots +10% dry growth; Sharp Edge +5% axe crits, Timber 10% extra wood, Seed Saver 8% seed back, Light Swing 10% free axe swings; Prospector 6% extra ore, Ladder Sense ladder 2 rocks sooner, Geologist 4% geode, Steady Hands 10% free pick swings; Haggler +3% sell price, Bulk Buyer -5% seeds, Charmer +5% order coins, Merchant's Pal -5% merchant prices; Overclock +4% machine speed, Fuel Saver -10% coal, Extra Slot +1 queue slot, Big Trays +2 producer batches. Reset costs 100 coins per level.
  - Tonics (Workbench TONICS tab, used from the Barn): Energy Brew +40 energy (L4), Growth Tonic every growing crop +2 hours (L6), Rain Totem waters every crop (L8), Lucky Charm 10 minutes of better quality and extra crops (L11).
- **Scale:** crops are redrawn smaller (about two thirds) so buildings tower over the fields.
- **World scale (round 1b):** the cottage (about 44px) is the yardstick. Farmhands are about 25px, vehicles about a farmhand tall, waist-high things (workbench, compost bin, beehive, anvil, kettle) at most 26px, people-sized machines at most 34px, sheds and big machines stay under the cottage; only the barn, silo, windmill and drill rig are taller. Buildings, machines and vehicles are re-rendered at their size by `Tools/ArtGen/scaled.py` (part of `generate.mjs`), and `ScaleTest` fails the build if a sprite leaves its band. The `farm_scale` screenshot shows the farmstead for a visual check.
- **Farmhand look:** style D3 "tall" (11x21 body, hand-placed pixels: collar, buttons, pocket, knee patches, boot soles), picked from five options (`Docs/farmhand_styles.png`).
- **Audio:** sound effects vary in pitch and have variants; ambient loops (birds, wind, crickets, forest, mine drips); weather ambience (rain, thunder, snowy wind); lo-fi, very calm background music (replaces the earlier "SFX only"), with toggles.

## Playtest round 2 (owner feedback)
- **Fixes:** long toasts wrap to two lines; a locked or for-sale field shows its level or price on its sign board; street lamps get a small soft glow (no painted ground circle) and house windows light at night; tall art never covers panel titles.
- **Energy:** planting, watering and harvesting are free; chopping and mining still cost energy.
- **Unlocks (hybrid + guided):** one guided quest at a time (Grandpa's notebook) with a pointer and a reward. Tools, the quarry and mine, forest zones and machine blueprints unlock by doing (the stone axe within about 15 minutes, after chopping); machines also need their level. Levels still gate crops, machines and buildings, fields and farmhands, villagers and orders. Pace: level 5 on day 1, level 10 after about a week, level 20 near a month.
- **Orders:** only ask for items the player can get right now with their current tools, buildings and zones, in amounts they can make.
- **Tools:** stone tools at the Workbench; iron and gold tools at the village Blacksmith (opens with village tier 1, gold work from tier 2).
- **Big goal: restore the valley.** Four whole-village tiers over about 3-4 weeks: wooden huts, stone, brick, concrete. Each tier rebuilds every house and road (dirt, gravel, cobbles, paved) and the village grows (4, 6, 8, 10 houses). Tiers are paid with bundles of goods plus coins, donated a bit at a time. Rewards: new villagers and orders, farm perks, and shops (tier 1 Seed shop and Blacksmith, tier 2 Carpenter and gold work, tier 3 Cafe). Art variations for the tiers are shown before they are built.
- **Small goals:** a goal chip on the HUD shows a goal the player pins (a village bundle, an achievement, a field or building to save for, chores or the contract); three daily chores each real day with a streak; one truck contract each real week. One chip: the pin wins, else the notebook quest, else the next village bundle. Chores are quick and mixed (one farm, one craft, one orders/chop/rocks), from level 3. The streak forgives one missed day a week, and its bonus grows to day 7. The contract asks for three crops you can grow now, shipped by truck, and pays coins, XP and gold bars or lucky charms.
- **Maps:** the Forest becomes a big map like the farm with a tree wall; the Mine surface and floors are filled (cliffs, cave walls) so no void shows. Forest 44x44: the zones are clearings joined by a trail, with room left for later areas; its edge is trees plus rock cliffs. Mine surface: a quarry with stepped rock cliffs, gravel and scrub, the Mine Mouth at the cliff foot. Mine floors: cave walls with ore glints, fading to black; each band of ten floors has its own colours.

- **Story (round 3):** Goldcrest Bank's smug loan officer, Mr. Sterling, refuses the farmer a loan under the government's Rural Growth Scheme because the village "gives nothing back"; the farmer vows to make the valley the best farming village. Portrait dialogue: the opening in the bank's office, then the officer visits the village after each rebuild (rating C, then B) and at the concrete town approves the loan (50,000 coins) and the valley is named Best Farming Village. The player picks one of four farmer looks and types a name at the start. Older saves skip the scenes behind them; Settings replays the opening.

## Later (not v1)
- New areas opened by the village: a lake with fishing, a ranch with animals, an orchard hill, a beach.
- Farm animals.
