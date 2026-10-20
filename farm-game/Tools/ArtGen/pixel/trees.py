"""Forest trees and standing objects on iso tiles (pixel-direct, folder trees/).

Trees (pine, birch, oak, maple, ancient_oak): <type>_sapling, <type>_young, <type> (mature),
<type>_fall, <type>_snow (mature size), <type>_stump.
Objects: log_windfall, branch_pile, berry_bush, berry_bush_empty, apple_tree,
apple_tree_empty, mushroom_spot, brambles, signpost, beehive.
Orchard (farm): orchard_<kind> (resting), orchard_<kind>_fruit, orchard_<kind>_sapling for
apple, cherry, peach and orange.

Every sprite is drawn on one 64x72 canvas with the trunk base on row GROUND and column BX,
cropped symmetrically around BX and finished with finish() (1px ink outline + hard drop
shadow). Pivot = base of the trunk (+pad). Endesga-32 only, light from the top-left, checker
dithering between foliage steps, hard steps on bark.
"""
import math
import random
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E402
from _pix import E, Sprites, finish, new, rng  # noqa: E402

W, H = 64, 72
BX, GROUND = 32, 67          # base column (corner) and last trunk row on the canvas
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
_L = (-0.55, -0.7, 0.6)
_m = math.sqrt(sum(v * v for v in _L))
LIGHT = tuple(v / _m for v in _L)

# Ramps run dark -> light (index 0 is the crevice colour).
LEAF = ["teal_dd", "green_dd", "green_d", "green", "yellow"]
BIRCH_LEAF = ["green_dd", "green_d", "green", "yellow"]
MAPLE_LEAF = ["rust", "red_d", "red", "orange", "amber"]
OAK_FALL = ["brown", "rust", "orange", "amber", "yellow"]
MAPLE_FALL = ["purple", "red_d", "red", "amber", "yellow"]
BIRCH_FALL = ["brown", "clay", "amber", "yellow", "cream"]
ANC_FALL = ["red_d", "rust", "orange", "amber", "yellow"]
BARK = ["brown_d", "brown", "brown_l", "tan_d"]
PINE_BARK = ["ink", "brown_d", "brown", "brown_l"]
BIRCH_BARK = ["grey_d", "grey", "grey_l", "white"]
RING = ["cream", "tan", "tan_d", "brown"]
LOG = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
MUSH_RED = ["purple", "red_d", "red", "pink"]
MUSH_BROWN = ["brown_d", "brown", "brown_l", "tan"]
STEM = ["tan_d", "tan", "cream"]
BRAMBLE = ["teal_dd", "green_dd", "green_d", "green"]
HIVE = ["brown", "clay", "tan_d", "tan", "cream"]
BOARD = ["tan_d", "tan", "cream"]
POST = ["brown_d", "brown", "brown_l"]
APPLE_MAP = [".g.", "rwr", "rrd", ".d."]
APPLE_KEY = {"g": "green_d", "r": "red", "w": "pink", "d": "red_d"}

# Tree geometry in mature coordinates (x, y on the 64x72 canvas, base at BX, GROUND).
OAK_LOBES = [(32, 33, 17, 13), (21, 28, 9, 8), (43, 28, 9, 8), (14, 38, 6, 6), (50, 38, 6, 6),
             (23, 42, 8, 6), (41, 42, 8, 6), (32, 22, 9, 7)]
MAPLE_LOBES = [(32, 30, 15, 12), (21, 24, 8, 7), (43, 24, 8, 7), (32, 18, 7, 6), (14, 33, 6, 6),
               (50, 33, 6, 6), (23, 39, 7, 6), (41, 39, 7, 6)]
BIRCH_LOBES = [(32, 27, 9, 8), (23, 24, 7, 6), (41, 24, 7, 6), (30, 18, 6, 5), (38, 34, 6, 5),
               (24, 34, 6, 5), (32, 40, 8, 4)]
