> **Status: draft v1.** Merged from the forest, mine, machines, progression and tools designs. The balance and scope critiques have not been applied yet, and numbers in the JSON data tables (`app/src/main/assets/data`) are authoritative when they differ.

# Dither Farm: Master Game Design Document (v1)

Merged from five domain designs: Progression and Economy, Tools and Crafting, Forest, Mine, and Machines and Automation. Where sources disagree, §0 gives the canonical value. All systems are data-driven JSON tables.

**Time units.** 1 game day = 20 real min. 1 game hour = 50 s. 1 season = 14 game days = 4 h 40 min. 1 year = 4 seasons = 18 h 40 min. Times in tables are real time unless marked "game".

---

## 0. Decisions (source conflicts resolved)

| Topic | Conflict in sources | Canonical v1 value |
|---|---|---|
| Stone / Iron / Gold tiers | Stone at L2–4, Iron L8–12, Gold L15–22 | **All five tools: Stone L4, Iron L10, Gold L18** |
| Energy per swing | 1 (Forest), 1–3 (Mine), 2 (Tools, Economy) | **2 per axe or pickaxe swing.** Sickle: 1 in Forest, 0 on Farm. Farm, travel and menus cost 0 |
| Energy max and regen | +3/level, full in 50 min (Forest) vs +5/level, full in 90 min (Economy) | **Max = 100 + 5×(L−1). Regen = max/90 per real min, offline too** |
| Axe perks | Cleave/Sweep (Forest) vs area patterns (Tools) | **Area patterns** (Plus5 at Iron, Square9 at Gold, with splash). Cleave/Sweep removed |
| Gold axe recipe | 8,000c + Varnish (Forest) vs 1,500c (Tools) | **Tools recipes** (§10) |
| Combo, Clean Fell, Sharpen buff | Forest only | Removed from v1 (Later) |
| Crit chance | Forest 5–12%, Mine 15% | **Wood 5%, Stone 8%, Iron 12%, Gold 15%**, double damage |
| Tool damage | Forest vs Tools vs Mine | **Wood 1, Stone 2, Iron 3, Gold 6**. Tree and stump HP from Forest, node HP from Mine |
| Gestures | Forest hold-to-auto-hit vs Tools brush | **Tap = one action. Hold 180 ms then slide = brush. Hold on a target = auto-swing at 3/s** |
| Workbench | Pre-built Farm Workbench (Tools) vs Forest Woodworker's Bench | **One Workbench**, hammer button on every screen. Forest bench recipes move there |
| Charcoal | Kiln recipes differ in all three sources | **Charcoal Kiln (L9): 5 Wood → 2 Coal, 20 min.** Charcoal item removed |
| Glue, Varnish, Tool Handle, Crate, Sawdust | Forest and Machines | **Removed** (use Gear, Nails, Rope) |
| Torches | 1 wood + 1 resin → 3 (Forest) vs 1 wood + 1 coal → 4 (Mine) | **Workbench: 1 Wood + 1 Resin → 3 Torches** (unlock L6) |
| Lightning Rod / Scarecrow | Economy L5, Machines L8 / L2 | **Both L5.** Rod: 250c, 10 Stone, 4 Copper Bar, 1 Iron Bar. Scarecrow: 150c, 10 Wood, 2 Wheat |
| Sprinklers | Economy 4/8/24 plots at L3/L10/L22; Machines 4/16/36 at L6/L14/L24 | **Machines' vertex model:** Copper L6 (4 plots), Iron L14 (16), Gold L24 (36) |
| Storm damage | Economy vs Machines | **Economy rules** (lightning, wind battering). Crows → Later |
| Field protection | Zones of 12 (Economy) vs radius (Machines) | **Machines' radius:** Rod covers 10×10, Scarecrow 6×6 |
| Storage | Barn/Silo start at 50, 100/75 or 150/100 | **Barn 100, Silo 75 at start.** Upgrades per Tools table, levels 2–10 (Barn to 500, Silo to 400). Above 10 → Later |
| Storage full | Minecart overflow (Mine) vs blocked action (Tools) | **Blocked.** Minecart → Later |
| Machine unlocks | Many differ | **Machines design owns machine unlocks** (§11). Sawmill L3 agreed by Forest and Machines |
| Harvest / Planter automation | Economy L9/L7 vs Machines L16/L18 | **Harvest Shed L16, Planter Shed L18** |
| Forest automation | Timber Bot L17 (Machines) vs Logging Hut L9 (Forest) | **Logging Hut L9 and Tree Nursery L14** (Forest). Timber Bot → Later |
| Drills | Quarry Drill L12 (Mine), Drill L15 (Economy), Drill Rig L19 (Machines) | **Drill Rig L19** in Mine slot M4 |
| Furnace | L8 (Economy), L5 (Machines, Mine) | **Furnace L5**, Machines recipes and fuel model |
| Bram | Lumberjack (Forest) vs blacksmith (Economy) | **Bram = blacksmith villager.** Forest lumberjack renamed **Dell** |
| Mine depth | 40 floors (Mine) vs 50 (Economy) | **40 floors in v1.** Endless depth → Later |
| Mine access | Economy floor-by-level vs Mine gates | **Mine gates:** floors 1–9 open at L4 (Mine Mouth repair); Gate 10 at L8; Gate 20 at L14; Gate 30 at L20 |
| Forest zones | Economy zone names vs Forest zones | **Forest zones:** A L2, B L6, C L12, D L16, E L24 |
| Market swing | ±30% (Economy) vs ±15% for processed goods (Machines) | **±30% for everything** (one formula) |
| Fertilizer | Compost (Economy), Mulch (Forest), fibre fertilizer (Tools) | **Compost Bin (L3) and Wood Chipper Mulch both make Basic Fertilizer.** Fibre recipe removed |
| Greenhouse | 3,000c (Machines) vs 25,000c (Economy) | **25,000c, 100 Planks, 60 Stone, 15 Iron Bars, 20 Glass** (L12) |
| Starting kit | 5 wood / 10 stone (Economy) vs 20 wood / 15 stone / 6 fibre / 2 plank (Tools) | **Tools kit** (§17) |
| Market Cart fee | 8% / 4% / 0% | **No fee in v1** |
| Dishes | Economy vs Machines recipe lists | **Machines Bakery Oven recipes** and their energy values. Economy's energy values apply to raw crops |
| Silo / Barn sell | Auto-Seller (Economy) vs Market Cart (Machines) | **Market Cart (L13)** with price rules |
| Weather Station / Weather Vane | Economy L19 vs Machines L9 | Free HUD forecast for tomorrow in v1. Station and Vane → Later |
| Sap Tapper, Rock Crusher, Lapidary Bench, Storm Shard | Machines and Mine | Sap Tapper, Rock Crusher, Lapidary → Later. Storm Shard → Later (rods protect, no shard) |

---

## 1. Pillars and core loops

**Pillars**
1. **Every tap pays.** Each hit has a sound, a haptic tick and a visible result. Drops fly to the Barn or Silo.
2. **Plant for your return time.** Short crops suit active play. Long crops pay more per check-in.
3. **Short sessions, long timers.** Visits take 2–10 min. Timers run through the day and offline, so there is always something ready.
4. **No punishment.** Machines never break. Blocked states (no fuel, full storage, no energy) pause and never destroy progress.
5. **One rule set.** Every building, tool and crop is a JSON row, not a special case.

**Core loops (text diagram)**
```
MINUTE LOOP (2-10 min)
  Tap crops / trees / rocks -> items fly to Barn or Silo
        ^                              |
        |                              v
  Energy (2 per swing)     Orders + Market Cart + Workbench -> coins, XP
        ^                              |
        +--- food / 90-min regen ------+--> plots, tools, machines, storage

DAY LOOP (20 real min)
  06:00 dawn roll (weather, prices, rain waters crops, timers finish)
  07:00 Market Cart sells by rules
  14:00 storm strikes (storm days only)

SEASON LOOP (4 h 40 min)
  Plant for the season -> outdoor crops still growing wither at season end
  Festival goal per season -> Greenhouse (any crop, any season) as the escape
```

---

## 2. Time, seasons and weather

- **Calendar:** Year 1 Spring Day 1, 06:00. Days 1–3 are forced Sunny. Festival days are Sunny.
- **Night:** 20:00–05:00 game time. Dark overlay and firefly/owl SFX. Machines with lights glow. The Mine ignores day and night.
- **Weather** is rolled at 06:00 with seed `(saveSeed, dayIndex)`. No storms before Day 7 and before L5. No two storm days in a row.

| Season | Sunny | Rain / Snow | Storm |
|---|---|---|---|
| Spring | 58% | Rain 35% | 7% |
| Summer | 68% | Rain 22% | 10% |
| Autumn | 54% | Rain 37% | 9% |
| Winter | 52% | Snow 42% (counts as watering) | Blizzard 6% |

**Storm resolution** (14:00 game time, also in offline catch-up)
- All outdoor crops count as watered.
- **Lightning:** `strikes = min(3, 1 + floor(U/16))`, where U = unprotected outdoor planted plots. A strike scorches a random unprotected crop. Scorched plots are cleared for 1 Coal. A Lightning Rod covering the plot blocks the strike.
- **Wind:** each growing crop without Scarecrow cover has a 15% chance of Battered. At harvest, a Battered crop drops one quality tier, or loses 1 yield if already Normal, and loses its care bonus.
- **Forest:** 1–3 mature trees become Windfall Logs (half HP, +1 Wood). 15% chance of a Lightning-struck tree (1 hit, 3 Coal-equivalent fuel, 1 Wood).
- **Mine:** 1–2 Storm-split Boulders replace empty quarry spots.
- Greenhouse is immune to all storm effects.

**Seasons**

| Season | Crops allowed outdoors | Forest | Mine quarry tilt |
|---|---|---|---|
| Spring | Carrot, Wheat, Potato, Cabbage, Strawberry | Sap and Bird Nest ×2; beehive 6%/day | Copper ×1.5 |
| Summer | Wheat, Corn, Tomato, Sunflower, Eggplant, Melon, Grapes | Berries and apples; beehive 12%/day | Coal ×1.5 |
| Autumn | Wheat, Carrot, Corn, Potato, Sunflower, Eggplant, Pumpkin, Grapes | Seeds ×2; beehive 6%/day | Geode Rock always 3% |
| Winter | Wheat, Carrot, Cabbage | Resin ×1.5; no beehives; no forage | Quartz ×2 |

- **Withering:** at each season change, outdoor crops still growing that are not in the new season's list wither. Mature crops never wither. A mature regrowing crop gives one final harvest. Trees never wither.
- **Late-planting warning:** when season time left is less than the grow time, the seed button asks for confirmation.

---

## 3. Screens and controls

**Screens** (bottom bar: Farm / Forest / Mine, plus Barn and Workbench buttons)

| Screen | Unlock | Contents | Fixed slots |
|---|---|---|---|
| **Farm** | Start | Plot field (6 at start, up to 48 plus 12 Greenhouse). Fixed: Barn, Silo, Workbench, Well, Greenhouse (L12, 6×5 frame). Workshop slots: 4 at start, +1 at L6, L9, L12, L14, L16, L18, L22 (12 total). Field vertices for sprinklers, scarecrows and rods | Barn, Silo, Workbench, Well, Greenhouse, 12 workshop slots |
| **Forest** | L2 | Zones A–E (tree spots, brambles over locked zones, signposts). Isometric 70×70 grid with a validity mask. Stumps, branches, mushrooms, berry bushes, wild apples | F1–F7 (zone-gated) |
| **Mine** (surface) | L3 | Quarry yard with 12/16/20 regrowing spots, Mine Mouth (elevator), Minecart removed. Machine slots M1–M4 | M1 Furnace (L5), M2 Geode Anvil (L7), M3 empty (Later), M4 Drill Rig (L19) |
| **Mine** (underground) | L4 (floors 1–9) | Current floor: rock grid 9×9 to 12×12, ladder, mushrooms, crates, dark overlay on deeper bands | Entry tile only |
| **Menus** | | Barn and Silo grid (filters), Workbench (Tools, Parts, Farm tabs), Market (arrows, sparkline, saturation preview), Order Board, Villagers, Collection Book, Achievements, Settings (SFX, haptics, notifications, export/import), Elevator panel | |

