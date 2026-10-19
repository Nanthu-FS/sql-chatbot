"""Villager face portraits (pixel-direct): mara, tobias, ivy, bram, juniper, pip, sela, fennel.

portraits/<id>: a 30x30 bust on a rounded-corner tile with no frame. Each portrait is a
character map: 15 columns per row, mirrored to the right (a row of 30 characters is used
as written, for the asymmetric details). Letters name a region (S skin, H hair, C main
clothing, D second clothing, A cap, B cap brim, G collar, acc for small accessories) or a
feature (e eye, w eye shine, b brow, m mouth, T teeth, n nose, r blush, f freckle, ...).

Regions get dome shading lit from the top-left, checker-dithered between ramp steps (the
same model as the crop and icon modules). Features are flat palette colours on top. A 1px
ink line outlines the bust, then finish() adds the tile outline and the hard drop shadow.
Pivot = centre of the body (+ pad).
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, Sprites, new, finish  # noqa: E402
import math  # noqa: E402

W = H = 30
_L = (-0.55, -0.7, 0.6)
LIGHT = tuple(v / math.sqrt(sum(u * u for u in _L)) for v in _L)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
DEPTH = {"skin": 3.0, "cloth": 2.8, "cloth2": 2.6, "hair": 2.2}


def col(c):
    return E[c] if isinstance(c, str) else c


def tone(ramp, v, x, y):
    """Ramp colour for a fractional level v: the nearest step (clean, no dither; art round 3)."""
    n = len(ramp) - 1
    return col(ramp[int(round(max(0.0, min(float(n), v))))])


class Canvas:
    def __init__(self):
        self.im = new(W, H)
        self.px = self.im.load()

    def put(self, x, y, c):
        if c is not None and 0 <= x < W and 0 <= y < H:
            self.px[x, y] = col(c)


def rounded(r=7):
    """Rounded-corner tile: the portrait frame is this shape, no border."""
    out = set()
    for y in range(H):
        for x in range(W):
            dx = max(0.0, r - (x + 0.5), (x + 0.5) - (W - r))
            dy = max(0.0, r - (y + 0.5), (y + 0.5) - (H - r))
            if dx * dx + dy * dy <= r * r:
                out.add((x, y))
    return out


MASK = rounded(7)


def dist_in(s):
    """BFS distance (1 = edge) of every pixel of s to the outside of s."""
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


def sh(c, s, ramp, depth=2.5):
    """Dome-shade a pixel set, lit from the top-left (same model as crops_a / icons_a)."""
    dist = dist_in(s)
    hgt = {p: depth * math.sqrt(max(0.0, 1 - (1 - min(1.0, (d - 0.5) / depth)) ** 2)) for p, d in dist.items()}
    n = len(ramp) - 1
    for (x, y) in s:
        gx = (hgt.get((x + 1, y), 0) - hgt.get((x - 1, y), 0)) / 2
        gy = (hgt.get((x, y + 1), 0) - hgt.get((x, y - 1), 0)) / 2
        nl = math.sqrt(gx * gx + gy * gy + 1)
        lv = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / nl
        v = ((lv + 0.25) / 1.25) * n
        c.put(x, y, tone(ramp, v, x, y))


# ---------------------------------------------------------------- palettes

SKIN_FAIR = ["tan_d", "tan", "skin", "cream"]
SKIN_TAN = ["brown_l", "tan_d", "tan", "skin"]
SKIN_DEEP = ["brown", "brown_l", "tan_d", "tan"]
SKIN_BROWN = ["brown_d", "brown", "brown_l", "tan_d"]
HAIR_AUBURN = ["brown_d", "brown", "rust", "clay"]
HAIR_BROWN = ["brown_d", "brown", "brown_l", "tan"]
HAIR_AMBER = ["rust", "clay", "amber", "yellow"]
HAIR_GREY = ["slate", "grey_d", "grey", "grey_l"]
HAIR_INK = ["ink", "slate_d", "slate", "grey_d"]
HAIR_WHITE = ["grey_d", "grey", "grey_l", "white"]
RED = ["purple", "red_d", "red", "pink"]
APRON = ["grey_l", "cream", "white"]
BLUE = ["slate_d", "blue_d", "blue", "cyan"]
BROWN = ["brown_d", "brown", "brown_l"]
PINK = ["purple", "magenta", "pink"]
TEAL = ["teal_dd", "blue_d", "blue"]
GREY = ["slate", "grey_d", "grey", "grey_l"]
WINE = ["ink", "brown_d", "brown", "red_d"]
STRIPE = ["red_d", "red", "pink"]
CREAM = ["tan", "cream", "white"]
PURPLE = ["purple", "magenta", "pink"]
CAP_BROWN = ["brown_d", "brown", "brown_l", "tan"]
BRIM_BROWN = ["brown_d", "brown_d", "brown"]
CAP_ORANGE = ["rust", "red_d", "orange", "amber"]
BRIM_ORANGE = ["red_d", "rust", "orange"]
GREEN = ["green_dd", "green_d", "green", "yellow"]
FRAME = ["brown_d", "brown", "amber"]
DOT = ["ink", "ink"]

# Character meaning: (region group, colour). A colour of None keeps the shading.
BASE = {
    "_": None,
    "H": ("hair", None), "S": ("skin", None), "C": ("cloth", None), "D": ("cloth2", None),
    "A": ("cap", None), "B": ("brim", None), "G": ("collar", None),
    "e": ("skin", "ink"), "w": ("skin", "white"), "b": ("skin", "brown_d"),
    "m": ("skin", "brown"), "n": ("skin", "tan_d"), "r": ("skin", "pink"),
    "f": ("skin", "brown_l"), "k": ("skin", "brown_d"), "x": ("skin", "slate_d"),
    "o": ("skin", "brown"), "T": ("skin", "white"),
    "g": ("frame", "amber"), "l": ("frame", "cyan"),
    "F": ("cloth2", "white"), "M": ("acc", "grey_l"),
    "y": ("acc", "yellow"), "p": ("acc", "pink"), "Y": ("acc", "amber"),
    "q": ("acc", "green"), "Q": ("acc", "green_d"),
}


def paint(spec, bust=False):
    """Character map -> finished sprite (Image, pad). A [bust] is cut out: no tile background,
    for the story scenes."""
    grid = []
    for row in spec["rows"]:
        full = row + row[::-1] if len(row) == 15 else row
        assert len(full) == W, (len(full), row)
        grid.append(list(full))
    assert len(grid) == H, len(grid)
    for (x, y), ch in spec.get("patch", {}).items():
        grid[y][x] = ch
    key = dict(BASE)
    key.update(spec.get("key", {}))
    c = Canvas()
    bg = spec["bg"]
    n = len(bg) - 1
    groups, over = {}, []
    for x, y in MASK:
        ch = grid[y][x]
        if ch == "_":
            if not bust:
                c.put(x, y, tone(bg, n * (1.0 - 0.8 * y / (H - 1) - 0.2 * x / (W - 1)), x, y))
            continue
        grp, ov = key[ch]
        groups.setdefault(grp, set()).add((x, y))
        if ov:
            over.append((x, y, ov))
    for grp, s in groups.items():
        sh(c, s, spec["ramps"].get(grp, DOT), depth=DEPTH.get(grp, 2.2))
    for x, y, ov in over:
        c.put(x, y, ov)
    figure = set().union(*groups.values())
    for x, y in MASK:
        if (x, y) in figure:
            continue
        if any((x + dx, y + dy) in figure for dx, dy in N4):
            c.put(x, y, "ink")
    return finish(c.im, outline=not bust)


# ---------------------------------------------------------------- characters

MARA = dict(
    # Warm baker: auburn bun, red dress, cream flour apron, rosy cheeks, open smile.
    rows=[
        "_____________HH",
        "____________HHH",
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
        "_________SSSSSS",
        "_________SSmSSS",
        "_________SSSmmm",
        "__________SSSSS",
        "___________SSSS",
        "____________SSS",
        "_________CCCSSS",
        "______CCCCCCSSS",
        "____CCCCCCCCCCC",
        "___CCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDFDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
    ],
    bg=["amber", "yellow", "white"],
    ramps={"hair": HAIR_AUBURN, "skin": SKIN_FAIR, "cloth": RED, "cloth2": APRON},
    key={"m": ("skin", "red_d")},
)

TOBIAS = dict(
    # Carpenter: brown cap, beard, blue work shirt over brown bib, pencil behind the ear.
    rows=[
        "_______________",
        "_________AAAAAA",
        "________AAAAAAA",
        "_______AAAAAAAA",
        "_______AAAAAAAA",
        "______BBBBBBBBB",
        "________SSSSSSS",
        "________SSSSSSS",
        "________SSSSSSS",
        "________SbbbSSS",
        "_______SSSeeSSS",
        "_______SSSeeSSS",
        "________SSSSSSS",
        "________SSSSSSn",
        "________HHHHHHH",
        "________HHHHHHH",
        "_________HHHmmm",
        "__________HHHHH",
        "___________HHHH",
        "____________SSS",
        "____________SSS",
        "_________CCCSSS",
        "______CCCCCCSSS",
        "____CCCCCCCCCCC",
        "___CCCCCCDDDDDD",
        "__CCCCCCCDDDDDD",
        "__CCCCCCCDDDDDD",
        "__CCCCCCCDDDDDD",
        "__CCCCCCCDDDDDD",
        "__CCCCCCCDDDDDD",
    ],
    bg=["slate", "grey_d", "grey"],
    ramps={"hair": HAIR_BROWN, "skin": SKIN_TAN, "cloth": BLUE, "cloth2": BROWN,
           "cap": CAP_BROWN, "brim": BRIM_BROWN},
    key={"m": ("skin", "brown_d"), "n": ("skin", "tan_d")},
    patch={(22, 9): "Y", (23, 8): "y", (24, 7): "y", (25, 6): "p"},
)

IVY = dict(
    # Florist: amber hair with a pink flower, pink dress with a green collar, big grin.
    rows=[
        "_______________",
        "__________HHHHH",
        "_________HHHHHH",
        "________HHHHHHH",
        "______HHHHHHHHH",
        "_____HHHHHHHHHH",
        "_____HHHHSSSSSS",
        "_____HHHSbbbSSS",
        "_____HHHSSSSSSS",
        "_____HHHSSweSSS",
        "_____HHHSSeeSSS",
        "_____HHHSrrSSSS",
        "_____HHHSSSSSSn",
        "_____HHHSSSSSSS",
        "_____HH__SSSSSS",
        "_____HH__SSmmmm",
        "_____HH__SSTTTT",
        "______H___Smmmm",
        "____________SSS",
        "____________SSS",
        "_________GGGSSS",
        "______CCCCCCSSS",
        "____CCCCCCCCCCC",
        "___CCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
    ],
    bg=["green_dd", "green_d", "green"],
    ramps={"hair": HAIR_AMBER, "skin": SKIN_FAIR, "cloth": PINK, "collar": GREEN},
    key={"m": ("skin", "brown_d")},
    patch={(21, 4): "y", (20, 4): "p", (22, 4): "p", (21, 3): "p", (21, 5): "p",
           (20, 3): "p", (22, 5): "p", (20, 5): "q", (23, 4): "q",
           (9, 24): "y", (20, 26): "y", (13, 28): "y"},
)

BRAM = dict(
    # Blacksmith: bald and broad, soot on the forehead and cheek, big moustache, leather apron.
    rows=[
        "_______________",
        "_______________",
        "_________SSSSSS",
        "________SSSSSSS",
        "_______SSSSSSSS",
        "_______SSSSSSSS",
        "_______SSxxxSSS",
        "_______SSSSSSSS",
        "_______SbbbbSSS",
        "_______SSweSSSS",
        "_______SSeeSSSS",
        "_______SSSSSSSS",
        "_______SxSSSSSn",
        "______HHHHHHHHH",
        "________HHHHHHH",
        "__________mmmmS",
        "_________SSSSSS",
        "__________SSSSS",
        "__________SSSSS",
        "__________SSSSS",
        "__________SSSSS",
        "_____CCCCCCCCCC",
        "___CCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCDDDDDDDDD",
        "__CCCCDDDDDDDDD",
        "__CCCCDDMDDDDDD",
        "__CCCCDDDDDDDDD",
        "__CCCCDDDDDDDDD",
        "__CCCCDDDDDDDDD",
    ],
    bg=["red_d", "rust", "orange"],
    ramps={"hair": HAIR_INK, "skin": SKIN_DEEP, "cloth": GREY, "cloth2": BROWN},
    key={"m": ("skin", "brown_d"), "x": ("skin", "ink")},
)

JUNIPER = dict(
    # Innkeeper: grey bun, round cat-eye glasses, red shawl over a teal dress, kind smile.
    rows=[
        "____________HHH",
        "___________HHHH",
        "_________HHHHHH",
        "________HHHHHHH",
        "______HHHHHHHHH",
        "______HHHHHHHHH",
        "______HHHSSSSSS",
        "______HHSbbbSSS",
        "______HHSggggSS",
        "______HHgllllgg",
        "______HHgleelgS",
        "______HHgllllgS",
        "______HHSggggSS",
        "______HHSrrSSSS",
        "______HHSSSSSSn",
        "_________SSSSSS",
        "_________SSmSSS",
        "_________SSSmmm",
        "__________SSSSS",
        "___________SSSS",
        "____________SSS",
        "______DDDDDDSSS",
        "____DDDDCCCCCCC",
        "___DDDDDCCCCCCC",
        "__DDDDDDDCCCCCC",
        "__DDDDDDDCCCCCC",
        "__DDDDDDDCCCCCC",
        "__DDDDDDDCCCCCC",
        "__DDDDDDDCCCCCC",
        "__DDDDDDDCCCCCC",
    ],
    bg=["purple", "magenta", "pink"],
    ramps={"hair": HAIR_GREY, "skin": SKIN_TAN, "cloth": TEAL, "cloth2": STRIPE, "frame": FRAME},
    key={"b": ("skin", "slate"), "n": ("skin", "brown")},
    patch={(11, 23): "y"},
)

PIP = dict(
    # Village kid: orange cap, messy brown hair, freckles, gap-toothed grin, striped shirt.
    rows=[
        "_______________",
        "_________AAAAAA",
        "________AAAAAAA",
        "_______AAAAAAAA",
        "_______AAAAAAAA",
        "____BBBBBBBBBBB",
        "______HHHSSSSSS",
        "______HHSSSSSSS",
        "______HHSSbbSSS",
        "______HHSSeeSSS",
        "______HHSSweSSS",
        "______HHSSeeSSS",
        "______HHSfSfSSS",
        "______HHSSfSSSn",
        "______HHSSSSSSS",
        "_________SSSSSS",
        "_________SSmmmm",
        "_________SSTTTm",
        "__________Smmmm",
        "____________SSS",
        "____________SSS",
        "_________CCCSSS",
        "______CCCCCCSSS",
        "____CCCCCCCCCCC",
        "___DDDDDDDDDDDD",
        "__CCCCCCCCCCCCC",
        "__DDDDDDDDDDDDD",
        "__CCCCCCCCCCCCC",
        "__DDDDDDDDDDDDD",
        "__CCCCCCCCCCCCC",
    ],
    bg=["blue_d", "blue", "cyan"],
    ramps={"hair": HAIR_BROWN, "skin": SKIN_TAN, "cloth": STRIPE, "cloth2": CREAM,
           "cap": CAP_ORANGE, "brim": BRIM_ORANGE},
    key={"m": ("skin", "brown_d"), "f": ("skin", "brown"), "b": ("skin", "brown_d")},
)

SELA = dict(
    # Vintner: sleek ink-black bob with a middle part, sly half-lidded eyes, purple scarf.
    rows=[
        "_______________",
        "__________HHHHH",
        "_________HHHHHH",
        "________HHHHHHH",
        "_______HHHHHHHH",
        "______HHHHHHHHH",
        "______HHHHHHSSS",
        "______HHHSSSSSS",
        "______HHSbbbSSS",
        "______HHSSSSSSS",
        "______HHSSeeeSS",
        "______HHSSSeSSS",
        "______HHSSSSSSS",
        "______HHSkSSSSn",
        "______HHSSSSSSS",
        "______HH_SSSSSS",
        "______HH_SSSSmm",
        "______HH_SSSSSS",
        "__________SSSSS",
        "____________SSS",
        "_________DDDDDD",
        "_______DDDDDDDD",
        "______CCCCCCDDD",
        "____CCCCCCCCDDD",
        "___CCCCCCCCCDDD",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
    ],
    bg=["slate", "grey_d", "grey"],
    ramps={"hair": HAIR_INK, "skin": SKIN_FAIR, "cloth": WINE, "cloth2": PURPLE},
    key={"m": ("skin", "red"), "b": ("skin", "brown_d")},
    patch={(17, 16): "m", (17, 15): "m"},
)

FENNEL = dict(
    # Doc Fennel: old herbalist, bald crown, white hair and beard, round spectacles, herb sprig.
    rows=[
        "_______________",
        "_______________",
        "_________SSSSSS",
        "______HHSSSSSSS",
        "_____HHHSSSSSSS",
        "_____HHHSSSSSSS",
        "_____HHHSooooSS",
        "_____HHHbbbbSSS",
        "_____HHHSgggSSS",
        "_____HHHglllgSS",
        "_____HHHglelggg",
        "_____HHHglllgSS",
        "_____HHHSgggSSS",
        "_____HHHSSSSSSn",
        "_____HHHSSSSSSS",
        "_____HHHSSSSSSS",
        "_____HHHHHHmmmH",
        "______HHHHHHHHH",
        "_______HHHHHHHH",
        "________HHHHHHH",
        "____________SSS",
        "_________DDDSSS",
        "______CCCCCCDDD",
        "____CCCCCCCCDDD",
        "___CCCCCCCCCCDD",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
    ],
    bg=["teal_dd", "teal_dd", "blue_d"],
    ramps={"hair": HAIR_WHITE, "skin": SKIN_BROWN, "cloth": BROWN, "cloth2": CREAM, "acc": GREEN},
    key={"b": ("skin", "grey_l"), "l": ("frame", "grey_l")},
    patch={(20, 20): "q", (21, 22): "Q", (22, 21): "q", (23, 20): "q", (23, 19): "Q",
           (24, 18): "q", (25, 17): "q", (22, 19): "q", (24, 19): "Q", (20, 23): "Q"},
)

# ---------------------------------------------------------------- the story (bank loan)

def mood(spec, rows):
    """The same face with some rows replaced ({row index: row}), e.g. another expression."""
    out = dict(spec)
    out["rows"] = [rows.get(i, r) for i, r in enumerate(spec["rows"])]
    return out


SUIT = ["ink", "slate_d", "slate", "grey_d"]
STRAW = ["brown_l", "tan_d", "tan", "cream"]
STRAW_BRIM = ["brown", "brown_l", "tan_d"]
BANDANA = ["red_d", "red", "pink"]
ORANGE = ["rust", "orange", "amber", "yellow"]
BEANIE = ["teal_dd", "blue_d", "blue", "cyan"]
BEANIE_FOLD = ["teal_dd", "teal_dd", "blue_d"]
OVERALL_TEAL = ["teal_dd", "green_dd", "green_d", "green"]

OFFICER = dict(
    # Mr. Sterling, the loan officer: slicked black hair, glasses, a smirk, charcoal suit, red tie.
    rows=[
        "_______________",
        "_________HHHHHH",
        "_______HHHHHHHH",
        "______HHHHHHHHH",
        "______HHHHHHHHH",
        "______HHSSSSSSS",
        "______HSSSSSSSS",
        "______HSSSSSSSS",
        "______HSSbbbbSS",
        "______SSLLLLLLL",
        "______SSLckkSLS",
        "______SSLSweSLS",
        "______SSLLLLLLS",
        "______SSSSSSSSn",
        "______SSSSSSSSS",
        "_______SSSSSSSSSSSmSSSSS______",
        "_______SSSSSSmmmmmSSSSSS______",
        "________SSSSSSS",
        "_________SSSSSS",
        "___________SSSS",
        "_________CCGGGG",
        "______CCCCCGGGD",
        "____CCCCCCCCGGD",
        "___CCCCCCCCCCGD",
        "__CCCCCCCCCCCGD",
        "__CCCCCCCCCCCCD",
        "__CCCCCCCCCCCCD",
        "__CCCCCCCCCCCCD",
        "__CCCCCCCCCCCCD",
        "__CCCCCCCCCCCCD",
    ],
    bg=["teal_dd", "blue_d", "blue"],
    ramps={"hair": HAIR_INK, "skin": SKIN_FAIR, "cloth": SUIT, "cloth2": RED, "collar": CREAM},
    key={"L": ("acc", "slate_d"), "c": ("skin", "white"), "k": ("skin", "brown_d")},
    # Smug: the right brow raised.
    patch={**{(x, 8): "S" for x in range(17, 21)}, **{(x, 7): "b" for x in range(17, 21)}},
)

OFFICER_SHOCK = mood(OFFICER, {
    7: "______HSSbbbbSS",
    8: "______HSSSSSSSS",
    10: "______SSLSweSLS",
    11: "______SSLSeeSLS",
    15: "_______SSSSSSSSoooSSSSSS______",
    16: "_______SSSSSSSSoooSSSSSS______",
})
OFFICER_SHOCK["key"] = dict(OFFICER["key"], o=("skin", "brown_d"))
OFFICER_SHOCK["patch"] = {(24, 9): "l", (24, 10): "l"}

FARMER_HAT = dict(
    # Straw hat, brown hair, freckles, denim overalls over a red shirt.
    rows=[
        "_______________",
        "__________AAAAA",
        "_________AAAAAA",
        "________AAAAAAA",
        "_______DDDDDDDD",
        "___BBBBBBBBBBBB",
        "______HHHHHHHHH",
        "______HHSSSSSSS",
        "______HSSSSSSSS",
        "______HSSbbbSSS",
        "______HSSSweSSS",
        "______HSSSeeSSS",
        "______HSSrfSSSS",
        "_______SSSSSSSn",
        "_______SSSSSSSS",
        "________SSSSSSS",
        "________SSSmSSS",
        "_________SSSmmm",
        "__________SSSSS",
        "___________SSSS",
        "____________SSS",
        "_________DDDSSS",
        "______DDCCDDDDD",
        "____DDDDCCDDDDD",
        "___DDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
    ],
    bg=["green_dd", "green_d", "green"],
    ramps={"hair": HAIR_BROWN, "skin": SKIN_FAIR, "cloth": BLUE, "cloth2": RED, "cap": STRAW, "brim": STRAW_BRIM},
    patch={(10, 26): "y", (19, 26): "y"},
)

FARMER_BANDANA = dict(
    # Red bandana, curly black hair, deep brown skin, denim shirt over a white tee.
    rows=[
        "_______________",
        "__________HHHHH",
        "________HHHHHHH",
        "_______HHHHHHHH",
        "______AAAAAAAAA",
        "______AAAAAAAAA",
        "_____HHHSSSSSSS",
        "_____HHSSSSSSSS",
        "_____HHSSSSSSSS",
        "_____HHSSbbbSSS",
        "_____HHSSSweSSS",
        "_____HHSSSeeSSS",
        "______HSSSSSSSS",
        "______HSSSSSSSn",
        "_______SSSSSSSS",
        "________SSSSSSS",
        "________SSSmSSS",
        "_________SSSmmm",
        "__________SSSSS",
        "___________SSSS",
        "____________SSS",
        "_________CCGSSS",
        "______CCCCCGSSS",
        "____CCCCCCCCGSS",
        "___CCCCCCCCCCGS",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
    ],
    bg=["rust", "clay", "tan"],
    ramps={"hair": HAIR_INK, "skin": SKIN_BROWN, "cloth": BLUE, "cap": BANDANA, "collar": CREAM},
    patch={(24, 5): "A", (25, 6): "A", (25, 4): "A", (6, 26): "M", (7, 26): "M"},
)

FARMER_BRAID = dict(
    # Long amber hair parted in the middle, tan skin, freckles, an orange plaid shirt.
    rows=[
        "_______________",
        "_________HHHHHH",
        "_______HHHHHHHH",
        "______HHHHHHHHH",
        "_____HHHHHHHHHH",
        "_____HHHHHHHHHS",
        "_____HHHSSSSSSS",
        "_____HHSSSSSSSS",
        "_____HHSSSSSSSS",
        "_____HHSSbbbSSS",
        "_____HHSSSweSSS",
        "_____HHSSSeeSSS",
        "_____HHSSrfSSSS",
        "_____HHSSSSSSSn",
        "_____HHHSSSSSSS",
        "____HHHHSSSSSSS",
        "____HHHHSSSmSSS",
        "____HHHH_SSSmmm",
        "____HHH___SSSSS",
        "____HHH____SSSS",
        "_____HH_____SSS",
        "_____HH__CCCSSS",
        "______CCCCCCSSS",
        "____CCCCCCCCCCC",
        "___CCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
    ],
    bg=["teal_dd", "blue_d", "blue"],
    ramps={"hair": HAIR_AMBER, "skin": SKIN_TAN, "cloth": ORANGE, "cloth2": ["brown_d", "rust", "red_d"]},
    patch={**{(x, 25): "D" for x in range(3, 27) if x % 2}, **{(x, 28): "D" for x in range(3, 27) if x % 2},
           **{(x, y): "D" for x in (8, 21) for y in range(23, 30)}},
)

FARMER_BEANIE = dict(
    # Teal beanie, short black hair, brown skin, a white tee under teal overalls.
    rows=[
        "_______________",
        "_________AAAAAA",
        "_______AAAAAAAA",
        "______AAAAAAAAA",
        "______AAAAAAAAA",
        "______BBBBBBBBB",
        "______HHSSSSSSS",
        "______HSSSSSSSS",
        "______HSSbbbSSS",
        "______SSSSweSSS",
        "______SSSSeeSSS",
        "______SSSSSSSSS",
        "_______SSSSSSSn",
        "_______SSSSSSSS",
        "________SSSSSSS",
        "________SSSmSSS",
        "_________SSSmmm",
        "__________SSSSS",
        "___________SSSS",
        "____________SSS",
        "____________SSS",
        "_________DDDSSS",
        "______DDCDDDDDD",
        "____DDDDCDDDDDD",
        "___DDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
        "__DDDDDDCCCCCCC",
    ],
    bg=["amber", "yellow", "white"],
    ramps={"hair": HAIR_INK, "skin": SKIN_DEEP, "cloth": OVERALL_TEAL, "cloth2": CREAM, "cap": BEANIE, "brim": BEANIE_FOLD},
    patch={(10, 26): "y", (19, 26): "y"},
)


def determined(spec, brow, eye, mouth):
    """Brows pulled down at the middle and a flat mouth: the face for "you'll see"."""
    rows = spec["rows"]
    b = next(i for i, r in enumerate(rows) if "bbb" in r)
    m = next(i for i, r in enumerate(rows) if r.rstrip("_").endswith("mmm"))
    return mood(spec, {b: brow, b + 1: eye, m - 1: rows[m - 1].replace("m", "S"), m: mouth})


FARMERS = [FARMER_HAT, FARMER_BANDANA, FARMER_BRAID, FARMER_BEANIE]
FARMERS_DET = [
    determined(FARMER_HAT, "______HSbbSSSSS", "______HSSSbeSSS", "_________SSSSmm"),
    determined(FARMER_BANDANA, "_____HHSbbSSSSS", "_____HHSSSbeSSS", "_________SSSSmm"),
    determined(FARMER_BRAID, "_____HHSbbSSSSS", "_____HHSSSbeSSS", "____HHHH_SSSSmm"),
    determined(FARMER_BEANIE, "______HSbbSSSSS", "______SSSSbeSSS", "_________SSSSmm"),
]

PORTRAITS = [("mara", MARA), ("tobias", TOBIAS), ("ivy", IVY), ("bram", BRAM),
             ("juniper", JUNIPER), ("pip", PIP), ("sela", SELA), ("fennel", FENNEL),
             ("officer", OFFICER), ("officer_shock", OFFICER_SHOCK)]
PORTRAITS += [(f"farmer_{i}", f) for i, f in enumerate(FARMERS)]
PORTRAITS += [(f"farmer_{i}_det", f) for i, f in enumerate(FARMERS_DET)]


def main():
    sp = Sprites(sys.argv[1], "portraits")
    for name, spec in PORTRAITS:
        out, pad = paint(spec)
        sp.save(name, out, 15 + pad, 15 + pad)
        if name.startswith(("officer", "farmer_")):
            out, pad = paint(spec, bust=True)
            sp.save(name + "_bust", out, 15 + pad, 29 + pad)
    sp.done()


if __name__ == "__main__":
    main()