ANC_LOBES = [(32, 27, 22, 14), (14, 31, 9, 8), (50, 31, 9, 8), (20, 17, 8, 7), (44, 17, 8, 7),
             (32, 14, 9, 7), (11, 39, 6, 5), (53, 39, 6, 5), (19, 42, 8, 6), (45, 42, 8, 6),
             (32, 42, 11, 6)]
APPLE_LOBES = [(32, 42, 11, 9), (23, 38, 6, 5), (41, 38, 6, 5), (31, 34, 6, 5), (38, 47, 6, 4)]
PINE_TIERS = [(40, 58, 14), (31, 48, 12), (22, 38, 10), (15, 29, 7)]   # bottom tier first
BUSH_LOBES = [(32, 59, 11, 7), (23, 62, 6, 5), (41, 62, 6, 5), (32, 53, 7, 5), (26, 55, 5, 4),
              (38, 55, 5, 4)]
BERRY_SPOTS = [(27, 53), (36, 51), (21, 59), (33, 58), (43, 58), (29, 63), (39, 63)]
BRAMBLE_LOBES = [(32, 57, 14, 8), (22, 53, 8, 6), (42, 53, 8, 6), (18, 60, 6, 5), (46, 60, 6, 5),
                 (32, 49, 6, 4), (32, 62, 10, 4)]
CANES = [((30, 60), (16, 49)), ((34, 56), (48, 47)), ((26, 62), (22, 66)), ((38, 62), (44, 65)),
         ((28, 52), (24, 44)), ((36, 52), (41, 45))]

SNOW_SWAP = {"green": "green_d", "green_d": "green_dd", "green_dd": "teal_dd", "yellow": "grey_l"}
SNOW_CAP = {}  # filled lazily from E on first use
SNOW_SKIP = {"brown_d", "brown", "brown_l", "tan_d", "tan", "clay", "ink", "cream", "white"}


class Canvas:
    def __init__(self):
        self.im = new(W, H)
        self.px = self.im.load()

    def inb(self, x, y):
        return 0 <= x < W and 0 <= y < H

    def on(self, x, y):
        return self.inb(x, y) and self.px[x, y][3] > 0

    def put(self, x, y, c):
        if c is not None and self.inb(x, y):
            self.px[x, y] = E[c] if isinstance(c, str) else c

    def erase(self, x, y):
        if self.inb(x, y):
            self.px[x, y] = (0, 0, 0, 0)


class Geo:
    """Maps mature-tree coordinates to a scaled tree that keeps the base at (BX, GROUND)."""

    def __init__(self, s=1.0):
        self.s = s

    def p(self, x, y):
        return BX + (x - BX) * self.s, GROUND - (GROUND - y) * self.s

    def r(self, v):
        return max(1.2, v * self.s)

    def w(self, v):
        return max(2, int(round(v * self.s)))


def ell(cx, cy, rx, ry):
    out = set()
    for y in range(math.floor(cy - ry) - 1, math.ceil(cy + ry) + 2):
        for x in range(math.floor(cx - rx) - 1, math.ceil(cx + rx) + 2):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                out.add((x, y))
    return out


def tone(ramp, v, x, y):
    """Ramp colour for a fractional level v: the nearest step (clean bands, no dither)."""
    n = len(ramp) - 1
    return ramp[int(round(max(0.0, min(float(n), v))))]


def clusters(cx, cy, rx, ry):
    """Leaf-clump centres (x, y, radius) on a jittered grid about 4px apart inside a lobe."""
    rnd = random.Random(int(cx * 131 + cy * 71 + rx * 13 + ry * 7))
    step = 4.9
    out = []
    row = 0
    y = cy - ry + 1.2
    while y <= cy + ry - 0.6:
        x = cx - rx + 1.2 + (step / 2 if row % 2 else 0.0)
        while x <= cx + rx - 0.6:
            jx, jy = x + rnd.uniform(-0.9, 0.9), y + rnd.uniform(-0.7, 0.7)
            if ((jx - cx) / rx) ** 2 + ((jy - cy) / ry) ** 2 <= 1.0:
                out.append((jx, jy, rnd.uniform(2.8, 3.6)))
            x += step
        y += step * 0.78
        row += 1
    return out or [(cx, cy, max(rx, ry))]