**HUD:** top bar shows level badge with XP bar, coins, energy bar (tap for food quick-eat), season, day, clock and weather. Bottom bar shows screen buttons, Barn, Workbench (badge when a craft finishes), and the active tool icon.

**Camera:** drag-pan with fling. Pinch snaps to integer steps 2×, 3×, 4×. Remembers the last position per screen.

**Gestures** (identical on every screen)

| Gesture | Result |
|---|---|
| Tap an actionable tile | One action with the tile's tool and its area pattern |
| Hold 180 ms on a tile, then slide (brush) | Tool sticks to finger. Farm: each tile entered gets the same action. Forest/Mine: auto-swing at 3/s on whatever is under the finger |
| Drag more than 8 px before 180 ms, or from a non-action tile | Pan |
| Pinch | Zoom in integer steps |
| Rapid tapping | Capped at 5 swings per second |
| Drag a seed across empty tiles | Sows one per tile touched |
| Drag an item from a quick-slot to a tile | Places torch, support beam or rope ladder (Mine) |

**Tile to action resolution** (pure function `resolve(screen, tile) → action?`)

| Tile state | Tool | Action |
|---|---|---|
| Weeds or withered crop | Hoe | Clears it, +1 Fibre |
| Untilled plot | Hoe | Tills it |
| Tilled empty plot | none | Opens seed tray |
| Planted and dry | Watering can | Waters it |
| Planted and wet, growing | none | Info bubble: time left, stars, fertilizer button |
| Ready crop | Sickle | Harvests it |
| Well or water tile | Watering can | Refills free |
| Tree, stump, log | Axe | Swings |
| Grass or bush (Forest) | Sickle | Cuts for Fibre |
| Rock or vein | Pickaxe | Swings |
| Building slot | none | Opens its panel |

**Feedback.** The pattern footprint lights up on touch-down. Blocked actions show a reason bubble ("Needs Stone axe", "Barn full", "Too tired"), cost 0 energy and reset nothing. HP pips appear after the first hit. Chips fly, and the tree or rock shakes 1 px for 2–3 frames. Crit shows a flash and "CRIT" or "CRACK!". Break or fell plays a dust puff. Drops arc to the HUD and auto-collect.

---

## 4. Energy

| Rule | Value |
|---|---|
| Max energy | `100 + 5×(L−1)`. L30 = 245. +10 from Kitchen collection page, +15 from Doc Fennel Heart 5 |
| Regen | max/90 per real minute. Full in 90 min at any level. Timestamp-based, works offline, capped at max |
| Level-up | Refills to max |
| Costs | Axe or pickaxe swing 2. Sickle in Forest 1. Stump or ore hit 2 (same swing rule). Beehive smoke: 1 Torch or 4 energy. Cave-in: 5 energy. Farm, travel, menus, collect, plant, build: 0. Blocked actions: 0 |
| At 0 energy | Chopping and mining blocked. "Too tired" bubble opens quick-eat. Tip shows "full in X min" |
| Food | Tap item, then Eat. Blocked if it would overflow. Quality ×1.25 (Silver), ×1.5 (Gold) |

**Food energy**

| Food | Energy | Source |
|---|---|---|
| Carrot / Corn / Potato / Cabbage | 12 / 15 / 12 / 25 | raw |
| Tomato / Strawberry / Eggplant / Grapes | 10 / 12 / 20 / 10 | raw |
| Pumpkin / Melon | 50 / 60 | raw |
| Dishes | See §12 | Bakery Oven |
| Apple / Blackberry / Wild Mushroom / Chanterelle | 5 / 4 / 5 / 10 | Forest |
| Honey | 15 | Beehive |
| Maple Syrup | 20 | Syrup Kettle |
| Cave Mushroom | 10 | Mine |

Forage in season adds about 20–40 energy per real hour.

**Session sanity check.** Clearing all 54 Zone A trees (6 Pine, 4 Birch) at Lv 2–3 with a Wood axe costs 54 hits × 2 = 108 energy. That is about one full bar plus regen, which fits a 2–3 minute visit.

---

## 5. Levels and XP

**Formulas** (levels.json is authoritative)
- `XPtoNext(L) = niceRound(12·L² + 40·1.25^L − 20)`
- `LevelUpCoins(L) = niceRound(40·L^1.6)`
- Plot cap = `min(48, 6 + 2·(L−1))`. Order slots: 3 at L1, 4 at L5, 5 at L10, 6 at L15.
- After L30, every 40,000 XP gives a Mastery Star with a chest worth about 5,000c.

**Unified level table (1–30)**

| L | XP to next | Total XP | Max E | Plots | Orders | Level-up coins | Unlocks (v1) |
|---|---|---|---|---|---|---|---|
| 1 | 40 | 0 | 100 | 6 | 3 | – | Wheat, Carrot; Farm, Market, Order Board, Workbench, Barn 100, Silo 75; wood tools; Mara, Tobias |
| 2 | 90 | 40 | 105 | 8 | 3 | 120 | Corn; Forest Zone A; Windmill; Silo 2 |
| 3 | 170 | 130 | 110 | 10 | 3 | 230 | Mine screen + Quarry Lv1; Sawmill; Compost Bin; Barn 2; Ivy |
| 4 | 270 | 300 | 115 | 12 | 3 | 370 | Potato; Bakery Oven; stone-tier tools; Mine Mouth repair (floors 1–9) |
| 5 | 400 | 570 | 120 | 14 | 4 | 530 | Cabbage; Furnace; Lightning Rod; Scarecrow; storms begin; Silo 3; Dell and beehive events |
| 6 | 560 | 970 | 125 | 16 | 4 | 700 | Forest Zone B; Copper Sprinkler; Torches; Barn 3; Bram |
| 7 | 760 | 1,530 | 130 | 18 | 4 | 900 | Tomato; Preserves Jar; Wood Chipper; Workbench L2; Geode Anvil; Silo 4 |
| 8 | 990 | 2,290 | 135 | 20 | 4 | 1,100 | Beehive; Lantern; Quarry Lv2; Gate 10 open; Barn 4 |
| 9 | 1,250 | 3,280 | 140 | 22 | 4 | 1,350 | Strawberry; Seed Maker; Logging Hut; Charcoal Kiln; Support Beam; Juniper |
| 10 | 1,550 | 4,530 | 145 | 24 | 5 | 1,600 | Iron-tier tools; Fruit Press; Beam; Silo 5; Big Orders |
| 11 | 1,900 | 6,080 | 150 | 26 | 5 | 1,850 | Sunflower; Barn 5; Pumpkin Pie recipe |
| 12 | 2,300 | 7,980 | 155 | 28 | 5 | 2,150 | Greenhouse; Forest Zone C; Rope Ladder; Pip |
| 13 | 2,750 | 10,280 | 160 | 30 | 5 | 2,400 | Eggplant; Market Cart; Syrup Kettle; Veggie Stew; Silo 6 |
| 14 | 3,250 | 13,030 | 165 | 32 | 5 | 2,750 | Oil Press; Tree Nursery; Iron Sprinkler; Quality Fertilizer; Gate 20 open; Quarry Lv3; Barn 6 |
| 15 | 3,800 | 16,280 | 170 | 34 | 6 | 3,050 | Pumpkin; Gold Bar smelting; Potato Chips |
| 16 | 4,450 | 20,080 | 175 | 36 | 6 | 3,400 | Harvest Shed; Ratatouille; Workbench L3; Forest Zone D; Silo 7 |
| 17 | 5,200 | 24,530 | 180 | 38 | 6 | 3,700 | Barn 7 |
| 18 | 6,100 | 29,730 | 185 | 40 | 6 | 4,100 | Melon; gold-tier tools; Planter Shed; Tree Nursery L2 |
| 19 | 7,100 | 35,830 | 190 | 42 | 6 | 4,450 | Drill Rig; Silo 8 |
| 20 | 8,250 | 42,930 | 195 | 44 | 6 | 4,850 | Greenhouse Expansion (+12 plots); Barn 8; Doc Fennel |
| 21 | 9,600 | 51,180 | 200 | 46 | 6 | 5,200 | Grapes |
| 22 | 11,200 | 60,780 | 205 | 48 | 6 | 5,600 | Logging Hut L3; plot cap maxed |
| 23 | 13,100 | 71,980 | 210 | 48 | 6 | 6,050 | Silo 9 |
| 24 | 15,400 | 85,080 | 215 | 48 | 6 | 6,450 | Gold Sprinkler; Forest Zone E; Barn 9 |
| 25 | 18,100 | 100,480 | 220 | 48 | 6 | 6,900 | – |
| 26 | 21,300 | 118,580 | 225 | 48 | 6 | 7,350 | – |
| 27 | 25,300 | 139,880 | 230 | 48 | 6 | 7,800 | Silo 10 |
| 28 | 30,100 | 165,180 | 235 | 48 | 6 | 8,250 | Barn 10 |
| 29 | 35,900 | 195,280 | 240 | 48 | 6 | 8,750 | – |
| 30 | – | 231,180 | 245 | 48 | 6 | 9,250 | "Master Farmer" title; Mastery Stars begin |

**XP sources**

| Source | XP |
|---|---|
| Crop harvest | Crop XP (2–15) ×1.25 Silver, ×1.5 Gold, rounded up. Per plot, not per unit |
| First harvest of a crop | +10 (collection entry) |
| Tree fell | Pine 2, Birch 3, Oak 6, Maple 10, Ancient Oak 40. Stump cleared +1 |
| Quarry rock / coal / copper / clay | 1 / 2 / 3 / 1 |
| Mine floor nodes | Rock 1, iron 4, gold 7, gem 10 |
| First arrival on a floor | 5 × band number. +25 at each elevator stop reached by ladder |
| Machine batch collected | max(1, round(outputValue/10)). Auto-collected: 50% |
| Build or upgrade | round(coinCost/50), max 500 |
| Market sale | 1 per 50 coins (Market Cart: 1 per 100) |
| Order delivered | round(0.30 × V) + 5 × lines, minimum 10 |
| Crafting | 1 per plank, rope or nails unit; 3 per gear; 5 per Farm-tab item |
| Achievement tier | 25 / 100 / 300 |
| Collection entry | 10 for crops, 5 for others, +5 per star |
| Festival mini-goal | 50 + 10 × L |
| Tutorial steps | 5–15 each (about 120 total) |

Planting, watering and walking give 0 XP.

---

## 6. Crops

12 crops. Grow times assume watered. Profit is per harvest after seed cost. XP is per plot.

