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
    if spec.get("blush"):
        # Round 7 (romance): rosy cheeks. Existing blush grows a row; else two pixels under each eye.
        cheeks = {(x, y) for y, row in enumerate(grid) for x, ch in enumerate(row) if ch == "r"}
        if not cheeks:
            eye = [(x, y) for y, row in enumerate(grid) for x, ch in enumerate(row) if ch in "ew"]
            if eye:
                ey = max(y for _, y in eye) + 2
                xs = sorted({x for x, _ in eye})
                for x in (xs[0] - 1, xs[0], xs[-1], xs[-1] + 1):
                    cheeks.add((x, ey))
        for x, y in list(cheeks):
            for cx, cy in ((x, y), (x, y + 1), (x + (-1 if x < W // 2 else 1), y)):
                if 0 <= cy < H and 0 <= cx < W and grid[cy][cx] in "Sr":
                    grid[cy][cx] = "r"
    anim = spec.get("anim")
    if anim == "talk":
        # The mouth opens: its pixels go dark and the skin under them becomes the lower lip.
        mouth = {(x, y) for y, row in enumerate(grid) for x, ch in enumerate(row) if ch == "m"}
        for x, y in mouth:
            grid[y][x] = "k"
        for x, y in mouth:
            if y + 1 < H and (x, y + 1) not in mouth and grid[y + 1][x] == "S":
                grid[y + 1][x] = "m"
    elif anim == "blink":
        # Eyes shut: in the eye rows every eye pixel becomes skin, and the lowest one in each column a lid line.
        eyes = spec.get("eyes", "ew")
        rows = [y for y, row in enumerate(grid) if any(ch in eyes.replace("k", "") for ch in row)]
        cols = {}
        for y in rows:
            for x, ch in enumerate(grid[y]):
                if ch in eyes:
                    cols.setdefault(x, []).append(y)
        for x, ys in cols.items():
            for y in ys:
                grid[y][x] = "k" if y == max(ys) else "S"
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

# Ms. Kessler of Vexmoor Agri-Corp (round 6, Story Act 2): the officer's sharp face and pose, with
# an auburn bob, red-framed glasses and a green corporate suit with a gold brooch.
KESSLER = dict(OFFICER)
KESSLER["rows"] = [
    ("_____" + r[5:] if i < 5 else ("_____HH" + r[7:] if 5 <= i <= 14 and r.startswith("______") else r))
    for i, r in enumerate(OFFICER["rows"])
]
KESSLER["ramps"] = dict(OFFICER["ramps"], hair=HAIR_AUBURN, cloth=["teal_dd", "green_dd", "green_d", "green"], cloth2=["rust", "orange", "amber", "yellow"])
KESSLER["key"] = dict(OFFICER["key"], L=("acc", "red_d"))
KESSLER["bg"] = ["green_dd", "green_d", "green"]
KESSLER_SHOCK = mood(KESSLER, {
    10: "_____HHSLSweSLS",
    11: "_____HHSLSeeSLS",
    15: "_______SSSSSSSSoooSSSSSS______",
    16: "_______SSSSSSSSoooSSSSSS______",
})
KESSLER_SHOCK["key"] = dict(KESSLER["key"], o=("skin", "brown_d"))

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


# ---------------------------------------------------------------- round 7: Rebuild The Village

# The second female look (replaces the bandana look): long curly black hair under a wide straw sun
# hat with a green band and a pink flower, deep brown skin, gold earrings, a magenta dress.
FARMER_CURLY = dict(
    rows=[
        "_______________",
        "_______________",
        "__________AAAAA",
        "_________AAAAAA",
        "________AAAAAAA",
        "________DDDDDDD",
        "__BBBBBBBBBBBBB",
        "_BBBBBBBBBBBBBB",
        "___HHHHHSSSSSSS",
        "__HHHHHSSbbbSSS",
        "__HHHHHSSSweSSS",
        "__HHHHHSSSeeSSS",
        "__HHHHHSSrSSSSS",
        "__HHHHHSSSSSSSn",
        "__HHHHHHSSSSSSS",
        "__HHHHHHHSSSSSS",
        "__HHHHHHHSSmSSS",
        "__HHHHHHHHSSmmm",
        "__HHHHHHHHHSSSS",
        "_HHHHHHHHH__SSS",
        "_HHHHHHHHH__SSS",
        "_HHHHHHHHHCCSSS",
        "__HHHHHHHCCCCCC",
        "__HHHHHHCCCCCCC",
        "_HHHHHHHCCCCCCC",
        "_HHHHHHCCCCCCCC",
        "__HHHHCCCCCCCCC",
        "__HHHCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
        "__CCCCCCCCCCCCC",
    ],
    bg=["rust", "clay", "tan"],
    ramps={"hair": HAIR_INK, "skin": SKIN_BROWN, "cloth": PURPLE, "cloth2": GREEN, "cap": STRAW, "brim": STRAW_BRIM, "collar": CREAM},
    key={"m": ("skin", "red"), "h": ("hair", "slate"), "z": ("hair", "grey_d")},
    # Curls: a scalloped outer edge, highlight loops through the hair; the flower on the band.
    patch={(8, 16): "y", (21, 16): "y", (19, 5): "p", (20, 5): "y", (21, 5): "p", (20, 4): "p", (20, 6): "p"},
)


def _curls(spec):
    """Little lit arcs with a dark underside all through the hair, and a bumpy outer edge: curls."""
    grid = [list(r + r[::-1] if len(r) == 15 else r) for r in spec["rows"]]
    hair = {(x, y) for y, row in enumerate(grid) for x, ch in enumerate(row) if ch == "H"}
    patch = {}
    for cy in range(8, 30, 3):
        for cx in range(1 + (cy // 3) % 2 * 1, 30, 3):
            arc = [(cx - 1, cy), (cx, cy - 1), (cx + 1, cy)]
            if all(p in hair for p in arc) and (cx, cy + 1) in hair and cy > 8:
                for p in arc:
                    patch[p] = "h"
                patch[(cx, cy + 1)] = "z"
    for y in range(9, 28):
        for x in range(30):
            if (x, y) in hair and ((x - 1, y) not in hair or (x + 1, y) not in hair) and (x < 6 or x > 23) and (y % 3 == 0):
                patch[(x, y)] = "_"
    spec["patch"] = {**patch, **spec["patch"]}


_curls(FARMER_CURLY)

CAP_TWEED = ["slate_d", "slate", "grey_d", "grey"]
CAP_FADED = ["slate", "grey_d", "grey", "grey_l"]
SUSPENDER = ["brown_d", "brown", "brown_l"]
JACKET_GREEN = ["teal_dd", "green_dd", "green_d", "green"]
CARDIGAN = ["brown_d", "rust", "clay", "tan_d"]
SKIN_PALE = ["tan_d", "tan", "skin", "cream"]
PILLOW = ["slate", "grey_d", "grey", "grey_l"]
QUILT = ["purple", "red_d", "red", "pink"]
PAJAMA = ["blue_d", "blue", "cyan"]

# Grandpa Elias, who spent his life asking the bank to help rebuild Willowmere. The same face at
# three ages: a square jaw, a strong nose and a flat cap he never took off.
ELIAS_YOUNG = dict(
    # About thirty: dark hair, clean-shaven, blue work shirt and brown suspenders, eager.
    rows=[
        "_______________",
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
        "______SSSSSSSSn",
        "_______SSSSSSSS",
        "_______SSSSSSSS",
        "________SSSmmmm",
        "________SSSSSSS",
        "_________SSSSSS",
        "___________SSSS",
        "____________SSS",
        "_________CCGSSS",
        "______CCCCCGSSS",
        "____CCCDCCCCGSS",
        "___CCCCDCCCCCGS",
        "__CCCCCDCCCCCCC",
        "__CCCCCDCCCCCCC",
        "__CCCCCDCCCCCCC",
        "__CCCCCDCCCCCCC",
        "__CCCCCDCCCCCCC",
    ],
    bg=["brown", "brown_l", "tan_d"],
    ramps={"hair": HAIR_BROWN, "skin": SKIN_TAN, "cloth": BLUE, "cloth2": SUSPENDER, "cap": CAP_TWEED, "brim": ["ink", "slate_d", "slate"], "collar": CREAM},
    patch={(7, 26): "Y", (22, 26): "Y"},
)

ELIAS_MID = mood(ELIAS_YOUNG, {
    # About fifty: greying, a full mustache, a green work jacket, tired lines under the eyes.
    12: "______SSSSxSSSS",
    15: "_______SSSHHHHH",
    16: "________SSSSmmm",
})
ELIAS_MID.update(ramps=dict(ELIAS_YOUNG["ramps"], hair=HAIR_GREY, cloth=JACKET_GREEN, cap=CAP_FADED), bg=["slate", "grey_d", "grey"])

ELIAS_OLD = mood(ELIAS_YOUNG, {
    # About seventy-five: white hair and beard, round glasses, a rust cardigan, still smiling.
    9: "______HSSbbbSSS",
    10: "______SSLLLLLLL",
    11: "______SSLSeeSLS",
    12: "______SSLLLLLLS",
    14: "______HSSSSSSSS",
    15: "______HSSHHHHHH",
    16: "______HHHSSSmmm",
    17: "______HHHHHHHHH",
    18: "_______HHHHHHHH",
    19: "_________HHHHHH",
    20: "___________HHHH",
    21: "_________CCGHHH",
    22: "______CCCCCGSHH",
})
ELIAS_OLD.update(ramps=dict(ELIAS_YOUNG["ramps"], hair=HAIR_WHITE, cloth=CARDIGAN, cloth2=CARDIGAN, cap=CAP_FADED),
                 key={"L": ("acc", "slate_d")}, bg=["green_dd", "green_d", "green"])
ELIAS_OLD["patch"] = {(14, 24): "Y", (15, 24): "Y", (14, 27): "Y", (15, 27): "Y"}
# No suspenders under the cardigan: the D strap becomes cardigan front.
ELIAS_OLD["rows"] = [r.replace("D", "C") for r in ELIAS_OLD["rows"]]

ELIAS_BED = dict(
    eyes="ek",
    # The last evening: propped on pillows, cap off, white hair loose, pale, eyes heavy, under the
    # quilt Grandma made.
    rows=[
        "_______________",
        "____PPPPPPPPPPP",
        "__PPPPPPPPPPPPP",
        "_PPPPPPPHHHHHHH",
        "_PPPPPHHHHHHHHH",
        "_PPPPHHHHHHHHHH",
        "_PPPPHHHHSSSSSS",
        "_PPPPHHSSSSSSSS",
        "_PPPPHSSSSSSSSS",
        "_PPPPHSSSbbbSSS",
        "_PPPPPSSLLLLLLL",
        "_PPPPPSSLkeeSLS",
        "_PPPPPSSLLLLLLS",
        "_PPPPPSSSSSSSSn",
        "_PPPPPHSSSSSSSS",
        "_PPPPPHSSHHHHHH",
        "_PPPPPHHHSSSmmm",
        "_PPPPPHHHHHHHHH",
        "_PPPPPPHHHHHHHH",
        "_PPPPPPPPHHHHHH",
        "_PPPPPPPPPPHHHH",
        "_PPPPPPPPCCGHHH",
        "_PPPPPCCCCCGSHH",
        "_PPPCCCCCCCCGSS",
        "_DDDDDDDDDDDDDD",
        "DDDDDDDDDDDDDDD",
        "DDDDDDDDDDDDDDD",
        "DDDDDDDDDDDDDDD",
        "DDDDDDDDDDDDDDD",
        "DDDDDDDDDDDDDDD",
    ],
    bg=["slate_d", "slate", "grey_d"],
    ramps={"hair": HAIR_WHITE, "skin": SKIN_PALE, "cloth": PAJAMA, "cloth2": QUILT, "collar": CREAM, "pillow": PILLOW},
    key={"P": ("pillow", None), "L": ("acc", "grey_d"), "k": ("skin", "brown"), "m": ("skin", "brown")},
    # Patchwork: some squares of the quilt in other colours, with stitched seams.
    patch={**{(x, y): ("y" if ((x // 5) + (y // 3)) % 3 == 0 else "Q") for x in range(0, 30) for y in range(24, 30)
              if ((x // 5) + (y // 3)) % 3 != 1 and x % 5 and y % 3},
           **{(x, 24): "M" for x in range(1, 29, 2)}},
)

# Old Mr. Sterling, who refused Elias for forty years: his son's face (the officer), the family's
# round glasses and smirk, with a pencil mustache that grows and greys, and a balding head at the end.
STERLING_SR_YOUNG = mood(OFFICER, {14: "______SSSSSSkkk"})
STERLING_SR_YOUNG["ramps"] = dict(OFFICER["ramps"], cloth2=["ink", "slate_d", "slate"])
STERLING_SR_YOUNG["bg"] = ["purple", "magenta", "pink"]

STERLING_SR_MID = mood(OFFICER, {13: "______SSSSSSSSn", 14: "______SSSSSHHHH", 15: "_______SSSSSSSSSSHHSSSSS______"})
STERLING_SR_MID["ramps"] = dict(OFFICER["ramps"], hair=HAIR_GREY, cloth2=["ink", "slate_d", "slate"])
STERLING_SR_MID["bg"] = ["purple", "magenta", "pink"]

STERLING_SR_OLD = mood(OFFICER, {
    1: "_______________",
    2: "_______________",
    3: "_________SSSSSS",
    4: "_______SSSSSSSS",
    5: "______HSSSSSSSS",
    6: "______HSSSSSSSS",
    7: "______HSSSSSSSS",
    8: "______HSSbbbbSS",
    14: "______SSSSHHHHH",
    15: "_______SSSSSHHHHHHHHSSSS______",
    16: "_______SSSSSSkkkkkkSSSSS______",
})
STERLING_SR_OLD["ramps"] = dict(OFFICER["ramps"], hair=HAIR_WHITE, skin=SKIN_PALE, cloth2=["ink", "slate_d", "slate"])
STERLING_SR_OLD["bg"] = ["purple", "magenta", "pink"]
STERLING_SR_OLD["patch"] = {(11, 4): "c", (12, 4): "c", (10, 5): "c"}


# ---------------------------------------------------------------- round 7: the romance candidates

LEATHER = ["brown_d", "brown", "brown_l"]
NECKERCHIEF = ["red_d", "red", "pink"]
DENIM = ["slate_d", "blue_d", "blue", "cyan"]
BLONDE = ["clay", "amber", "yellow", "cream"]
SANDY = ["brown_l", "tan_d", "tan", "cream"]
RAINCOAT = ["clay", "amber", "yellow", "cream"]
VEXMOOR = ["teal_dd", "green_dd", "green_d", "green"]
HAIR_DARK = ["ink", "brown_d", "brown", "brown_l"]

ROOK = dict(
    # The young smith: messy black hair, soot on one cheek, a red neckerchief, a leather apron over a grey tee.
    rows=[
        "_______________",
        "_________H_HH_H",
        "________HHHHHHH",
        "_______HHHHHHHH",
        "______HHHHHHHHH",
        "______HHHHHHHHH",
        "______HHSSSHSSS",
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
        "_________GGGSSS",
        "________GGGGGGG",
        "______CCCCGGGGG",
        "____CCCCCCDDDDD",
        "___CCCCCCDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
    ],
    bg=["rust", "orange", "amber"],
    ramps={"hair": HAIR_INK, "skin": SKIN_DEEP, "cloth": GREY, "cloth2": LEATHER, "collar": NECKERCHIEF},
    key={"m": ("skin", "brown_d")},
    patch={(9, 12): "x", (10, 13): "x", (14, 23): "M", (15, 23): "M"},
)

WILLA = dict(
    # The young carpenter: a high blonde ponytail, freckles, a pencil behind her ear, a red plaid shirt
    # under denim overalls.
    rows=[
        "_____________HH",
        "____________HHH",
        "_________HHHHHH",
        "_______HHHHHHHH",
        "______HHHHHHHHH",
        "______HHHHHHHHH",
        "______HHSSSSSSS",
        "______HSSSSSSSS",
        "______HSSbbbSSS",
        "______HSSSweSSS",
        "______HSSSeeSSS",
        "______HSSrfSSSS",
        "______HSSSSSSSn",
        "_______SSSSSSSS",
        "________SSSSSSS",
        "________SSSmSSS",
        "_________SSSmmm",
        "__________SSSSS",
        "___________SSSS",
        "____________SSS",
        "_________CCCSSS",
        "______CCCCCCSSS",
        "____CCCCDDCCCCC",
        "___CCCCCDDCCCCC",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
    ],
    bg=["green_d", "green", "yellow"],
    ramps={"hair": BLONDE, "skin": SKIN_FAIR, "cloth": RED, "cloth2": DENIM},
    key={"m": ("skin", "red_d"), "u": ("cloth", "red_d")},
    patch={**{(x, y): "u" for y in range(21, 30) for x in list(range(2, 8)) + list(range(22, 28)) if x % 3 == 0 or y % 3 == 0},
           (22, 7): "Y", (23, 6): "y", (24, 5): "y", (25, 4): "p", (10, 26): "y", (19, 26): "y"},
)

COCO = dict(
    # The cafe owner: a big curly bun, warm brown skin, gold earrings, a wide smile, an orange top
    # under a white apron.
    rows=[
        "____________HHH",
        "__________HHHHH",
        "__________HHHHH",
        "___________HHHH",
        "_______HHHHHHHH",
        "______HHHHHHHHH",
        "______HHHSSSSSS",
        "______HHSSSSSSS",
        "______HSSbbbSSS",
        "______HSSSweSSS",
        "______HSSSeeSSS",
        "______HSSrrSSSS",
        "______HSSSSSSSn",
        "_______SSSSSSSS",
        "________SSSSSSS",
        "________SSmSSSS",
        "_________SSmmmm",
        "__________SSSSS",
        "___________SSSS",
        "____________SSS",
        "_________CCCSSS",
        "______CCCCCCSSS",
        "____CCCCCCDDDDD",
        "___CCCCCCDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
        "__CCCCCCDDDDDDD",
    ],
    bg=["red_d", "red", "pink"],
    ramps={"hair": HAIR_DARK, "skin": SKIN_BROWN, "cloth": ORANGE, "cloth2": APRON},
    key={"m": ("skin", "red"), "h": ("hair", "brown_l")},
    patch={(8, 13): "y", (21, 13): "y", (13, 0): "h", (11, 1): "h", (16, 1): "h", (12, 2): "h", (18, 2): "h", (14, 4): "h", (9, 5): "h", (20, 5): "h",
           (13, 26): "Y", (16, 26): "Y"},
)

FINN = dict(
    # The river fisher: sun-bleached hair under a teal bucket hat, a sunburnt nose, stubble, a yellow
    # raincoat open over a striped shirt.
    rows=[
        "_______________",
        "_______________",
        "_________AAAAAA",
        "________AAAAAAA",
        "________AAAAAAA",
        "_____BBBBBBBBBB",
        "_____HHHSSSSSSS",
        "______HSSSSSSSS",
        "______HSSbbbSSS",
        "______SSSSweSSS",
        "______SSSSeeSSS",
        "______SSSSSSSrr",
        "_______SSSSSSSn",
        "_______SSSSSSSS",
        "________SSSSSSS",
        "________SSSSSSS",
        "_________SSSSmm",
        "__________SSSSS",
        "___________SSSS",
        "____________SSS",
        "_________CCCSSS",
        "______CCCCCCSSS",
        "____CCCCCCCGDDD",
        "___CCCCCCCCGDDD",
        "__CCCCCCCCCGDDD",
        "__CCCCCCCCCGDDD",
        "__CCCCCCCCCGDDD",
        "__CCCCCCCCCGDDD",
        "__CCCCCCCCCGDDD",
        "__CCCCCCCCCGDDD",
    ],
    bg=["blue_d", "blue", "cyan"],
    ramps={"hair": SANDY, "skin": SKIN_TAN, "cloth": RAINCOAT, "cloth2": BLUE, "collar": ["rust", "clay", "amber"],
           "cap": BEANIE, "brim": BEANIE_FOLD},
    key={"m": ("skin", "brown_d"), "r": ("skin", "pink"), "f": ("skin", "brown_l")},
    patch={**{(x, y): "F" for y in range(22, 30, 2) for x in range(12, 18)},
           **{(x, y): "f" for (x, y) in ((10, 16), (12, 17), (17, 17), (19, 16), (14, 18), (15, 18), (11, 15), (18, 15))}},
)

JASPER = dict(
    # The travelling fiddler: wavy auburn hair to the jaw, a goatee, a crooked grin, a green scarf and a
    # brown waistcoat with a gold watch chain.
    rows=[
        "_______________",
        "_________HHHHHH",
        "_______HHHHHHHH",
        "______HHHHHHHHH",
        "_____HHHHHHHHHH",
        "_____HHHHHHHHHH",
        "_____HHHHSSSSSS",
        "_____HHHSSSSSSS",
        "_____HHSSbbbSSS",
        "_____HHSSSweSSS",
        "_____HHSSSeeSSS",
        "_____HHSSSSSSSS",
        "_____HHSSSSSSSn",
        "_____HHHSSSSSSS",
        "______HHSSSSSSS",
        "______HHSSSmSSS",
        "_______H_SSSmmm",
        "__________SSSHH",
        "___________SSHH",
        "____________SSS",
        "_________GGGSSS",
        "______GGGGGGGSS",
        "____CCCGGGGGDDD",
        "___CCCCCGGCCDDD",
        "__CCCCCCCGCCDDD",
        "__CCCCCCCCCCDDD",
        "__CCCCCCCCCCDDD",
        "__CCCCCCCCCCDDD",
        "__CCCCCCCCCCDDD",
        "__CCCCCCCCCCDDD",
    ],
    bg=["blue_d", "purple", "magenta"],
    ramps={"hair": HAIR_AUBURN, "skin": SKIN_FAIR, "cloth": LEATHER, "cloth2": CREAM, "collar": GREEN},
    key={"m": ("skin", "brown_d")},
    patch={(6, 14): "y", (8, 26): "Y", (9, 27): "Y", (10, 27): "Y", (11, 26): "Y", (12, 25): "y",
           # The grin is crooked: one corner up.
           (17, 15): "m", (18, 15): "S"},
)

MARCO = dict(
    # Kessler's surveyor who quit to stay: neat black hair with a side part, olive skin, a shy smile, the
    # Vexmoor-green jacket worn open over a white shirt, the tie loosened.
    rows=[
        "_______________",
        "_______________",
        "_________HHHHHH",
        "_______HHHHHHHH",
        "______HHHHHHHHH",
        "______HHHHHHHHH",
        "______HHSSSSSSS",
        "______HSSSSSSSS",
        "______HSSbbbSSS",
        "______SSSSweSSS",
        "______SSSSeeSSS",
        "______SSSSSSSSS",
        "_______SSSSSSSn",
        "_______SSSSSSSS",
        "________SSSSSSS",
        "________SSSSSSS",
        "_________SSSmmm",
        "__________SSSSS",
        "___________SSSS",
        "____________SSS",
        "_________CCGGGG",
        "______CCCCCGGGG",
        "____CCCCCCCCGGD",
        "___CCCCCCCCCCGD",
        "__CCCCCCCCCCCGD",
        "__CCCCCCCCCCCCG",
        "__CCCCCCCCCCCCG",
        "__CCCCCCCCCCCCG",
        "__CCCCCCCCCCCCG",
        "__CCCCCCCCCCCCG",
    ],
    bg=["slate", "grey_d", "grey"],
    ramps={"hair": HAIR_INK, "skin": SKIN_TAN, "cloth": VEXMOOR, "cloth2": RED, "collar": CREAM},
    key={"m": ("skin", "brown_d")},
    # The side part, and the tie pulled loose and askew.
    patch={(11, 2): "S", (11, 3): "S", (12, 4): "H", (14, 22): "G", (15, 22): "D", (16, 23): "D", (15, 23): "G", (14, 23): "G",
           (6, 24): "y", (7, 24): "Y"},
)

# Who can be courted (Part 2/3 of round 7), with a blushing face for the romance scenes.
ROMANCE = ["ivy", "sela", "willa", "coco", "rook", "finn", "jasper", "marco"]


def determined(spec, brow, eye, mouth):
    """Brows pulled down at the middle and a flat mouth: the face for "you'll see"."""
    rows = spec["rows"]
    b = next(i for i, r in enumerate(rows) if "bbb" in r)
    m = next(i for i, r in enumerate(rows) if r.rstrip("_").endswith("mmm"))
    return mood(spec, {b: brow, b + 1: eye, m - 1: rows[m - 1].replace("m", "S"), m: mouth})


FARMERS = [FARMER_HAT, FARMER_CURLY, FARMER_BRAID, FARMER_BEANIE]
FARMERS_DET = [
    determined(FARMER_HAT, "______HSbbSSSSS", "______HSSSbeSSS", "_________SSSSmm"),
    determined(FARMER_CURLY, "__HHHHHSbbSSSSS", "__HHHHHSSSbeSSS", "__HHHHHHHHSSSmm"),
    determined(FARMER_BRAID, "_____HHSbbSSSSS", "_____HHSSSbeSSS", "____HHHH_SSSSmm"),
    determined(FARMER_BEANIE, "______HSbbSSSSS", "______SSSSbeSSS", "_________SSSSmm"),
]

PORTRAITS = [("mara", MARA), ("tobias", TOBIAS), ("ivy", IVY), ("bram", BRAM),
             ("juniper", JUNIPER), ("pip", PIP), ("sela", SELA), ("fennel", FENNEL),
             ("officer", OFFICER), ("officer_shock", OFFICER_SHOCK), ("kessler", KESSLER), ("kessler_shock", KESSLER_SHOCK)]
PORTRAITS += [(f"farmer_{i}", f) for i, f in enumerate(FARMERS)]
PORTRAITS += [(f"farmer_{i}_det", f) for i, f in enumerate(FARMERS_DET)]


# Round 7: the romance candidates' new faces (Rook, Willa and Coco were recolours of older villagers).
PORTRAITS += [("rook", ROOK), ("willa", WILLA), ("coco", COCO), ("finn", FINN), ("jasper", JASPER), ("marco", MARCO)]
# Round 7: Grandpa Elias and old Mr. Sterling for the new opening.
PORTRAITS += [("elias_young", ELIAS_YOUNG), ("elias_mid", ELIAS_MID), ("elias_old", ELIAS_OLD), ("elias_bed", ELIAS_BED),
              ("sterling_sr_young", STERLING_SR_YOUNG), ("sterling_sr_mid", STERLING_SR_MID), ("sterling_sr_old", STERLING_SR_OLD)]
# Glints and pupils behind the Sterlings' glasses blink too.
for _spec in (OFFICER, OFFICER_SHOCK, KESSLER, KESSLER_SHOCK, STERLING_SR_YOUNG, STERLING_SR_MID, STERLING_SR_OLD):
    _spec["eyes"] = "ewck"
# Round 7: every villager has a bust too, for the porch scene and the heart events.
BUSTS = ("officer", "farmer_", "kessler", "elias", "sterling", "mara", "tobias", "ivy", "bram", "juniper", "pip", "sela", "fennel", "rook", "willa", "coco",
         "finn", "jasper", "marco")


def main():
    sp = Sprites(sys.argv[1], "portraits")
    for name, spec in PORTRAITS:
        # Each face also talks (mouth open) and blinks (round 7: the story scenes animate them); the
        # romance candidates blush too.
        moods = [("", False)] + ([("_blush", True)] if name in ROMANCE else [])
        for (mood_suffix, blush), (suffix, anim) in [(m, a) for m in moods for a in (("", None), ("_talk", "talk"), ("_blink", "blink"))]:
            suffix = mood_suffix + suffix
            frame = dict(spec, anim=anim, blush=blush)
            out, pad = paint(frame)
            sp.save(name + suffix, out, 15 + pad, 15 + pad)
            if name.startswith(BUSTS):
                out, pad = paint(frame, bust=True)
                sp.save(name + "_bust" + suffix, out, 15 + pad, 29 + pad)
    sp.done()


if __name__ == "__main__":
    main()