def lobe(c, cx, cy, rx, ry, ramp, lift=0.0, sparkle=True):
    """Foliage clump painted over what is already on the canvas: sphere-lit overall, built from
    small leaf clusters, each lit on its top-left with a dark gap where clusters meet, and a
    scalloped rim where edge clusters bulge out. Where it meets earlier foliage it gets a
    crevice line."""
    n = len(ramp) - 1
    cl = clusters(cx, cy, rx, ry)
    s = ell(cx, cy, rx, ry)
    for kx, ky, kr in cl:
        if ((kx - cx) / rx) ** 2 + ((ky - cy) / ry) ** 2 > 0.45:
            s |= ell(kx, ky, kr, kr * 0.85)
    for x, y in sorted(s, key=lambda p: (p[1], p[0])):
        px, py = x + 0.5, y + 0.5
        nx = (px - cx) / (rx + 1.5)
        ny = (py - cy) / (ry + 1.5)
        nz = math.sqrt(max(0.0, 1.0 - min(1.0, nx * nx + ny * ny)))
        lv = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        v = (lv + 0.45) / 1.45 * n + lift
        # The leaf cluster this pixel belongs to, and how close the next one is.
        d1 = d2 = 1e9
        k1 = cl[0]
        for k in cl:
            d = math.hypot((px - k[0]) / k[2], (py - k[1]) / (k[2] * 0.85))
            if d < d1:
                d2, d1, k1 = d1, d, k
            elif d < d2:
                d2 = d
        kx, ky, kr = k1
        v += -0.75 * ((px - kx) / kr * 0.6 + (py - ky) / kr * 0.9)
        if d2 - d1 < 0.16 and d1 > 0.6:
            v -= 1.1
        if (x + 1, y) not in s or (x, y + 1) not in s:
            v -= 0.6
        cc = tone(ramp, v, x, y)
        behind = [(dx, dy) for dx, dy in N4 if (x + dx, y + dy) not in s and c.on(x + dx, y + dy)]
        if behind:
            if any(dy > 0 or dx > 0 for dx, dy in behind):
                cc = ramp[0]
            else:
                cc = ramp[max(0, ramp.index(cc) - 1)]
        elif sparkle and v >= n - 0.1 and d1 < 0.35 and (x * 7 + y * 3) % 5 == 0:
            cc = ramp[n]
        c.put(x, y, cc)


def canopy(c, g, lobes, ramp, lift=0.0):
    for cx, cy, rx, ry in lobes:
        x, y = g.p(cx, cy)
        lobe(c, x, y, g.r(rx), g.r(ry), ramp, lift)


def trunk(c, g, y_top, w_top, w_base, ramp, lean=0.0, flare=0, mark=None):
    """Tapered trunk from y_top down to GROUND, lit on the left, with optional root flare."""
    n = len(ramp) - 1
    top = int(round(g.p(BX, y_top)[1]))
    span = max(1.0, GROUND - top)
    wt, wb = g.w(w_top), g.w(w_base)
    for y in range(top, GROUND + 1):
        t = (y - top) / span
        w = int(round(wt + (wb - wt) * t)) + (2 if flare and y > GROUND - flare else 0)
        x0 = BX - w // 2 + int(round(lean * (GROUND - y)))
        for i in range(w):
            f = (i + 0.5) / w
            v = n * (0.92 - 0.8 * f)
            cc = ramp[max(0, min(n, int(round(v))))]
            if mark:
                cc = mark(x0 + i, y, i, w, cc) or cc
            c.put(x0 + i, y, cc)


