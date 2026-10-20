"""Food and processed goods (item icons), pixel-direct.

items/<id>      body 14x14: chunky silhouettes that read at 1x.
items_big/<id> body 28x28: the same subjects with extra detail (dither, seams, seeds, steam),
                not an upscale.
Pivot = centre of the body (+ the finish() pad).

Each shape is defined once in 14-unit space and sampled at pixel centres, so both sizes share
one silhouette; checks on c.n add the 28px detail. Endesga-32 only, light from the top-left,
finished with finish() (1px ink outline + hard drop shadow) like every other sprite.

Covers: flour, cornmeal, cooking_oil, bread, cornbread, roast_veggies, strawberry_tart,
pumpkin_pie, pancakes, veggie_stew, potato_chips, ratatouille, strawberry/melon/grapes jam,
seven pickles (one jar function: colour + shape), five juices (one bottle function: colour).
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, Sprites, new, finish  # noqa: E402
import json  # noqa: E402
import math  # noqa: E402

_L = (-0.55, -0.7, 0.6)
LIGHT = tuple(v / math.sqrt(sum(u * u for u in _L)) for v in _L)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

# Ramps run dark -> light (Endesga-32 keys from _pix.E).
FLOUR = ["tan_d", "tan", "cream", "white"]
CORNMEAL = ["rust", "orange", "amber", "yellow"]
CORK = ["brown_d", "brown", "brown_l", "tan"]
METAL = ["slate", "grey_d", "grey", "grey_l"]
GLASS = ["slate", "grey_d", "grey", "grey_l"]
OIL = ["rust", "orange", "amber", "yellow"]
BREAD = ["brown", "rust", "clay", "amber", "tan"]
CORN_TOP = ["orange", "amber", "yellow", "cream"]
CORN_L = ["rust", "orange", "amber"]
CORN_R = ["brown", "rust", "orange"]
PLATE = ["grey", "grey_l", "white"]
CARROT = ["rust", "orange", "amber", "yellow"]
POTATO = ["brown", "tan_d", "tan", "cream"]
TART_CRUST = ["clay", "amber", "tan", "cream"]
CUSTARD = ["tan", "cream", "white"]
BERRY = ["red_d", "red", "pink"]
PIE_FILL = ["red_d", "rust", "orange", "amber"]
PIE_CRUST = ["brown", "clay", "amber", "yellow"]
CREAM = ["tan", "cream", "white"]
PAN_TOP = ["clay", "amber", "tan", "cream"]
PAN_SIDE = ["rust", "clay", "tan"]
SYRUP = ["brown_d", "brown", "rust", "amber"]
BUTTER = ["amber", "yellow", "white"]
BOWL = ["brown", "clay", "tan", "cream"]
BOWL_RIM = ["clay", "tan", "cream", "white"]
SOUP = ["red_d", "red", "orange", "amber"]
FOIL = ["slate", "grey", "grey_l", "white"]
CHIP_BAG = ["rust", "orange", "amber", "yellow"]
RED_LABEL = ["red_d", "red", "pink"]
CHIP = ["tan", "cream", "white"]
DISH = ["clay", "tan", "cream"]
DISH_SIDE = ["brown", "clay"]
PURPLE_VEG = ["purple", "magenta", "pink"]
RED_VEG = ["red_d", "red", "pink"]
GREEN_VEG = ["green_d", "green", "yellow"]
YELLOW_VEG = ["amber", "yellow", "white"]
CLOTH = ["tan_d", "tan", "cream", "white"]
JAM_RED = ["purple", "red_d", "red", "pink"]
JAM_GREEN = ["green_dd", "green_d", "green", "yellow"]
JAM_GRAPE = ["slate_d", "purple", "magenta", "pink"]
JAM_CHERRY = ["ink", "purple", "red_d", "red"]
JAM_PEACH = ["rust", "orange", "amber", "cream"]
PLATE_EDGE = ["slate", "grey_d", "grey"]
LEAF = ["green_dd", "green_d", "green", "yellow"]

PICKLE = {
    "carrot": (["rust", "orange", "amber", "yellow"], "spear"),
    "potato": (["brown", "tan_d", "tan", "cream"], "chunk"),
    "tomato": (["red_d", "red", "pink", "white"], "chunk"),
    "corn": (["clay", "amber", "yellow", "cream"], "chunk"),
    "pumpkin": (["brown", "rust", "orange", "amber"], "chunk"),
    "cabbage": (["green_dd", "green_d", "green", "yellow"], "chunk"),
    "eggplant": (["slate_d", "purple", "magenta", "pink"], "chunk"),
}
JUICE = {
    "carrot": ["rust", "orange", "amber", "yellow"],
    "tomato": ["brown", "red_d", "red", "pink"],
    "strawberry": ["red_d", "red", "pink", "white"],
    "melon": ["green_d", "green", "yellow", "white"],
    "grapes": ["slate_d", "purple", "magenta", "pink"],
    "apple": ["clay", "amber", "yellow", "cream"],
    "orange": ["orange", "amber", "yellow", "cream"],
}


class Canvas:
    def __init__(self, n):
        self.n = n
        self.k = n / 14.0
        self.dither = n > 14
        self.im = new(n, n)
        self.px = self.im.load()

    def on(self, x, y):
        return 0 <= x < self.n and 0 <= y < self.n and self.px[x, y][3] > 0

    def put(self, x, y, cc):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.px[x, y] = E[cc] if isinstance(cc, str) else cc


# ---------------------------------------------------------------- shapes (14-unit space, pixel centres)

def disc(cx, cy, rx, ry=None):
    ry = rx if ry is None else ry
    return lambda u, v: ((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2 <= 1.0


def rect(x0, y0, x1, y1):
    """Unit-space box [x0, x1) x [y0, y1)."""
    return lambda u, v: x0 <= u < x1 and y0 <= v < y1


def poly(pts):
    def f(u, v):
        inside = False
        j = len(pts) - 1
        for i in range(len(pts)):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > v) != (yj > v) and u < (xj - xi) * (v - yi) / (yj - yi) + xi:
                inside = not inside
            j = i
        return inside
    return f


def seg(a, b, w):
    (ax, ay), (bx, by) = a, b
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy or 1e-9

    def f(u, v):
        t = max(0.0, min(1.0, ((u - ax) * dx + (v - ay) * dy) / l2))
        return math.hypot(u - (ax + dx * t), v - (ay + dy * t)) <= w / 2.0
    return f


def sector(ax, ay, r, a0, a1):
    """Pie wedge from apex (ax, ay), radius r, angles a0..a1 in degrees (screen coords)."""
    def f(u, v):
        dx, dy = u - ax, v - ay
        if dx * dx + dy * dy > r * r:
            return False
        return a0 <= math.degrees(math.atan2(dy, dx)) <= a1
    return f


def U(*fs):
    return lambda u, v: any(f(u, v) for f in fs)


def I(*fs):
    return lambda u, v: all(f(u, v) for f in fs)


def D(a, *bs):
    return lambda u, v: a(u, v) and not any(b(u, v) for b in bs)


# ---------------------------------------------------------------- levels (v indexes the ramp)

def tone(ramp, v, x, y, dither):
    top = len(ramp) - 1
    v = max(0.0, min(float(top), v))
    b = int(v)
    f = v - b
    if not dither:
        return ramp[min(top, b + (1 if f >= 0.5 else 0))]
    if b >= top or f < 0.3:
        return ramp[b]
    if f > 0.7:
        return ramp[b + 1]
    return ramp[b + 1] if (x + y) % 2 == 0 else ramp[b]


def ball(cx, cy, rx, ry, N, bias=0.3, shift=0.0):
    """Sphere-like lighting from the top-left: 0 at the dark edge, N at the lit edge."""
    def f(u, v):
        nx, ny = (u - cx) / rx, (v - cy) / ry
        nz = math.sqrt(max(0.0, 1.0 - min(1.0, nx * nx + ny * ny)))
        lv = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        return ((lv + bias) / (1.0 + bias)) * N + shift
    return f


def hgrad(u0, u1, a, b):
    return lambda u, v: a + (b - a) * max(0.0, min(1.0, (u - u0) / (u1 - u0)))


def vgrad(v0, v1, a, b):
    return lambda u, v: a + (b - a) * max(0.0, min(1.0, (v - v0) / (v1 - v0)))


def const(val):
    return lambda u, v: val


# ---------------------------------------------------------------- painting

def cover(c, sf):
    k = c.k
    return {(x, y) for y in range(c.n) for x in range(c.n) if sf((x + 0.5) / k, (y + 0.5) / k)}


def paint(c, cols, sep=None):
    """Write {pixel: colour}. Edges touching pixels painted earlier get the sep colour."""
    for (x, y), cc in cols.items():
        if sep is not None and any((x + dx, y + dy) not in cols and c.on(x + dx, y + dy) for dx, dy in N4):
            cc = sep
        c.put(x, y, cc)


def fill(c, sf, ramp, lv, sep=None):
    k = c.k
    cols = {}
    for (x, y) in cover(c, sf):
        cols[(x, y)] = tone(ramp, lv((x + 0.5) / k, (y + 0.5) / k), x, y, c.dither)
    paint(c, cols, sep)
    return set(cols)


def dot(c, u, v, cc, r=0.45):
    fill(c, disc(u, v, r), [cc], const(0))


def chunk(c, cx, cy, r, ramp):
    """Round piece of food, lit top-left, dark rim where it meets whatever is under it."""
    fill(c, disc(cx, cy, r, r * 0.9), ramp, ball(cx - 0.3, cy - 0.3, r, r * 0.9, len(ramp) - 1, bias=0.5), sep=ramp[0])


def finish_save(sp, name, c):
    out, pad = finish(c.im)
    sp.save(name, out, c.n // 2 + pad, c.n // 2 + pad)


# ---------------------------------------------------------------- sacks (flour, cornmeal)

SACK_BODY = poly([(4.2, 4.2), (9.8, 4.2), (11.8, 6.0), (12.6, 8.8), (12.2, 11.8), (10.4, 13.2),
                  (3.6, 13.2), (1.8, 11.8), (1.4, 8.8), (2.2, 6.0)])
SACK_NECK = poly([(5.2, 1.0), (8.8, 1.0), (9.6, 4.6), (4.4, 4.6)])
SACK_TIE = rect(3.6, 3.6, 10.4, 4.8)


def sack(c, ramp, mark):
    body = U(SACK_BODY, SACK_NECK)
    fill(c, body, ramp, ball(6.6, 8.4, 6.2, 5.6, len(ramp) - 1, bias=0.5, shift=-0.2))
    fill(c, I(seg((6.2, 6.4), (5.4, 9.2), 0.7), SACK_BODY), [ramp[1]], const(0))
    fill(c, I(seg((9.2, 8.6), (10.6, 11.2), 0.7), SACK_BODY), [ramp[1]], const(0))
    fill(c, SACK_TIE, ["brown_d", "brown", "brown_l"], vgrad(3.6, 4.8, 0.8, 2.0), sep="brown_d")
    mark(c)


def flour_mark(c):
    fill(c, rect(4.4, 6.6, 9.6, 10.6), ["white"], const(0), sep="tan_d")
    fill(c, seg((7.0, 7.4), (7.0, 9.6), 0.7), ["brown"], const(0))
    dot(c, 6.2, 7.8, "brown", r=0.4)
    dot(c, 7.8, 7.8, "brown", r=0.4)
    dot(c, 6.2, 8.9, "brown", r=0.4)
    dot(c, 7.8, 8.9, "brown", r=0.4)
    if c.n > 14:
        fill(c, seg((5.4, 10.0), (8.6, 10.0), 0.5), ["brown_l"], const(0))


def cornmeal_mark(c):
    fill(c, rect(4.4, 6.6, 9.6, 10.6), ["cream", "white"], const(1), sep="rust")
    for x, y in ((6.0, 8.0), (7.0, 8.0), (8.0, 8.0), (6.5, 9.2), (7.5, 9.2)):
        dot(c, x, y, "amber" if (x + y) % 2 else "yellow", r=0.5)
    if c.n > 14:
        for x, y in ((5.6, 9.6), (8.6, 9.6), (6.2, 10.2), (7.6, 10.2)):
            dot(c, x, y, "orange", r=0.35)


def it_flour(c):
    sack(c, FLOUR, flour_mark)
    return c


def it_cornmeal(c):
    sack(c, CORNMEAL, cornmeal_mark)
    return c


# ---------------------------------------------------------------- bottles (oil, juices)

def bottle(c, ramp, cap=METAL, label=None):
    """Glass bottle with shoulders and a straight body, full of coloured liquid.
    One function for the oil and every juice. Silhouette: cap, short neck, sloped
    shoulders, rounded-corner body (reads as a bottle, not a flask)."""
    fill(c, rect(5.2, 0.8, 8.8, 2.6), cap, vgrad(0.8, 2.6, 3.0, 0.4), sep="ink")
    neck = rect(6.0, 2.4, 8.0, 4.6)
    shoulder = poly([(6.0, 4.2), (8.0, 4.2), (10.8, 6.2), (3.2, 6.2)])
    body = poly([(3.0, 6.0), (11.0, 6.0), (11.0, 11.8), (10.2, 13.2), (3.8, 13.2), (3.0, 11.8)])
    fill(c, U(neck, shoulder, body), GLASS, hgrad(3.0, 11.0, 2.8, 0.4), sep="ink")
    liquid = poly([(6.4, 4.6), (7.6, 4.6), (9.6, 6.6), (10.0, 6.8), (10.0, 11.8), (9.2, 12.6),
                   (4.8, 12.6), (4.0, 11.8), (4.0, 6.8), (4.4, 6.6)])
    fill(c, liquid, ramp, ball(6.4, 8.6, 3.2, 4.2, len(ramp) - 1, bias=0.4, shift=-0.2))
    fill(c, I(rect(4.0, 7.2, 4.9, 11.2), liquid), ["white"], const(0))
    if label:
        fill(c, rect(4.0, 8.2, 10.0, 11.0), label, const(1), sep="ink")
    return c


def it_cooking_oil(c):
    bottle(c, OIL, cap=CORK, label=["cream", "white"])
    dot(c, 6.5, 9.8, "green", r=0.5)
    dot(c, 7.5, 9.8, "green", r=0.5)
    if c.n > 14:
        fill(c, seg((5.6, 10.6), (8.4, 10.6), 0.5), ["green_d"], const(0))
    return c


def make_juice(name):
    def fn(c):
        bottle(c, JUICE[name], cap=METAL)
        return c
    return fn


# ---------------------------------------------------------------- baked goods

def it_bread(c):
    """Domed loaf: rounded top, straight sides, three diagonal scores."""
    dome = disc(7.0, 6.8, 5.8, 3.4)
    body = poly([(1.0, 6.8), (13.0, 6.8), (13.0, 11.4), (12.2, 12.6), (1.8, 12.6), (1.0, 11.4)])
    loaf = U(dome, body)
    fill(c, loaf, BREAD, ball(6.0, 6.2, 6.4, 5.6, len(BREAD) - 1, bias=0.3, shift=-0.4), sep="brown")
    fill(c, I(rect(0.0, 11.6, 14.0, 12.6), loaf), [BREAD[0]], const(0))
    scores = [((3.2, 8.0), (5.2, 5.6)), ((6.4, 8.2), (8.4, 5.6)), ((9.6, 8.0), (11.2, 5.8))]
    for a, b in scores:
        fill(c, I(seg(a, b, 0.9), loaf), ["brown"], const(0))
        fill(c, I(seg((a[0] + 0.9, a[1] - 0.1), (b[0] + 0.9, b[1] - 0.1), 0.5), loaf), ["amber"], const(0))
    if c.n > 14:
        for x, y in ((2.6, 5.4), (10.6, 9.4), (7.0, 10.2)):
            dot(c, x, y, "cream", r=0.35)
    return c


def it_cornbread(c):
    T, R, B, L = (7.0, 2.4), (13.2, 5.4), (7.0, 8.4), (0.8, 5.4)
    top = poly([T, R, B, L])
    left = poly([L, B, (7.0, 13.6), (0.8, 10.6)])
    right = poly([B, R, (13.2, 10.6), (7.0, 13.6)])
    fill(c, right, CORN_R, const(1))
    fill(c, left, CORN_L, vgrad(5.4, 13.6, 2.2, 1.0), sep="brown")
    topset = fill(c, top, CORN_TOP, vgrad(2.4, 8.4, 2.8, 2.0), sep="rust")
    for (x, y) in topset:
        if (x * 5 + y * 3) % (7 if c.n == 14 else 9) == 0:
            c.put(x, y, "orange")
        if any((x + dx, y + dy) not in topset for dx, dy in N4):
            c.put(x, y, "orange")
    if c.n > 14:
        for x, y in ((3.2, 4.0), (9.6, 6.6), (4.4, 6.8), (8.0, 4.8)):
            dot(c, x, y, "cream", r=0.35)
    return c


def it_roast_veggies(c):
    fill(c, disc(7.0, 8.0, 6.4, 4.5), PLATE_EDGE, const(1))
    fill(c, disc(7.0, 6.8, 6.4, 4.5), PLATE, vgrad(2.3, 11.3, 2.0, 1.0), sep="grey_d")
    fill(c, disc(7.0, 6.6, 4.6, 3.0), ["grey", "grey_l"], const(0.6), sep="grey")
    chunk(c, 5.2, 6.2, 1.5, CARROT)
    chunk(c, 9.2, 5.8, 1.4, POTATO)
    chunk(c, 7.8, 8.6, 1.6, CARROT)
    chunk(c, 10.2, 9.4, 1.3, POTATO)
    chunk(c, 4.8, 9.0, 1.2, POTATO)
    fill(c, seg((6.4, 4.6), (8.2, 3.2), 1.0), ["green"], const(0))
    dot(c, 8.2, 3.2, "green_d", r=0.45)
    return c


def it_strawberry_tart(c):
    """Golden crust ring, pale custard well, strawberry halves on top."""
    fill(c, disc(7.0, 8.2, 6.2, 4.4), ["rust", "clay"], const(1))
    fill(c, disc(7.0, 7.0, 6.2, 4.4), TART_CRUST, ball(5.8, 5.2, 6.4, 4.6, len(TART_CRUST) - 1, bias=0.2), sep="brown")
    fill(c, disc(7.0, 7.0, 5.0, 3.2), CUSTARD, ball(5.8, 5.4, 5.0, 3.2, len(CUSTARD) - 1, bias=0.2, shift=0.4), sep="clay")
    for bx, by, r in ((4.6, 6.6, 1.3), (8.8, 5.8, 1.3), (6.6, 8.4, 1.3), (10.2, 8.0, 1.2)):
        fill(c, disc(bx, by, r, r * 1.1), BERRY, ball(bx - 0.3, by - 0.4, r, r, 2, bias=0.6), sep="red_d")
        if c.n > 14:
            for sx, sy in ((bx - 0.6, by - 0.4), (bx + 0.4, by + 0.5)):
                dot(c, sx, sy, "yellow", r=0.3)
    return c


def it_pumpkin_pie(c):
    ax, ay, r = 3.2, 11.6, 10.6
    a0, a1 = -60.0, 6.0
    t0, t1 = math.radians(a0), math.radians(a1)
    sec = sector(ax, ay, r, a0, a1)

    def rim(u, v):
        if r - math.hypot(u - ax, v - ay) < 2.0:
            return True
        e0 = abs((u - ax) * math.sin(t0) - (v - ay) * math.cos(t0))
        e1 = abs((u - ax) * math.sin(t1) - (v - ay) * math.cos(t1))
        return min(e0, e1) < 1.1
    fill(c, I(sec, rim), PIE_CRUST, ball(6.0, 7.6, 7.0, 6.0, len(PIE_CRUST) - 1, bias=0.3), sep="brown")
    fill(c, I(sec, lambda u, v: not rim(u, v)), PIE_FILL, ball(6.6, 9.4, 6.4, 6.0, len(PIE_FILL) - 1, bias=0.3), sep="rust")
    fill(c, disc(8.8, 8.8, 2.1, 1.9), CREAM, ball(8.4, 8.4, 2.1, 1.9, 2, bias=0.5), sep="tan")
    return c


def it_pancakes(c):
    """Three golden pancakes, each a flat disc with a visible edge; syrup puddle and a butter pat."""
    for cy in (10.2, 7.8, 5.4):
        side = U(disc(7.0, cy + 1.0, 6.0, 2.4), rect(1.0, cy, 13.0, cy + 1.0))
        fill(c, side, PAN_SIDE, const(1))
        fill(c, disc(7.0, cy, 6.0, 2.4), PAN_TOP, ball(6.2, cy - 1.0, 6.0, 2.4, len(PAN_TOP) - 1, bias=0.2, shift=-0.4),
             sep="rust")
        if c.n > 14:
            for x, y in ((3.4, cy - 0.6), (9.8, cy + 0.6), (6.0, cy + 1.2)):
                dot(c, x, y, "clay", r=0.4)
    fill(c, disc(6.6, 5.2, 3.8, 1.0), SYRUP[1:], ball(6.0, 4.8, 3.8, 1.0, len(SYRUP) - 2, bias=0.4), sep="brown_d")
    fill(c, seg((4.2, 5.8), (4.2, 7.4), 0.9), ["rust"], const(0))
    fill(c, seg((9.2, 5.8), (9.2, 6.8), 0.9), ["rust"], const(0))
    fill(c, rect(6.0, 3.2, 8.4, 4.6), BUTTER, vgrad(3.2, 4.6, 1.0, 2.4), sep="amber")
    fill(c, rect(6.0, 4.6, 8.4, 5.2), ["amber"], const(0))
    return c


def it_veggie_stew(c):
    body = I(disc(7.0, 5.6, 6.2, 5.8), lambda u, v: v >= 5.6)
    fill(c, rect(4.6, 10.8, 9.4, 12.6), ["brown"], const(0))
    fill(c, body, BOWL, ball(6.0, 6.4, 6.2, 5.8, len(BOWL) - 1, bias=0.4, shift=-0.3), sep="brown")
    if c.n > 14:
        fill(c, I(body, rect(0.0, 7.6, 14.0, 8.4)), ["red"], const(0))
    rim = disc(7.0, 5.6, 6.4, 2.3)
    soup = disc(7.0, 5.6, 5.1, 1.6)
    fill(c, D(rim, soup), BOWL_RIM, const(2), sep="brown")
    fill(c, soup, SOUP, vgrad(4.0, 7.2, 2.6, 1.0), sep="red_d")
    fill(c, disc(5.2, 5.4, 0.9, 0.7), GREEN_VEG[:2], const(1))
    fill(c, disc(9.0, 5.4, 0.9, 0.7), GREEN_VEG[:2], const(1))
    fill(c, disc(7.0, 6.6, 1.0, 0.8), CARROT[:3], const(1))
    dot(c, 10.4, 6.4, "tan", r=0.6)
    if c.n > 14:
        steam = [(5.0, 2.8), (4.2, 1.8), (5.2, 0.9)], [(8.6, 2.8), (7.8, 1.6), (9.0, 0.6)]
        for pts in steam:
            for a, b in zip(pts, pts[1:]):
                fill(c, seg(a, b, 0.5), ["grey_l"], const(0))
    return c


def it_potato_chips(c):
    fill(c, rect(3.4, 2.2, 10.6, 4.0), FOIL, vgrad(2.2, 4.0, 0.4, 2.6), sep="slate")
    bag = poly([(3.6, 3.6), (10.4, 3.6), (11.0, 12.2), (10.0, 13.2), (4.0, 13.2), (3.0, 12.2)])
    fill(c, bag, CHIP_BAG, hgrad(3.0, 11.0, 3.0, 1.2), sep="rust")
    fill(c, rect(4.4, 6.0, 9.6, 11.0), RED_LABEL, hgrad(4.4, 9.6, 1.0, 1.8), sep="red_d")
    fill(c, disc(7.0, 8.5, 2.3, 2.0), CHIP, ball(6.4, 7.9, 2.3, 2.0, len(CHIP) - 1, bias=0.6), sep="tan")
    dot(c, 6.2, 8.2, "tan", r=0.4)
    dot(c, 8.0, 9.4, "tan", r=0.4)
    if c.n > 14:
        fill(c, seg((4.8, 4.8), (5.8, 6.4), 0.5), ["white"], const(0))
        fill(c, seg((8.4, 8.2), (9.0, 8.8), 0.4), ["tan_d"], const(0))
    return c


def it_ratatouille(c):
    fill(c, disc(7.0, 8.2, 6.4, 4.6), DISH_SIDE, const(0))
    fill(c, disc(7.0, 6.8, 6.4, 4.6), DISH, ball(6.2, 5.4, 6.4, 4.6, len(DISH) - 1, bias=0.4), sep="brown")
    fill(c, disc(7.0, 6.8, 4.9, 3.1), ["brown", "clay"], const(0), sep="brown")
    slices = ((4.6, 5.4, PURPLE_VEG), (7.2, 5.0, RED_VEG), (9.8, 5.6, GREEN_VEG),
              (3.6, 7.9, RED_VEG), (6.0, 7.6, GREEN_VEG), (8.6, 7.8, PURPLE_VEG), (11.0, 7.6, YELLOW_VEG),
              (5.0, 10.0, YELLOW_VEG), (6.8, 9.6, GREEN_VEG))
    for cx, cy, ramp in slices:
        fill(c, disc(cx, cy, 1.3), ramp, ball(cx - 0.2, cy - 0.3, 1.3, 1.3, len(ramp) - 1, bias=0.5), sep=ramp[0])
    return c


# ---------------------------------------------------------------- jams and pickles (jars)

def jam_jar(c, ramp, spots):
    """Wide jar with fruit-coloured jam and a cloth cover tied with string."""
    cloth = poly([(2.2, 2.2), (4.0, 0.8), (7.0, 0.4), (10.0, 0.8), (11.8, 2.2), (11.2, 3.6), (9.8, 3.2),
                  (8.4, 4.0), (7.0, 3.4), (5.6, 4.0), (4.2, 3.2), (2.8, 3.6)])
    fill(c, cloth, CLOTH, vgrad(0.4, 4.0, 2.8, 1.4), sep="tan_d")
    if c.n > 14:
        fill(c, I(cloth, lambda u, v: (int(u * 2) + int(v * 2)) % 3 == 0), ["tan"], const(0))
    fill(c, rect(4.4, 4.0, 9.6, 5.0), ["brown_d", "brown", "brown_l"], vgrad(4.0, 5.0, 0.8, 1.8), sep="brown_d")
    jar = poly([(2.4, 5.0), (11.6, 5.0), (11.6, 12.0), (10.8, 13.2), (3.2, 13.2), (2.4, 12.0)])
    fill(c, jar, GLASS, hgrad(2.4, 11.6, 2.6, 0.6), sep="ink")
    jam = poly([(3.2, 5.8), (10.8, 5.8), (10.8, 12.0), (10.0, 12.8), (4.0, 12.8), (3.2, 12.0)])
    top = len(ramp) - 1
    fill(c, jam, ramp, lambda u, v: top - 0.9 * (v - 5.8) / 7.0 + 0.4 * (1 - (u - 3.2) / 7.6) - 0.4)
    fill(c, I(jam, rect(3.2, 5.8, 10.8, 6.6)), [ramp[-1]], const(0))
    for x, y, cc in spots:
        dot(c, x, y, cc)
    fill(c, I(rect(2.4, 6.4, 3.2, 11.6), jar), ["white"], const(0))
    return c


def pickle_jar(c, veg, shape="chunk"):
    """Tall jar: brine with the vegetable colour packed inside, screw lid."""
    fill(c, rect(3.2, 1.2, 10.8, 4.4), METAL, vgrad(1.2, 4.4, 3.0, 0.2), sep="ink")
    if c.n > 14:
        for y in (2.0, 2.8, 3.6):
            fill(c, I(rect(3.2, y - 0.2, 10.8, y + 0.2), rect(3.2, 1.2, 10.8, 4.4)), ["slate"], const(0))
    jar = poly([(2.4, 4.6), (11.6, 4.6), (11.6, 12.0), (10.8, 13.2), (3.2, 13.2), (2.4, 12.0)])
    fill(c, jar, GLASS, hgrad(2.4, 11.6, 2.6, 0.6), sep="ink")
    inner = poly([(3.2, 5.4), (10.8, 5.4), (10.8, 12.0), (10.0, 12.8), (4.0, 12.8), (3.2, 12.0)])
    fill(c, inner, ["grey_l", "white"], const(1))
    fill(c, I(rect(3.2, 5.4, 10.8, 6.2), inner), ["white"], const(0))
    if shape == "spear":
        sticks = U(seg((4.6, 5.8), (6.6, 12.4), 1.5), seg((7.8, 5.8), (9.8, 12.2), 1.5))
        fill(c, I(sticks, inner), veg, hgrad(4.0, 10.0, 1.0, float(len(veg) - 1)), sep=veg[0])
    else:
        # Staggered rounds packed in rows: reads as jarred vegetables, not a face.
        for cx, cy, r in ((4.6, 6.6, 1.25), (7.0, 6.6, 1.25), (9.4, 6.6, 1.25), (5.8, 9.0, 1.2), (8.2, 9.0, 1.2),
                          (4.6, 11.4, 1.25), (7.0, 11.4, 1.25), (9.4, 11.4, 1.25)):
            chunk(c, cx, cy, r, veg)
    fill(c, I(rect(2.4, 5.2, 3.2, 11.6), jar), ["white"], const(0))
    return c


def make_pickle(name):
    veg, shape = PICKLE[name]

    def fn(c):
        return pickle_jar(c, veg, shape)
    return fn


# ---------------------------------------------------------------- registry

ITEMS = {
    "flour": it_flour,
    "cornmeal": it_cornmeal,
    "cooking_oil": it_cooking_oil,
    "bread": it_bread,
    "cornbread": it_cornbread,
    "roast_veggies": it_roast_veggies,
    "strawberry_tart": it_strawberry_tart,
    "pumpkin_pie": it_pumpkin_pie,
    "pancakes": it_pancakes,
    "veggie_stew": it_veggie_stew,
    "potato_chips": it_potato_chips,
    "ratatouille": it_ratatouille,
    "strawberry_jam": lambda c: jam_jar(c, JAM_RED, [(5.5, 8.5, "yellow"), (8.5, 9.5, "yellow"), (6.5, 11.5, "yellow"), (9.5, 11.5, "yellow")]),
    "melon_jam": lambda c: jam_jar(c, JAM_GREEN, [(6.5, 8.5, "yellow"), (9.5, 8.5, "yellow"), (7.5, 10.5, "yellow"), (5.5, 11.5, "yellow")]),
    "grapes_jam": lambda c: jam_jar(c, JAM_GRAPE, [(5.5, 8.5, "magenta"), (9.5, 9.5, "magenta"), (6.5, 11.5, "pink"), (9.5, 11.5, "magenta")]),
    "cherry_jam": lambda c: jam_jar(c, JAM_CHERRY, [(5.5, 8.5, "pink"), (8.5, 9.5, "red"), (6.5, 11.5, "pink"), (9.5, 11.5, "red")]),
    "peach_jam": lambda c: jam_jar(c, JAM_PEACH, [(5.5, 8.5, "pink"), (8.5, 9.5, "yellow"), (6.5, 11.5, "pink"), (9.5, 11.5, "yellow")]),
}
for _name in PICKLE:
    ITEMS[f"{_name}_pickles"] = make_pickle(_name)
for _name in JUICE:
    ITEMS[f"{_name}_juice"] = make_juice(_name)


def main():
    art = sys.argv[1]
    small, big = Sprites(art, "items"), Sprites(art, "items_big")
    for name, fn in ITEMS.items():
        for sp, n in ((small, 14), (big, 28)):
            c = Canvas(n)
            fn(c)
            finish_save(sp, name, c)
    print(json.dumps({**small.pivots, **big.pivots}))


main()
