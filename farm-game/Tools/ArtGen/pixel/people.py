"""Five named farmhands (pixel-direct): folder "people" holds their body frames (drawn by
_farmhands.py, style D3) and folder "portraits" their 30x30 busts (fh_<name>). Names: rosa, milo,
hana, otis, wren. The 14x22 body templates below are the first style, kept for reference.

Body frames, pivot = the point between the feet on the ground (+ pad), as in deco.py:
  se_idle, se_walk0, se_walk1      facing the viewer, walking down-right (legs alternate)
  se_work0, se_work1               bent over: hoe raised, then struck into the soil
  se_carry0, se_carry1             walking with a small wooden crate of produce held in front
  ne_idle, ne_walk0, ne_walk1      seen from behind, walking up-right
  ne_carry0, ne_carry1             from behind with the crate (its ends show at the sides)

All five share one drawing function, frame(). Proportions, poses and the outline never change;
a look (colour ramps, head style, outfit) is the only thing that differs between them. Each
region is dome-lit from the top-left and checker-dithered between ramp steps (the same model as
portraits.py and buildings.py), flat features (eyes, mouth, freckles) sit on top, and finish()
adds the 1px ink outline and the hard drop shadow.

Character grids: one char per pixel, '.' is transparent. Each char is a region with its own
ramp (see ramps_of) or a flat colour (see look()).
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, Sprites, new, finish  # noqa: E402
import json  # noqa: E402
import math  # noqa: E402

W, H = 14, 22
DEPTH_BODY = 2.0
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
_L = (-0.55, -0.7, 0.6)
_LN = math.sqrt(sum(c * c for c in _L))
LIGHT = tuple(c / _LN for c in _L)


def col(c):
    return E[c] if isinstance(c, str) else c


def tone(ramp, v, x, y):
    """Ramp colour for a fractional level v, checker-dithered between neighbouring steps."""
    n = len(ramp) - 1
    v = max(0.0, min(float(n), v))
    b = int(v)
    f = v - b
    if b >= n or f < 0.3:
        return col(ramp[b])
    if f > 0.7:
        return col(ramp[b + 1])
    return col(ramp[b + 1]) if (x + y) % 2 == 0 else col(ramp[b])


def dist_in(s):
    """BFS distance (1 = edge) of every pixel of region s to the outside of s."""
    dist = {}
    q = [p for p in s if any((p[0] + dx, p[1] + dy) not in s for dx, dy in N4)]
    for p in q:
        dist[p] = 1
    i = 0
    while i < len(q):
        x, y = q[i]
        i += 1
        for dx, dy in N4:
            m = (x + dx, y + dy)
            if m in s and m not in dist:
                dist[m] = dist[(x, y)] + 1
                q.append(m)
    return dist


def shade(px, s, ramp, depth):
    """Dome-shade one region (a set of pixels) lit from the top-left, written into px."""
    dist = dist_in(s)
    hgt = {p: depth * math.sqrt(max(0.0, 1 - (1 - min(1.0, (d - 0.5) / depth)) ** 2))
           for p, d in dist.items()}
    n = len(ramp) - 1
    for (x, y) in s:
        gx = (hgt.get((x + 1, y), 0) - hgt.get((x - 1, y), 0)) / 2
        gy = (hgt.get((x, y + 1), 0) - hgt.get((x, y - 1), 0)) / 2
        nl = math.sqrt(gx * gx + gy * gy + 1)
        lv = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / nl
        v = ((lv + 0.25) / 1.25) * n
        px[x, y] = tone(ramp, v, x, y)


# ---------------------------------------------------------------- palettes (dark to light)

BROWN_HAIR = ["ink", "brown_d", "brown", "brown_l"]
BLACK_HAIR = ["ink", "slate_d", "slate", "grey_d"]
RUST_HAIR = ["brown", "rust", "clay", "amber"]
BLOND = ["clay", "amber", "yellow", "cream"]
GREY_HAIR = ["slate", "grey_d", "grey", "grey_l"]
BEARD = ["grey", "grey_l", "white"]
SKIN_DEEP = ["brown", "brown_l", "tan_d", "tan"]
SKIN_FAIR = ["tan_d", "tan", "skin", "cream"]
SKIN_LIGHT_TAN = ["brown_l", "tan_d", "tan", "skin"]
SKIN_MEDIUM = ["brown_l", "tan_d", "tan", "skin"]
CREAM = ["tan", "cream", "white"]
WHITE_SHIRT = ["grey", "grey_l", "white"]
YELLOW_SHIRT = ["clay", "amber", "yellow"]
ORANGE_SHIRT = ["red_d", "rust", "orange", "amber"]
PURPLE = ["purple", "magenta", "pink"]
GREEN = ["green_dd", "green_d", "green"]
BLUE = ["slate_d", "blue_d", "blue"]
DENIM = ["slate", "blue_d", "blue"]
SLATE_TROUSER = ["ink", "slate_d", "slate"]
BROWN_TROUSER = ["brown_d", "brown", "brown_l"]
APRON = ["brown_d", "brown", "brown_l"]
RED = ["red_d", "red", "pink"]
STRAW = ["tan_d", "tan", "amber", "yellow"]
CAP = ["brown_d", "brown", "brown_l"]
CAP_BRIM = ["ink", "brown_d", "brown"]
BELT = ["ink", "brown_d", "brown"]
BOOT = ["ink", "brown_d", "brown"]
WOOD = ["brown", "brown_l", "tan_d", "tan"]
PLANK = ["ink", "brown_d", "brown"]
HANDLE = ["brown_d", "brown", "brown_l"]
IRON = ["slate", "grey", "grey_l"]
LEAF = ["green_dd", "green_d", "green"]
DOT = ["ink", "ink"]

# ---------------------------------------------------------------- body templates (14 wide)

# Head: 8 rows (0-7), the neck is row 7. Front ("fr") faces the viewer, back ("bk") is seen from behind.
# H hair, S skin, R band, G hat crown, g hat brim, F beard; e, m, f are flat features on the skin.
HEAD = {
    "bun": {
        "fr": [
            ".....HHHH.....",
            "...HHHHHHHH...",
            "...RRRRRRRR...",
            "...HSSSSSSH...",
            "...HSeSSeSH...",
            "...HSSmmSSH...",
            "....SSSSSS....",
            ".....SSSS.....",
        ],
        "bk": [
            ".....HHHH.....",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...RRRRRRRR...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...RHHHHHHR...",
            ".....SSSS.....",
        ],
    },
    "straw": {
        "fr": [
            "....GGGGGG....",
            "...RRRRRRRR...",
            ".gggggggggggg.",
            "...HHHHHHHH...",
            "...HSeSSeSH...",
            "...HfSSSSfH...",
            "....SSmmSS....",
            ".....SSSS.....",
        ],
        "bk": [
            "....GGGGGG....",
            "...RRRRRRRR...",
            ".gggggggggggg.",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "....HHHHHH....",
            ".....SSSS.....",
        ],
    },
    "bob": {
        "fr": [
            "....HHHHHH....",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HSeSSeSH...",
            "...HSSSSSSH...",
            "...HSSmmSSH...",
            "...HHSSSSHH...",
            ".....SSSS.....",
        ],
        "bk": [
            "....HHHHHH....",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            ".....SSSS.....",
        ],
    },
    "cap": {
        "fr": [
            "...GGGGGGGG...",
            "...GGGGGGGG...",
            "..gggggggggg..",
            "...HSeSSeSH...",
            "...HSSSSSSH...",
            "...FFFmmFFF...",
            "....FFFFFF....",
            ".....FFFF.....",
        ],
        "bk": [
            "...GGGGGGGG...",
            "...GGGGGGGG...",
            "...gggggggg...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "....HHHHHH....",
            ".....SSSS.....",
        ],
    },
    "braid": {
        "fr": [
            "....HHHHHH....",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HSeSSeSH...",
            "...HSSSSSSH...",
            "...HSSmmSSH...",
            "....SSSSSS....",
            ".....SSSS.....",
        ],
        "bk": [
            "....HHHHHH....",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "...HHHHHHHH...",
            "....HHHHHH....",
            ".....SSSS.....",
        ],
    },
}

# Torso: 6 rows, rows 8-13. Arms are 'a' (shirt ramp), belt 'Y', overall bib or apron 'B',
# trousers 'L' (hip row) with the legs below. "apron_bk" is the back of the apron wearer.
TORSO = {
    "bib": [
        "..aaBCCCCBaa..",
        "..aaBBBBBBaa..",
        "..aaBBBBBBaa..",
        "..aaBBBBBBaa..",
        "..aaYYYYYYaa..",
        "..aaBBBBBBaa..",
    ],
    "plain": [
        "..aaCCCCCCaa..",
        "..aaCCCCCCaa..",
        "..aaCCCCCCaa..",
        "..aaCCCCCCaa..",
        "..aaYYYYYYaa..",
        "..aaBBBBBBaa..",
    ],
    "apron": [
        "..aaCCCCCCaa..",
        "..aaCCCCCCaa..",
        "..aaBBBBBBaa..",
        "..aBBBBBBBBa..",
        "..aaBBBBBBaa..",
        "..aaBBBBBBaa..",
    ],
    "apron_bk": [
        "..aaCCCCCCaa..",
        "..aaCCCCCCaa..",
        "..aaCCCCCCaa..",
        "..aBCCCCCCBa..",
        "..aaYYYYYYaa..",
        "..aaLLLLLLaa..",
    ],
}

# Crate of produce held in front (front view), over torso rows 8-13. '.' keeps the torso.
CRATE_FR = [
    "..............",
    "..aaQVVVVQaa..",
    "..aSWWWWWWSa..",
    "..aaWwWWwWaa..",
    "..aaWWwWWWaa..",
    "..aawwwwwwaa..",
]
# From behind only the crate's ends show, just outside the body.
CRATE_BK = [
    "..............",
    "..............",
    "..............",
    ".W..........W.",
    ".W..........W.",
    ".W..........W.",
]

# Legs: 8 rows (14-21). Feet end on row 21. The walk frames lift the other foot by one row.
LEG_IDLE = [
    "...LLL..MMM...",
    "...LLL..MMM...",
    "...LLL..MMM...",
    "...LLL..MMM...",
    "...LLL..MMM...",
    "...LLL..MMM...",
    "...KKK..KKK...",
    "...KKK..KKK...",
]
LEG_WALK0 = [
    "..LLL...MMM..",
    "..LLL...MMM..",
    "..LLL...MMM..",
    "..LLL...MMM..",
    "..LLL...MMM..",
    "..LLL...KKK..",
    "..KKK...KKK..",
    "..KKK........",
]
LEG_WALK1 = [r[::-1] for r in LEG_WALK0]
# Bent over: the knees bend, so the legs are two rows shorter (6 rows, placed at row 16).
LEG_WORK = LEG_IDLE[0:4] + LEG_IDLE[6:8]

# Braid overlays, as (x, row offset from the torso top) for the front and (x, row offset) for the back.
BRAID_FR = [(4, 0), (4, 1), (5, 2), (4, 3), (4, 4), (5, 5)]
BRAID_BK = [(6, -1), (7, 0), (6, 1), (7, 2), (6, 3), (7, 4), (6, 5)]

# Work pose: hands on the hoe grip, then the hoe raised (work0) or driven into the soil (work1).
WORK_HANDS = [(2, 15, "S"), (11, 15, "S")]
HOE_UP = [(12, 15, "d"), (12, 14, "d"), (13, 13, "d"), (13, 12, "d"), (13, 11, "d"),
          (12, 9, "I"), (13, 9, "I"), (12, 10, "I"), (13, 10, "I")]
HOE_DOWN = [(12, 15, "d"), (12, 16, "d"), (12, 17, "d"), (13, 18, "d"), (13, 19, "d"),
            (12, 20, "I"), (13, 20, "I"), (13, 21, "I")]

FLAT_REGION = {"e": "S", "m": "S", "f": "S"}


def place(g, rows, y0):
    for i, row in enumerate(rows):
        y = y0 + i
        for x, ch in enumerate(row):
            if ch != "." and 0 <= y < H and 0 <= x < W:
                g[y][x] = ch


def merge(base, over):
    """Rows of base with the non-transparent chars of over laid on top."""
    return ["".join(oc if oc != "." else bc for oc, bc in zip(o, b)) for b, o in zip(base, over)]


def frame(lk, view, pose, phase=0):
    """One 14x22 frame as a grid of chars. view 'se' faces the viewer, 'ne' is seen from behind."""
    g = [["."] * W for _ in range(H)]
    back = view == "ne"
    vk = "bk" if back else "fr"
    hy, ty = (2, 10) if pose == "work" else (0, 8)
    place(g, HEAD[lk["head"]][vk], hy)

    tkey = lk["torso"] + ("_bk" if back and lk["torso"] == "apron" else "")
    torso = TORSO[tkey]
    if pose == "carry":
        torso = merge(torso, CRATE_BK if back else CRATE_FR)
    place(g, torso, ty)

    if lk["head"] == "braid" and not (pose == "carry" and not back):
        for x, dy in (BRAID_BK if back else BRAID_FR):
            if 0 <= ty + dy < H:
                g[ty + dy][x] = "T"

    if pose == "work":
        place(g, LEG_WORK, 16)
        for x, y, ch in WORK_HANDS:
            g[y][x] = ch
        for x, y, ch in (HOE_UP if phase == 0 else HOE_DOWN):
            g[y][x] = ch
    else:
        if pose == "idle":
            legs = LEG_IDLE
        else:
            legs = LEG_WALK0 if phase == 0 else LEG_WALK1
        place(g, legs, 14)
    return g


def ramps_of(p):
    """Region char -> ramp for one farmhand (all five share this mapping)."""
    return {
        "S": p["skin"], "H": p["hair"], "T": p["hair"], "F": p["beard"] or BEARD,
        "G": p["hat"] or CAP, "g": p["brim"] or CAP_BRIM, "R": p["band"] or RED,
        "a": p["shirt"], "C": p["shirt"], "B": p["bib"], "L": p["legs"], "M": p["legs"],
        "Y": BELT, "K": BOOT, "W": WOOD, "w": PLANK, "V": p["produce"], "Q": LEAF,
        "d": HANDLE, "I": IRON,
    }


def look(p):
    p = {"beard": None, "hat": None, "brim": None, "band": None, "freck": "brown_l", **p}
    return {
        "head": p["head"], "torso": p["torso"], "ramps": ramps_of(p),
        "flat": {"e": "ink", "m": p["mouth"], "f": p.get("freck", "brown_l")},
    }


def paint(g, lk):
    """Char grid -> finished sprite (Image, pad). Flat features are shaded with their skin region."""
    im = new(W, H)
    px = im.load()
    regions, flats = {}, []
    for y in range(H):
        for x in range(W):
            ch = g[y][x]
            if ch == ".":
                continue
            if ch in lk["flat"]:
                flats.append((x, y, ch))
                region = FLAT_REGION[ch]
            else:
                region = ch
            regions.setdefault(region, set()).add((x, y))
    for region, s in regions.items():
        shade(px, s, lk["ramps"][region], DEPTH_BODY)
    for x, y, ch in flats:
        px[x, y] = col(lk["flat"][ch])
    return finish(im)


FRAMES = [
    ("se", "idle", 0, "idle"), ("se", "walk", 0, "walk0"), ("se", "walk", 1, "walk1"),
    ("se", "work", 0, "work0"), ("se", "work", 1, "work1"),
    ("se", "carry", 0, "carry0"), ("se", "carry", 1, "carry1"),
    ("ne", "idle", 0, "idle"), ("ne", "walk", 0, "walk0"), ("ne", "walk", 1, "walk1"),
    ("ne", "carry", 0, "carry0"), ("ne", "carry", 1, "carry1"),
]

# ---------------------------------------------------------------- the five farmhands

PEOPLE = {
    # Warm brown skin, dark hair in a bun, red bandana, green overalls over a cream shirt.
    "rosa": dict(head="bun", torso="bib", skin=SKIN_DEEP, hair=BROWN_HAIR, shirt=CREAM,
                 bib=GREEN, legs=GREEN, band=RED, produce=RED, mouth="red_d"),
    # Freckles, wide straw hat with a red band, ginger hair, blue overalls over a white shirt.
    "milo": dict(head="straw", torso="bib", skin=SKIN_FAIR, hair=RUST_HAIR, shirt=WHITE_SHIRT,
                 bib=BLUE, legs=BLUE, hat=STRAW, brim=STRAW, band=RED, produce=ORANGE_SHIRT,
                 mouth="red_d", freck="brown_l"),
    # Black bob with a fringe, light-tan skin, yellow shirt, denim trousers.
    "hana": dict(head="bob", torso="plain", skin=SKIN_LIGHT_TAN, hair=BLACK_HAIR, shirt=YELLOW_SHIRT,
                 bib=DENIM, legs=DENIM, produce=LEAF, mouth="red_d"),
    # Older, grey beard and hair, brown flat cap, orange shirt, brown trousers.
    "otis": dict(head="cap", torso="plain", skin=SKIN_MEDIUM, hair=GREY_HAIR, shirt=ORANGE_SHIRT,
                 bib=BROWN_TROUSER, legs=BROWN_TROUSER, hat=CAP, brim=CAP_BRIM, beard=BEARD,
                 produce=ORANGE_SHIRT, mouth="brown_d"),
    # Blonde braid over one shoulder, purple shirt, brown work apron, dark trousers.
    "wren": dict(head="braid", torso="apron", skin=SKIN_FAIR, hair=BLOND, shirt=PURPLE,
                 bib=APRON, legs=SLATE_TROUSER, produce=PURPLE, mouth="red_d"),
}


# ---------------------------------------------------------------- portraits (30x30 busts)

PBASE = {
    "_": None,
    "H": ("hair", None), "S": ("skin", None), "C": ("cloth", None), "D": ("cloth2", None),
    "A": ("cap", None), "B": ("brim", None), "G": ("collar", None),
    "P": ("apron", None), "W": ("beard", None), "R": ("band", None), "Z": ("braid", None),
    "e": ("skin", "ink"), "w": ("skin", "white"), "b": ("skin", "brown_d"),
    "m": ("skin", "brown"), "n": ("skin", "tan_d"), "r": ("skin", "pink"),
    "f": ("skin", "brown_l"), "k": ("skin", "brown_d"), "x": ("skin", "slate_d"),
    "o": ("skin", "brown"), "T": ("skin", "white"),
    "y": ("acc", "yellow"),
}
PDEPTH = {"skin": 3.0, "cloth": 2.8, "cloth2": 2.6, "hair": 2.2}
PMASK = set()
for _y in range(30):
    for _x in range(30):
        _dx = max(0.0, 7 - (_x + 0.5), (_x + 0.5) - 23)
        _dy = max(0.0, 7 - (_y + 0.5), (_y + 0.5) - 23)
        if _dx * _dx + _dy * _dy <= 49:
            PMASK.add((_x, _y))


def paint_portrait(spec):
    grid = []
    for row in spec["rows"]:
        full = row + row[::-1] if len(row) == 15 else row
        assert len(full) == 30, (len(full), row)
        grid.append(list(full))
    assert len(grid) == 30, len(grid)
    for (x, y), ch in spec.get("patch", {}).items():
        grid[y][x] = ch
    key = dict(PBASE)
    key.update(spec.get("key", {}))
    im = new(30, 30)
    px = im.load()
    bg = spec["bg"]
    n = len(bg) - 1
    groups, over = {}, []
    for x, y in PMASK:
        ch = grid[y][x]
        if ch == "_":
            px[x, y] = tone(bg, n * (1.0 - 0.8 * y / 29 - 0.2 * x / 29), x, y)
            continue
        grp, ov = key[ch]
        groups.setdefault(grp, set()).add((x, y))
        if ov:
            over.append((x, y, ov))
    for grp, s in groups.items():
        shade(px, s, spec["ramps"].get(grp, DOT), PDEPTH.get(grp, 2.2))
    for x, y, ov in over:
        px[x, y] = col(ov)
    figure = set().union(*groups.values())
    for x, y in PMASK:
        if (x, y) in figure:
            continue
        if any((x + dx, y + dy) in figure for dx, dy in N4):
            px[x, y] = E["ink"]
    return finish(im)


def braid_patch(points):
    """A 2px-wide braid: each centre point also fills the pixel to its right."""
    out = {}
    for x, y in points:
        out[(x, y)] = "Z"
        out[(x + 1, y)] = "Z"
    return out


_ROSA_ROWS = [
    "_______________",
    "_____________HH",
    "____________HHH",
    "_________HHHHHH",
    "______HHHHHHHHH",
    "______RRRRRRRRR",
    "______HHHSSSSSS",
    "______HHSSSSSSS",
    "______HHSSSSSSS",
    "______HHSbbbSSS",
    "______HHSSweSSS",
    "______HHSSeeSSS",
    "______HHSrrSSSS",
    "______HHSSSSSSn",
    "______HHSSSSSSS",
    "_________SSSSSS",
    "_________SmTTmS",
    "___________mmmm",
    "___________SSSS",
    "_____________SS",
    "____________SSS",
    "_________DDDDDD",
    "______CCDDDDDDD",
    "____CCCCCCCCCCC",
    "___CCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
]
_MILO_ROWS = [
    "_______________",
    "_______________",
    "__________AAAAA",
    "_________AAAAAA",
    "________AAAAAAA",
    "________RRRRRRR",
    "_BBBBBBBBBBBBBB",
    "_BBBBBBBBBBBBBB",
    "____HHHHHHHHHHH",
    "___HSSSSSSSSSSS",
    "___HSSSSSSeSSSS",
    "___HSSfSfSSSSSS",
    "___HSSSSSSSSSSS",
    "___HSSSSSSSSmmm",
    "___HSSSSSSSSSSS",
    "_____SSSSSSSSSS",
    "____________SSS",
    "____________SSS",
    "_________DDDDDD",
    "______CCDDDDDDD",
    "____CCCCCCCCCCC",
    "___CCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
]
_HANA_ROWS = [
    "_______________",
    "__________HHHHH",
    "_________HHHHHH",
    "________HHHHHHH",
    "_____HHHHHHHHHH",
    "______HHHHHHHHH",
    "______HHHHHSSSS",
    "______HHHSSSSSS",
    "______HHSSSSSSS",
    "______HHSbbbSSS",
    "______HHSSweSSS",
    "______HHSSeeSSS",
    "______HHSSSSSSS",
    "______HHSSSSSSn",
    "______HHSSSSSSS",
    "_______HSSSSSSS",
    "_________SSSSSS",
    "_________SSSmmm",
    "__________SSSSS",
    "___________SSSS",
    "____________SSS",
    "_________GGGSSS",
    "______CCCCCCCCC",
    "____CCCCCCCCCCC",
    "___CCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
]
_OTIS_ROWS = [
    "_______________",
    "_______________",
    "__________AAAAA",
    "_________AAAAAA",
    "________AAAAAAA",
    "________AAAAAAA",
    "_____BBBBBBBBBB",
    "______HHHSSSSSS",
    "______HHSSSSSSS",
    "______HHSbbbSSS",
    "______HHSSweSSS",
    "______HHSSeeSSS",
    "______HHSSSnSSS",
    "______HHSSSWWWW",
    "______HHWWWWWWW",
    "________WWWWWWW",
    "_________WWWWmm",
    "_________WWWWWW",
    "__________WWWWW",
    "___________WWWW",
    "____________SSS",
    "_________CCCCCC",
    "______CCCCCCCCC",
    "____CCCCCCCCCCC",
    "___CCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
]
_WREN_ROWS = [
    "_______________",
    "__________HHHHH",
    "_________HHHHHH",
    "________HHHHHHH",
    "______HHHHHHHHH",
    "______HHHHHHHHH",
    "______HHHSSSSSS",
    "______HHSSSSSSS",
    "______HHSSSSSSS",
    "______HHSbbbSSS",
    "______HHSSweSSS",
    "______HHSSeeSSS",
    "______HHSrrSSSS",
    "______HHSSSSSSn",
    "______HHSSSSSSS",
    "_______HSSSSSSS",
    "_________SSSmmm",
    "_________SSSSSS",
    "__________SSSSS",
    "___________SSSS",
    "____________SSS",
    "_________CCCCCC",
    "______CCCCCCCCC",
    "____CCCCCCCCCCC",
    "___CCCCCCCCCCCC",
    "__CCCCCCCCCCCCC",
    "_____PPPPPPPPPP",
    "_____PPPPPPPPPP",
    "_____PPPPPPPPPP",
    "_____PPPPPPPPPP",
]
_WREN_BRAID = braid_patch([(8, 16), (7, 17), (7, 18), (6, 19), (6, 20), (6, 21), (5, 22), (5, 23),
                           (4, 24), (4, 25), (4, 26)])

PORTRAITS = {
    "rosa": dict(
        rows=_ROSA_ROWS, bg=["rust", "orange", "amber"],
        ramps={"hair": BROWN_HAIR, "skin": SKIN_DEEP, "cloth": GREEN, "cloth2": CREAM, "band": RED},
        key={"m": ("skin", "red_d")},
        patch={(7, 23): "y", (22, 23): "y"},
    ),
    "milo": dict(
        rows=_MILO_ROWS, bg=["green_dd", "green_d", "green"],
        ramps={"hair": RUST_HAIR, "skin": SKIN_FAIR, "cap": STRAW, "brim": STRAW, "band": RED,
               "cloth": BLUE, "cloth2": WHITE_SHIRT},
        key={"m": ("skin", "red_d")},
        patch={(7, 19): "y", (22, 19): "y"},
    ),
    "hana": dict(
        rows=_HANA_ROWS, bg=["slate", "grey_d", "grey"],
        ramps={"hair": BLACK_HAIR, "skin": SKIN_LIGHT_TAN, "cloth": YELLOW_SHIRT, "collar": CREAM},
        key={"m": ("skin", "red_d")},
        patch={},
    ),
    "otis": dict(
        rows=_OTIS_ROWS, bg=["teal_dd", "blue_d", "blue"],
        ramps={"hair": GREY_HAIR, "skin": SKIN_MEDIUM, "beard": BEARD, "cap": CAP, "brim": CAP_BRIM,
               "cloth": ORANGE_SHIRT},
        key={"b": ("hair", "grey_l"), "m": ("beard", "brown_d"), "n": ("skin", "brown")},
        patch={},
    ),
    "wren": dict(
        rows=_WREN_ROWS, bg=["amber", "yellow", "cream"],
        ramps={"hair": BLOND, "braid": BLOND, "skin": SKIN_FAIR, "cloth": PURPLE, "apron": APRON,
               "cloth2": PURPLE},
        key={"b": ("hair", "amber"), "m": ("skin", "red_d"), "Z": ("braid", None)},
        patch=_WREN_BRAID,
    ),
}


def main():
    art = sys.argv[1]
    sp = Sprites(art, "people")
    pt = Sprites(art, "portraits")
    import _farmhands as F
    for name in PEOPLE:
        for view, pose, phase, suffix in F.FRAMES:
            im, pad, h = F.sprite(name, view, pose, phase)
            sp.save(f"{name}_{view}_{suffix}", im, F.CANVAS_W // 2 + pad, (h - 1) + pad)
        im, pad = paint_portrait(PORTRAITS[name])
        pt.save(f"fh_{name}", im, 15 + pad, 15 + pad)
    print(json.dumps({**sp.pivots, **pt.pivots}))


main()