def pine_tier(c, g, apex, base, hw, ramp):
    """Triangular conifer tier with a sawtooth skirt, lit from the top-left."""
    n = len(ramp) - 1
    ay = g.p(BX, apex)[1]
    by = g.p(BX, base)[1]
    hws = g.r(hw)
    y1 = int(round(by))
    for y in range(int(math.floor(ay)), y1 + 1):
        t = min(1.0, max(0.0, (y + 0.5 - ay) / max(1.0, by - ay)))
        half = max(0.5, hws * t)
        for x in range(int(math.floor(BX - half)) - 1, int(math.ceil(BX + half)) + 1):
            if abs(x + 0.5 - BX) > half:
                continue
            if y >= y1 and (x + y) % 3 == 0:
                continue
            lx = (x + 0.5 - BX) / half
            v = n * (0.8 - 0.35 * lx - 0.4 * t)
            # Branch layers: chevrons drooping outward, lit along their top, shaded under.
            band = (y - ay - abs(x + 0.5 - BX) * 0.55) % 4.0
            if band < 1.0:
                v += 0.7 if lx < 0.35 else 0.3
            elif band >= 3.0:
                v -= 0.6
            if y >= y1:
                v -= 1.0
            c.put(x, y, tone(ramp, v, x, y))


def birch_mark(x, y, i, w, cc):
    return "grey_d" if (y * 3 + i) % 7 == 0 and i > 0 else None


def knot_mark(x, y, i, w, cc):
    return "brown_d" if (x * 7 + y * 5) % 23 == 0 else None


def stroke(c, a, b, color, w=2):
    (x0, y0), (x1, y1) = (round(a[0]), round(a[1])), (round(b[0]), round(b[1]))
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for k in range(steps + 1):
        t = k / steps
        x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
        for j in range(w):
            c.put(x, y + j, color)


def thick_line(c, a, b, hi, lo):
    """2px stick: light upper row, dark lower row."""
    (x0, y0), (x1, y1) = (round(a[0]), round(a[1])), (round(b[0]), round(b[1]))
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for k in range(steps + 1):
        t = k / steps
        x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
        c.put(x, y, hi)
        c.put(x, y + 1, lo)


def snowify(c):
    """Winter: white caps on every upward-facing edge with a dithered second row, and the
    greens pushed cooler."""
    px = c.px
    if not SNOW_CAP:
        SNOW_CAP.update({E[k]: E[v] for k, v in SNOW_SWAP.items()})
    skip = {E[k] for k in SNOW_SKIP}
    caps = set()
    for y in range(H):
        for x in range(W):
            p = px[x, y]
            if p[3] == 0 or p in skip:
                continue
            above = px[x, y - 1][3] if y > 0 else 0
            if above == 0:
                px[x, y] = E["white"]
                caps.add((x, y))
            elif p in SNOW_CAP:
                px[x, y] = SNOW_CAP[p]
    for x, y in caps:
        if c.on(x, y + 1) and (x, y + 1) not in caps and (x + y) % 2 == 0 and px[x, y + 1] not in skip:
            px[x, y + 1] = E["grey_l"]


def litter(c, seed):
    """Fallen leaves scattered on the ground around the trunk (autumn variants)."""
    r = rng(seed)
    for x in range(BX - 11, BX + 12):
        if not c.on(x, GROUND) and r.random() < 0.35:
            c.put(x, GROUND, r.choice(["rust", "orange", "amber"]))


# ---------------------------------------------------------------- trees

def pine(c, g, ramp):
    trunk(c, g, 52, 2, 3, PINE_BARK, flare=1)
    for apex, base, hw in PINE_TIERS:
        pine_tier(c, g, apex, base, hw, ramp)


def birch(c, g, ramp):
    trunk(c, g, 36, 3, 3, BIRCH_BARK, lean=0.02, mark=birch_mark)
    canopy(c, g, BIRCH_LOBES, ramp)


