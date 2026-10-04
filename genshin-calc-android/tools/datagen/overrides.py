"""Per-character corrections to the auto-derived talent hits.

Keys are genshin-db character ids, hit ids are "<talent>/<slug of the row name>".
Patch fields: category (NORMAL/CHARGED/PLUNGE/SKILL/BURST), element (PYRO.., PHYSICAL,
or None for "physical unless infused"), name, special, partElements, remove.

Every entry here was checked against the in-game talent text ("This DMG is considered
Normal Attack DMG", ...) and cross-checked with Genshin Optimizer's character sheets.
"""

NORMAL, CHARGED, PLUNGE, SKILL, BURST = "NORMAL", "CHARGED", "PLUNGE", "SKILL", "BURST"
PHYS = "PHYSICAL"


def cat(c):
    return {"category": c}


def ele(e):
    return {"element": e}


def lunar(kind):
    return {"special": kind}


def extra(talent, key, name, category, element, terms, cons=None, asc=None, special=None, count=1, flat=None):
    """A damage row that comes from a passive or constellation instead of a talent table.

    terms: list of (stat, multiplier) with constant multipliers, e.g. [("ATK", 0.75)], or (stat, "paramN")
    to reuse a scaling of the same talent.
    """
    prefix = {"NORMAL": "normal", "SKILL": "skill", "BURST": "burst"}[talent]
    params, out_terms = {}, []
    for i, (stat, value) in enumerate(terms):
        if isinstance(value, str):  # an existing talent scaling, e.g. "param15"
            out_terms.append({"stat": stat, "param": value})
            continue
        pname = f"x_{key}_{i}"
        params[pname] = value
        out_terms.append({"stat": stat, "param": pname})
    hit = {
        "id": f"{prefix}/{key}", "talent": talent, "name": name, "kind": "DMG", "category": category,
        "element": element, "parts": [{"terms": out_terms, "flat": None, "count": count, "element": None}],
        "params": params,
    }
    if cons:
        hit["constellation"] = cons
    if asc:
        hit["ascension"] = asc
    if special:
        hit["special"] = special
    return hit