| # | Crop | Unlock L | Seasons | Grow (game h / real) | Type (regrow h / real) | Seed | Yield | Price | Profit / harvest | XP | Energy (raw) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Wheat | 1 | All | 3 / 2m30s | Single | 2 | 3 | 3 | 7 | 2 | Inedible, mill to Flour |
| 2 | Carrot | 1 | Spr, Fall, Win | 6 / 5m | Single | 4 | 2 | 8 | 12 | 3 | 12 |
| 3 | Corn | 2 | Sum, Fall | 9 / 7m30s | Single | 6 | 2 | 12 | 18 | 3 | 15 |
| 4 | Potato | 4 | Spr, Fall | 12 / 10m | Single | 8 | 3 | 10 | 22 | 4 | 12 |
| 5 | Cabbage | 5 | Spr, Win | 18 / 15m | Single | 12 | 2 | 22 | 32 | 5 | 25 |
| 6 | Tomato | 7 | Sum | 24 / 20m | Regrow 12h / 10m | 30 | 3 | 8 | 24 | 3 | 10 |
| 7 | Strawberry | 9 | Spr | 30 / 25m | Regrow 12h / 10m | 45 | 3 | 9 | 27 | 3 | 12 |
| 8 | Sunflower | 11 | Sum, Fall | 36 / 30m | Single | 20 | 2 | 42 | 64 | 8 | Inedible, press to Oil |
| 9 | Eggplant | 13 | Sum, Fall | 36 / 30m | Regrow 18h / 15m | 60 | 2 | 20 | 40 | 4 | 20 |
| 10 | Pumpkin | 15 | Fall | 72 / 1h | Single | 40 | 1 | 160 | 120 | 12 | 50 |
| 11 | Melon | 18 | Sum | 96 / 1h20m | Single | 55 | 1 | 215 | 160 | 15 | 60 |
| 12 | Grapes | 21 | Sum, Fall | 120 / 1h40m | Regrow 48h / 40m | 120 | 5 | 22 | 110 | 6 | 10 |

**Growth:** watered = 100% speed. Dry = 60% speed (never stalls). Sprinklers water at planting. Rain, snow and storm waters at 06:00.

**Quality** (rolled once per plot harvest, shared by all units)
```
care  = watered (1) + hoe bonus (Iron +1, Gold +2), max 3
gold   = min(0.50, 0.01 + 0.004·L + 0.02·care + fertGold)
silver = min(0.90 − gold, 0.10 + 0.008·L + 0.06·care + fertSilver)
normal = remainder
```
These per-point coefficients are starting values for tuning. Multipliers: Normal ×1.0, Silver ×1.25, Gold ×1.5 (price, XP, energy).

| Fertilizer | Unlock | Source | Effect |
|---|---|---|---|
| Basic | L3 | Compost Bin (5 crops or 10 Plant Scraps → 2), or Wood Chipper Mulch (2 Wood → 3, 5 min, L7) | Silver +20%, Gold +5% |
| Quality | L14 | 2 Basic + 1 Coal (Workbench) | Silver +25%, Gold +15% |

Speed-Gro and Deluxe fertilizer → Later.

**Withering and full silo:** wither rules in §2. Full Silo blocks harvest. The crop waits on the plot.

---

## 7. Land and plot prices

Plot k (7–48) costs `niceRound(80 × 1.125^(k−7))`. A plot is buyable when your plot cap is at least k. The Greenhouse's 12 plots are separate and do not affect prices.

| Plots | Prices (coins) |
|---|---|
| 7–12 | 80, 90, 100, 110, 130, 140 |
| 13–18 | 160, 180, 210, 230, 260, 290 |
| 19–24 | 330, 370, 420, 470, 530, 590 |
| 25–30 | 670, 750, 840, 950, 1,050, 1,200 |
| 31–36 | 1,350, 1,500, 1,700, 1,900, 2,150, 2,450 |
| 37–42 | 2,750, 3,100, 3,450, 3,900, 4,400, 4,950 |
| 43–48 | 5,550, 6,250, 7,050, 7,900, 8,900, 10,000 |

Totals: plots 7–12 = 650. Plots 7–48 = 89,400.

---

## 8. Forest

### 8.1 Zones

| Zone | Unlock | Cost | Trees (native) | Other | Building slots | Unlock XP |
|---|---|---|---|---|---|---|
| A Edge Grove | L2 | Free | 6 Pine, 4 Birch, 2 Oak (Oak needs Stone axe) | 2 Wild Apple, 2 Berry Bush, 3 mushroom spots | F1, F2 | – |
| B Birch Hollow | L6 | 800c, 20 Wood | 7 Birch, 3 Pine, 2 Oak | 1 Berry Bush, 3 mushroom spots | F3, F4 | 50 |
| C Maple Ridge | L12 | 4,000c, 30 Planks, 5 Iron Bars | 6 Maple, 4 Oak | 1 Wild Apple | F5 | 100 |
| D Mushroom Glade | L16 | 9,000c, 40 Planks, 10 Hardwood | 5 Oak, 3 Birch, 2 Maple | 6 mushroom spots, Glowcap ×2 | F6 | 200 |
| E Ancient Heart | L24 | 25,000c, 10 Beams, 5 Gold Bars, 3 Varnish→**3 Gear** | 3 Ancient Oak (2×2), 4 Maple | – | F7 | 400 |

Totals: 51 tree spots (9 Pine, 14 Birch, 13 Oak, 12 Maple, 3 Ancient). Locked zones show brambles, a 50% dither and a signpost. Unlocking clears the brambles in a 1.5 s dither fade.

### 8.2 Trees

Hits with no crit, by tool damage (Wood 1, Stone 2, Iron 3, Gold 6). Stumps: 2 HP (Ancient stump 10).

| Tree | Zones | Min axe | HP | Hits W/S/I/G | Wood | Hardwood | Extra | Grow (real) | XP | Seed (chance) |
|---|---|---|---|---|---|---|---|---|---|---|
| Pine | A, B | Wood | 5 | 5 / 3 / 2 / 1 | 2–3 | – | Resin 30%, Amber 2% (rare), Bird Nest 1% | 30 min | 2 | Pinecone 25% |
| Birch | A, B, D | Wood | 6 | 6 / 3 / 2 / 1 | 2–4 | – | Sap 20%, Bird Nest 3% | 40 min | 3 | Birch Seed 25% |
| Oak | A–D | Stone | 12 | locked / 6 / 4 / 2 | 2–3 | 1–2 | Wild Mushroom 10%, Bird Nest 5% | 1h20m | 6 | Acorn 30% |
| Maple | C–E | Iron | 20 | locked / locked / 7 / 4 | 2 | 2–3 | Sap 1–2, Bird Nest 4% | 2h | 10 | Maple Seed 30% |
| Ancient Oak (2×2) | E | Gold | 60 | locked / locked / locked / 10 | 8 | 6 | Heartwood 1 (25% for a 2nd), Petrified Wood 10%, Bird Nest 10% | 4h40m (one season) | 40 | Golden Acorn 15% |
| Wild Apple | A, C | Not choppable | – | – | – | – | Apples: shake for 1 per 2 days, max 3, Summer and Autumn | – | 1 per shake | – |

- Planting a tree costs 0 energy and gives 1 XP. A seed of the spot's native type can be planted in any normal spot. Ancient spots accept only Golden Acorn.
- A spot left empty for 2 days reseeds as wild sapling. Stumps rot after 3 days.
- Growth time uses the planting season: Spring ×0.8, Summer ×1.0, Autumn ×1.0, Winter ×1.33.
- Damage on a living tree persists until felled. Crack overlay below 50% HP.

**Fell bonus.** Stump removal gives 1 Wood and 1 XP. Stump removal also has a 1% Old Coin and a 5% Wild Mushroom (Oak and Birch).

### 8.3 Forest objects (all 0 energy)

| Object | Spawn | Cap | Yield |
|---|---|---|---|
| Fallen Branch | 2 per dawn on free tiles (4 after storms) | 6 | 1 Wood, 10% +1 Resin |
| Mushroom | 1 per dawn (+1 with Glade, ×2 on rain) | 5 | Spring/Summer: Wild Mushroom. Autumn: 60% Wild, 40% Chanterelle. Night: 5% Glowcap. Winter: none |
| Berry Bush | Fixed spots, 1 berry per 2 days in Summer | 3 per bush | Blackberry |
| Wild Apple | See trees | 3 per tree | Apple |

Collect by tap or swipe.

### 8.4 Forest buildings and automation

| Building | Slot | Unlock | Cost | Recipe | Notes |
|---|---|---|---|---|---|
| Sawmill | F1 | L3 | 200c, 25 Wood, 10 Stone | 2 Wood → 1 Plank (1 min). 2 Hardwood → 1 Beam (8 min, L10) | No fuel |
| Wood Chipper | F2 | L7 | 700c, 12 Planks, 8 Stone | 2 Wood → 3 Mulch (5 min) | Mulch = Basic Fertilizer |
| Logging Hut | F3 | L9 | 1,500c, 30 Planks, 10 Stone | Auto-fells 1 tree per 20 min (Pine, Birch; 80% of manual yield; no crits). L2 at L15 adds Oak and 15-min interval; L3 at L22 adds Maple and 10-min interval | Never fells Ancient or hive trees. Clears stumps at L2+. No energy |
| Charcoal Kiln | F4 | L9 | 500c, 30 Stone, 15 Brick, 10 Wood | 5 Wood → 2 Coal (20 min) | Burns its own input |
| Syrup Kettle | F5 | L13 | 3,000c, 15 Planks, 4 Iron Bars | 3 Sap + 1 Wood → 1 Maple Syrup (15 min) | |
| Tree Nursery | F6 | L14 | 3,500c, 25 Planks, 6 Gear | Every 10 min: clears 1 stump or plants 1 seed from Barn in an empty spot. L2 at L18: every 5 min, 10% faster growth | Per-zone planting plan |

Processor queue: 3 slots at L1, 5 with +1 upgrade, 7 with the second upgrade (Forest default; the Machines design defers to this).

### 8.5 Events

All events appear at dawn. Timers start only the first time the Forest is open while the event is present. At most one of each type.

- **Wild Beehive (L5+):** attaches to a random mature Birch, Oak or Maple, lasts 6 game days. Smoke with 1 Torch or 4 energy for 3 Honey, 10 XP and a 25% Wildflower Seed. Chopping the tree without smoking stings for −10 energy and drops 1 Honey. The Logging Hut never touches a hive tree.
- **Dell the Lumberjack (L5+):** visits once per season at a random dawn from day 3 to day 12. Stays 9 game days. Offers, one each per visit:
  - **"Fell for me":** the player taps up to 5 mature trees, Dell fells them at 0 energy, keeps 20% of the Wood.
  - **"Buying wood":** buys up to 40 Wood or Planks at +60% of today's Market price.
- Each accepted deal gives +10 friendship if Dell is an order-board villager. He is not a villager in v1, so this is optional.

**Bird Nest** (never stored, toast on open): 40% 1–2 tree seeds; 25% 15–40 coins; 20% 1 crop seed; 10% Feather (worth 15); 4% Golden Feather; 1% gem.

---

## 9. Mine

### 9.1 Surface quarry (regrows, offline too)

| Quarry | Unlock | Cost | Spots | Weights (Stone / Coal / Copper / Clay / Quartz / Iron / Geode Rock) | Respawn per spot (real min) |
|---|---|---|---|---|---|
| Lv1 | L3 (screen unlock) | Free | 12 | 60 / 20 / 10 / 10 / 0 / 0 / 0 | 20 (±20% jitter) |
| Lv2 | L8 | 300c, 20 Planks, 30 Stone | 16 | 50 / 20 / 10 / 8 / 8 / 0 / 4 | 17 |
| Lv3 | L14 | 1,200c, 8 Iron Bars, 40 Stone | 20 | 42 / 18 / 10 / 6 / 8 / 10 / 6 | 14 |

- Quarry pacing: full Lv1 clear with a stone pickaxe takes about 45 s and about 16 energy.
- Storm end adds 1–2 Storm-split Boulders on empty spots.
- Notification (optional): "The quarry is full again."

### 9.2 Mine Mouth and elevator

