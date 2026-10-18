"""Forest goods and seeds (item icons), pixel-direct.

items/<id>      body 14x14: chunky shapes that read at 1x.
items_big/<id> body 28x28: the same subjects drawn at native 28px resolution with
                dithered shading and extra detail (not an upscale).
Pivot = centre of the body (+ the finish() pad). Endesga-32 only, light from the
top-left, finished with finish() (1px ink outline + hard drop shadow).

Coordinates in the helpers below are in 14-grid units; a Canvas of 28 px scales them
by 2, so one shape definition draws both sizes.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E402
from _pix import E, CLEAR, Sprites, new, finish  # noqa: E402
import json  # noqa: E402
import math  # noqa: E402
import random  # noqa: E402

_L = (-0.55, -0.7, 0.6)
LIGHT = tuple(v / math.sqrt(sum(u * u for u in _L)) for v in _L)
LD = (-0.7071, -0.7071)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
DITHER = [True]

# Ramps run dark -> light. Names are Endesga-32 keys from _pix.E.
HONEY = ["rust", "orange", "amber", "yellow", "cream"]
WOOD = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
BURLAP = ["brown", "brown_l", "tan_d", "tan", "cream"]
LEAF = ["teal_dd", "green_dd", "green_d", "green", "yellow"]
DEAD = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
SYRUP = ["brown_d", "brown", "rust", "clay", "amber"]
CAP_GREY = ["slate_d", "slate", "grey_d", "grey", "grey_l"]
RED_LEAF = ["red_d", "red", "red", "pink"]
PAPER = ["tan_d", "tan", "cream", "white"]
SHROOM = ["brown_d", "brown", "brown_l", "tan", "cream"]
STEM_PALE = ["tan_d", "tan", "cream", "white"]
ORANGE_SH = ["red_d", "rust", "orange", "amber", "yellow"]
TEAL_GLOW = ["teal_dd", "blue_d", "blue", "cyan", "white"]
GLOW_STEM = ["blue_d", "blue", "cyan", "cyan", "white"]
RED = ["purple", "red_d", "red", "pink", "white"]
BERRY = ["ink", "slate_d", "purple", "magenta", "pink"]
CONE = ["brown_d", "brown", "brown_l", "clay", "tan"]
WING = ["tan_d", "tan", "cream", "white"]
ACORN_CAP = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
ACORN_NUT = ["brown", "clay", "tan", "cream", "white"]
GOLD = ["brown", "clay", "amber", "yellow", "white"]
MAPLE_WING = ["brown", "rust", "clay", "tan", "cream"]
SEED_DARK = ["ink", "brown_d", "brown", "clay"]
EGG = ["tan_d", "tan", "cream", "white"]
EGG_BLUE = ["slate", "blue_d", "cyan", "white"]
NEST = ["brown_d", "brown", "brown_l", "tan"]
NEST_IN = ["ink", "brown_d", "brown_d", "brown"]
AMBER_GEM = ["rust", "orange", "amber", "amber", "yellow"]
PETRI_RINGS = ["grey_l", "tan", "grey", "pink", "tan_d", "grey"]
BRONZE = ["brown", "rust", "clay", "tan", "cream"]
FEATHER_W = ["slate", "grey", "grey_l", "white", "white"]
GOLD_FEATHER = ["rust", "orange", "amber", "yellow", "white"]
MULCH = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
QUALITY = ["slate_d", "blue_d", "blue", "cyan", "white"]
BLUE_BG = ["blue_d", "blue", "blue", "cyan"]


def col(c):
    return E[c] if isinstance(c, str) else c


def tone(ramp, v, x, y):
    """Ramp colour for level v. Dithered at 28px, rounded at 14px so 1x stays crisp."""
    n = len(ramp) - 1
    v = max(0.0, min(float(n), v))
    b = int(v)
    f = v - b
    if not DITHER[0]:
        return ramp[min(n, b + (1 if f >= 0.5 else 0))]
    if b >= n or f < 0.3:
        return ramp[b]
    if f > 0.7:
        return ramp[b + 1]
    return ramp[b + 1] if (x + y) % 2 == 0 else ramp[b]


class Canvas:
    def __init__(self, n):
        self.n = n
        self.k = n / 14
        self.im = new(n, n)
        self.px = self.im.load()

    def on(self, x, y):
        return 0 <= x < self.n and 0 <= y < self.n and self.px[x, y][3] > 0

    def put(self, x, y, c):
        if c is not None and 0 <= x < self.n and 0 <= y < self.n:
            self.px[x, y] = col(c)

    def dot(self, x, y, c):
        """One 14-grid cell (1 px at 14, 2x2 at 28)."""
        k = int(round(self.k))
        for dx in range(k):
            for dy in range(k):
                self.put(int(x * self.k) + dx, int(y * self.k) + dy, c)


# ---------------------------------------------------------------- shapes (14-grid units)

def disc(c, cx, cy, rx, ry=None):
    ry = rx if ry is None else ry
    k = c.k
    cx, cy, rx, ry = cx * k, cy * k, rx * k, ry * k
    return {(x, y) for y in range(c.n) for x in range(c.n)
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0}


def rot_ellipse(c, cx, cy, a, b, angle):
    k = c.k
    cx, cy, a, b = cx * k, cy * k, a * k, b * k
    t = math.radians(angle)
    ct, st = math.cos(t), math.sin(t)
    out = set()
    for y in range(c.n):
        for x in range(c.n):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            u = px * ct + py * st
            v = -px * st + py * ct
            if (u / a) ** 2 + (v / b) ** 2 <= 1.0:
                out.add((x, y))
    return out


def rrect(c, x0, y0, x1, y1, r=1.0):
    k = c.k
    x0, y0, x1, y1, r = x0 * k, y0 * k, x1 * k, y1 * k, r * k
    s = set()
    for y in range(c.n):
        for x in range(c.n):
            px, py = x + 0.5, y + 0.5
            if not (x0 <= px <= x1 and y0 <= py <= y1):
                continue
            qx = min(max(px, x0 + r), x1 - r)
            qy = min(max(py, y0 + r), y1 - r)
            if (px - qx) ** 2 + (py - qy) ** 2 <= r * r + 1e-9:
                s.add((x, y))
    return s


def pip(px, py, pts):
    inside = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def poly(c, pts):
    P = [(x * c.k, y * c.k) for x, y in pts]
    return {(x, y) for y in range(c.n) for x in range(c.n) if pip(x + 0.5, y + 0.5, P)}


def star_pts(cx, cy, R, r, rot=-90.0):
    out = []
    for i in range(10):
        a = math.radians(rot + i * 36.0)
        rr = R if i % 2 == 0 else r
        out.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return out


def seg_d(px, py, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    L2 = dx * dx + dy * dy or 1e-9
    t = max(0.0, min(1.0, ((px - a[0]) * dx + (py - a[1]) * dy) / L2))
    return math.hypot(px - (a[0] + dx * t), py - (a[1] + dy * t))


def stroke(c, a, b, w=1.2):
    """Thick line segment, width w in 14-grid units (at least ~1px at 14)."""
    k = c.k
    A, B, r = (a[0] * k, a[1] * k), (b[0] * k, b[1] * k), w * k / 2
    return {(x, y) for y in range(c.n) for x in range(c.n) if seg_d(x + 0.5, y + 0.5, A, B) <= r}


def keep_above(s, c, yl):
    return {p for p in s if p[1] + 0.5 <= yl * c.k}


def keep_below(s, c, yt):
    return {p for p in s if p[1] + 0.5 >= yt * c.k}


def leaf_map(c, base, tip, hw, bend=0.0):
    """Lens-shaped leaf or feather vane: {pixel: (t, a)}, t 0..1 along it, a -1..1 across."""
    k = c.k
    bx, by = base[0] * k, base[1] * k
    tx, ty = tip[0] * k, tip[1] * k
    hw = hw * k
    L = math.hypot(tx - bx, ty - by) or 1e-9
    ux, uy = (tx - bx) / L, (ty - by) / L
    vx, vy = -uy, ux
    out = {}
    for y in range(c.n):
        for x in range(c.n):
            px, py = x + 0.5 - bx, y + 0.5 - by
            t = (px * ux + py * uy) / L
            if not 0.0 <= t <= 1.0:
                continue
            across = px * vx + py * vy - bend * L * math.sin(math.pi * t)
            w = hw * math.sin(math.pi * t) ** 0.6
            if abs(across) <= w:
                out[(x, y)] = (t, across / max(w, 1e-6))
    return out


# ---------------------------------------------------------------- painting

def paint(c, cols, sep=None):
    """Paint {pixel: colour}; edges that touch earlier (behind) pixels get the sep colour."""
    for (x, y), cc in cols.items():
        if sep is not None and any((x + dx, y + dy) not in cols and c.on(x + dx, y + dy) for dx, dy in N4):
            cc = sep
        c.put(x, y, cc)


def dist_in(s):
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


def shade(c, s, ramp, depth=2.0, off=0.0):
    """Soft dome shading for any pixel set, lit from the top-left. depth in 14-grid units."""
    depth = depth * c.k
    dist = dist_in(s)
    hgt = {p: depth * math.sqrt(max(0.0, 1 - (1 - min(1.0, (d - 0.5) / depth)) ** 2)) for p, d in dist.items()}
    n = len(ramp) - 1
    cols = {}
    for (x, y) in s:
        gx = (hgt.get((x + 1, y), 0) - hgt.get((x - 1, y), 0)) / 2
        gy = (hgt.get((x, y + 1), 0) - hgt.get((x, y - 1), 0)) / 2
        nl = math.sqrt(gx * gx + gy * gy + 1)
        lv = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / nl
        cols[(x, y)] = tone(ramp, ((lv + 0.25) / 1.25) * n + off, x, y)
    return cols


def spot(c, s, cols, spec):
    """Highlight dots given as (x, y, colour) in 14-grid units, kept inside the shape."""
    k = int(round(c.k))
    for x, y, color in spec:
        for dx in range(k):
            for dy in range(k):
                p = (int(x * c.k) + dx, int(y * c.k) + dy)
                if p in s:
                    cols[p] = color


def dome(c, s, ramp, depth=2.0, off=0.0, sep=None, spec=()):
    cols = shade(c, s, ramp, depth, off)
    spot(c, s, cols, spec)
    paint(c, cols, sep)
    return s


def ball(c, cx, cy, rx, ry, ramp, off=0.0, sep=None, spec=()):
    s = disc(c, cx, cy, rx, ry)
    n = len(ramp) - 1
    cols = {}
    for (x, y) in s:
        nx = (x + 0.5 - cx * c.k) / (rx * c.k)
        ny = (y + 0.5 - cy * c.k) / (ry * c.k)
        nz = math.sqrt(max(0.0, 1 - min(1.0, nx * nx + ny * ny)))
        lv = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        cols[(x, y)] = tone(ramp, ((lv + 0.3) / 1.3) * n + off, x, y)
    spot(c, s, cols, spec)
    paint(c, cols, sep)
    return s


def leaf(c, base, tip, hw, ramp, bend=0.0, sep=None, vein=True):
    lm = leaf_map(c, base, tip, hw, bend)
    ux, uy = tip[0] - base[0], tip[1] - base[1]
    L = math.hypot(ux, uy) or 1.0
    vx, vy = -uy / L, ux / L
    side = 1.0 if vx * LD[0] + vy * LD[1] > 0 else -1.0
    n = len(ramp) - 1
    cols = {}
    for (x, y), (t, a) in lm.items():
        v = n * 0.5 + 0.9 * a * side - 0.4 * (t - 0.5)
        if vein and abs(a) < 0.16 and 0.2 < t < 0.85:
            v -= 1.0
        cols[(x, y)] = tone(ramp, v, x, y)
    paint(c, cols, sep)
    return set(lm)


def sparkle(c, x, y, core="white", arm="cyan"):
    c.dot(x, y, core)
    for dx, dy in N4:
        c.dot(x + dx, y + dy, arm)


# ---------------------------------------------------------------- items

def it_honey(c):
    lid = rrect(c, 3.0, 1.0, 11.0, 4.0, 0.9)
    dome(c, lid, WOOD, depth=1.2, off=0.6, sep="brown_d")
    body = rrect(c, 1.8, 4.0, 12.2, 13.2, 2.6)
    dome(c, body, HONEY, depth=2.6, off=-0.2, sep="rust")
    paint(c, {p: "amber" for p in stroke(c, (8.6, 3.6), (8.6, 5.8), 1.2)})
    label = rrect(c, 4.0, 6.4, 10.0, 10.6, 0.8)
    dome(c, label, PAPER, depth=1.0, off=0.6, sep="tan_d")
    paint(c, {p: "tan" for p in stroke(c, (4.6, 8.5), (9.4, 8.5), 1.0)})
    paint(c, {p: "white" for p in stroke(c, (3.0, 6.0), (3.0, 10.0), 1.0)})


def it_maple_syrup(c):
    cap = rrect(c, 4.2, 0.6, 9.8, 3.0, 0.8)
    dome(c, cap, CAP_GREY, depth=1.0, off=0.4, sep="ink")
    neck = rrect(c, 5.4, 2.6, 8.6, 4.8, 0.6)
    bottle = rrect(c, 2.2, 5.4, 11.8, 13.4, 2.8) | poly(c, [(5.2, 4.4), (8.8, 4.4), (11.6, 6.0), (2.4, 6.0)]) | neck
    dome(c, bottle, SYRUP, depth=2.6, off=0.0, sep="brown_d")
    label = rrect(c, 4.2, 7.4, 9.8, 11.4, 0.6)
    dome(c, label, PAPER, depth=0.8, off=0.6, sep="tan_d")
    leaf(c, (7.0, 10.6), (7.0, 7.6), 1.4, RED_LEAF, sep="red_d", vein=False)
    paint(c, {p: "white" for p in stroke(c, (3.2, 7.0), (3.2, 11.0), 1.0)})


def it_wild_mushroom(c):
    stem = rrect(c, 6.4, 6.0, 10.4, 12.8, 1.0)
    dome(c, stem, STEM_PALE, depth=1.6, off=0.0, sep="tan_d")
    cap = keep_above(disc(c, 8.4, 6.2, 4.8, 3.6), c, 6.8)
    dome(c, cap, SHROOM, depth=2.4, off=0.0, sep="brown_d")
    for x, y in ((6.2, 3.8), (9.6, 3.6), (11.2, 5.4)):
        c.dot(x, y, "tan")
    if c.k > 1:
        for x, y in ((5.4, 5.0), (8.2, 3.2), (12.4, 6.2)):
            c.dot(x, y, "cream")
    small_stem = rrect(c, 2.4, 9.2, 4.4, 12.8, 0.8)
    dome(c, small_stem, STEM_PALE, depth=1.2, off=0.0, sep="tan_d")
    small = keep_above(disc(c, 3.4, 9.4, 2.8, 2.2), c, 9.8)
    dome(c, small, ["tan_d", "tan", "cream", "white"], depth=1.6, off=0.0, sep="brown")


def it_chanterelle(c):
    stem = rrect(c, 5.2, 7.4, 8.8, 13.2, 1.0)
    dome(c, stem, STEM_PALE, depth=1.4, off=0.2, sep="tan_d")
    # funnel cap: wavy wide rim tapering into the stem
    cap = poly(c, [(1.2, 4.4), (3.0, 5.0), (4.6, 4.2), (6.4, 5.0), (8.0, 4.2), (9.6, 5.0),
                   (11.2, 4.2), (12.8, 4.4), (10.4, 8.4), (3.6, 8.4)])
    dome(c, cap, ORANGE_SH, depth=2.6, off=-0.2, sep="rust")
    for x in (5.0, 7.0, 9.0):
        paint(c, {p: "rust" for p in stroke(c, (x, 6.0), (x - 0.5, 8.0), 1.0)})
    if c.k > 1:
        for x in (2.6, 11.2):
            paint(c, {p: "yellow" for p in stroke(c, (x, 4.6), (x + 0.2, 5.6), 0.8)})


def it_glowcap(c):
    stem = rrect(c, 5.2, 6.0, 8.8, 13.0, 1.0)
    dome(c, stem, GLOW_STEM, depth=1.4, off=0.4, sep="blue_d")
    cap = keep_above(disc(c, 7.0, 6.4, 5.4, 4.0), c, 6.8)
    dome(c, cap, TEAL_GLOW, depth=2.4, off=0.0, sep="teal_dd")
    for x, y in ((4.4, 4.0), (9.2, 3.6), (7.0, 5.6)):
        c.dot(x, y, "white")
    for x, y in ((6.0, 2.6), (10.4, 6.0), (4.0, 6.6)):
        c.dot(x, y, "cyan")
    sparkle(c, 1.4, 9.4)
    sparkle(c, 12.2, 2.4)


def it_apple(c):
    ball(c, 7.0, 8.4, 5.6, 5.0, RED, sep="red_d", spec=[(4.0, 6.0, "white"), (4.0, 7.4, "pink")])
    paint(c, {p: "brown" for p in stroke(c, (7.2, 3.6), (7.8, 1.2), 1.1)})
    leaf(c, (7.8, 2.6), (12.4, 0.6), 1.5, LEAF, sep="green_dd")


def it_blackberry(c):
    pts = [(4.4, 4.8), (9.6, 4.8), (3.2, 8.0), (7.0, 7.6), (10.8, 8.0), (5.4, 11.0), (8.6, 11.0)]
    for x, y in pts:
        ball(c, x, y, 2.3, 2.3, BERRY, sep="ink", spec=[(x - 1.0, y - 1.0, "pink")])
    leaf(c, (7.0, 3.4), (4.0, 1.2), 1.2, LEAF, sep="green_dd")
    leaf(c, (7.0, 3.4), (10.0, 1.2), 1.2, LEAF, sep="green_dd")
    paint(c, {p: "green_d" for p in stroke(c, (7.0, 3.4), (7.0, 1.6), 0.9)})


def it_apple_sapling(c):
    trunk = stroke(c, (7.0, 10.0), (7.0, 4.4), 1.4)
    paint(c, {p: "brown" for p in trunk}, sep="brown_d")
    leaf(c, (7.0, 6.0), (2.0, 2.6), 2.0, LEAF, bend=0.1, sep="green_dd")
    leaf(c, (7.0, 5.4), (12.2, 2.0), 2.0, LEAF, bend=-0.1, sep="green_dd")
    leaf(c, (7.0, 4.4), (7.0, 0.8), 1.4, LEAF, sep="green_dd")
    bundle = rrect(c, 3.2, 9.4, 10.8, 13.4, 1.8)
    dome(c, bundle, BURLAP, depth=2.0, off=0.2, sep="brown")
    paint(c, {p: "tan_d" for p in stroke(c, (3.4, 10.2), (10.6, 10.2), 0.9)})


def it_pinecone(c):
    cone = disc(c, 7.0, 7.6, 3.9, 5.4)
    cols = shade(c, cone, CONE, depth=2.2, off=0.0)
    for (x, y) in cone:
        if (x + y) % 3 == 0:
            i = CONE.index(cols[(x, y)])
            cols[(x, y)] = CONE[max(0, i - 1)]
    paint(c, cols, sep="brown_d")
    paint(c, {p: "brown_d" for p in stroke(c, (7.2, 3.4), (7.8, 1.2), 1.1)})
    c.dot(7.6, 1.0, "green_d")


def it_birch_seed(c):
    wing = rot_ellipse(c, 8.6, 5.8, 4.6, 3.0, -40)
    dome(c, wing, WING, depth=1.4, off=0.3, sep="grey")
    paint(c, {p: "tan_d" for p in stroke(c, (5.4, 8.6), (11.4, 3.2), 0.8)})
    ball(c, 4.2, 10.2, 2.3, 2.2, SEED_DARK, spec=[(3.4, 9.2, "tan")])


def acorn_body(c, nut_ramp, cap_ramp, tip=True):
    nut = disc(c, 7.0, 9.6, 3.7, 3.5)
    if tip:
        nut = nut | poly(c, [(5.0, 10.0), (9.0, 10.0), (7.8, 13.4), (6.2, 13.4)])
    dome(c, nut, nut_ramp, depth=2.2, off=0.2, sep="brown")
    cap = keep_above(disc(c, 7.0, 5.4, 4.5, 2.6), c, 7.0)
    cols = shade(c, cap, cap_ramp, depth=1.4, off=0.4)
    if c.k > 1:
        for (x, y) in cap:
            if (x + y) % 4 == 0 and cols[(x, y)] != cap_ramp[0]:
                cols[(x, y)] = cap_ramp[0]
    else:
        for (x, y) in cap:
            if (x + y) % 2 == 0 and cols[(x, y)] != cap_ramp[0]:
                cols[(x, y)] = cap_ramp[max(0, cap_ramp.index(cols[(x, y)]) - 1)]
    paint(c, cols, sep="brown_d")
    dome(c, rrect(c, 6.4, 0.6, 7.8, 3.2, 0.5), ["ink", "brown_d", "brown", "brown_l"], depth=0.8, off=0.4)


def it_acorn(c):
    acorn_body(c, ACORN_NUT, ACORN_CAP)


def it_golden_acorn(c):
    acorn_body(c, GOLD, GOLD, tip=True)
    sparkle(c, 2.4, 3.0)
    if c.k > 1:
        c.dot(10.8, 6.8, "white")


def it_maple_seed(c):
    leaf(c, (6.6, 10.6), (1.0, 1.8), 2.8, MAPLE_WING, bend=0.1, sep="brown_d")
    leaf(c, (7.4, 10.6), (13.0, 1.8), 2.8, MAPLE_WING, bend=-0.1, sep="brown_d")
    ball(c, 7.0, 11.0, 2.1, 2.1, SEED_DARK, spec=[(6.2, 10.2, "tan")])


def it_wildflower_seed(c):
    body = rrect(c, 2.4, 1.2, 11.6, 13.0, 0.6)
    dome(c, body, PAPER, depth=1.0, off=0.4, sep="tan_d")
    crimp = {}
    for x in range(3, 12):
        crimp[(int(x * c.k), int(1.4 * c.k))] = "tan" if x % 2 else CLEAR
    for p, cc in crimp.items():
        c.put(p[0], p[1], cc)
    band = rrect(c, 3.4, 4.0, 10.6, 10.6, 0.3)
    dome(c, band, BLUE_BG, depth=0.8, off=0.4)
    for x, y in ((6.0, 6.0), (8.0, 6.0), (7.0, 5.0), (7.0, 7.0)):
        ball(c, x, y, 1.0, 1.0, ["magenta", "pink", "pink", "white"])
    c.dot(7.0, 6.0, "yellow")
    paint(c, {p: "green_d" for p in stroke(c, (7.0, 7.8), (7.0, 9.8), 0.9)})
    leaf(c, (7.0, 9.2), (9.6, 8.0), 1.0, LEAF, sep="green_dd")
    paint(c, {p: "tan_d" for p in stroke(c, (4.4, 11.6), (9.6, 11.6), 0.9)})


def it_bird_nest(c):
    bowl = disc(c, 7.0, 9.6, 6.0, 3.6)
    dome(c, bowl, NEST, depth=1.6, off=0.4, sep="brown_d")
    inner = disc(c, 7.0, 8.6, 4.2, 1.6)
    dome(c, inner, NEST_IN, depth=1.0, off=0.0)
    paint(c, {p: "tan" for p in stroke(c, (1.8, 9.0), (4.2, 8.0), 0.9)})
    paint(c, {p: "tan" for p in stroke(c, (10.0, 8.2), (12.4, 9.2), 0.9)})
    ball(c, 4.6, 6.8, 1.8, 2.0, EGG, sep="tan_d", spec=[(4.0, 5.8, "white")])
    ball(c, 9.4, 6.6, 1.8, 2.0, EGG_BLUE, sep="slate", spec=[(8.8, 5.6, "white")])
    c.dot(4.6, 7.4, "tan")
    ball(c, 7.0, 6.2, 1.6, 1.8, EGG, sep="tan_d", spec=[(6.4, 5.4, "white")])
    c.dot(7.0, 6.6, "tan")


def it_amber(c):
    gem = disc(c, 7.0, 7.2, 5.4, 5.2)
    dome(c, gem, AMBER_GEM, depth=3.0, off=-0.5, sep="rust")
    paint(c, {p: "orange" for p in stroke(c, (3.6, 9.6), (9.0, 4.6), 0.9)})
    paint(c, {p: "ink" for p in stroke(c, (7.0, 7.6), (9.0, 7.6), 1.0)})
    c.dot(6.4, 8.2, "ink")
    c.dot(9.6, 8.2, "ink")
    c.dot(3.8, 4.8, "white")


def it_petrified_wood(c):
    k = c.k
    log = disc(c, 7.0, 7.0, 6.2, 5.8)
    cols = {}
    for (x, y) in log:
        ux, uy = (x + 0.5) / k - 7.0, (y + 0.5) / k - 7.0
        th = math.atan2(uy, ux)
        # wobbly growth rings so it reads as stone, not a target
        d = math.hypot(ux / 6.2, uy / 5.8) + 0.05 * math.sin(3 * th + 1.0) + 0.03 * math.sin(7 * th)
        if d > 0.92:
            cols[(x, y)] = "slate_d"
        elif d > 0.84:
            cols[(x, y)] = "slate"
        else:
            ring = int((0.84 - d) * 4.6)
            cols[(x, y)] = PETRI_RINGS[ring % len(PETRI_RINGS)]
    paint(c, cols, sep="slate_d")
    paint(c, {p: "slate_d" for p in stroke(c, (6.2, 6.4), (10.2, 8.6), 0.8)})
    paint(c, {p: "slate_d" for p in stroke(c, (4.0, 8.4), (5.4, 10.8), 0.8)})
    c.dot(3.6, 4.4, "cyan")
    c.dot(9.8, 4.0, "white")
    c.dot(10.4, 10.2, "cream")


def it_old_coin(c):
    rim = disc(c, 7.0, 7.0, 6.2, 6.2)
    face = disc(c, 7.0, 7.0, 4.9, 4.9)
    rim = rim - face
    dome(c, rim, BRONZE, depth=1.4, off=0.6, sep="brown_d")
    dome(c, face, BRONZE[:4], depth=2.0, off=0.6, sep="rust")
    paint(c, {p: "brown_d" for p in rrect(c, 6.0, 6.0, 8.0, 8.0, 0.0)})
    paint(c, {p: "tan" for p in stroke(c, (3.6, 4.4), (5.4, 3.4), 0.8)})
    if c.k > 1:
        # reeded milled edge on the rim
        for i in range(12):
            a = i * math.pi / 6
            x, y = 7.0 + 5.6 * math.cos(a), 7.0 + 5.6 * math.sin(a)
            paint(c, {p: "clay" for p in stroke(c, (x, y), (x + 0.6 * math.cos(a), y + 0.6 * math.sin(a)), 0.8)})


def feather(c, ramp, quill, hw=3.0, base=(3.0, 12.8), tip=(12.0, 1.4)):
    lm = leaf_map(c, base, tip, hw, bend=-0.05)
    n = len(ramp) - 1
    cols = {}
    for (x, y), (t, a) in lm.items():
        v = n * 0.55 + (0.9 if a < 0 else -0.6) + 0.4 * (1 - t) - 0.3
        if c.k > 1 and (x + y) % 3 == 0 and abs(a) > 0.35:
            v -= 0.7
        cols[(x, y)] = tone(ramp, v, x, y)
    paint(c, cols, sep="slate_d")
    spine = {p for p in lm if abs(lm[p][1]) < 0.14}
    paint(c, {p: quill for p in spine})


def it_feather(c):
    feather(c, FEATHER_W, "tan")


def it_golden_feather(c):
    feather(c, GOLD_FEATHER, "cream", hw=3.6)
    if c.k > 1:
        c.dot(8.2, 5.0, "white")


def it_mulch(c):
    k = c.k
    heap = disc(c, 7.0, 9.8, 6.2, 3.4) | disc(c, 7.0, 7.4, 4.6, 3.0)
    cols = shade(c, heap, MULCH, depth=2.2, off=0.0)
    paint(c, cols, sep="brown_d")
    rng = random.Random(3)
    cells = sorted(p for p in heap if p[1] < 10 * k)
    length = int(2 * k)
    for _ in range(int(7 * k * k)):
        x, y = rng.choice(cells)
        chip = rng.choice(["tan", "cream", "tan"])
        run = [(x + i, y) for i in range(length) if (x + i, y) in heap]
        if len(run) < length:
            continue
        for p in run:
            c.put(p[0], p[1], chip)
        # dark underside of each chip sliver
        for p in run:
            if (p[0], p[1] + 1) in heap:
                c.put(p[0], p[1] + 1, "brown_d")
    for _ in range(int(3 * k * k)):
        x, y = rng.choice(cells)
        c.put(x, y, "ink")


def sack(c, ramp, tie, sep_col):
    body = rrect(c, 2.2, 5.2, 11.8, 13.4, 2.8)
    top = poly(c, [(4.8, 2.2), (9.2, 2.2), (11.6, 5.6), (2.4, 5.6)])
    shape = body | top
    dome(c, shape, ramp, depth=2.6, off=0.0, sep=sep_col)
    paint(c, {p: tie for p in stroke(c, (2.6, 5.0), (11.4, 5.0), 1.0)})
    paint(c, {p: sep_col for p in stroke(c, (6.8, 2.6), (6.6, 4.4), 0.8)})


def it_basic_fertilizer(c):
    sack(c, BURLAP, "tan", "brown_d")
    paint(c, {p: "green_d" for p in stroke(c, (7.0, 11.4), (7.0, 8.4), 1.0)})
    leaf(c, (7.0, 8.8), (3.6, 6.4), 1.8, LEAF, sep="green_dd")
    leaf(c, (7.0, 8.4), (10.4, 6.2), 1.8, LEAF, bend=0.1, sep="green_dd")


def star_set(c, cx, cy, R, r):
    return poly(c, star_pts(cx, cy, R, r))


def it_quality_fertilizer(c):
    sack(c, QUALITY, "amber", "slate_d")
    star = star_set(c, 7.0, 8.8, 3.6, 1.6)
    dome(c, star, GOLD, depth=0.9, off=0.6, sep="rust")


def it_withered_crop(c):
    stalk = stroke(c, (7.0, 13.0), (6.6, 6.0), 1.6) | stroke(c, (6.6, 6.0), (7.2, 1.8), 1.3)
    dome(c, stalk, DEAD, depth=1.0, off=0.4, sep="brown_d")
    leaf(c, (6.8, 9.6), (1.2, 11.8), 1.6, DEAD, bend=0.12, sep="brown_d")
    leaf(c, (6.8, 6.8), (12.6, 8.8), 1.5, DEAD, bend=-0.12, sep="brown_d")
    leaf(c, (7.0, 4.6), (2.6, 2.2), 1.3, DEAD, bend=0.08, sep="brown_d")
    leaf(c, (7.0, 4.2), (11.6, 2.4), 1.2, DEAD, bend=-0.08, sep="brown_d")
    ball(c, 7.2, 1.6, 1.2, 1.2, DEAD, sep="brown_d")


def it_plant_scraps(c):
    mound = disc(c, 7.0, 10.0, 6.2, 3.4) | disc(c, 7.0, 7.8, 4.4, 2.8)
    dome(c, mound, LEAF, depth=2.0, off=-0.6, sep="teal_dd")
    paint(c, {p: "brown" for p in stroke(c, (6.6, 7.0), (6.2, 4.8), 0.9)})
    leaf(c, (3.2, 9.2), (0.8, 6.0), 1.5, LEAF, bend=0.1, sep="teal_dd")
    leaf(c, (10.6, 9.0), (13.2, 5.8), 1.5, LEAF, bend=-0.1, sep="teal_dd")
    leaf(c, (7.4, 7.6), (9.8, 2.4), 1.4, LEAF, bend=-0.15, sep="teal_dd")
    leaf(c, (5.6, 7.4), (3.6, 2.8), 1.3, LEAF, bend=0.1, sep="teal_dd")


ITEMS = {
    "honey": it_honey,
    "maple_syrup": it_maple_syrup,
    "wild_mushroom": it_wild_mushroom,
    "chanterelle": it_chanterelle,
    "glowcap": it_glowcap,
    "apple": it_apple,
    "blackberry": it_blackberry,
    "apple_sapling": it_apple_sapling,
    "pinecone": it_pinecone,
    "birch_seed": it_birch_seed,
    "acorn": it_acorn,
    "maple_seed": it_maple_seed,
    "golden_acorn": it_golden_acorn,
    "wildflower_seed": it_wildflower_seed,
    "bird_nest": it_bird_nest,
    "amber": it_amber,
    "petrified_wood": it_petrified_wood,
    "old_coin": it_old_coin,
    "golden_feather": it_golden_feather,
    "feather": it_feather,
    "mulch": it_mulch,
    "basic_fertilizer": it_basic_fertilizer,
    "quality_fertilizer": it_quality_fertilizer,
    "withered_crop": it_withered_crop,
    "plant_scraps": it_plant_scraps,
}


def main():
    art = sys.argv[1]
    small, big = Sprites(art, "items"), Sprites(art, "items_big")
    for name, fn in ITEMS.items():
        for sp, n in ((small, 14), (big, 28)):
            DITHER[0] = n > 14
            c = Canvas(n)
            fn(c)
            out, pad = finish(c.im)
            sp.save(name, out, n // 2 + pad, n // 2 + pad)
    print(json.dumps({**small.pivots, **big.pivots}))


main()
