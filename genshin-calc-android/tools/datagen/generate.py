#!/usr/bin/env python3
"""Generates the game data bundled with the app (core/src/main/resources/gamedata).

Sources (both MIT licensed):
  * genshin-db (npm package) - names, descriptions, talent labels + scaling tables,
    base stats/curves, weapons, artifact sets, enemies.
  * Genshin Optimizer's datamined stat file - artifact main/sub stat tables.

Usage:
  python3 tools/datagen/generate.py [--cache DIR] [--report FILE]

Talent "hits" (the rows of the damage table) are derived from the in-game talent
labels, e.g. "Charged Attack DMG|{param6:F1P}+{param7:F1P}" becomes one hit with two
parts that scale off ATK. Elements/attack categories follow the game's defaults and are
corrected per character in overrides.py.
"""
import argparse
import gzip
import io
import json
import os
import re
import sys
import tarfile
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from overrides import CHARACTER_OVERRIDES  # noqa: E402

GENSHIN_DB_VERSION = "5.2.14"
GENSHIN_DB_URL = f"https://registry.npmjs.org/genshin-db/-/genshin-db-{GENSHIN_DB_VERSION}.tgz"
GO_STATS_URL = ("https://raw.githubusercontent.com/frzyc/genshin-optimizer/master/"
                "libs/gi/stats/src/allStat_gen.json")
# Character skill ids of the Enka.Network showcase API (MIT).
ENKA_AVATARS_URL = "https://raw.githubusercontent.com/EnkaNetwork/API-docs/master/store/gi/avatars.json"
ENKA_ELEMENTS = {"Fire": "pyro", "Water": "hydro", "Wind": "anemo", "Electric": "electro", "Grass": "dendro",
                 "Ice": "cryo", "Rock": "geo"}
TRAVELER_AVATARS = {"10000005", "10000007"}

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT_DIR = os.path.join(PROJECT, "core", "src", "main", "resources", "gamedata")

ELEMENTS = {
    "ELEMENT_PYRO": "PYRO", "ELEMENT_HYDRO": "HYDRO", "ELEMENT_ANEMO": "ANEMO",
    "ELEMENT_ELECTRO": "ELECTRO", "ELEMENT_DENDRO": "DENDRO", "ELEMENT_CRYO": "CRYO",
    "ELEMENT_GEO": "GEO",
}
WEAPONS = {
    "WEAPON_SWORD_ONE_HAND": "SWORD", "WEAPON_CLAYMORE": "CLAYMORE", "WEAPON_POLE": "POLEARM",
    "WEAPON_BOW": "BOW", "WEAPON_CATALYST": "CATALYST",
}
PROPS = {
    "FIGHT_PROP_HP": "HP", "FIGHT_PROP_HP_PERCENT": "HP_PCT",
    "FIGHT_PROP_ATTACK": "ATK", "FIGHT_PROP_ATTACK_PERCENT": "ATK_PCT",
    "FIGHT_PROP_DEFENSE": "DEF", "FIGHT_PROP_DEFENSE_PERCENT": "DEF_PCT",
    "FIGHT_PROP_ELEMENT_MASTERY": "EM", "FIGHT_PROP_CHARGE_EFFICIENCY": "ER",
    "FIGHT_PROP_CRITICAL": "CRIT_RATE", "FIGHT_PROP_CRITICAL_HURT": "CRIT_DMG",
    "FIGHT_PROP_HEAL_ADD": "HEALING_BONUS",
    "FIGHT_PROP_PHYSICAL_ADD_HURT": "PHYSICAL_DMG", "FIGHT_PROP_FIRE_ADD_HURT": "PYRO_DMG",
    "FIGHT_PROP_WATER_ADD_HURT": "HYDRO_DMG", "FIGHT_PROP_WIND_ADD_HURT": "ANEMO_DMG",
    "FIGHT_PROP_ELEC_ADD_HURT": "ELECTRO_DMG", "FIGHT_PROP_GRASS_ADD_HURT": "DENDRO_DMG",
    "FIGHT_PROP_ICE_ADD_HURT": "CRYO_DMG", "FIGHT_PROP_ROCK_ADD_HURT": "GEO_DMG",
}
GO_ART_KEYS = {
    "hp": "HP", "hp_": "HP_PCT", "atk": "ATK", "atk_": "ATK_PCT", "def": "DEF", "def_": "DEF_PCT",
    "eleMas": "EM", "enerRech_": "ER", "critRate_": "CRIT_RATE", "critDMG_": "CRIT_DMG",
    "heal_": "HEALING_BONUS", "physical_dmg_": "PHYSICAL_DMG", "pyro_dmg_": "PYRO_DMG",
    "hydro_dmg_": "HYDRO_DMG", "anemo_dmg_": "ANEMO_DMG", "electro_dmg_": "ELECTRO_DMG",
    "dendro_dmg_": "DENDRO_DMG", "cryo_dmg_": "CRYO_DMG", "geo_dmg_": "GEO_DMG",
}
TALENT_KEYS = [("combat1", "NORMAL"), ("combat2", "SKILL"), ("combatsp", "SPECIAL"),
               ("combatju", "SPECIAL"), ("combat3", "BURST")]