- **Repair** the Mine Mouth (25 Wood + 100c) at L4 to open floors 1–9.
- The elevator panel lists Surface, B5, B10, B15 … B40 with padlocks and requirements. Rides are free and instant.
- Floors 5, 10 … 40 are elevator stops. Reaching a floor by ladder unlocks that stop permanently (+25 XP).
- The elevator remembers the deepest stop. The Up button returns to the surface for free. If you are not on a stop, a confirm dialog warns you that you restart from the last stop.

### 9.3 Bands and gates

| Band | Floors | Palette | Grid | Density | Dark chance | Geode |
|---|---|---|---|---|---|---|
| 1 | 1–10 | Topsoil browns | 9×9 | 45% | 0% | Geode |
| 2 | 11–20 | Dripstone teal and blue | 10×10 | 50% | 35% | Geode |
| 3 | 21–30 | Ember reds | 11×11 | 55% | 100% | Ember Geode |
| 4 | 31–40 | Crystal purples | 12×12 | 55% | 100% | Crystal Geode |

**Rock weights per band** (each row sums to 100; minimum floors apply)

| Band | Stone | Coal | Copper | Quartz | Iron (floor 6+) | Amethyst | Gold (floor 16+) | Emerald | Ruby (floor 25+) | Diamond | Geode Rock |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 58 | 15 | 15 | 5 | 4 | – | – | – | – | – | 3 |
| 2 | 45 | 14 | 10 | 5 | 16 | 4 | 2 | – | – | – | 4 |
| 3 | 40 | 14 | – | 3 | 18 | 3 | 12 | 3 | 2 | – | 5 |
| 4 | 35 | 10 | – | 5 | 15 | – | 18 | 4 | 4 | 2 | 7 |

**Gates** (each is also an elevator stop)

| Floor | Requirement | First-visit chest |
|---|---|---|
| 10 (Old Timber Gate) | L8 + Lantern + Stone pickaxe | 300c, 6 Torches |
| 20 (Flooded Iron Gate) | L14 + Iron pickaxe | 800c, 3 Ember Geodes |
| 30 (Ember Seal) | L20 + Gold pickaxe | 2,000c, 3 Crystal Geodes, 2 Gold Bars |
| 40 (Heart of the Mountain) | None | 5,000c, Crystal Heart decoration, "Rock Bottom" achievement |

Floor 40 becomes a repeatable Rich Vein floor (no ladder) after the first visit.

### 9.4 Floor generation (seeded, deterministic)

1. `seed = hash(saveSeed, floor, visitCounter)`. The floor is regenerated fresh each visit. Floors do not progress offline.
2. Build the band grid with a border wall and 2–4 wall blobs. Pick entry tile E (empty, next to the border).
3. Roll the special type: Normal 70%, Rich Vein 10% (ore ×2, stone ×0.5), Mushroom Grotto 8% (density 25%, 6–10 mushrooms), Geode Pocket 7% (geode weight ×4), Unstable 5% (floors 8+; 3 cave-in markers, ore drops +1).
4. Fill open tiles with rocks at band density. Mushrooms: band 1 has 0–2, bands 2–4 have 1–3. Crate on 20% of floors.
5. Choose the **ladder rock**: at least 4 Manhattan tiles from E, and its minimum tier must be no higher than the pickaxe tier.
6. Add cave-in markers (floors 8+: 25% of floors have 1–2 markers) and the dark flag.

**Finding the ladder**
- **Hollow knock:** hitting a rock within 1 tile of the ladder rock (Chebyshev) plays a wooden "tok" with a dust ring. Hitting the ladder rock itself plays "tok-tok".
- **Pity rule:** after 10 breaks without finding the ladder, the next breakable rock reveals it. Expected about 5–6 breaks.
- **Rope Ladder** (10 Stone + 1 Plank, L12): drag onto any empty tile to descend at once. Not on gate floors.

### 9.5 Nodes

| Node | Where | HP | Min pickaxe | Drops | XP |
|---|---|---|---|---|---|
| Stone | Quarry, floors 1–40 | 2 | Wood | 1 Stone (1–2 on floors); 2% Geode | 1 |
| Coal Rock | Quarry, floors 1–40 | 3 | Wood | 1 Coal (+1 at 30%) | 2 |
| Copper Vein | Quarry, floors 1–20 | 4 | Wood | 1–2 Copper Ore | 3 |
| Clay Bank | Quarry | 2 | Wood | 1–2 Clay | 1 |
| Quartz Cluster | Quarry Lv2+, floors 1–40 | 4 | Wood | 1 Quartz (+1 at 15%) | 3 |
| Geode Rock | Quarry Lv3, floors 1–40 | 5 | Stone | 1 geode of the band | 4 |
| Iron Vein | Quarry Lv3 (rare), floors 6+ | 6 | Stone | 1–2 Iron Ore | 5 |
| Amethyst Node | Floors 11–30 | 8 | Stone | 1 Amethyst | 8 |
| Gold Vein | Floors 16+ | 10 | Iron | 1–2 Gold Ore | 8 |
| Emerald Node | Floors 21–40 | 10 | Iron | 1 Emerald | 12 |
| Ruby Node | Floors 25–40 | 12 | Iron | 1 Ruby | 14 |
| Diamond Node | Floors 31–40 | 14 | Gold | 1 Diamond | 25 |
| Storm-split Boulder | Quarry, after storms | 8 | Stone | 60%: 3–5 Iron Ore; 30%: 2–3 Gold Ore; 10%: Geode + 2 Quartz | 10 |
| Rubble (cave-in) | Floors | 2 | Wood | 1 Stone at 50% | 0 |
| Treasure Crate | 20% of floors | 1 | Wood | 20–60c, 2–4 Torches, or an in-season seed | 1 |
| Glow Mushroom | Floors | – | – | 1 Cave Mushroom (+10 energy) | 1 |

- **Depth bonus:** ore and gem nodes on floor N have an N% chance of +1 item.
- **Locked rock** (tier too low) shows a shimmer. Hitting it costs 0 energy and shows "Needs Iron Pickaxe".
- Floor rocks never respawn within a visit.

### 9.6 Hazards

| Hazard | Floors | Tell | Effect | Counter |
|---|---|---|---|---|
| Darkness | 11–20 (35%), 21–40 (all) | Bayer-dither dark overlay | Unlit rocks show as "?". Type revealed on first hit. Hits still cost 2 energy | **Lantern** (L8: 2 Copper Bars, 1 Quartz, 2 Coal; light radius 2 and ore sparkle 1 beyond). **Torch** dragged onto a tile (radius 3 for the visit) |
| Cave-in | 8+ | Cracked beam on the ceiling | Breaking within 2 tiles of a marker has a 30% chance of a 2.5 s rumble. If not braced: 3×3 around the marker fills with Rubble and you lose 5 energy | Tap the marker 3× during the rumble to brace (+5 XP). **Support Beam** (2 Planks, L9) disarms a marker for the visit |
| Geodes | All | Bumpy rock | Contents unknown until cracked | Geode Anvil (M2) |

### 9.7 Geode Anvil and geode contents

Drag a geode onto the Anvil and tap 3 times. Fee: Geode 10c, Ember 25c, Crystal 50c.

| Geode | Ore bundle | Mineral (book) | Gem | Prismatic Shard |
|---|---|---|---|---|
| Geode | 40%: 2–4 Copper or Iron Ore, or 2 Coal | 50%: Calcite, Jasper, Mica, Agate, Fluorite | 8% Quartz ×2, 2% Amethyst | – |
| Ember Geode | 35%: 2–3 Gold Ore or 3–5 Iron Ore | 55%: Obsidian, Garnet, Jet, Fire Opal, Sunstone | 6% Emerald or Ruby, 3.5% Amethyst | 0.5% |
| Crystal Geode | 31%: 3–4 Gold Ore | 58%: Moonstone, Celestine, Opal, Star Sapphire, Aurora Quartz | Emerald 4%, Ruby 3.5%, Diamond 2.5% | 1% |

### 9.8 Mine machines

| Machine | Slot | Unlock | Cost | Fuel | Function |
|---|---|---|---|---|---|
| Furnace | M1 | L5 | 400c, 30 Stone, 10 Clay, 6 Planks, 5 Coal | Coal | Ore → bars, Brick, Glass (§12) |
| Geode Anvil | M2 | L7 | 200c, 30 Stone, 2 Copper Bars | None | Cracks geodes |
| Drill Rig | M4 | L19 | See §11 | Coal | Auto-mines quarry and deep ore |

Lapidary Bench and Rock Crusher → Later.

### 9.9 Mine tutorial tips

- Locked rock: "Needs a Stone Pickaxe."
- First floor: "Find the ladder. Listen for a hollow knock!"
- B5: "The elevator saves every 5 floors."
- First dark floor: "Drag a torch to light the dark."
- First rumble: "Tap the crack 3× to brace!"
- First geode: "Crack geodes at the Anvil."

---

## 10. Tools, Workbench and storage

### 10.1 Tools and tiers

Five tools: Axe, Pickaxe, Hoe, Watering Can, Sickle. The player starts with Wood tier for all five. Tools never break, and there is no repair or durability.

**Axe and pickaxe** (one row per tier; pattern is an offset list centred on the tapped tile; splash hits are half damage, rounded up; energy is charged once per swing)

| Tier | Unlock | Damage | Pattern | Splash | Crit | Bonus drop | Can break |
|---|---|---|---|---|---|---|---|
| Wood | Start | 1 | Single | – | 5% | 0% | Axe: saplings, young and mature trees. Pick: stone, coal, copper, clay, quartz rocks |
| Stone | L4 | 2 | Single | – | 8% | 0% | + stumps (axe), + iron veins (pick), + geode rocks |
| Iron | L10 | 3 | Plus5 | 2 | 12% | 10% | + ancient logs (axe), + gold veins and gem rocks (pick) |
| Gold | L18 | 6 | Square9 | 3 | 15% | 20% | Everything |

**Hoe, Watering Can, Sickle**

| Tool | Wood | Stone | Iron | Gold |
|---|---|---|---|---|
| Hoe (pattern; extra) | Single; tills, clears weeds and withered crops | Plus5 | Square9; +1 care | Square25; +2 care |
| Can (pattern; capacity in tiles; extra) | Single; 12 | Plus5; 25 | Square9; 50 | Square25; 100; soil stays wet 50% longer |
| Sickle (pattern; bonus crop chance; fibre per bush) | Single; 0%; 1 | Plus5; 5%; 1 | Square9; 10%; 2 | Square25; 20%; 2 |

- **Wood-tier swings to break** (axe, no crit): Sapling 1 / Young tree 3 / Mature tree 5–6 / Stump 2 / Ancient 20 (Gold axe: 4).
- Watering uses one charge per tile watered. The Well (fixed Farm slot) and water tiles refill free. Rain refills to full.

**Tier upgrade costs** (new tool replaces old; you keep the old one until the timer ends)