def oak(c, g, ramp):
    trunk(c, g, 44, 5, 7, BARK, flare=3)
    canopy(c, g, OAK_LOBES, ramp)


def maple(c, g, ramp):
    trunk(c, g, 46, 4, 6, BARK, flare=2)
    canopy(c, g, MAPLE_LOBES, ramp)


def ancient(c, g, ramp):
    for a, b in (((BX - 6, 44), (BX - 18, 36)), ((BX + 6, 42), (BX + 17, 34))):
        pa, pb = g.p(*a), g.p(*b)
        stroke(c, pa, pb, "brown", w=max(2, g.w(3)))
    trunk(c, g, 40, 10, 14, BARK, flare=3, mark=knot_mark)
    if g.s >= 0.99:
        for x, y in ell(BX - 1, 53, 2.4, 3.2):
            c.put(x, y, "ink" if (x + y) % 2 else "brown_d")
    canopy(c, g, ANC_LOBES, ramp)


TREE_FUNCS = {"pine": pine, "birch": birch, "oak": oak, "maple": maple, "ancient_oak": ancient}


def draw_tree(c, kind, g, season):
    """season: green | fall | snow. Fall swaps the foliage ramp (pine keeps green, adds litter)."""
    fall = season == "fall"
    if kind == "pine":
        ramp = LEAF
    elif kind == "birch":
        ramp = BIRCH_FALL if fall else BIRCH_LEAF
    elif kind == "oak":
        ramp = OAK_FALL if fall else LEAF
    elif kind == "maple":
        ramp = MAPLE_FALL if fall else MAPLE_LEAF
    else:
        ramp = ANC_FALL if fall else LEAF
    TREE_FUNCS[kind](c, g, ramp)
    if fall:
        litter(c, len(kind) * 17 + 3)
    if season == "snow":
        snowify(c)


def stump(c, rx, h, bark, ring):
    """Cut stump: side bark lit on the left, face with concentric rings, flared base."""
    ry = max(2, int(round(rx * 0.4)))
    top = GROUND - h
    n = len(bark) - 1
    x0 = BX - rx
    for y in range(top, GROUND + 1):
        fl = 1 if y >= GROUND - 1 else 0
        for x in range(x0 - fl, BX + rx + fl):
            f = (x + 0.5 - (x0 - fl)) / (2 * rx + 2 * fl)
            v = n * (0.95 - 0.75 * f)
            cc = bark[max(0, min(n, int(round(v))))]
            if (x * 5 + y * 3) % 11 == 0:
                cc = bark[max(0, int(round(v)) - 1)]
            c.put(x, y, cc)
    for x, y in ell(BX, top, rx, ry):
        d = math.sqrt(((x + 0.5 - BX) / rx) ** 2 + ((y + 0.5 - top) / ry) ** 2)
        c.put(x, y, ring[min(len(ring) - 1, int(d * len(ring)))])


# ---------------------------------------------------------------- objects

def log_windfall(c):
    top = GROUND - 9
    x0, x1 = BX - 13, BX + 12
    n = len(LOG) - 1
    for x in range(x0, x1):
        for y in range(top, GROUND + 1):
            f = (y - top) / 9.0
            v = n * (0.95 - 0.8 * f)
            cc = LOG[max(0, min(n, int(round(v))))]
            if (x * 5) % 7 == 1 and y > top + 1:
                cc = "brown_d"
            c.put(x, y, cc)
    for x in range(x0, x1):
        if (x * 3) % 5 == 0:
            c.put(x, top, "green_d")
    fy = top + 4.5
    for x, y in ell(x1 - 1, fy, 3.0, 4.5):
        d = math.sqrt(((x + 0.5 - (x1 - 1)) / 3.0) ** 2 + ((y + 0.5 - fy) / 4.5) ** 2)
        c.put(x, y, RING[min(len(RING) - 1, int(d * 4))])