CHARACTER_OVERRIDES = {
    # Arataki Kesagiri / Saichimonji are physical until Royal Descent infuses them.
    "aratakiitto": {"hits": {
        "normal/arataki-kesagiri-combo-slash-dmg": ele(None),
        "normal/arataki-kesagiri-final-slash-dmg": ele(None),
        "normal/saichimonji-slash-dmg": ele(None),
    }},
    "charlotte": {"hits": {"normal/spiritbreath-thorn-dmg": cat(NORMAL)}},
    # Multitarget Fire (tap) is Normal Attack DMG, Shadowhunt Shells are Charged Attack DMG.
    "chasca": {"hits": {
        "skill/multitarget-fire-tap-dmg": cat(NORMAL),
        "skill/shadowhunt-shell-dmg": cat(CHARGED),
        "skill/shining-shadowhunt-shell-dmg": cat(CHARGED),
    }},
    # Swift Hunt / Impale the Night replace her Normal Attacks.
    "clorinde": {"hits": {
        "skill/swift-hunt-dmg-1": {"category": NORMAL, "name": "Swift Hunt DMG (Normal Shot)"},
        "skill/swift-hunt-dmg-2": {"category": NORMAL, "name": "Swift Hunt DMG (Piercing Shot)"},
        "skill/impale-the-night-dmg-1": {"category": NORMAL, "name": "Impale the Night DMG (BoL < 100%)"},
        "skill/impale-the-night-dmg-2": {"category": NORMAL, "name": "Impale the Night DMG (BoL ≥ 100%)"},
        "skill/impale-the-night-dmg-3": {"category": NORMAL, "name": "Impale the Night DMG (BoL ≥ 100%, Enhanced)"},
        "skill/impale-the-night-healing-2": {"remove": True},
        "skill/impale-the-night-healing-3": {"remove": True},
    }},
    "columbina": {"hits": {"normal/moondew-cleanse-dmg": lunar("LUNAR_BLOOM")}},
    "diluc": {"hits": {
        "skill/1-hit-dmg": cat(SKILL), "skill/2-hit-dmg": cat(SKILL), "skill/3-hit-dmg": cat(SKILL),
    }},
    "diona": {"hits": {"skill/icy-paw-dmg": {"name": "Icy Paw DMG (per Paw)"}}},
    # Lightfall Sword deals Physical DMG.
    "eula": {"hits": {
        "burst/lightfall-sword-base-dmg": ele(PHYS),
        "burst/dmg-per-stack": {"element": PHYS, "name": "Lightfall Sword DMG per Stack"},
    }},
    "flins": {"hits": {
        "burst/thunderous-symphony-dmg": lunar("LUNAR_CHARGED"),
        "burst/thunderous-symphony-additional-dmg": lunar("LUNAR_CHARGED"),
    }},
    "furina": {"hits": {"normal/spiritbreath-thorn-surging-blade-dmg": cat(NORMAL)}},
    "ifa": {"hits": {"skill/tonicshot-dmg": cat(NORMAL)}},
    "kukishinobu": {"hits": {
        "burst/total-dmg-1": {"name": "Total DMG (HP > 50%)"},
        "burst/total-dmg-2": {"name": "Total DMG (HP ≤ 50%)"},
    }},
    "kinich": {"hits": {"normal/mid-air-normal-attack-dmg": {"category": NORMAL, "element": None}}},
    "lauma": {
        "hits": {"skill/1-hit-hold-dmg": cat(SKILL)},
        "addHits": [{
            "id": "skill/2-hit-hold-dmg-per-verdant-dew", "talent": "SKILL",
            "name": "2-Hit Hold DMG (per Verdant Dew)", "kind": "DMG", "category": SKILL,
            "element": "DENDRO", "special": "LUNAR_BLOOM",
            "parts": [{"terms": [{"stat": "EM", "param": "param3"}], "flat": None, "count": 1, "element": None}],
        }],
    },
    "linnea": {"hits": {
        "skill/lumi-heavy-overdrive-hammer-dmg": lunar("LUNAR_CRYSTALLIZE"),
        "skill/lumi-million-ton-crush-dmg": lunar("LUNAR_CRYSTALLIZE"),
    }},
    "mualani": {"hits": {"skill/sharky-s-bite-base-dmg": cat(NORMAL)}},
    "nefer": {"hits": {
        "skill/phantasm-performance-1-hit-dmg-nefer": cat(CHARGED),
        "skill/phantasm-performance-2-hit-dmg-nefer": cat(CHARGED),
        "skill/phantasm-performance-1-hit-dmg-shades": {"category": CHARGED, "special": "LUNAR_BLOOM"},
        "skill/phantasm-performance-2-hit-dmg-shades": {"category": CHARGED, "special": "LUNAR_BLOOM"},
        "skill/phantasm-performance-3-hit-dmg-shades": {"category": CHARGED, "special": "LUNAR_BLOOM"},
        "burst/1-hit-dmg": cat(BURST), "burst/2-hit-dmg": cat(BURST),
    }},
    "nilou": {"hits": {
        "skill/sword-dance-1-hit-dmg": cat(SKILL), "skill/sword-dance-2-hit-dmg": cat(SKILL),
        "skill/whirling-steps-1-hit-dmg": cat(SKILL), "skill/whirling-steps-2-hit-dmg": cat(SKILL),
    }},
    # Musou Isshin: Normal/Charged/Plunging Attacks count as Elemental Burst DMG.
    "raidenshogun": {"hits": {
        "burst/1-hit-dmg": cat(BURST), "burst/2-hit-dmg": cat(BURST), "burst/3-hit-dmg": cat(BURST),
        "burst/4-hit-dmg": cat(BURST), "burst/5-hit-dmg": cat(BURST),
        "burst/charged-attack-dmg": cat(BURST), "burst/plunge-dmg": cat(BURST),
        "burst/low-plunge-dmg": cat(BURST), "burst/high-plunge-dmg": cat(BURST),
    }},
    "sandrone": {"hits": {
        "normal/charged-attack-sweeping-fire-dmg": ele("CRYO"),
        "normal/charged-attack-condensed-beam-dmg": ele("CRYO"),
        "normal/charged-attack-condensed-beam-stellar-conduct-dmg": ele("CRYO"),
        "normal/charged-attack-condensed-beam-stellar-swirl-dmg": ele("CRYO"),
    }},
    "tartaglia": {"hits": {
        "normal/riptide-flash-dmg": cat(NORMAL), "normal/riptide-burst-dmg": cat(NORMAL),
    }},
    "varesa": {"hits": {"burst/volcano-kablam-dmg": cat(PLUNGE)}},
    "varka": {"hits": {"skill/azure-devour-dmg": cat(CHARGED)}},
    "xiangling": {"hits": {
        "burst/1-hit-swing-dmg": cat(BURST), "burst/2-hit-swing-dmg": cat(BURST),
        "burst/3-hit-swing-dmg": cat(BURST),
    }},
    "xianyun": {"hits": {
        "skill/driftcloud-wave-dmg-1": {"category": PLUNGE, "name": "Driftcloud Wave DMG (1 Leap)"},
        "skill/driftcloud-wave-dmg-2": {"category": PLUNGE, "name": "Driftcloud Wave DMG (2 Leaps)"},
        "skill/driftcloud-wave-dmg-3": {"category": PLUNGE, "name": "Driftcloud Wave DMG (3 Leaps)"},
    }},
    # Blade Roller (Nightsoul) attacks and plunges deal DEF-based Geo DMG.
    "xilonen": {"hits": {
        "normal/blade-roller-1-hit-dmg": ele("GEO"), "normal/blade-roller-2-hit-dmg": ele("GEO"),
        "normal/blade-roller-3-hit-dmg": ele("GEO"), "normal/blade-roller-4-hit-dmg": ele("GEO"),
        "normal/plunge-dmg": ele("GEO"), "normal/low-plunge-dmg": ele("GEO"),
        "normal/high-plunge-dmg": ele("GEO"),
    }},
    "xinyan": {"hits": {"burst/skill-dmg": {"element": PHYS, "name": "Skill DMG (Physical)"}}},
    "yanfei": {"hits": {
        "normal/charged-attack-1": {"name": "Charged Attack (0 Seals)"},
        "normal/charged-attack-2": {"name": "Charged Attack (1 Seal)"},
        "normal/charged-attack-3": {"name": "Charged Attack (2 Seals)"},
        "normal/charged-attack-4": {"name": "Charged Attack (3 Seals)"},
        "normal/charged-attack-5": {"name": "Charged Attack (4 Seals)"},
    }},
    "zibai": {"hits": {
        "skill/spirit-steed-s-stride-1-hit-dmg": cat(SKILL),
        "skill/spirit-steed-s-stride-2-hit-dmg": {"category": SKILL, "special": "LUNAR_CRYSTALLIZE"},
        "skill/lunar-phase-shift-4-hit-additional-dmg": lunar("LUNAR_CRYSTALLIZE"),
        "burst/skill-2-hit-dmg": lunar("LUNAR_CRYSTALLIZE"),
    }},
}