| Tier | Tool | Cost | Time |
|---|---|---|---|
| Stone (L4) | Hoe | 6 Wood, 10 Stone, 40c | 1 min |
| Stone | Axe | 8 Wood, 15 Stone, 50c | 2 min |
| Stone | Pickaxe | 10 Wood, 12 Stone, 50c | 2 min |
| Stone | Sickle | 5 Wood, 8 Stone, 2 Rope, 60c | 2 min |
| Stone | Can | 4 Plank, 2 Copper Bar, 80c | 3 min |
| Iron (L10) | Axe / Pickaxe | 5 Iron Bar, 4 Plank, 2 Hardwood, 400c | 15 min |
| Iron (L10) | Hoe | 3 Iron Bar, 3 Plank, 6 Nails, 300c | 15 min |
| Iron (L10) | Can | 4 Iron Bar, 2 Copper Bar, 6 Nails, 400c | 20 min |
| Iron (L10) | Sickle | 3 Iron Bar, 3 Plank, 4 Rope, 350c | 20 min |
| Gold (L18) | Axe / Pickaxe | 4 Gold Bar, 3 Iron Bar, 5 Hardwood, 1,500c | 45 min |
| Gold (L18) | Hoe | 3 Gold Bar, 2 Iron Bar, 3 Hardwood, 1,200c | 45 min |
| Gold (L18) | Can | 3 Gold Bar, 3 Iron Bar, 2 Gear, 1,500c | 60 min |
| Gold (L18) | Sickle | 3 Gold Bar, 2 Iron Bar, 6 Rope, 3 Hardwood, 1,500c | 60 min |

**Resource gate chain:** Stone axe fells stumps, which give Hardwood for Iron axe. Stone pickaxe breaks iron veins, which give Iron Bars for Iron tier. Iron pickaxe breaks gold veins, which give Gold Bars for Gold tier.

### 10.2 Workbench

- Pre-built at a fixed Farm slot. The hammer button opens it from any screen.
- **Queue:** each queue slot runs one recipe at a time. Batch time = per-unit time × units. Inputs are taken at craft start. Cancelling refunds 100%.
- Finished parts wait in their slot until the Barn has space. Tools equip themselves when done.
- Timers use the shared game clock and offline cap. No speed-ups in v1.

| Workbench level | Unlock | Queue slots | Max batch | Cost |
|---|---|---|---|---|
| 1 | Start | 1 | 5 | – |
| 2 | L7 | 2 | 10 | 12 Planks, 8 Nails, 4 Rope, 400c |
| 3 | L16 | 3 | 20 | 6 Iron Bars, 4 Hardwood, 2 Gear, 1,500c |

**Recipes at the Workbench**

| Recipe | Inputs → output | Time / unit | Unlock | Tab |
|---|---|---|---|---|
| Plank | 3 Wood → 1 | 30 s | L1 | Parts |
| Rope | 3 Fibre → 1 | 30 s | L3 | Parts |
| Nails | 1 Copper Bar → 4 | 30 s | L5 | Parts |
| Gear | 1 Iron Bar + 1 Copper Bar → 2 | 2 min | L10 | Parts |
| Torch | 1 Wood + 1 Resin → 3 | 2 min | L6 | Parts |
| Lantern | 2 Copper Bar + 1 Quartz + 2 Coal → 1 | 5 min | L8 | Parts |
| Support Beam | 2 Plank → 1 | 3 min | L9 | Parts |
| Rope Ladder | 10 Stone + 1 Plank → 1 | 5 min | L12 | Parts |
| Quality Fertilizer | 2 Basic Fertilizer + 1 Coal → 1 | 2 min | L14 | Farm |
| Scarecrow | 10 Wood + 2 Wheat → 1 | 3 min | L5 | Farm |
| Lightning Rod | 4 Copper Bar + 1 Iron Bar + 10 Stone → 1 | 10 min | L5 | Farm |

Tool upgrades appear in the Tools tab with a mini pattern diagram, the next tier's changes, cost and an UPGRADE button. Missing inputs show a GO button that switches screen and pans to the source.

### 10.3 Materials

Base sell prices are before the daily swing (§13).

| Material | Source | Use | Price |
|---|---|---|---|
| Wood | Trees, branches, stumps, Wood Chipper | Planks, buildings, Kiln, Torches | 3 |
| Hardwood | Oak, Maple, Ancient, stump | Beams, Iron/Gold tools | 12 |
| Fibre | Grass, bushes, weeds | Rope | 1 |
| Stone | Quarry, mine | Stone tools, buildings | 2 |
| Coal | Coal rock, Kiln | Furnace and Drill fuel | 6 |
| Copper / Iron / Gold Ore | Quarry, mine | Furnace | 6 / 12 / 25 |
| Planks | Workbench, Sawmill | Buildings, upgrades | 6 |
| Copper / Iron / Gold Bar | Furnace | Tools, machines, storage | 30 / 55 / 105 |
| Nails, Rope, Gear | Workbench | Upgrades | 8 / 5 / 40 |

### 10.4 Storage (Barn and Silo)

- **Silo** holds crops of all qualities.
- **Barn** holds everything else: materials, parts, processed goods, food, fertilizer, placeable items.
- Tools, seeds and coins take no space.
- Check happens **before** an action. A swing or harvest is allowed if at least one slot is free. The HUD icon turns red at 90%.
- Upgrades are instant, with a fanfare and 20 XP × new level. The sprite changes at levels 4, 7 and 10.

**Barn** (start 100)

| To | Capacity | Unlock L | Planks | Nails | Rope | Iron Bar | Gold Bar | Coins |
|---|---|---|---|---|---|---|---|---|
| 2 | 125 | 3 | 6 | – | 2 | – | – | 200 |
| 3 | 150 | 6 | 10 | 6 | 3 | – | – | 400 |
| 4 | 175 | 8 | 14 | 10 | 4 | – | – | 700 |
| 5 | 200 | 11 | 18 | 14 | 6 | 2 | – | 1,000 |
| 6 | 250 | 14 | 22 | 18 | 8 | 4 | – | 1,500 |
| 7 | 300 | 17 | 26 | 22 | 10 | 6 | – | 2,100 |
| 8 | 350 | 20 | 30 | 26 | 12 | 8 | 1 | 2,800 |
| 9 | 425 | 24 | 35 | 30 | 15 | 10 | 2 | 3,800 |
| 10 | 500 | 28 | 40 | 35 | 18 | 12 | 3 | 5,000 |

**Silo** (start 75)

| To | Capacity | Unlock L | Stone | Planks | Nails | Iron Bar | Hardwood | Coins |
|---|---|---|---|---|---|---|---|---|
| 2 | 100 | 2 | 12 | 4 | – | – | – | 120 |
| 3 | 125 | 5 | 20 | 8 | – | – | – | 300 |
| 4 | 150 | 7 | 30 | 12 | 6 | – | – | 550 |
| 5 | 175 | 10 | 40 | 15 | 10 | 2 | – | 850 |
| 6 | 200 | 13 | 50 | 20 | 14 | 4 | – | 1,250 |
| 7 | 250 | 16 | 65 | 25 | 18 | 6 | 2 | 1,800 |
| 8 | 300 | 19 | 80 | 30 | 22 | 8 | 4 | 2,500 |
| 9 | 350 | 23 | 100 | 35 | 26 | 10 | 6 | 3,400 |
| 10 | 400 | 27 | 120 | 40 | 30 | 12 | 8 | 4,600 |

---

## 11. Machines and automation (v1)

### 11.1 Rules

- **Slots:** Farm has 12 workshop slots, Forest F1–F7 (zone-gated), Mine M1–M4. A machine goes in any free slot of its screen. Build, move (free) or demolish (50% coin refund, no materials). Field fixtures use vertices.
- **Queue:** tap the machine, drag a recipe onto it. Inputs leave storage at once. Finished batches show a bouncing bubble, and one tap collects all.
- **One of each** machine in v1, except Preserves Jar (2), Beehive (3: L8, L12, L16) and Harvest Shed/Planter Shed (1 each).
- **Speed:** all v1 machines are T1 (`time = baseTime`). T2/T3 tiers and the Hopper auto-mode → Later. Exceptions: Harvest Shed and Planter Shed are automation by design.
- **Fuel:** only the Furnace and Drill Rig burn Coal. Fuel only burns while a batch runs. An empty tank pauses and keeps progress.
- **Blocked:** full output storage pauses the machine with a red "!". No fuel shows a coal icon.
- **Timers:** absolute game-clock timestamps, never ticked per frame.
- **Determinism:** random rolls seeded by `(saveSeed, machineId, batchIndex)`.

### 11.2 v1 machine list

| Machine | Screen / slot | Unlock | Build cost | Fuel | Role |
|---|---|---|---|---|---|
| Windmill | Farm | L2 | 150c, 15 Wood, 10 Wheat | – | Flour, Cornmeal |
| Sawmill | Forest F1 | L3 | 200c, 25 Wood, 10 Stone | – | Planks, Beams |
| Compost Bin | Farm | L3 | 100c, 10 Wood, 4 Planks | – | Basic Fertilizer |
| Bakery Oven | Farm | L4 | 300c, 10 Planks, 25 Stone | – | Dishes (glows at night) |
| Furnace | Mine M1 | L5 | 400c, 30 Stone, 10 Clay, 6 Planks, 5 Coal | Coal (6 min per coal, tank 20) | Bars, Brick, Glass |
| Lightning Rod | Field vertex | L5 | 250c, 10 Stone, 4 Copper Bar, 1 Iron Bar | – | Lightning protection (10×10) |
| Scarecrow | Field vertex | L5 | 150c, 10 Wood, 2 Wheat | – | Wind protection (6×6) |
| Copper Sprinkler | Field vertex | L6 | 50c, 2 Copper Bar, 1 Planks | – | Waters 4 plots at 06:00 and at planting |
| Wood Chipper | Forest F2 | L7 | 700c, 12 Planks, 8 Stone | – | Mulch |
| Preserves Jar | Farm | L7 | 200c, 4 Planks, 2 Glass, 1 Copper Bar (2nd: ×1.5) | – | Jam, Pickles |
| Geode Anvil | Mine M2 | L7 | 200c, 30 Stone, 2 Copper Bars | – | Cracks geodes |
| Beehive | Farm | L8 | 250c, 12 Planks, 6 Wheat, 1 Copper Bar | – | Honey (up to 3) |
| Logging Hut | Forest F3 | L9 | 1,500c, 30 Planks, 10 Stone | – | Auto-fells trees |
| Charcoal Kiln | Forest F4 | L9 | 500c, 30 Stone, 15 Brick, 10 Wood | – | Coal |
| Seed Maker | Farm | L9 | 400c, 6 Planks, 2 Copper Bars, 2 Iron Bars | – | Seeds from crops |
| Fruit Press | Farm | L10 | 800c, 20 Planks, 5 Copper Bars, 3 Iron Bars | – | Juice |
| Syrup Kettle | Forest F5 | L13 | 3,000c, 15 Planks, 4 Iron Bars | – | Maple Syrup |
| Market Cart | Farm | L13 | 1,200c, 20 Planks, 6 Iron Bars, 4 Copper Bars | – | Auto-sell by rules |
| Oil Press | Farm | L14 | 1,500c, 12 Planks, 10 Brick, 6 Iron Bars | – | Cooking Oil |
| Tree Nursery | Forest F6 | L14 | 3,500c, 25 Planks, 6 Gear | – | Auto-replant |
| Greenhouse | Farm (fixed) | L12 | 25,000c, 100 Planks, 60 Stone, 15 Iron Bars, 20 Glass | – | 12 plots, any season, weatherproof, no rain |
| Harvest Shed | Farm | L16 | 3,000c, 25 Planks, 10 Iron Bars, 4 Copper Bars, 3 Cooking Oil | – | Auto-harvest 8 plots per 20 min |
| Planter Shed | Farm | L18 | 3,500c, 25 Planks, 12 Iron Bars, 6 Brick, 3 Cooking Oil | – | Auto-plant 8 plots per 20 min (Replant and Fill modes) |
| Drill Rig | Mine M4 | L19 | 4,000c, 40 Stone, 10 Brick, 6 Beams, 14 Iron Bars, 4 Cooking Oil | Coal (1 per 10 min, tank 20) | 4 Stone + 1 quarry roll per 10 min. Runs offline, capped by fuel |
| Greenhouse Expansion | Farm | L20 | 60,000c, 200 Planks, 30 Iron Bars, 10 Gold Bars | – | +12 greenhouse plots |