def branch_pile(c):
    sticks = [((BX - 10, GROUND - 3), (BX + 8, GROUND - 6)),
              ((BX - 8, GROUND - 6), (BX + 9, GROUND - 3)),
              ((BX - 3, GROUND - 9), (BX + 5, GROUND - 6)),
              ((BX - 11, GROUND - 2), (BX - 2, GROUND - 2)),
              ((BX + 2, GROUND - 1), (BX + 10, GROUND - 2))]
    for a, b in sticks:
        thick_line(c, a, b, "tan", "brown")
    for x, y in [(BX - 6, GROUND - 6), (BX + 4, GROUND - 4)]:
        c.put(x, y, "brown_d")


def berry_bush(c, berries):
    canopy(c, Geo(1.0), BUSH_LOBES, LEAF)
    if berries:
        for x, y in BERRY_SPOTS:
            if all(c.on(x + dx, y + dy) for dx in (0, 1) for dy in (0, 1)):
                c.put(x, y, "white")
                c.put(x + 1, y, "red")
                c.put(x, y + 1, "red")
                c.put(x + 1, y + 1, "red_d")


def apple_tree(c, apples):
    g = Geo(1.0)
    trunk(c, g, 46, 3, 4, BARK, flare=1)
    canopy(c, g, APPLE_LOBES, LEAF)
    if apples:
        for x, y in [(26, 37), (36, 39), (22, 42), (31, 43), (41, 44), (34, 34)]:
            if all(c.on(x + dx, y + dy) for dx in range(3) for dy in range(4)):
                for j, row in enumerate(APPLE_MAP):
                    for i, ch in enumerate(row):
                        if ch != ".":
                            c.put(x + i, y + j, APPLE_KEY[ch])


# Orchard trees: smaller and rounder than forest trees, planted two tiles apart.
ORCHARD = {
    # lobes, leaf ramp, fruit map, fruit key, fruit spots
    "apple": (APPLE_LOBES, LEAF, APPLE_MAP, APPLE_KEY,
              [(26, 37), (36, 39), (22, 42), (31, 43), (41, 44), (34, 34), (27, 46)]),
    "cherry": ([(32, 41, 13, 8), (21, 38, 6, 5), (43, 38, 6, 5), (30, 33, 7, 5), (38, 46, 6, 4)],
               ["teal_dd", "green_dd", "green_d", "green", "green"],
               ["..g..", ".g.g.", "wr.wr", "rd.rd"], {"g": "green_dd", "w": "white", "r": "red", "d": "red_d"},
               [(22, 35), (33, 36), (27, 41), (38, 42), (18, 40), (30, 46), (41, 35), (23, 45), (43, 40)]),
    "peach": ([(32, 42, 12, 9), (24, 37, 6, 5), (40, 37, 6, 5), (32, 33, 7, 5)],
              ["teal_dd", "green_dd", "green_d", "green", "yellow"],
              [".g.", "wap", "app", ".r."], {"g": "green_d", "w": "cream", "a": "amber", "p": "pink", "r": "rust"},
              [(25, 37), (35, 39), (22, 43), (31, 44), (40, 44), (33, 33)]),
    "orange": ([(32, 40, 10, 10), (25, 36, 6, 6), (39, 36, 6, 6), (32, 31, 7, 5)],
               ["teal_dd", "teal_dd", "green_dd", "green_d", "green"],
               [".g.", "yoo", "oor", ".r."], {"g": "green_dd", "y": "yellow", "o": "orange", "r": "rust"},
               [(26, 35), (35, 37), (24, 42), (32, 44), (39, 43), (31, 31), (29, 39)]),
}


def orchard_tree(c, kind, fruit, s=1.0):
    lobes, leaf, fmap, fkey, spots = ORCHARD[kind]
    g = Geo(s)
    trunk(c, g, 46, 3, 4, BARK, flare=1)
    canopy(c, g, lobes, leaf)
    if fruit:
        for x, y in spots:
            w, h = len(fmap[0]), len(fmap)
            if all(c.on(x + dx, y + dy) for dx in range(w) for dy in range(h)):
                for j, row in enumerate(fmap):
                    for i, ch in enumerate(row):
                        if ch != ".":
                            c.put(x + i, y + j, fkey[ch])


