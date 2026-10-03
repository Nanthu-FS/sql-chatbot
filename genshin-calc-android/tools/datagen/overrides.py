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

    terms: list of (stat, multiplier) with constant multipliers, e.g. [("ATK", 0.75)].
    """
    prefix = {"NORMAL": "normal", "SKILL": "skill", "BURST": "burst"}[talent]
    params, out_terms = {}, []
    for i, (stat, value) in enumerate(terms):
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
    "zhongli": [],
}

for _cid, _hits in EXTRA_HITS.items():
    CHARACTER_OVERRIDES.setdefault(_cid, {}).setdefault("addHits", []).extend(_hits)
CHARACTER_OVERRIDES.setdefault("zhongli", {}).setdefault("hits", {})["skill/stone-stele"] = {"name": "Stone Stele DMG"}