**Farm workshop slots:** 12 total. Starting 4, then +1 at L6, L9, L12, L14, L16, L18, L22.

**Automation specifics**
- **Harvest Shed:** order is plots that would wither next, highest quality, longest-ripe. Crops go to Silo. If the Silo is full, the shed pauses with a red "!".
- **Planter Shed:** Replant (remembers last crop) or Fill (one chosen seed). Season-safe: never plants a crop whose grow time exceeds the season left. Seeds come from the Silo. Planting triggers sprinklers.
- **Automated output:** 50% XP.
- **Hands-off limits:** throughput caps, storage caps, Market Cart daily cap, jars and hives always need a tap.

**Field fixtures** (vertices between plots, never use a crop plot)
- Sprinkler coverage: Copper r=1 (4 plots), Iron r=2 (16), Gold r=3 (36). Gold sprinkler L24.
- A plot is protected from lightning if a Rod covers it, and from wind if a Scarecrow covers it.
- Placement shows a dithered overlay of covered plots: blue for water, yellow for scarecrow, purple for rod.

### 11.3 Market Cart (L13)

- A rule has an item or category, a price condition (Any, ▲ ≥ base, ≥ +10%, ≥ +20%), and a keep-at-least amount (default 10).
- Sells once per game day at 07:00, after prices roll. Rules run in priority order up to a daily cap (T1 only: 15 units per day).
- Never sells Pouch items (gems, Tools) unless named.
- Sales log covers 7 game days. "While you were away" shows totals.

---

## 12. Recipes (v1)

Machine recipes. Times are real minutes at T1. Energy is the dish energy restored.

**Windmill (L2)**

| Recipe | Inputs → output | Time | Unlock | Value | Energy |
|---|---|---|---|---|---|
| Flour | 2 Wheat → 1 Flour | 4 | L2 | 12 | – |
| Cornmeal | 2 Corn → 1 Cornmeal | 6 | L5 | 26 | – |

**Sawmill and Kiln**

| Recipe | Inputs → output | Time | Unlock | Value |
|---|---|---|---|---|
| Plank | 2 Wood → 1 Plank | 1 | L3 | 6 |
| Beam | 2 Hardwood → 1 Beam | 8 | L10 | 30 |
| Coal | 5 Wood → 2 Coal (Kiln) | 20 | L9 | 6 each |

**Furnace (L5 unless noted)**

| Recipe | Inputs → output | Time | Unlock | Value | Fuel |
|---|---|---|---|---|---|
| Copper Bar | 3 Copper Ore → 1 | 3 | L5 | 30 | 0.5 coal |
| Brick | 2 Clay → 1 | 2 | L5 | 7 | 0.33 coal |
| Iron Bar | 3 Iron Ore → 1 | 6 | L6 | 55 | 1 coal |
| Glass | 1 Quartz → 2 | 3 | L6 | 8 each | 0.5 coal |
| Gold Bar | 3 Gold Ore → 1 | 10 | L15 | 105 | 1.7 coal |

**Bakery Oven (L4+)**

| Dish | Inputs → output | Time | Unlock | Value | Energy |
|---|---|---|---|---|---|
| Bread | 2 Flour → 1 | 8 | L4 | 40 | 30 |
| Roast Veggies | 1 Potato + 1 Carrot → 1 | 6 | L5 | 26 | 20 |
| Cornbread | 2 Cornmeal → 1 | 10 | L6 | 80 | 35 |
| Strawberry Tart | 1 Flour + 1 Strawberry Jam → 1 | 15 | L9 | 100 | 45 |
| Pumpkin Pie | 1 Flour + 1 Pumpkin + 1 Honey → 1 | 25 | L11 | 110 | 60 |
| Pancakes | 2 Flour + 1 Maple Syrup → 1 | 12 | L12 | 85 | 50 |
| Veggie Stew | 1 Cabbage + 1 Carrot + 1 Potato + 1 Tomato → 1 | 20 | L13 | 80 | 70 |
| Potato Chips | 2 Potato + 1 Cooking Oil → 1 | 10 | L15 | 75 | 30 |
| Ratatouille | 1 Eggplant + 1 Tomato + 1 Cooking Oil → 1 | 18 | L16 | 95 | 55 |

**Compost, Preserves, Press, Oil, Beehive, Seed Maker**

| Machine (unlock) | Recipe | Time | Value | Energy |
|---|---|---|---|---|
| Compost Bin (L3) | 5 crops (any mix) or 10 Plant Scraps → 2 Basic Fertilizer | 30 | 15 each | – |
| Preserves Jar (L7) | 2 identical fruit → Jam | 30 | ×1.8 | 15 |
| Preserves Jar (L7) | 2 identical vegetables → Pickles | 30 | ×1.8 | 10 |
| Fruit Press (L10) | 3 identical crops → Juice | 40 | ×1.7 | 20 (Melon, Grape: 30) |
| Oil Press (L14) | 3 Sunflower → 2 Cooking Oil | 20 | 28 each | – |
| Oil Press (L14) | 5 Corn → 2 Cooking Oil | 30 | 28 each | – |
| Beehive (L8) | → 1 Honey | 40 | 25 | 15 |
| Seed Maker (L9) | 1 crop → 2 seeds (25% chance of 3rd) | 8 | – | – |
| Syrup Kettle (L13) | 3 Sap + 1 Wood → 1 Maple Syrup | 15 | 40 | 20 |

Jam, Pickles and Juice exist per crop: Strawberry Jam (value 50), Melon Jam (144), Grape Jelly (79); Pickled Carrot (22), Pickled Potato (29), Pickled Tomato (36), Pickled Corn (32), Pickled Pumpkin (108), Sauerkraut (65), Pickled Eggplant (58); Carrot Juice (31), Tomato Juice (51), Strawberry Juice (71), Melon Juice (204), Grape Juice (112).

**Market Cart and automation:** Harvest Shed, Planter Shed, Logging Hut and Tree Nursery run on timers (§11, §8.4). Drill Rig: 4 Stone + 1 quarry roll (Coal 30%, Copper 35%, Clay 25%, Iron 10%) per cycle.

---

## 13. Market

**Selling price per unit**
```
unitPrice = max(1, round(base × dailyMult × seasonMult × qualityMult × supplyMult))
```

**Daily swing (±30%, deterministic)**
```kotlin
fun dailyMult(item: Int, day: Long): Double {
    val knot = Math.floorDiv(day, 4L)                   // trend knot every 4 game days
    val f = (day - knot * 4) / 4.0
    val a = hash01(seed, item, knot) * 2 - 1
    val b = hash01(seed, item, knot + 1) * 2 - 1
    val s = (1 - cos(PI * f)) / 2                       // cosine ease
    val trend  = 0.20 * (a + (b - a) * s)               // slow wave ±20%
    val jitter = 0.10 * (hash01(seed, item, day, 7L) * 2 - 1)   // daily noise ±10%
    return (1 + trend + jitter).coerceIn(0.70, 1.30)
}
// hash01: SplitMix64 over (seed xor key terms), mapped to [0,1)
```

- **Season:** a crop sold out of season (Greenhouse or stored) gets ×1.25. Wheat never does.
- **Supply saturation:** each item keeps `glutValue`, base coins sold recently. `T = 200 + 100·L`, `supplyMult = max(0.50, 1 − 0.25 × max(0, glut − T)/T)`. Each unit sold adds its base. Each new game day, `glut ×= 0.5` (offline: ×0.5^days). The sell sheet shows the preview, for example "40 Melons: avg 171 (flooded −20%)". Orders and the Collection Book ignore saturation.
- **Arrows:** ▲ green when today − yesterday ≥ +0.03, ▼ red when ≤ −0.03, ▬ otherwise. Each item shows "% vs base" and a 7-day sparkline.
- **Buying:** seeds have fixed prices and unlimited stock, and can be bought in any season. Emergency buys: Wood 9, Stone 6, Coal 18, Copper Ore 15 (3× base), up to 50 each per day.
- **Value-add:** processed base ≈ niceRound(1.4 × Σ input base). Fuel converters are roughly break-even.

**Base sell prices** are in the master items table (§16).

---

## 14. Orders and villagers

**Order generation** (per empty slot when its timer has finished)
```
villager  = weighted pick among unlocked villagers with no active order
lines     = L1–4: 1 (60%) / 2 (40%); L5–14: 1/2/3 = 30/50/20%; L15+: 2/3 = 50/50%
V         = (20 + 10·L + 1.6·L²) × U(0.8, 1.2)
Big Order (L10+, 10%): 3–4 lines, V × 2.5, plus a reward item
items     = unlocked AND obtained at least once; skip out-of-season crops unless stored or Greenhouse built
weights   = favorite ×3, in-season ×1.5, in storage ×1.5, requested in last 2 orders ×0.3
qty       = clamp(round((V/lines)/base), 1, 40)
coins     = niceRound(Σ qty·base × 1.35 × (1 + heartBonus))
XP        = round(0.30 × Σ qty·base) + 5 × lines, minimum 10
```
Rules: delivery uses lowest quality first. The next order arrives after 2 min (L1–4) or 5 min (L5+). A trashed order waits 15 min. Orders never expire.

**Order item families:** L2–6: Flour, Planks, Bread, Copper Bar. L7–12: Jams, Pickles, Honey, Brick, Glass, Iron Bar, Cornbread, Tart. L13–20: Juices, Oil, Pie, Pancakes, Stew, Beam. L21+: Gold Bar, Chips, Ratatouille. Forest items: Planks (L3), Apples and Mushrooms (L5), Torches (L8), Coal (L11), Maple Syrup (L13), Honey (any). Mine: bars, gems.

**Villagers** (8)

| Villager | Role | Unlock | Favorites (×3 weight) | Heart-5 perk |
|---|---|---|---|---|
| Mara | Baker | L1 | Wheat, Flour, Corn, Bread, Pumpkin Pie | Oven 15% faster |
| Tobias | Carpenter | L1 | Wood, Planks, Stone, Hardwood | Storage upgrades 15% cheaper |
| Ivy | Florist | L3 | Sunflower, Strawberry, Carrot, Honey | +3% Gold quality chance |
| Bram | Blacksmith | L6 | Coal, Ores, Bars | Tool upgrades −15% coins |
| Juniper | Innkeeper | L9 | Potato, Tomato, Cabbage, any dish | Dishes +20% energy |
| Pip | Village kid | L12 | Carrot, Strawberry, Melon, Quartz, Jam | Mine ladders appear 15% more often |
| Sela | Vintner | L16 | Grapes, Juice, Oil, Cut Gems | Merchant −15% prices |
| Doc Fennel | Herbalist | L20 | Cabbage, Eggplant, Honey, Pickles, Amethyst | +15 max energy, +10% regen |

**Friendship:** each order gives 10 + 5 per line, +5 per favorite line, +20 for a Big Order. Hearts at 100 / 250 / 500 / 850 / 1,300. Heart 1: +5% coins. Heart 2: one gift. Heart 3: +10% coins and 20% of orders include an item. Heart 4: a recipe or blueprint gift. Heart 5: the perk above.

---

## 15. Goals: achievements, collection, events

### 15.1 Achievements (tiers count once)