TRAVELERS = ["anemo", "geo", "electro", "dendro", "hydro", "pyro", "cryo"]
# Characters without playable kits in the data (NPC mannequins).
SKIP_CHARACTERS = {"manekin", "manekina", "aether", "lumine"}


# --------------------------------------------------------------------------- sources

def fetch(url, path):
    if not os.path.exists(path):
        print(f"downloading {url}", file=sys.stderr)
        with urllib.request.urlopen(url) as r, open(path, "wb") as f:
            f.write(r.read())
    return path


def load_sources(cache):
    os.makedirs(cache, exist_ok=True)
    gdb_path = os.path.join(cache, f"genshin-db-{GENSHIN_DB_VERSION}-en.json.gz")
    if not os.path.exists(gdb_path):
        tgz = fetch(GENSHIN_DB_URL, os.path.join(cache, f"genshin-db-{GENSHIN_DB_VERSION}.tgz"))
        with tarfile.open(tgz) as tar:
            member = tar.getmember("package/src/min/data.min.json")
            full = json.load(tar.extractfile(member))
        en = full["data"]["English"]
        subset = {
            "en": {k: en[k] for k in ["characters", "talents", "constellations", "weapons",
                                       "artifacts", "enemies"]},
            "stats": full["stats"], "curve": full["curve"], "version": full["version"],
        }
        with gzip.open(gdb_path, "wt", encoding="utf-8") as f:
            json.dump(subset, f)
    with gzip.open(gdb_path, "rt", encoding="utf-8") as f:
        gdb = json.load(f)
    go = json.load(open(fetch(GO_STATS_URL, os.path.join(cache, "allStat_gen.json"))))
    return gdb, go


# --------------------------------------------------------------------------- helpers