def mushroom(c, x, y, cap):
    for yy in range(y - 2, y + 1):
        c.put(x - 1, yy, STEM[1])
        c.put(x, yy, STEM[0])
    lobe(c, x, y - 3, 3.0, 2.3, cap, sparkle=False)
    if cap is MUSH_RED:
        c.put(x - 1, y - 4, "white")
        c.put(x + 1, y - 3, "white")


def mushroom_spot(c):
    cy = GROUND - 4
    for x, y in ell(BX, cy, 13, 6) - ell(BX, cy, 10.5, 4):
        c.put(x, y, "green_d")
    spots = []
    for k, ang in enumerate((0, 60, 120, 180, 240, 300)):
        t = math.radians(ang + 20)
        spots.append((round(BX + 10.5 * math.cos(t)), round(cy + 3.5 * math.sin(t) + 2),
                      MUSH_RED if k % 2 == 0 else MUSH_BROWN))
    for x, y, cap in sorted(spots, key=lambda s: s[1]):
        mushroom(c, x, y, cap)


def brambles(c):
    canopy(c, Geo(1.0), BRAMBLE_LOBES, BRAMBLE)
    for a, b in CANES:
        stroke(c, a, b, "brown_d", w=1)
        steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
        for k in range(2, steps, 3):
            t = k / steps
            x = round(a[0] + (b[0] - a[0]) * t)
            y = round(a[1] + (b[1] - a[1]) * t)
            c.put(x + 1, y - 1, "grey_d")
            c.put(x, y - 1, "grey_l")
    # Thorn speckles: pale single pixels poking out above the exposed top edge of the bush.
    for y in range(GROUND - 22, GROUND):
        for x in range(BX - 18, BX + 18):
            if c.on(x, y) and not c.on(x, y - 1) and (x * 3 + y) % 5 == 0:
                c.put(x, y - 1, "grey_l")
    for x, y in [(24, 50), (40, 56), (30, 63)]:
        if c.on(x, y):
            c.put(x, y, "purple")
            c.put(x + 1, y, "magenta")


def board(c, x0, x1, y0, arrow):
    h = 6
    for y in range(y0, y0 + h):
        for x in range(x0, x1):
            shade = BOARD[2] if y == y0 else BOARD[1] if y < y0 + h - 1 else BOARD[0]
            c.put(x, y, shade)
    mid = y0 + h // 2
    # Arrow head: the board's outer corners on the arrow side are cut away and a point sticks out.
    if arrow > 0:
        for y in (y0, y0 + h - 1):
            c.erase(x1 - 1, y)
        for k in range(1, 4):
            c.put(x1 - 1 + k, mid, BOARD[1] if k < 3 else BOARD[0])
        for y in (y0 + 1, y0 + h - 2):
            c.put(x1, y, BOARD[1])
    else:
        for y in (y0, y0 + h - 1):
            c.erase(x0, y)
        for k in range(1, 4):
            c.put(x0 - k, mid, BOARD[1] if k < 3 else BOARD[0])
        for y in (y0 + 1, y0 + h - 2):
            c.put(x0 - 1, y, BOARD[1])
    for yy in (y0 + 2, y0 + 4):
        for x in range(x0 + 2, x1 - 2, 2):
            c.put(x, yy, "brown")


def signpost(c):
    for y in range(GROUND - 24, GROUND + 1):
        c.put(BX - 1, y, POST[1])
        c.put(BX, y, POST[0])
        c.put(BX - 2, y, POST[2])
    board(c, BX - 9, BX + 8, GROUND - 19, +1)
    board(c, BX - 8, BX + 9, GROUND - 12, -1)
    c.put(BX - 1, GROUND - 24, "brown_l")