| Achievement | Condition (tiers) | Reward per tier |
|---|---|---|
| First Sprout | Harvest 1 crop | 50c, 25 XP |
| Green Thumb I–III | 250 / 2,500 / 25,000 crop units | 500c / 5,000c / 25,000c |
| Full Basket | Harvest all 12 crops | 3,000c |
| Star Farmer | 100 Gold-star crops | 5,000c |
| Four Seasons | Reach Spring Year 2 | 1,000c |
| Greenhouse Effect | Sell an out-of-season crop | 1,500c |
| Lumberjack I–II | 100 / 1,000 trees | 800c / 8,000c |
| Rock Breaker I–II | 250 / 2,500 rocks or nodes | 800c / 8,000c |
| Spelunker I–II | Reach floor 25 / 50 | 3,000c / 15,000c |
| Gem Hunter | All 4 gem types | 5,000c |
| Land Baron I–II | Own 24 / 48 plots | 1,500c / 15,000c |
| Industrialist I–II | Build 5 machines / every v1 machine type | 1,000c / 30,000c |
| Market Wizard | Sell 20+ of one item at ≥ +20% | 500c |
| Order Up! I–III | 25 / 250 / 1,000 orders | 1,000c / 10,000c / 40,000c |
| Best Friends I–II | Heart 5 with one villager / all 8 | 2,000c / 25,000c |
| Storm Chaser | Storm with 12+ crops and no damage | 1,000c |
| Toolsmith I–II | All tools at Iron / Gold | 3,000c / 20,000c |
| Hearty Eater | Eat 50 dishes | 2,000c |
| Bookworm I–II | 1 collection page / all pages | 2,000c / 30,000c |
| Tycoon I–II | 100,000 / 1,000,000 coins earned | 5,000c / 25,000c |
| Elder Feller I–II (v1 addition) | 1 / 10 Ancient Oaks felled | 500 XP / 3 Heartwood |
| Bird Watcher (v1 addition) | 5 / 25 Bird Nests | 100c / 400c |
| Rock Bottom (v1 addition) | Floor 40 | 5,000c |
| Steady Hands (v1 addition) | 10 cave-ins braced | 1,000c |

### 15.2 Collection Book (pages)

Each entry is a silhouette until found, then shows the icon, first date, total produced, best quality and base price.

| Page | Entries | Page reward |
|---|---|---|
| Crops | 12 crops × 3 star stamps | All 12: Seed Shop −10%. All stars: +2% Gold chance |
| Forest | Wood, Hardwood, Resin, Sap, Pinecone, Birch Seed, Acorn, Maple Seed, Golden Acorn, Wild Mushroom, Chanterelle, Glowcap, Honey, Heartwood, Amber, Petrified Wood, Old Coin, Golden Feather, Feather | +10 max energy |
| Mine | Coal, Copper Ore, Iron Ore, Gold Ore, Quartz, Clay, 4 gems, 15 minerals, Prismatic Shard | +5% sell price on ores |
| Goods | Flour, Planks, 3 bars, Oil, Jam, Pickles, Juice, Honey, Basic and Quality Fertilizer | Machines 5% faster |
| Kitchen | 9 dishes | Dishes +10% energy |
| Villagers | 8 portraits and Heart-5 stamp | All met: orders 1 min sooner |

### 15.3 Events

- **Seasonal festival** (one per season, mini-goal, reward 50 + 10 × L XP plus coins):
  - Spring Arbor Day: plant 20 saplings or crops in the festival window. Reward Golden Acorn + 500c.
  - Summer Jam Fair: deliver 10 jams or jellies.
  - Autumn Pie Contest: deliver 5 Pumpkin Pies.
  - Winter Frost Fair: sell 50 crops.
  - Festival days are Sunny.
- **Daily login gift:** coins about 5% of a typical day's income, plus XP 5 × streak day. Day 7 gives 5 Planks + 5 Coal.
- **Traveling Merchant** (from L10, every 7 game days): 6 stock items at 3–8× base: rare seeds, Rope Ladder, Torch bundle, Golden Acorn (600c, L20+), Apple Sapling (250c), Iron or Gold tier kits (inputs for one upgrade at about 1.5× value).

---

## 16. Master items table

Every item has one canonical snake_case id. "Family" rows expand to one id per listed crop. Base sell price is before the ±30% swing. "—" means not sellable.

| id | Name | Category | Source | Uses | Base sell |
|---|---|---|---|---|---|
| wheat | Wheat | Crop | Field | Flour, orders, Scarecrow, Windmill, Beehive | 3 |
| carrot | Carrot | Crop | Field | Eat, Roast Veggies, Stew, orders | 8 |
| corn | Corn | Crop | Field | Cornmeal, Cornbread, Oil | 12 |
| potato | Potato | Crop | Field | Roast Veggies, Stew, Chips | 10 |
| cabbage | Cabbage | Crop | Field | Stew, Pickles | 22 |
| tomato | Tomato | Crop | Field | Stew, Ratatouille, Juice | 8 |
| strawberry | Strawberry | Crop | Field | Tart, Jam, Juice | 9 |
| sunflower | Sunflower | Crop | Field | Cooking Oil, Sunflower Honey | 42 |
| eggplant | Eggplant | Crop | Field | Ratatouille, Pickles | 20 |
| pumpkin | Pumpkin | Crop | Field | Pumpkin Pie, Pickles | 160 |
| melon | Melon | Crop | Field | Eat, Melon Jam, Juice | 215 |
| grapes | Grapes | Crop | Field | Grape Jelly, Juice | 22 |
| seed_wheat … seed_grapes | Seeds (12) | Seed | Seed Shop, Seed Maker, Bird Nest | Planting only | — |
| withered_crop | Withered Crop | Crop | Season wither, scorch | Compost Bin input | — |
| plant_scraps | Plant Scraps | Fertilizer input | Clearing withered plots | Compost Bin (10 → 2 Basic) | — |
| flour | Flour | Food input | Windmill (2 Wheat) | Bread, Tart, Pie, Pancakes | 12 |
| cornmeal | Cornmeal | Food input | Windmill (2 Corn) | Cornbread | 26 |
| cooking_oil | Cooking Oil | Goods | Oil Press | Chips, Ratatouille, Harvest Shed, Planter Shed | 28 |
| bread | Bread | Dish | Bakery Oven | Eat, orders | 40 |
| cornbread | Cornbread | Dish | Bakery Oven | Eat, orders | 80 |
| roast_veggies | Roast Veggies | Dish | Bakery Oven | Eat | 26 |
| strawberry_tart | Strawberry Tart | Dish | Bakery Oven | Eat, orders | 100 |
| pumpkin_pie | Pumpkin Pie | Dish | Bakery Oven | Eat, orders | 110 |
| pancakes | Pancakes | Dish | Bakery Oven | Eat, orders | 85 |
| veggie_stew | Veggie Stew | Dish | Bakery Oven | Eat, orders | 80 |
| potato_chips | Potato Chips | Dish | Bakery Oven | Eat | 75 |
| ratatouille | Ratatouille | Dish | Bakery Oven | Eat, orders | 95 |
| {crop}_jam | Jam (Strawberry, Melon, Grape jelly) | Goods | Preserves Jar | Tart, orders | 50 / 144 / 79 |
| {crop}_pickles | Pickles (Carrot, Potato, Tomato, Corn, Pumpkin, Cabbage, Eggplant) | Goods | Preserves Jar | Orders | 22–108 |
| {crop}_juice | Juice (Carrot, Tomato, Strawberry, Melon, Grape) | Goods | Fruit Press | Orders | 31–204 |
| honey | Honey | Forest / Goods | Beehive, Forest hive event | Pie, orders, Carrot dish | 25 |
| maple_syrup | Maple Syrup | Forest | Syrup Kettle | Pancakes, orders | 40 |
| wild_mushroom | Wild Mushroom | Forest | Ground, Oak roots | Eat, orders | 6 |
| chanterelle | Chanterelle | Forest | Ground, Autumn | Eat | 18 |
| glowcap | Glowcap | Forest | Ground, night | Collection | 45 |
| apple | Apple | Forest | Wild Apple | Eat, orders, Apple Sapling | 8 |
| blackberry | Blackberry | Forest | Berry Bush | Eat | 5 |
| apple_sapling | Apple Sapling | Forest | Merchant | Plant on normal spot | — |
| wood | Wood | Forest | Trees, branches, stumps | Most builds, Sawmill, Kiln | 3 |
| hardwood | Hardwood | Forest | Oak, Maple, Ancient, stumps | Beam, Iron and Gold tools | 12 |
| heartwood | Heartwood | Forest | Ancient Oak | Top storage, Gold tools | 120 |
| resin | Resin | Forest | Pine, branches | Torch, Lantern (no), collection | 6 |
| sap | Sap | Forest | Birch, Maple | Syrup Kettle | 5 |
| plank | Plank | Material | Sawmill, Workbench | Buildings, storage | 6 |
| beam | Beam | Material | Sawmill | Support Beam (Mine), Drill Rig, Greenhouse | 30 |
| mulch | Mulch | Fertilizer | Wood Chipper | Basic Fertilizer equivalent | 2 |
| pinecone | Pinecone | Seed | Pine | Plant Pine | 2 |
| birch_seed | Birch Seed | Seed | Birch | Plant Birch | 2 |
| acorn | Acorn | Seed | Oak | Plant Oak | 4 |
| maple_seed | Maple Seed | Seed | Maple | Plant Maple | 6 |
| golden_acorn | Golden Acorn | Seed | Ancient Oak, Merchant, Festival | Plant Ancient Oak | 250 |
| wildflower_seed | Wildflower Seed | Seed | Beehive smoke | Plant (decor later) | — |
| bird_nest | Bird Nest | Forest | Felling | Opened on pickup, never stored | — |
| amber | Amber | Rare | Pine (rare) | Collection | 120 |
| petrified_wood | Petrified Wood | Rare | Ancient Oak | Collection | 150 |
| old_coin | Old Coin | Rare | Stump (rare) | Collection | 80 |
| golden_feather | Golden Feather | Rare | Bird Nest | Collection | 200 |
| feather | Feather | Rare | Bird Nest | Collection | 15 |
| fibre | Fibre | Material | Grass, bushes, weeds | Rope | 1 |
| stone | Stone | Mine | Quarry, floors | Stone tools, buildings | 2 |
| coal | Coal | Mine | Coal Rock, Kiln, Drill Rig | Furnace, Drill Rig, Lantern, Torch | 6 |
| copper_ore | Copper Ore | Mine | Copper Vein, Drill Rig | Copper Bar | 6 |
| iron_ore | Iron Ore | Mine | Iron Vein, floors, Boulder | Iron Bar | 12 |
| gold_ore | Gold Ore | Mine | Gold Vein, floors 16+ | Gold Bar | 25 |
| quartz | Quartz | Mine | Quartz Cluster, floors | Glass, Lantern | 15 |
| clay | Clay | Mine | Clay Bank, Drill Rig | Brick | 3 |
| copper_bar | Copper Bar | Material | Furnace | Tools, Sprinkler, Rod | 30 |
| iron_bar | Iron Bar | Material | Furnace | Iron tools, Gear, Drill | 55 |
| gold_bar | Gold Bar | Material | Furnace | Gold tools, orders | 105 |
| brick | Brick | Material | Furnace | Oven, Kiln, Drill, Oil Press | 7 |
| glass | Glass | Material | Furnace (Quartz) | Jar, Greenhouse | 8 |
| nails | Nails | Material | Workbench | Upgrades | 8 |
| rope | Rope | Material | Workbench | Upgrades, Rope Ladder | 5 |
| gear | Gear | Material | Workbench | Tree Nursery, Barn upgrades | 40 |
| torch | Torch | Mine | Workbench | Dark floors, Beehive smoke | 4 |
| lantern | Lantern | Mine | Workbench | Dark floors (equipped) | — |
| support_beam | Support Beam | Mine | Workbench | Disarms cave-in | — |
| rope_ladder | Rope Ladder | Mine | Workbench | Descend instantly | — |
| geode | Geode | Mine | Quarry, floors | Anvil → minerals and gems | 20 |
| ember_geode | Ember Geode | Mine | Band 3 floors | Anvil | 45 |
| crystal_geode | Crystal Geode | Mine | Band 4 floors | Anvil | 90 |
| mineral_{name} | 15 minerals | Mine | Geodes | Collection | 30–200 |
| amethyst | Amethyst | Gem | Floors | Greenhouse T3 (Later), sell | 60 |
| emerald | Emerald | Gem | Floors | Sell | 130 |
| ruby | Ruby | Gem | Floors | Sell | 160 |
| diamond | Diamond | Gem | Floors | Sell | 450 |
| cut_{gem} | Cut gem | Gem | Lapidary (Later) | Sell at 2.5× | — |
| prismatic_shard | Prismatic Shard | Rare | Geodes, Heart of the Mountain | Collection | 1,500 |
| cave_mushroom | Cave Mushroom | Mine food | Floors | Eat (+10 energy) | 15 |