def clean(text):
    if text is None:
        return None
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\{LINK#[A-Z]?\d+\}|\{/LINK\}", "", text)
    # Keep the mobile wording ("Tap") and drop the PC/console variants.
    text = re.sub(r"\{LAYOUT_MOBILE#([^}]*)\}", r"\1", text)
    text = re.sub(r"\{LAYOUT_(PC|PS)#[^}]*\}", "", text)
    text = text.replace("{TIMEZONE}", "")
    return text.replace("\\n", "\n").strip()


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


PLACEHOLDER = re.compile(r"\{(param\d+):([A-Z0-9]+)\}")
STAT_WORDS = [
    ("Max HP", "HP"), ("Elemental Mastery", "EM"), ("DEF", "DEF"), ("ATK", "ATK"),
    ("Corresponding Character's ATK", "ATK"),
]

# Names that are damage even though they do not contain "DMG".
DAMAGE_NAMES = {
    "aimed shot", "fully-charged aimed shot", "charged attack", "aimed shot charge level 1",
    "level 1 aimed shot", "riptide slash", "charged attack: equitable judgment",
}
NOT_DAMAGE_WORDS = re.compile(
    r"\b(bonus|increase|decrease|reduction|absorption|interval|conversion|ratio|cost|consumption|"
    r"duration|chance|regenerat\w*|healing|restored|res|stamina|energy|stacks|quota|inherited|cd|"
    r"drain|gain|loss|mitigation|spd|threshold|maximum)\b")
HEAL_WORDS = re.compile(r"\b(healing|regeneration|hp restored)\b")
HEAL_EXCLUDE = re.compile(r"\b(conversion|chance|triggering|incoming|ratio|interval|cost)\b")
SHIELD_WORDS = re.compile(r"\babsorption\b")


def attribute_kind(name, fmt):
    n = name.lower()
    has_pct = re.search(r"\{param\d+:[A-Z0-9]*P\}", fmt) is not None
    if not has_pct:
        return None
    if SHIELD_WORDS.search(n) and "bonus" not in n:
        return "SHIELD"
    if HEAL_WORDS.search(n) and not HEAL_EXCLUDE.search(n):
        if "bond of life" in fmt.lower() and "healing" not in n:
            return None
        return "HEAL"
    if n in DAMAGE_NAMES:
        return "DMG"
    if "dmg" in n or re.search(r"\bdot\b", n):
        if NOT_DAMAGE_WORDS.search(n):
            return None
        # "x% Normal Attack DMG" style rows describe multipliers, not hits.
        if re.search(r"(Normal|Charged) Attack DMG", fmt):
            return None
        if re.search(r"/(stack|point)|per (?!paw)|/s\b", fmt, re.I):
            return None
        return "DMG"
    return None


def parse_part(text):
    """'{param4:F1P} ATK' -> terms; returns dict(terms=[(stat,param)], flat=param|None, n=int)."""
    text = text.strip()
    count = 1
    m = re.search(r"\s*[×*]\s*(\d+)\s*$", text)
    if m:
        count = int(m.group(1))
        text = text[: m.start()].strip()
    if text.startswith("(") and text.endswith(")"):
        text = text[1:-1]
    pieces = re.split(r"\s*\+\s*", text)
    terms, flat, bare = [], None, []
    for piece in pieces:
        pm = PLACEHOLDER.search(piece)
        if not pm:
            continue
        param, fmt = pm.group(1), pm.group(2)
        rest = piece[pm.end():].strip()
        if not fmt.endswith("P"):
            flat = param
            continue
        stat = None
        for word, key in STAT_WORDS:
            if rest.startswith(word):
                stat = key
                break
        elem = None
        if rest in ("Cryo", "Physical", "Pyro", "Hydro", "Electro", "Anemo", "Geo", "Dendro"):
            elem = rest.upper()
        terms.append({"stat": stat, "param": param, "ele": elem})
        if stat is None:
            bare.append(len(terms) - 1)
    return {"terms": terms, "flat": flat, "n": count}


def parse_damage_format(fmt):
    """Returns a list of variants; each variant is a list of parts (summed hits)."""
    fmt = fmt.strip()
    fmt = re.sub(r"\s+each$", "", fmt)
    fmt = re.sub(r"\s+per Paw$", "", fmt)
    fmt = re.sub(r"^(1|2) Characters? ", "", fmt)
    # Split alternatives on '/' only when both sides hold placeholders.
    raw_variants = [v for v in re.split(r"\s*/\s*", fmt)]
    if len(raw_variants) > 1 and all(PLACEHOLDER.search(v) or v.strip() == "0%" for v in raw_variants):
        variants_txt = raw_variants
    else:
        variants_txt = [fmt]
    variants = []
    for vt in variants_txt:
        if vt.strip() == "0%":
            variants.append(None)
            continue
        # "x% + y% DEF": bare placeholders followed by a stat word -> multi-hit, same stat.
        # "x% ATK + y% Elemental Mastery": every placeholder has its own stat -> one hit.
        group = re.match(r"^\((.*)\)\s*[×*]\s*(\d+)$", vt.strip())
        if group:
            part = parse_part(group.group(1))
            part["n"] = int(group.group(2))
            variants.append([part])
            continue
        segments = re.split(r"\s*\+\s*", vt)
        parsed = [parse_part(s) for s in segments]
        has_flat = any(p["flat"] for p in parsed)
        stats = [t["stat"] for p in parsed for t in p["terms"]]
        elems = [t["ele"] for p in parsed for t in p["terms"]]
        if len(parsed) > 1 and not any(elems) and (has_flat or (all(stats) and len(set(stats)) == len(stats))):
            merged = {"terms": [t for p in parsed for t in p["terms"]],
                      "flat": next((p["flat"] for p in parsed if p["flat"]), None),
                      "n": max(p["n"] for p in parsed)}
            variants.append([merged])
        else:
            variants.append(parsed)
    # Propagate a trailing stat word to bare placeholders ("x%/y% Max HP", "x%+y% DEF").
    last_stat = None
    for v in reversed(variants):
        if v is None:
            continue
        for p in reversed(v):
            for t in reversed(p["terms"]):
                if t["stat"]:
                    last_stat = t["stat"]
                elif last_stat:
                    t["stat"] = last_stat
    for v in variants:
        if v is None:
            continue
        for p in v:
            for t in p["terms"]:
                t["stat"] = t["stat"] or "ATK"
    return variants


def split_variant_names(name, count):
    if count == 1:
        return [name]
    m = re.match(r"^(.*?)\b(Low)/(High)\b(.*)$", name)
    if m and count == 2:
        return [f"{m.group(1)}{m.group(2)}{m.group(4)}".strip(), f"{m.group(1)}{m.group(3)}{m.group(4)}".strip()]
    pieces = name.split("/")
    if len(pieces) == count:
        first_words = len(pieces[0].split())
        last = pieces[-1].split()
        suffix = " ".join(last[first_words:])
        alts = [p.strip() for p in pieces[:-1]] + [" ".join(last[:first_words])]
        return [f"{a} {suffix}".strip() for a in alts]
    return [f"{name} ({i + 1})" for i in range(count)]


HIT_RE = re.compile(r"(\d+)-Hit")


def default_category(talent, name, weapon):
    n = name.lower()
    if "plunge" in n or "plunging" in n:
        return "PLUNGE"
    if "charged attack" in n or "aimed shot" in n or "charge level" in n and talent == "NORMAL":
        return "CHARGED"
    if n == "normal attack dmg":
        return "NORMAL"
    if HIT_RE.search(name) and "skill" not in n:
        return "NORMAL" if talent in ("NORMAL", "SKILL", "BURST") else "NORMAL"
    if talent == "NORMAL":
        # Special shots/attacks inside the Normal Attack talent are charged attacks
        # (Frostflake Arrow, Breakthrough Barb, Prop Arrow, ...).
        return "CHARGED"
    if talent == "BURST":
        return "BURST"
    return "SKILL"


def default_element(talent, category, name, char_element, weapon):
    n = name.lower()
    if talent == "NORMAL":
        if weapon == "CATALYST":
            return char_element
        if category == "NORMAL" or category == "PLUNGE":
            return None
        if category == "CHARGED":
            if weapon == "BOW":
                if n == "aimed shot":
                    return None
                return char_element
            if n.startswith("charged attack") or n in ("charged attack",):
                return None
            return char_element
        return None
    return char_element


# --------------------------------------------------------------------------- builders

def build_curves(gdb, go):
    char_curves = {}
    for lvl, row in gdb["curve"]["characters"].items():
        for name, val in row.items():
            char_curves.setdefault(name, [0.0] * 101)[int(lvl)] = val
    weapon_curves = {}
    for lvl, row in gdb["curve"]["weapons"].items():
        for name, val in row.items():
            weapon_curves.setdefault(name, [0.0] * 101)[int(lvl)] = val
    enemy_curves = {}
    for lvl, row in gdb["curve"]["enemies"].items():
        for name, val in row.items():
            enemy_curves.setdefault(name, [0.0] * 201)[int(lvl)] = val
    art = go["art"]
    main = {}
    for rarity, table in art["main"].items():
        main[rarity] = {GO_ART_KEYS[k]: v for k, v in table.items() if k in GO_ART_KEYS}
    sub = {}
    for rarity, table in art["sub"].items():
        sub[rarity] = {GO_ART_KEYS[k]: v for k, v in table.items() if k in GO_ART_KEYS}
    return {
        "character": char_curves,
        "weapon": weapon_curves,
        "artifactMain": main,
        "artifactSub": sub,
    }


def build_talent(t, ttype, char_id):
    attrs = t.get("attributes") or {}
    labels = attrs.get("labels") or []
    return {
        "type": ttype,
        "name": t.get("name"),
        "description": clean(t.get("descriptionRaw") or t.get("description")),
        "attributes": [{"label": l.split("|", 1)[0].strip(), "value": l.split("|", 1)[1] if "|" in l else ""}
                       for l in labels],
    }


def build_hits(char_id, talents_raw, char_element, weapon):
    hits = []
    used_ids = set()
    for key, ttype in TALENT_KEYS:
        t = talents_raw.get(key)
        if not t or not t.get("attributes"):
            continue
        for label in t["attributes"].get("labels") or []:
            if "|" not in label:
                continue
            name, fmt = [x.strip() for x in label.split("|", 1)]
            kind = attribute_kind(name, fmt)
            if not kind:
                continue
            variants = parse_damage_format(fmt)
            names = split_variant_names(name, len(variants))
            for vname, parts in zip(names, variants):
                if parts is None:
                    continue
                cat = default_category(ttype, vname, weapon) if kind == "DMG" else "NONE"
                ele = default_element(ttype, cat, vname, char_element, weapon) if kind == "DMG" else None
                talent_prefix = {"NORMAL": "normal", "SKILL": "skill", "BURST": "burst",
                                 "SPECIAL": "special"}[ttype]
                hid = f"{talent_prefix}/{slug(vname)}"
                base_id, i = hid, 2
                while hid in used_ids:
                    hid = f"{base_id}-{i}"
                    i += 1
                used_ids.add(hid)
                hit = {
                    "id": hid, "talent": ttype, "name": vname, "kind": kind, "category": cat,
                    "element": ele,
                    "parts": [{
                        "terms": [{"stat": tm["stat"], "param": tm["param"]} for tm in p["terms"]],
                        "flat": p["flat"], "count": p["n"],
                        "element": next((tm["ele"] for tm in p["terms"] if tm["ele"]), None),
                    } for p in parts],
                }
                # Lunar / stellar direct damage rows.
                low = vname.lower()
                for tag, special in (("lunar-charged", "LUNAR_CHARGED"), ("lunar-bloom", "LUNAR_BLOOM"),
                                     ("lunar-crystallize", "LUNAR_CRYSTALLIZE"),
                                     ("stellar-conduct/stellar swirl", "STELLAR"),
                                     ("stellar-conduct", "STELLAR_CONDUCT"),
                                     ("stellar swirl", "STELLAR_SWIRL")):
                    if tag in low:
                        hit["special"] = special
                        break
                hits.append(hit)
    return hits


def apply_overrides(char_id, hits, talents):
    ov = CHARACTER_OVERRIDES.get(char_id, {})
    by_id = {h["id"]: h for h in hits}
    for hid, patch in ov.get("hits", {}).items():
        if hid not in by_id:
            raise SystemExit(f"override for unknown hit {char_id}:{hid}; known: {sorted(by_id)}")
        if patch.get("remove"):
            hits.remove(by_id[hid])
            continue
        h = by_id[hid]
        for k, v in patch.items():
            if k == "partElements":
                for p, e in zip(h["parts"], v):
                    p["element"] = e
            else:
                h[k] = v
    for extra in ov.get("addHits", []):
        extra = dict(extra)
        # Synthetic scaling values (constant across talent levels) live in the talent's params.
        for pname, value in extra.pop("params", {}).items():
            talent = next(t for t in talents if t["type"] == extra["talent"])
            talent["params"][pname] = value if isinstance(value, list) else [value] * 15
        hits.append(extra)
    return hits


def build_characters(gdb):
    en = gdb["en"]
    chars = []
    talents_all = en["talents"]
    for cid in sorted(talents_all):
        if cid in SKIP_CHARACTERS:
            continue
        if cid.startswith("traveler"):
            elem = cid[len("traveler"):]
            base = en["characters"]["aether"]
            stats = gdb["stats"]["characters"]["aether"]
            c = dict(base)
            c["name"] = f"Traveler ({elem.capitalize()})"
            c["elementType"] = f"ELEMENT_{ {'anemo':'WIND','geo':'ROCK','electro':'ELEC','dendro':'GRASS','hydro':'WATER','pyro':'FIRE','cryo':'ICE'}[elem] }"
            element = elem.upper()
            const_raw = en["constellations"].get(cid, {})
            version = gdb["version"]["talents"].get(cid) if isinstance(gdb["version"].get("talents"), dict) else None
        else:
            if cid not in en["characters"]:
                continue
            c = en["characters"][cid]
            stats = gdb["stats"]["characters"][cid]
            element = ELEMENTS.get(c["elementType"])
            const_raw = en["constellations"].get(cid, {})
            version = gdb["version"]["characters"].get(cid)
        if element is None:
            continue
        weapon = WEAPONS[c["weaponType"]]
        t = talents_all[cid]
        talents = []
        for key, ttype in TALENT_KEYS:
            if key in t and t[key].get("name"):
                talents.append(build_talent(t[key], ttype, cid))
        params = {}
        if cid in gdb["stats"]["talents"]:
            st = gdb["stats"]["talents"][cid]
            for key, ttype in TALENT_KEYS:
                if key in st:
                    params[ttype] = {p: v for p, v in st[key].items()}
        for tal in talents:
            tal["params"] = params.get(tal["type"], {})
        passives = []
        for i in range(1, 5):
            p = t.get(f"passive{i}")
            if p:
                passives.append({"name": p["name"], "description": clean(p.get("descriptionRaw") or p.get("description"))})
        consts = []
        c3 = c5 = None
        for i in range(1, 7):
            cc = const_raw.get(f"c{i}")
            if cc:
                desc = clean(cc.get("descriptionRaw") or cc.get("description"))
                consts.append({"name": cc["name"], "description": desc})
                if i in (3, 5) and desc and re.search(r"by 3|3 Levels", desc):
                    first = desc.split("\n")[0]
                    target = None
                    for tal in talents:
                        if tal["name"] and tal["type"] != "SPECIAL" and tal["name"].lower() in first.lower():
                            target = tal["type"]
                    if target is None:
                        if "Elemental Skill" in first:
                            target = "SKILL"
                        elif "Elemental Burst" in first:
                            target = "BURST"
                        elif "Normal Attack" in first:
                            target = "NORMAL"
                    if i == 3:
                        c3 = target
                    else:
                        c5 = target
        promo = stats["promotion"]
        asc_stat = PROPS.get(stats.get("specialized"))
        hits = build_hits(cid, t, element, weapon)
        hits = apply_overrides(cid, hits, talents)
        chars.append({
            "id": cid,
            "name": c["name"],
            "title": c.get("title"),
            "element": element,
            "weapon": weapon,
            "rarity": c.get("rarity"),
            "region": c.get("region"),
            "affiliation": c.get("affiliation"),
            "constellation": c.get("constellation"),
            "birthday": c.get("birthday"),
            "description": clean(c.get("description")),
            "release": version,
            "stats": {
                "hp": stats["base"]["hp"], "atk": stats["base"]["attack"], "def": stats["base"]["defense"],
                "curveHp": stats["curve"]["hp"], "curveAtk": stats["curve"]["attack"],
                "curveDef": stats["curve"]["defense"],
                "ascHp": [p["hp"] for p in promo], "ascAtk": [p["attack"] for p in promo],
                "ascDef": [p["defense"] for p in promo],
                "ascStat": asc_stat, "ascStatValues": [p.get("specialized", 0) for p in promo],
            },
            "talents": talents,
            "passives": passives,
            "constellations": consts,
            "c3": c3, "c5": c5,
            "hits": hits,
        })
    return chars


def build_weapons(gdb):
    en = gdb["en"]
    out = []
    for wid, w in sorted(en["weapons"].items()):
        st = gdb["stats"]["weapons"].get(wid)
        if not st:
            continue
        refinements = []
        for r in range(1, 6):
            rr = w.get(f"r{r}")
            if rr:
                refinements.append(rr.get("values") or [])
        out.append({
            "id": wid,
            "name": w["name"],
            "type": WEAPONS[w["weaponType"]],
            "rarity": w["rarity"],
            "description": clean(w.get("description")),
            "baseAtk": st["base"]["attack"],
            "curveAtk": st["curve"]["attack"],
            "substat": PROPS.get(st.get("specialized")),
            "substatBase": st["base"].get("specialized", 0),
            "curveSub": st["curve"].get("specialized"),
            "ascAtk": [p.get("attack", 0) for p in st["promotion"]],
            "effectName": w.get("effectName"),
            "effectTemplate": clean(w.get("effectTemplateRaw")),
            "refinements": refinements,
            "release": gdb["version"]["weapons"].get(wid),
        })
    return out


def build_artifacts(gdb):
    en = gdb["en"]
    out = []
    for aid, a in sorted(en["artifacts"].items()):
        pieces = {}
        for slot in ["flower", "plume", "sands", "goblet", "circlet"]:
            if slot in a:
                pieces[slot] = a[slot]["name"]
        out.append({
            "id": aid,
            "name": a["name"],
            "rarities": a.get("rarityList") or [],
            "onePiece": clean(a.get("effect1Pc")),
            "twoPiece": clean(a.get("effect2Pc")),
            "fourPiece": clean(a.get("effect4Pc")),
            "pieces": pieces,
            "release": gdb["version"]["artifacts"].get(aid),
        })
    return out


def build_enemies(gdb):
    en = gdb["en"]
    out = []
    for eid, e in sorted(en["enemies"].items()):
        st = gdb["stats"]["enemies"].get(eid)
        if not st or "resistance" not in st:
            continue
        res = {k.upper(): v for k, v in st["resistance"].items()}
        out.append({
            "id": eid, "name": e["name"],
            "type": e.get("enemyType"), "category": e.get("categoryText"),
            "res": res,
        })
    return out


def build_showcase_ids(gdb, enka, chars, weapons, sets):
    """Game ids used by the in-game showcase (Enka.Network API) -> ids of this data set."""
    en = gdb["en"]
    char_ids = {c["id"] for c in chars}
    by_avatar = {str(en["characters"][cid]["id"]): cid for cid in char_ids if cid in en["characters"]}
    characters = {}
    for key, a in sorted(enka.items()):
        avatar, _, depot = key.partition("-")
        if avatar in TRAVELER_AVATARS:
            elem = ENKA_ELEMENTS.get(a.get("Element"))
            cid = f"traveler{elem}" if depot and elem else None
        else:
            cid = None if depot else by_avatar.get(avatar)
        if cid in char_ids and len(a.get("SkillOrder", [])) == 3:
            characters[key] = {"id": cid, "skills": a["SkillOrder"]}
    missing = sorted(char_ids - {v["id"] for v in characters.values()})
    if missing:
        print(f"showcase: no skill ids for {missing}", file=sys.stderr)
    weapon_ids = {str(en["weapons"][w["id"]]["id"]): w["id"] for w in weapons if "id" in en["weapons"][w["id"]]}
    set_ids = {str(en["artifacts"][a["id"]]["id"]): a["id"] for a in sets if "id" in en["artifacts"][a["id"]]}
    return {"characters": characters, "weapons": dict(sorted(weapon_ids.items())), "sets": dict(sorted(set_ids.items()))}


def write(name, data):
    path = os.path.join(OUT_DIR, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {path} ({os.path.getsize(path) // 1024} KB)", file=sys.stderr)


def report(chars, path):
    lines = []
    for c in chars:
        lines.append(f"## {c['id']} {c['name']} {c['element']} {c['weapon']} c3={c['c3']} c5={c['c5']}")
        for h in c["hits"]:
            parts = " + ".join(
                ("(" + " + ".join(f"{t['stat']}:{t['param']}" for t in p["terms"]) +
                 (f" + flat:{p['flat']}" if p['flat'] else "") + ")" +
                 (f"x{p['count']}" if p['count'] != 1 else "") + (f"[{p['element']}]" if p.get('element') else ""))
                for p in h["parts"])
            lines.append(f"  {h['id']:<55} {h['kind']:<6} {h['category']:<8} {str(h['element']):<9} "
                         f"{h.get('special') or '':<14} {parts}")
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=os.path.join(PROJECT, "build", "datagen-cache"))
    ap.add_argument("--report", default=None)
    ap.add_argument("--only", choices=["showcase"], help="only regenerate this file")
    args = ap.parse_args()
    gdb, go = load_sources(args.cache)
    enka = json.load(open(fetch(ENKA_AVATARS_URL, os.path.join(args.cache, "enka-avatars.json"))))
    os.makedirs(OUT_DIR, exist_ok=True)
    chars = build_characters(gdb)
    weapons = build_weapons(gdb)
    sets = build_artifacts(gdb)
    write("showcase.json", build_showcase_ids(gdb, enka, chars, weapons, sets))
    if args.only:
        return
    write("characters.json", {"gameVersion": "7.1", "characters": chars})
    write("weapons.json", {"weapons": weapons})
    write("artifacts.json", {"artifacts": sets})
    write("enemies.json", {"enemies": build_enemies(gdb)})
    write("curves.json", build_curves(gdb, go))
    if args.report:
        report(chars, args.report)
        print(f"report: {args.report}", file=sys.stderr)


if __name__ == "__main__":
    main()