def beehive(c):
    stump(c, 7, 5, BARK, RING)
    top = GROUND - 5
    n = len(HIVE) - 1
    ry = 10
    s = ell(BX, top, 8, ry)
    for x, y in s:
        if y >= top:
            continue
        nx = (x + 0.5 - BX) / 8
        ny = (y + 0.5 - top) / ry
        nz = math.sqrt(max(0.0, 1.0 - min(1.0, nx * nx + ny * ny)))
        lv = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        v = (lv + 0.45) / 1.45 * n
        if ((top - 1 - y) // 2) % 3 == 0:
            v -= 1
        c.put(x, y, tone(HIVE, v, x, y))
    for x in (BX - 1, BX, BX + 1):
        c.put(x, top - 1, "ink")
    c.put(BX + 9, top - 10, "ink")
    c.put(BX + 10, top - 10, "yellow")
    c.put(BX - 11, top - 13, "ink")
    c.put(BX - 10, top - 13, "yellow")


# ---------------------------------------------------------------- output

def save_canvas(sp, name, c):
    x0, y0, x1, y1 = c.im.getbbox()
    half = max(BX - x0, x1 - BX)
    body = c.im.crop((BX - half, y0, BX + half, GROUND + 1))
    out, pad = finish(body)
    sp.save(name, out, half + pad, GROUND - y0 + pad)


def main():
    sp = Sprites(sys.argv[1], "trees")
    kinds = ["pine", "birch", "oak", "maple", "ancient_oak"]
    stumps = {"pine": (6, 5, PINE_BARK, RING), "birch": (6, 5, BIRCH_BARK, RING),
              "oak": (7, 5, BARK, RING), "maple": (7, 5, BARK, RING),
              "ancient_oak": (11, 7, BARK, RING)}
    for kind in kinds:
        for stage, s in (("sapling", 0.3), ("young", 0.6)):
            c = Canvas()
            draw_tree(c, kind, Geo(s), "green")
            save_canvas(sp, f"{kind}_{stage}", c)
        c = Canvas()
        draw_tree(c, kind, Geo(1.0), "green")
        save_canvas(sp, kind, c)
        c = Canvas()
        draw_tree(c, kind, Geo(1.0), "fall")
        save_canvas(sp, f"{kind}_fall", c)
        c = Canvas()
        draw_tree(c, kind, Geo(1.0), "snow")
        save_canvas(sp, f"{kind}_snow", c)
        c = Canvas()
        rx, h, bark, ring = stumps[kind]
        stump(c, rx, h, bark, ring)
        save_canvas(sp, f"{kind}_stump", c)

    c = Canvas(); log_windfall(c); save_canvas(sp, "log_windfall", c)
    c = Canvas(); branch_pile(c); save_canvas(sp, "branch_pile", c)
    c = Canvas(); berry_bush(c, True); save_canvas(sp, "berry_bush", c)
    c = Canvas(); berry_bush(c, False); save_canvas(sp, "berry_bush_empty", c)
    c = Canvas(); apple_tree(c, True); save_canvas(sp, "apple_tree", c)
    c = Canvas(); apple_tree(c, False); save_canvas(sp, "apple_tree_empty", c)
    for kind in ORCHARD:
        c = Canvas(); orchard_tree(c, kind, False); save_canvas(sp, f"orchard_{kind}", c)
        c = Canvas(); orchard_tree(c, kind, True); save_canvas(sp, f"orchard_{kind}_fruit", c)
        c = Canvas(); orchard_tree(c, kind, False, 0.45); save_canvas(sp, f"orchard_{kind}_sapling", c)
    c = Canvas(); mushroom_spot(c); save_canvas(sp, "mushroom_spot", c)
    c = Canvas(); brambles(c); save_canvas(sp, "brambles", c)
    c = Canvas(); signpost(c); save_canvas(sp, "signpost", c)
    c = Canvas(); beehive(c); save_canvas(sp, "beehive", c)
    sp.done()


main()