# Extra damage rows from passives and constellations.
EXTRA_HITS = {
    "xiangling": [extra("NORMAL", "c2-implode", "Implode DMG (C2)", "NONE", "PYRO", [("ATK", 0.75)], cons=2)],
    "yelan": [extra("BURST", "c2-water-arrow", "Additional Water Arrow (C2)", "BURST", "HYDRO", [("HP", 0.14)], cons=2)],
    "nahida": [extra("SKILL", "c6-karmic-oblivion", "Tri-Karma Purification: Karmic Oblivion (C6)", "SKILL", "DENDRO",
                     [("ATK", 2.0), ("EM", 4.0)], cons=6)],
    "keqing": [extra("SKILL", "c1-blink-dmg", "Blink DMG (C1)", "NONE", "ELECTRO", [("ATK", 0.5)], cons=1)],
    "chongyun": [extra("NORMAL", "c1-ice-blade", "Ice Blade DMG (C1, x3)", "NONE", "CRYO", [("ATK", 0.5)], cons=1, count=3)],
    "yanfei": [extra("NORMAL", "a4-blazing-eye", "Blazing Eye DMG (A4, on CRIT)", "CHARGED", "PYRO", [("ATK", 0.8)], asc=4)],
    "baizhu": [extra("SKILL", "c2-gossamer-splice", "Gossamer Sprite: Splice DMG (C2)", "SKILL", "DENDRO", [("ATK", 2.5)], cons=2)],
    "beidou": [extra("NORMAL", "c4-electro-dmg", "Stunning Revenge Electro DMG (C4)", "NONE", "ELECTRO", [("ATK", 0.2)], cons=4)],
    "cyno": [extra("SKILL", "a1-duststalker-bolt", "Duststalker Bolt DMG (A1, x3)", "SKILL", "ELECTRO", [("ATK", 1.0)], asc=1, count=3)],
    "wanderer": [extra("SKILL", "a4-wind-arrow", "Gales of Reverie Wind Arrow DMG (A4, x4)", "NONE", "ANEMO", [("ATK", 0.35)], asc=4, count=4)],
    "tighnari": [extra("NORMAL", "c6-clusterbloom-arrow", "Additional Clusterbloom Arrow DMG (C6)", "CHARGED", "DENDRO", [("ATK", 1.5)], cons=6)],
    "collei": [
        extra("SKILL", "a1-sprout", "Sprout DMG (A1)", "SKILL", "DENDRO", [("ATK", 0.4)], asc=1),
        extra("SKILL", "c6-miniature-cuilein-anbar", "Miniature Cuilein-Anbar DMG (C6)", "NONE", "DENDRO", [("ATK", 2.0)], cons=6),
    ],
    "candace": [extra("BURST", "c6-wave", "Crimson Crown Wave DMG (C6)", "BURST", "HYDRO", [("HP", 0.15)], cons=6)],
    "kaveh": [extra("BURST", "c6-pairidaeza-light", "Pairidaeza's Light DMG (C6)", "NONE", "DENDRO", [("ATK", 0.618)], cons=6)],
    "dori": [extra("BURST", "c2-jinni-toop", "Jinni Toop DMG (C2)", "NONE", "ELECTRO", [("ATK", 0.5)], cons=2)],
    "yaoyao": [extra("SKILL", "c6-mega-radish", "Mega Radish DMG (C6)", "BURST", "DENDRO", [("ATK", 0.75)], cons=6)],
    "neuvillette": [extra("NORMAL", "c6-current", "Additional Current DMG (C6, x2)", "CHARGED", "HYDRO", [("HP", 0.10)], cons=6, count=2)],
    "clorinde": [
        extra("SKILL", "c1-nightvigil-shade", "Nightvigil Shade DMG (C1, x2)", "NORMAL", "ELECTRO", [("ATK", 0.3)], cons=1, count=2),
        extra("SKILL", "c6-glimbright-shade", "Glimbright Shade DMG (C6)", "NORMAL", "ELECTRO", [("ATK", 2.0)], cons=6),
    ],
    "chevreuse": [extra("SKILL", "c2-chain-explosion", "Chain Explosion DMG (C2, x2)", "SKILL", "PYRO", [("ATK", 1.2)], cons=2, count=2)],
    "charlotte": [extra("BURST", "c6-coordinated-attack", "Coordinated Attack DMG (C6)", "BURST", "CRYO", [("ATK", 1.8)], cons=6)],
    "lyney": [extra("NORMAL", "c6-pyrotechnic-strike-reprised", "Pyrotechnic Strike: Reprised DMG (C6)", "CHARGED", "PYRO",
                    [("ATK", "param15")], cons=6)],
    "emilie": [extra("SKILL", "a1-cleardew-cologne", "Cleardew Cologne DMG (A1)", "NONE", "DENDRO", [("ATK", 6.0)], asc=1)],
    "escoffier": [extra("SKILL", "c6-special-grade-frosty-parfait", "Special-Grade Frosty Parfait DMG (C6)", "SKILL", "CRYO",
                        [("ATK", 5.0)], cons=6)],
    "mavuika": [
        extra("SKILL", "c6-flamestrider-crash", "Flamestrider Crash DMG (C6)", "SKILL", "PYRO", [("ATK", 2.0)], cons=6),
        extra("SKILL", "c6-scorching-ring", "Scorching Ring of Searing Radiance DMG (C6)", "SKILL", "PYRO", [("ATK", 5.0)], cons=6),
    ],
    "citlali": [extra("SKILL", "c4-spiritvessel-skull", "Obsidian Spiritvessel Skull DMG (C4)", "NONE", "CRYO", [("EM", 18.0)], cons=4)],
    "chasca": [
        extra("SKILL", "a4-burning-shadowhunt-shot", "Burning Shadowhunt Shot DMG (A4)", "CHARGED", "ANEMO", [("ATK", "param4")], asc=4),
        extra("SKILL", "c2-shining-shell-aoe", "Shining Shell AoE DMG (C2)", "CHARGED", "ANEMO", [("ATK", 4.0)], cons=2),
        extra("BURST", "c4-radiant-shell-aoe", "Radiant Soulseeker Shell AoE DMG (C4)", "CHARGED", "ANEMO", [("ATK", 4.0)], cons=4),
    ],
    "kinich": [extra("SKILL", "c6-cannon-bounce", "Scalespiker Cannon Bounce DMG (C6)", "SKILL", "DENDRO", [("ATK", 7.0)], cons=6)],
    "kachina": [extra("SKILL", "c6-shield-break", "Shield Break DMG (C6)", "NONE", "GEO", [("DEF", 2.0)], cons=6)],
    "ororon": [
        extra("SKILL", "a1-hypersense", "Hypersense DMG (A1)", "NONE", "ELECTRO", [("ATK", 1.6)], asc=1),
        extra("BURST", "c6-hypersense", "Burst Hypersense DMG (C6)", "NONE", "ELECTRO", [("ATK", 3.2)], cons=6),
    ],
    "ifa": [extra("SKILL", "c6-extra-tonicshot", "Additional Tonicshot DMG (C6, 50% chance)", "NORMAL", "ANEMO", [("ATK", 1.2)], cons=6)],
    "ineffa": [
        extra("SKILL", "a1-birgitta-additional", "Birgitta Additional Attack DMG (A1)", "NONE", "ELECTRO", [("ATK", 0.65)], asc=1,
              special="LUNAR_CHARGED"),
        extra("BURST", "c2-punishment-edict", "Punishment Edict DMG (C2)", "NONE", "ELECTRO", [("ATK", 3.0)], cons=2, special="LUNAR_CHARGED"),
        extra("SKILL", "c6-thundercloud-strike", "Thundercloud Follow-up DMG (C6)", "NONE", "ELECTRO", [("ATK", 1.35)], cons=6,
              special="LUNAR_CHARGED"),
    ],
    "flins": [extra("SKILL", "c2-additional", "Additional Lunar-Charged DMG (C2)", "NONE", "ELECTRO", [("ATK", 0.5)], cons=2,
                    special="LUNAR_CHARGED")],
    "lauma": [
        extra("SKILL", "c6-sanctuary-lunar-bloom", "Frostgrove Sanctuary Lunar-Bloom DMG (C6)", "NONE", "DENDRO", [("EM", 1.85)], cons=6,
              special="LUNAR_BLOOM"),
        extra("NORMAL", "c6-pale-hymn-normal", "Pale Hymn Normal Attack DMG (C6)", "NORMAL", "DENDRO", [("EM", 1.5)], cons=6,
              special="LUNAR_BLOOM"),
    ],
    "nefer": [
        extra("SKILL", "c6-phantasm-2-lunar", "Phantasm Performance 2-Hit DMG (Nefer, C6 Lunar-Bloom)", "CHARGED", "DENDRO", [("EM", 0.85)],
              cons=6, special="LUNAR_BLOOM"),
        extra("SKILL", "c6-phantasm-end", "Phantasm Performance Final DMG (C6)", "CHARGED", "DENDRO", [("EM", 1.2)], cons=6,
              special="LUNAR_BLOOM"),
    ],
    "aino": [extra("BURST", "c2-water-ball", "Additional Water Ball DMG (C2)", "BURST", "HYDRO", [("ATK", 0.25), ("EM", 1.0)], cons=2)],
    "illuga": [extra("BURST", "c2-aedon", "Aedon DMG (C2)", "BURST", "GEO", [("EM", 4.0), ("DEF", 2.0)], cons=2)],
    "traveleranemo": [extra("NORMAL", "a1-slitting-wind", "Slitting Wind DMG (A1)", "NONE", "ANEMO", [("ATK", 0.6)], asc=1)],
    "travelergeo": [
        extra("NORMAL", "a4-frenzied-rockslide", "Frenzied Rockslide DMG (A4)", "NONE", "GEO", [("ATK", 0.6)], asc=4),
        extra("SKILL", "c2-meteorite-explosion", "Meteorite Explosion DMG (C2)", "SKILL", "GEO", [("ATK", "param1")], cons=2),
    ],
    "arlecchino": [extra("SKILL", "c2-balemoon-bloodfire", "Balemoon Bloodfire DMG (C2)", "NONE", "PYRO", [("ATK", 9.0)], cons=2)],
    "skirk": [
        extra("SKILL", "c1-crystal-blade", "Crystal Blade DMG (C1, per Void Rift)", "CHARGED", "CRYO", [("ATK", 5.0)], cons=1),
        extra("BURST", "c6-havoc-sever-burst", "Havoc: Sever DMG on Burst (C6, x3)", "BURST", "CRYO", [("ATK", 7.5)], cons=6, count=3),
        extra("SKILL", "c6-havoc-sever-normal", "Havoc: Sever DMG on Normal Attack (C6, x3)", "NORMAL", "CRYO", [("ATK", 1.8)], cons=6,
              count=3),
    ],
    "lanyan": [extra("SKILL", "a1-absorbed-ring", "Feathermoon Ring Absorbed Element DMG (A1)", "SKILL", "ANEMO", [("ATK", "param1")],
                     asc=1)],
    "lohen": [extra("SKILL", "c2-evilsbane-blade", "Evilsbane Blade DMG (C2)", "NONE", "CRYO", [("ATK", 5.0)], cons=2)],
    "prune": [
        extra("BURST", "a1-banehunter-oathhammer", "Banehunter Oathhammer DMG (A1)", "BURST", "ANEMO", [("ATK", 1.5)], asc=1),
        extra("BURST", "c4-oathhammer-ricochet", "Oathhammer Ricochet DMG (C4)", "NONE", "ANEMO", [("ATK", 0.8)], cons=4),
    ],
    "varka": [extra("SKILL", "c2-additional-strike", "Additional Strike DMG (C2)", "NONE", "ANEMO", [("ATK", 8.0)], cons=2)],
    "odette": [
        extra("SKILL", "c1-duet-stellar-conduct", "Duet End Stellar-Conduct DMG (C1)", "NONE", "CRYO", [("ATK", 3.0)], cons=1,
              special="STELLAR_CONDUCT"),
        extra("SKILL", "c1-duet-stellar-swirl", "Duet End Stellar Swirl DMG (C1)", "NONE", "CRYO", [("ATK", 4.5)], cons=1,
              special="STELLAR_SWIRL"),
        extra("BURST", "c4-coordinated-stellar-conduct", "Coordinated Attack Stellar-Conduct DMG (C4)", "NONE", "CRYO", [("ATK", 0.66)],
              cons=4, special="STELLAR_CONDUCT"),
        extra("BURST", "c4-coordinated-stellar-swirl", "Coordinated Attack Stellar Swirl DMG (C4)", "NONE", "CRYO", [("ATK", 0.99)],
              cons=4, special="STELLAR_SWIRL"),
    ],
    "sandrone": [
        extra("SKILL", "c4-cannon-stellar-conduct", "Prismatic Resonance Cannon Stellar-Conduct DMG (C4)", "NONE", "CRYO",
              [("ATK", 1.25)], cons=4, special="STELLAR_CONDUCT"),
        extra("SKILL", "c4-cannon-stellar-swirl", "Prismatic Resonance Cannon Stellar Swirl DMG (C4)", "NONE", "CRYO",
              [("ATK", 1.875)], cons=4, special="STELLAR_SWIRL"),
        extra("NORMAL", "c6-cluster-beam", "Condensed Cluster Beam DMG (C6, x4)", "CHARGED", "CRYO", [("ATK", 1.0)], cons=6, count=4),
        extra("NORMAL", "c6-cluster-beam-stellar-conduct", "Condensed Cluster Beam Stellar-Conduct DMG (C6, x4)", "CHARGED", "CRYO",
              [("ATK", 0.8)], cons=6, count=4, special="STELLAR_CONDUCT"),
        extra("NORMAL", "c6-cluster-beam-stellar-swirl", "Condensed Cluster Beam Stellar Swirl DMG (C6, x4)", "CHARGED", "CRYO",
              [("ATK", 1.2)], cons=6, count=4, special="STELLAR_SWIRL"),
    ],
    "vesna": [
        extra("SKILL", "c6-transpose", "Windborne Sword: Transpose DMG (C6)", "SKILL", "ANEMO", [("ATK", 1.5)], cons=6),
        extra("SKILL", "c6-transpose-spirit-blade", "Transpose Spirit Blade DMG (C6)", "SKILL", "ANEMO", [("ATK", 2.0)], cons=6),
    ],
    "zhongli": [],
}

for _cid, _hits in EXTRA_HITS.items():
    CHARACTER_OVERRIDES.setdefault(_cid, {}).setdefault("addHits", []).extend(_hits)
CHARACTER_OVERRIDES.setdefault("zhongli", {}).setdefault("hits", {})["skill/stone-stele"] = {"name": "Stone Stele DMG"}