Tools (axe, pickaxe, hoe, can, sickle) are ids `{tool}_{tier}` and are not sold.

---

## 17. Starting resources (new save)

| Item | Amount |
|---|---|
| Coins | 250 |
| Energy | 100 / 100 |
| Plots | 6 (2 with ripe wheat, 4 tilled and empty) |
| Seeds | 12 wheat, 6 carrot (seeds take no storage) |
| Food | 3 carrots |
| Materials | 20 wood, 15 stone, 6 fibre, 2 planks |
| Fertilizer | 2 Basic Fertilizer |
| Tools | Wood axe, pickaxe, hoe, can, sickle |
| Buildings | Barn (100), Silo (75), Workbench L1, Well |
| Orders | 3 scripted: Mara "4 Wheat", Tobias "8 Wood", Mara "3 Carrots" |
| Calendar | Year 1, Spring Day 1, 06:00. Days 1–3 Sunny |

---

## 18. Tutorial tip sequence

Each tip shows once as a one-line pixel bubble. Triggers are ordered.

1. **First harvest** (drag over 2 ripe wheat): "Hold, then slide across crops to harvest them all."
2. **First plant:** "Tap an empty plot to plant. Crops need water to grow fast."
3. **First dry crop:** "Dry crops grow slowly. Tap them with the Watering Can." (Watering Can icon shows.)
4. **Mara's order delivered** (L2): "Orders pay coins and XP. Your Forest is open."
5. **First market visit:** "▲ means prices are up today. Sell when the arrows are green."
6. **First tree tap:** "Tap a tree to chop. Each swing uses 2 energy."
7. **First fell:** "Tap the stump to clear it, then tap the empty spot to plant a seed."
8. **First Oak with Wood axe:** "Oak needs a Stone axe (Level 4)."
9. **Energy under 20:** "Low energy! Tap the bar to eat, or come back later."
10. **Plot purchase:** "Buy more land with coins. Each plot costs more than the last."
11. **L3 (Mine screen):** "Tap a rock. Hold to keep swinging."
12. **Sawmill built:** "Turn Wood into Planks here. Planks build almost everything."
13. **First Workbench craft:** "Crafts run while the app is closed. Tap the hammer to collect."
14. **First storm forecast:** "Storm coming! Lightning Rods catch strikes and Scarecrows block the wind."
15. **First Lightning Rod:** "Rods protect the plots they cover. Place them on the corners."
16. **Mine Mouth repaired:** "Find the ladder. Listen for a hollow knock!"
17. **First dark floor:** "Drag a torch to light the dark."
18. **First cave-in rumble:** "Tap the crack 3× to brace!"
19. **First machine collection:** "Tap the bubble to collect. Machines keep working while you're away."
20. **Barn 90% full:** "Barn almost full! Sell goods or upgrade." (SELL and UPGRADE buttons.)

---

## 19. Offline catch-up

**Principle:** progression uses absolute timestamps. Anything that can be computed in closed form is. Anything that needs ordering (weather, storms, season change, glut decay) is replayed in time order.

**Cap:** offline simulation covers up to **24 real hours** (72 game days). Beyond that, the state is capped at the cap point.

**Order within each game hour:**
1. **06:00 day roll:** prices, weather roll, rain and sprinkler watering, glut decay, season change (wither check).
2. **14:00 storm** (storm days): strikes, wind, Forest windfall, Mine boulders.
3. Crops ripen (timestamps). Trees and saplings ripen. Quarry spots respawn (each spot ±20% jitter).
4. Producers run: beehives, Logging Hut, Tree Nursery, Drill Rig (fuel and hopper capped).
5. Machine batches complete. Queued machines start the next batch.
6. Harvest Shed and Planter Shed (if due, every 20 min).
7. Workbench crafts and tool upgrades complete.
8. Market Cart sells at 07:00.
9. Events: Dell's visit and beehives run only when the Forest is open. Event timers start on first sight.

**Caps and rules:**
- Floors never progress offline. Mine state freezes at last visit.
- Object caps: branches 6, mushrooms 5, apples 3, quarry spots full at most.
- Output caps: Harvest Shed tray, machine output, Logging Hut output (20/35/50 items), Drill hopper (24). Full storage keeps items waiting.
- Energy regen is timestamp-based and capped at max.
- Random rolls seeded by `(saveSeed, dayIndex)`, so reloading never re-rolls.
- After a return, show the "While you were away" panel: production by machine, blocked reasons, storm damage, Market Cart sales, Dell or beehive visits.

---

## 20. Save data entities

Local JSON with automatic rolling backups and export/import. Each entity lists its key fields.

| Entity | Fields |
|---|---|
| `profile` | version, saveSeed, createdAt, lastSavedMs, lastOpenMs |
| `clock` | year, season (0–3), day (1–56), gameMinute, weather[day], forecast |
| `player` | level, xp, coins, energy, energyAtMs, mastery stars, plotCap |
| `farm.plots[]` | id, state (untilled/empty/planted/withered/scorched), crop, plantedMs, readyAtMs, watered, care, quality, fertilizer, battered |
| `farm.vertices[]` | vertexId, fixture (sprinkler/scarecrow/rod), tier |
| `forest.spots[]` | spot, zone, native, state (EMPTY/SAPLING/MATURE/STUMP/WINDFALL), type, hp, since, readyAtMs, event |
| `forest.objects` | branches[], mushrooms[], berries[], apples[] with spawn index and cap |
| `forest.events` | beehive {spot, firstSeenDay, expiresDay}, dell {visitSeason, firstSeenDay, dealsUsed[]} |
| `mine.quarry` | level, spots[] {type, respawnAtEpochMs} |
| `mine.current` | floor, seed, brokenMask, torches[], beams[], ladderFound, brokenCount, deepestStop, deepestFloor, visitCounter |
| `mine.elevator` | deepestStop, unlockedStops[] |
| `buildings[]` | id, screen, slot, level, queue[] {recipe, startMs, batch, n}, tray[], fuelMinutes, mode (planter), recipe (jar) |
| `drill` | fuel, hopper[], nextTickMs |
| `inventory` | barn {cap, items{}}, silo {cap, items{}} |
| `tools` | {axe, pickaxe, hoe, can, sickle}: tier and upgrade queue |
| `can` | charges, lastRefillMs |
| `workbench` | level, queue[] {recipe, n, startMs} |
| `market` | glut{item: value}, arrowsCache, sales[] (7 days) |
| `orders` | slots[] {villager, lines[], rewardCoins, rewardXp, startedMs, status}, nextAtMs[] |
| `villagers` | {id: {met, friendship, hearts}} |
| `achievements` | counters{}, tiersClaimed{} |
| `collection` | entries{id: {firstDate, total, bestQuality}}, pagesDone[] |
| `events` | festival {season, progress, claimed}, loginStreak, lastLoginDay, merchant {stock[], nextDay} |
| `settings` | sfx, haptics, notifications, zoom, cameraPos per screen |
| `stats` | fells, cleanFells (Later), geodesCracked, braces, quarryBreaks, bird nests, dishes eaten |

---

## 21. Notifications, audio and haptics

- **Notifications** (one global toggle; at most one per category per 30 real min; grouped):
  - "Crops are ready" (per screen: Farm, Forest).
  - "Machines are done: Oven, Windmill and Furnace" (grouped).
  - "Workbench craft done: Iron axe is ready!"
  - "Barn full" or "Silo full" at predicted time.
  - "Quarry is full again" (optional).
  - "Drill out of coal" or "Drill hopper full".
  - "Energy full."
  - "New order arrived" (optional).
  - "Storm tomorrow" (forecast warning, at most once per 2 real hours).
  - "A visitor is in the forest" (Dell or beehive).
- **Audio:** SFX only, no music in v1. Sample sets: `chop`, `fell`, `pick_hit`, `rock_break`, `clink` (blocked), `till`, `pour`, `craft_start`, `craft_done`, `upgrade_fanfare`, `coin`, `thunder`, `level_up`, `ka_ching`, `ding`, `zap`, `creak`, `owl` (night), `tok` (hollow knock).
- **Haptics** (API 29 predefined effects; off with the toggle): tick on hit (8 ms), crit (15 ms), break (20 ms), fell (heavy click), rare find (30-60-30 ms), blocked (double click), lightning-strike catch (double tick).

---

## 22. Later (not v1)

- Animals (with feed recipes for the Windmill, Cheese Press, Loom, etc.).
- Crows, pests and weeds.
- T2 and T3 machine tiers, the Hopper auto-mode, Timber Bot, Sap Tapper, Rock Crusher, Lapidary Bench, Weather Station, Storm Shard and Storm Shield Tower.
- Machine size classes (S/M/L) and free placement.
- Jam and dish variants (Honey Carrots, Fruit Salad, Harvest Feast), Growth Tonic, Speed-Gro, Deluxe Fertilizer.
- Glue, Varnish, Tool Handle, Crate, Charcoal item, Sawdust.
- Combo system, Clean Fell, Sharpened buff, rare-find multiplier, Cleave and Sweep.
- Diamond tier, bucket, shears, milk pail, fishing rod, hammer.
- Barn and Silo above level 10.
- Endless Deep Mine past B40, Deep Drill, blast charges, mine shafts, Minecart, Miner's Helmet, Quarry Lv4.
- Glass from sand, Gem Polisher, jewelry.
- Forest: Woodshed, Mushroom Log, fishing pond, fairy-ring event, Four-leaf Clover, choppable fruit trees, tree pests, weak-spot knots, Apiary, watering saplings.
- Greenhouse Heated Beds (L30) and Misting (T2).
- Festival cosmetics, tool skins, tool mastery, tool charms.
- Villager gifting and dialogue trees, Bram/Dell questline.
- Quality-specific orders, timed rush orders.
- Giant crops, hybrids, aging casks, food buffs, lightning-rod charge.
- Daily hot-item market bonus, market events.
- Village restoration, decorations, cloud save, trading, leaderboards.

---

## 23. Open questions for the owner

1. **Dry crops:** 60% speed (default) or stop until watered?
2. **Storm frequency:** about 6 storms per real day with cheap rods (default), or weather in 3-day fronts?
3. **Mature crops never wither** at season change (default). Keep?
4. **Barn full:** block the action (default) or add a Minecart later?
5. **Dell:** keep as a non-villager event visitor (default), or make him a villager too?
6. **Automation XP:** 50% (default), full or none?
7. **Market Cart:** free in v1 (default). Add a fee later?
8. **Offline cap:** 24 real hours (default). Shorter for check-back play?
9. **Endgame:** Mastery Stars after L30 (default), or raise the cap later?
10. **Quality on processed goods:** no stars (default).