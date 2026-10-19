"""Skill tree icons and tonic items (pixel-direct).

icons/skill_<id>, icons_big/skill_<id>   five branch badges (id = branch_*) and twenty skills
items/<id>, items_big/<id>               four tonics: growth_tonic, lucky_charm, energy_brew, rain_totem

Every drawing is written once in 14-unit space. A pixel is sampled at its centre
((x + 0.5) / k, (y + 0.5) / k) with k = n / 14, so the 28px sprite is the same drawing with
extra detail (glints, grain, vein lines), not an upscale. The skill icons use hard colour
steps at both sizes (no checker dither since art round 4); the tonic items keep the 28px
checker dither they were polished with. Endesga-32 only, lit from the top-left, finished with
finish() (1px ink outline + hard drop shadow) like the rest of the art. Pivot = body centre
(+ pad).

Branch colours: green thumb greens, woodsman browns, miner greys and blues, trader golds,
tinkerer oranges. Each skill takes the colour of its branch.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, Sprites, finish, new  # noqa: E402
import json  # noqa: E402

N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
_L = (-0.55, -0.7, 0.6)
_LN = math.sqrt(sum(c * c for c in _L))
LIGHT = tuple(c / _LN for c in _L)

# Ramps run dark to light; a level v picks ramp[round or dithered v].
GREEN = ["green_dd", "green_d", "green", "yellow"]
GREEN_LT = ["green_d", "green", "yellow", "white"]
TEAL_LEAF = ["green", "yellow", "white"]
BROWN = ["brown_d", "brown", "brown_l", "tan"]
TANW = ["brown", "brown_l", "tan", "cream"]
CREAM = ["tan", "cream", "white"]
STEEL = ["slate", "grey_d", "grey", "grey_l", "white"]
STONE = ["slate", "grey_d", "grey", "grey_l", "white"]
BLUE = ["blue_d", "blue", "cyan", "white"]
WAVE = ["blue", "cyan", "white"]
GOLD = ["brown", "clay", "amber", "yellow", "white"]
GOLD_D = ["rust", "clay", "amber", "yellow"]
ORANGE = ["red_d", "rust", "orange", "amber", "yellow"]
COPPER = ["brown", "rust", "clay", "orange", "amber"]
SKIN = ["tan_d", "tan", "skin", "cream"]
CLOUD = ["slate", "grey_d", "grey", "grey_l", "white"]
COAL = ["ink", "slate_d", "slate", "grey_d", "grey"]
SUN = ["amber", "yellow", "white"]
RED_FRUIT = ["purple", "red_d", "red", "pink"]


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


class Canvas:
    def __init__(self, n, dither=None):
        self.n = n
        self.k = n / 14.0
        self.dither = n > 14 if dither is None else dither
        self.im = new(n, n)
        self.px = self.im.load()

    def on(self, x, y):
        return 0 <= x < self.n and 0 <= y < self.n and self.px[x, y][3] > 0

    def put(self, x, y, cc):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.px[x, y] = E[cc] if isinstance(cc, str) else cc


# ---------------------------------------------------------------- shapes (14-unit space)

def disc(cx, cy, rx, ry=None):
    ry = rx if ry is None else ry
    return lambda u, v: ((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2 <= 1.0


def rect(x0, y0, x1, y1):
    """Half-open range [x0, x1) x [y0, y1) in 14-unit space."""
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


def U(*fs):
    return lambda u, v: any(f(u, v) for f in fs)


def D(a, *bs):
    return lambda u, v: a(u, v) and not any(b(u, v) for b in bs)


def I(*fs):
    return lambda u, v: all(f(u, v) for f in fs)


def arc(pts, w):
    return U(*[seg(p, q, w) for p, q in zip(pts, pts[1:])])


# ---------------------------------------------------------------- levels

def ball(cx, cy, rx, ry=None, N=4, bias=0.3, shift=0.0):
    """Sphere-like lighting from the top-left: 0 at the dark edge, N at the lit edge."""
    ry = rx if ry is None else ry

    def f(u, v):
        nx, ny = (u - cx) / rx, (v - cy) / ry
        nz = math.sqrt(max(0.0, 1.0 - min(1.0, nx * nx + ny * ny)))
        lv = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        return ((lv + bias) / (1.0 + bias)) * N + shift
    return f


def const(val):
    return lambda u, v: val


def hgrad(u0, u1, a, b):
    return lambda u, v: a + (b - a) * max(0.0, min(1.0, (u - u0) / (u1 - u0)))


def vgrad(v0, v1, a, b):
    return lambda u, v: a + (b - a) * max(0.0, min(1.0, (v - v0) / (v1 - v0)))


# ---------------------------------------------------------------- painting

def cover(c, sf):
    k = c.k
    return {(x, y) for y in range(c.n) for x in range(c.n) if sf((x + 0.5) / k, (y + 0.5) / k)}


def paint(c, cols, sep=None):
    """Write {pixel: colour}. Edges touching pixels already on the canvas get the sep colour."""
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


def dot(c, u, v, cc, r=0.5):
    fill(c, disc(u, v, r), [cc], const(0))


def tooth(cx, cy, a, r0, r1, w0, w1):
    ux, uy = math.cos(a), math.sin(a)
    tx, ty = -uy, ux
    bx, by = cx + ux * r0, cy + uy * r0
    qx, qy = cx + ux * r1, cy + uy * r1
    return poly([(bx + tx * w0, by + ty * w0), (qx + tx * w1, qy + ty * w1),
                 (qx - tx * w1, qy - ty * w1), (bx - tx * w0, by - ty * w0)])


# ---------------------------------------------------------------- compound shapes

def sprout(c, bx, by, h, s=1.0, ramp=GREEN, stem=("green_d", "green"), sep="green_dd"):
    """Seedling: a stem rising from (bx, by) with two diamond leaves at the top."""
    fill(c, seg((bx, by), (bx, by - h), 1.0 * s), list(stem), const(1.0), sep=sep)
    top = by - h
    leaf(c, bx, top + 0.3, -155, 3.2 * s, 1.4 * s, ramp, sep)
    leaf(c, bx, top + 0.3, -25, 3.6 * s, 1.5 * s, ramp, sep)


def leaf(c, ox, oy, ang, L, W, ramp, sep=None):
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    px_, py_ = -uy, ux
    pts = [(ox, oy),
           (ox + ux * L * 0.45 + px_ * W, oy + uy * L * 0.45 + py_ * W),
           (ox + ux * L, oy + uy * L),
           (ox + ux * L * 0.45 - px_ * W, oy + uy * L * 0.45 - py_ * W)]
    s = poly(pts)
    fill(c, s, ramp, ball(ox + ux * L * 0.5, oy + uy * L * 0.5, L * 0.5, max(W, 0.8),
                          N=len(ramp) - 1, bias=0.3), sep=sep)
    if c.n > 14:
        vein = seg((ox + ux * L * 0.15, oy + uy * L * 0.15), (ox + ux * L * 0.8, oy + uy * L * 0.8), 0.5)
        fill(c, I(vein, s), [ramp[0]], const(0))
    return s


def gear(c, cx, cy, R, rt, n_teeth, ramp, sep, hole=None):
    teeth = [tooth(cx, cy, k * 2 * math.pi / n_teeth + 0.2, R - 1.0, rt, 1.5, 1.0) for k in range(n_teeth)]
    body = U(disc(cx, cy, R), *teeth)
    fill(c, body, ramp, ball(cx - 1.0, cy - 1.0, R + 1.5, R + 1.5, N=len(ramp) - 1, bias=0.4), sep=sep)
    if hole:
        fill(c, disc(cx, cy, hole), ["ink", "brown_d"], const(0.6), sep="ink")
    return body


def bolt(c, ox, oy, sc, ramp=SUN, sep=None):
    pts = [(ox + x * sc, oy + y * sc) for x, y in
           [(8.0, 0.0), (12.4, 0.0), (8.8, 5.0), (12.6, 5.0), (4.6, 14.0), (6.3, 7.9), (2.4, 7.9)]]
    s = poly(pts)
    fill(c, s, ramp, vgrad(oy, oy + 14 * sc, len(ramp) - 1.0, 0.0), sep=sep)
    return s


def wave(c, cx, cy, r, w, ramp, sep=None):
    def f(u, v):
        d = math.hypot(u - cx, v - cy)
        a = math.atan2(v - cy, u - cx)
        return abs(d - r) <= w / 2.0 and abs(a) <= 0.95
    fill(c, f, ramp, const(1), sep=sep)


def crate(c, x0, y0, x1, y1, ramp=GOLD_D):
    body = rect(x0, y0, x1, y1)
    fill(c, body, ramp, vgrad(y0, y1, len(ramp) - 1.4, 0.4), sep="brown")
    mid = (y0 + y1) / 2.0
    fill(c, I(rect(x0 + 0.5, mid - 0.4, x1 - 0.5, mid + 0.4), body), ["brown"], const(0))
    if c.n > 14:
        for u, v in ((x0 + 1.2, y0 + 1.2), (x1 - 1.2, y0 + 1.2), (x0 + 1.2, y1 - 1.2), (x1 - 1.2, y1 - 1.2)):
            dot(c, u, v, "brown_d", r=0.45)


def coin(c, cx, cy, r, ramp=GOLD_D, sep="brown"):
    fill(c, disc(cx, cy, r), ramp, ball(cx - 0.8, cy - 0.8, r + 0.5, N=len(ramp) - 1, bias=0.3), sep=sep)
    fill(c, D(disc(cx, cy, r * 0.72), disc(cx, cy, r * 0.56)), [ramp[0]], const(0))


def sparkle(c, cx, cy, R, r=0.8, cc="white"):
    pts = []
    for i in range(8):
        a = math.radians(-90 + i * 45)
        rr = R if i % 2 == 0 else r
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    fill(c, poly(pts), [cc], const(0))


def star(c, cx, cy, R, r, ramp, sep):
    pts = []
    for i in range(10):
        a = math.radians(-90 + i * 36)
        rr = R if i % 2 == 0 else r
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    s = poly(pts)
    fill(c, s, ramp, ball(cx - 1.0, cy - 1.2, R, R, N=len(ramp) - 1, bias=0.5), sep=sep)
    return s


def heart_shape(cx, cy):
    """Heart filling the 14-unit canvas: two lobes with a clear top notch and a pointed tip."""
    return U(disc(cx - 2.9, cy - 1.6, 3.0, 2.9), disc(cx + 2.9, cy - 1.6, 3.0, 2.9),
             poly([(cx - 5.7, cy - 1.0), (cx + 5.7, cy - 1.0), (cx, cy + 5.6)]))


def gem(c, cx, cy, w, h, ramp, sep):
    top, mid, bot = cy - h * 0.5, cy - h * 0.1, cy + h * 0.5
    shape = poly([(cx - w * 0.5, mid), (cx - w * 0.3, top), (cx + w * 0.3, top), (cx + w * 0.5, mid),
                  (cx, bot)])
    fill(c, shape, ramp, lambda u, v: (len(ramp) - 1.0) - (v - top) / (bot - top) * (len(ramp) - 1.0)
         + (0.7 if u > cx else 0.0), sep=sep)
    return shape


# ---------------------------------------------------------------- branch badges

def br_green_thumb(c):
    heart = heart_shape(7.0, 6.8)
    fill(c, heart, GREEN, ball(5.2, 4.4, 6.0, 5.4, N=3, bias=0.35), sep="green_dd")
    sprout(c, 7.0, 11.4, 5.4, s=1.1, ramp=["yellow", "white"], stem=("yellow", "white"), sep=None)
    if c.n > 14:
        dot(c, 3.8, 4.0, "yellow", r=0.45)
    return c


def br_woodsman(c):
    log_body = rect(0.8, 8.4, 10.6, 12.8)
    fill(c, log_body, ["brown_d", "brown", "brown_l"], vgrad(8.4, 12.8, 2.6, 0.4), sep="brown_d")
    end = disc(10.6, 10.6, 2.2, 2.4)
    fill(c, end, ["brown", "brown_l", "tan", "cream"], ball(10.2, 10.0, 2.4, 2.5, N=3, bias=0.4), sep="brown_d")
    if c.n > 14:
        fill(c, D(disc(10.6, 10.6, 1.3, 1.4), disc(10.6, 10.6, 0.5, 0.55)), ["tan"], const(0))
    fill(c, seg((2.2, 11.8), (8.6, 5.4), 1.3), ["tan_d", "tan", "cream"], vgrad(5.4, 11.8, 2.2, 0.6), sep="brown_d")
    head = poly([(7.4, 5.6), (7.9, 1.6), (10.8, 1.0), (13.0, 3.2), (11.4, 6.2), (9.0, 6.6)])
    fill(c, head, STEEL, hgrad(7.4, 13.0, 3.4, 1.4), sep="slate_d")
    dot(c, 11.4, 2.6, "white", r=0.5)
    return c


def br_miner(c):
    shaft = seg((7.0, 2.2), (4.6, 12.6), 1.3)
    fill(c, shaft, BROWN, vgrad(2.2, 12.6, 2.4, 0.6), sep="brown_d")
    head = arc([(1.0, 5.4), (3.2, 2.2), (7.0, 1.2), (10.8, 2.2), (13.0, 5.4)], 1.6)
    fill(c, head, STEEL, vgrad(1.2, 5.4, 3.4, 1.0), sep="slate_d")
    gem(c, 10.2, 10.4, 6.4, 5.2, BLUE, sep="blue_d")
    dot(c, 8.8, 9.0, "white", r=0.5)
    return c


def br_trader(c):
    pouch = U(disc(7.0, 8.8, 4.7, 4.0), poly([(5.2, 4.6), (8.8, 4.6), (10.2, 6.8), (3.8, 6.8)]))
    fill(c, pouch, ["clay", "amber", "yellow", "white"], ball(5.8, 7.2, 5.2, 4.6, N=3, bias=0.3), sep="brown")
    fill(c, rect(4.4, 3.6, 9.6, 5.0), ["brown_d", "brown"], const(0.6), sep="brown_d")
    coin(c, 7.0, 9.4, 2.0, ramp=["rust", "clay", "amber"], sep="brown")
    coin(c, 11.0, 2.4, 1.7, ramp=["clay", "amber", "yellow"], sep="brown")
    return c


def br_tinkerer(c):
    gear(c, 6.6, 6.6, 4.8, 6.6, 8, ORANGE, "red_d", hole=1.6)
    dark_cu = ["ink", "brown_d", "brown", "rust", "clay"]
    rod = seg((2.6, 11.6), (9.0, 5.2), 1.5)
    fill(c, rod, dark_cu, vgrad(5.2, 11.6, 3.4, 1.0), sep="ink")
    head = D(disc(10.4, 3.8, 2.4), disc(12.0, 2.2, 1.1))
    fill(c, head, dark_cu, ball(10.0, 3.2, 2.6, 2.6, N=4, bias=0.3), sep="ink")
    fill(c, disc(2.6, 11.8, 1.5), dark_cu, ball(2.2, 11.2, 1.6, 1.6, N=4, bias=0.3), sep="ink")
    return c


# ---------------------------------------------------------------- green thumb skills

def ic_quick_sprout(c):
    for x0, x1, v in ((0.4, 3.6, 4.2), (0.2, 3.0, 6.8), (0.8, 3.6, 9.4)):
        fill(c, seg((x0, v), (x1, v), 0.9), ["grey_l", "white"], const(1.0))
    fill(c, disc(9.4, 12.6, 3.6, 1.3), BROWN, vgrad(11.3, 13.5, 2.0, 0.4), sep="brown_d")
    sprout(c, 9.4, 11.6, 6.4, s=1.1, ramp=GREEN_LT, stem=("green_d", "green"), sep="green_dd")
    return c


def ic_bountiful(c):
    mound = disc(7.0, 6.6, 5.2, 3.6)
    fill(c, mound, GREEN, ball(6.0, 4.6, 5.6, 4.0, N=3, bias=0.3), sep="green_dd")
    leaf(c, 4.0, 5.6, -150, 3.6, 1.3, GREEN_LT, sep="green_dd")
    leaf(c, 10.0, 5.4, -30, 3.8, 1.3, GREEN_LT, sep="green_dd")
    dot(c, 4.6, 7.4, "red", r=0.9)
    dot(c, 9.8, 7.8, "orange", r=0.8)
    basket = poly([(1.8, 7.8), (12.2, 7.8), (10.8, 12.8), (3.2, 12.8)])
    fill(c, basket, BROWN, vgrad(7.8, 12.8, 2.6, 0.4), sep="brown_d")
    fill(c, rect(1.2, 7.2, 12.8, 8.8), TANW, vgrad(7.2, 8.8, 2.6, 2.0), sep="brown_d")
    fill(c, I(rect(2.0, 10.2, 12.0, 10.9), basket), ["brown_d"], const(0))
    return c


def ic_star_grower(c):
    leaf(c, 1.4, 12.6, -52, 8.4, 2.6, GREEN, sep="green_dd")
    star(c, 9.4, 5.2, 4.4, 2.0, GOLD, sep="brown")
    dot(c, 8.0, 3.6, "white", r=0.5)
    return c


def ic_deep_roots(c):
    fill(c, disc(7.0, 7.6, 6.2, 2.0), BROWN, vgrad(6.0, 9.6, 2.0, 0.6), sep="brown_d")
    roots = [((7.0, 8.2), (3.0, 13.2)), ((7.0, 8.2), (10.8, 13.2)), ((7.0, 8.4), (7.0, 13.6)),
             ((4.6, 10.4), (1.2, 11.6)), ((9.4, 10.6), (12.8, 11.8))]
    for a, b in roots:
        fill(c, seg(a, b, 0.9), ["tan", "cream"], const(1.0), sep="brown_d")
    sprout(c, 7.0, 7.4, 4.6, s=1.0, ramp=GREEN, stem=("green_d", "green"), sep="green_dd")
    return c


def ic_seed_saver(c):
    stem = rect(5.6, 1.0, 6.8, 3.4)
    fill(c, stem, ["ink", "brown_d"], const(1.0), sep="ink")
    nut = disc(5.8, 8.8, 3.6, 3.8)
    fill(c, nut, TANW, ball(5.0, 8.0, 3.6, 3.6, N=3, bias=0.4), sep="brown_d")
    cap = disc(5.8, 5.4, 3.9, 2.0)
    fill(c, cap, BROWN, ball(5.2, 4.6, 3.9, 2.2, N=3, bias=0.3), sep="brown_d")
    badge = disc(11.2, 3.4, 2.7)
    fill(c, badge, GREEN, ball(10.4, 2.6, 2.7, 2.7, N=3, bias=0.3), sep="green_dd")
    fill(c, U(rect(10.4, 1.6, 12.0, 5.6), rect(8.8, 2.8, 13.6, 4.4)), ["white"], const(0))
    return c


# ---------------------------------------------------------------- woodsman skills

def ic_sharp_edge(c):
    fill(c, seg((4.8, 13.2), (8.2, 6.6), 1.6), BROWN, vgrad(6.6, 13.2, 2.2, 0.6), sep="brown_d")
    head = poly([(6.6, 6.6), (7.2, 1.6), (10.6, 0.8), (13.0, 2.8), (12.2, 6.2), (8.8, 7.4)])
    fill(c, head, STEEL, hgrad(7.2, 13.0, 3.6, 1.6), sep="slate_d")
    fill(c, I(seg((9.4, 1.6), (12.2, 2.0), 0.6), head), ["white"], const(0))
    if c.n > 14:
        sparkle(c, 12.2, 1.4, 1.5, 0.5)
    else:
        dot(c, 12.2, 1.6, "white")
    return c


def ic_timber(c):
    trunk = seg((2.0, 12.6), (8.8, 4.6), 3.2)
    fill(c, trunk, BROWN, lambda u, v: 2.2 - 0.8 * max(0.0, min(1.0, (v - 4.6) / 8.0)), sep="brown_d")
    fill(c, disc(9.8, 3.6, 2.8, 2.4), GREEN, ball(9.0, 2.8, 2.9, 2.5, N=3, bias=0.3), sep="green_dd")
    fill(c, disc(12.0, 6.0, 1.7, 1.6), GREEN, ball(11.6, 5.6, 1.8, 1.7, N=3, bias=0.3), sep="green_dd")
    fill(c, rect(0.4, 13.0, 9.0, 13.8), BROWN, const(0.8))
    for a, b in (((10.6, 9.2), (12.6, 9.2)), ((11.0, 11.6), (13.0, 11.6))):
        fill(c, seg(a, b, 0.8), ["grey", "grey_l"], const(1.0))
    return c


def ic_light_swing(c):
    fill(c, seg((2.6, 13.0), (6.6, 6.2), 1.4), BROWN, vgrad(6.2, 13.0, 2.2, 0.6), sep="brown_d")
    head = poly([(5.0, 6.4), (6.0, 2.8), (8.4, 2.6), (9.0, 5.8), (7.2, 7.4)])
    fill(c, head, STEEL, hgrad(5.0, 9.0, 3.4, 1.2), sep="slate_d")
    leaf(c, 7.6, 5.0, -48, 6.4, 2.6, CREAM, sep="tan_d")
    fill(c, seg((7.6, 5.0), (11.2, 1.2), 0.8), ["tan_d", "brown_l"], const(1.0))
    return c


# ---------------------------------------------------------------- miner skills

def ic_prospector(c):
    nug = U(disc(6.0, 8.6, 5.2, 3.9), disc(4.6, 6.2, 3.0, 2.8), disc(9.0, 7.6, 2.6, 2.9))
    fill(c, nug, STONE, ball(5.2, 6.0, 5.8, 5.0, N=4, bias=0.25), sep="slate_d")
    fill(c, disc(4.2, 8.6, 1.1, 1.0), GOLD_D, const(2.2), sep="brown")
    fill(c, disc(8.2, 9.6, 0.9, 0.8), GOLD_D, const(2.6), sep="brown")
    sparkle(c, 11.6, 2.4, 2.4, 0.9)
    return c


def ic_ladder_sense(c):
    rails = [rect(1.2, 0.6, 2.8, 13.4), rect(6.4, 0.6, 8.0, 13.4)]
    for r in rails:
        fill(c, r, STEEL, hgrad(1.2, 8.0, 3.2, 1.2), sep="slate_d")
    for y in (2.2, 5.4, 8.6, 11.8):
        fill(c, rect(2.8, y, 6.4, y + 1.2), ["grey_d", "grey", "grey_l"], vgrad(y, y + 1.2, 0.8, 2.0), sep="slate_d")
    wave(c, 9.2, 7.0, 2.6, 1.0, WAVE, sep=None)
    wave(c, 9.2, 7.0, 4.8, 1.0, WAVE, sep=None)
    return c


def ic_geologist(c):
    shell = disc(7.0, 7.4, 6.0, 5.6)
    inner = I(disc(8.4, 6.4, 3.8, 3.4), shell)
    fill(c, shell, STONE, ball(6.0, 5.8, 6.6, 6.2, N=4, bias=0.25), sep="slate_d")
    fill(c, inner, BLUE, ball(8.0, 5.4, 3.8, 3.4, N=3, bias=0.35), sep="blue_d")
    crack = arc([(1.8, 11.2), (4.6, 8.6), (6.2, 10.2), (9.0, 7.2), (12.2, 3.0)], 0.9)
    fill(c, I(crack, shell), ["ink"], const(0))
    dot(c, 9.8, 4.4, "white", r=0.5)
    return c


def ic_steady_hands(c):
    beam = rect(0.6, 5.2, 13.4, 8.4)
    fill(c, beam, STEEL, vgrad(5.2, 8.4, 3.6, 1.0), sep="slate_d")
    fill(c, I(rect(0.6, 5.2, 13.4, 5.9), beam), ["white"], const(0))
    for cx in (3.6, 10.4):
        hand = disc(cx, 8.8, 2.3, 2.5)
        fill(c, hand, SKIN, ball(cx - 0.6, 8.2, 2.4, 2.6, N=3, bias=0.3), sep="tan_d")
        if c.n > 14:
            fill(c, I(seg((cx - 1.4, 10.4), (cx + 1.2, 10.4), 0.5), hand), ["tan_d"], const(0))
    return c


# ---------------------------------------------------------------- trader skills

def ic_haggler(c):
    coin(c, 7.0, 7.2, 6.4, ramp=GOLD_D, sep="brown")
    arrow = U(poly([(7.0, 2.6), (10.4, 6.4), (3.6, 6.4)]), rect(5.8, 6.2, 8.2, 10.8))
    fill(c, arrow, ["cream", "white"], const(1.0), sep="brown")
    return c


def ic_bulk_buyer(c):
    crate(c, 0.8, 8.2, 7.0, 13.2)
    crate(c, 7.2, 8.2, 13.2, 13.2)
    crate(c, 3.4, 2.8, 10.6, 7.8)
    return c


def ic_charmer(c):
    h = heart_shape(7.0, 6.8)
    fill(c, h, GOLD, ball(5.4, 4.6, 6.0, 5.4, N=4, bias=0.35), sep="brown")
    for u in (5.0, 9.0):
        fill(c, disc(u, 5.4, 0.75), ["brown_d"], const(0))
    fill(c, arc([(4.6, 7.6), (7.0, 9.4), (9.4, 7.6)], 0.9), ["brown_d"], const(0))
    dot(c, 3.8, 4.0, "white", r=0.5)
    return c


def ic_merchants_pal(c):
    """Two gold sleeves rising diagonally to meet in a clasp, thumb on top."""
    fill(c, seg((0.6, 12.8), (5.0, 8.4), 3.2), GOLD_D, vgrad(8.4, 12.8, 2.6, 0.6), sep="brown")
    fill(c, seg((13.4, 12.8), (9.0, 8.4), 3.2), GOLD_D, vgrad(8.4, 12.8, 2.6, 0.6), sep="brown")
    hands = disc(7.0, 6.4, 3.6, 2.8)
    fill(c, hands, SKIN, ball(5.8, 5.6, 3.8, 3.0, N=3, bias=0.3), sep="tan_d")
    fill(c, disc(6.0, 4.6, 1.3, 0.9), SKIN, ball(5.6, 4.2, 1.4, 1.0, N=3, bias=0.4), sep="tan_d")
    for a, b in (((4.8, 8.0), (6.6, 8.6)), ((7.0, 8.6), (8.8, 8.0))):
        fill(c, I(seg(a, b, 0.7), hands), ["tan_d"], const(0))
    return c


# ---------------------------------------------------------------- tinkerer skills

def ic_overclock(c):
    gear(c, 7.0, 7.0, 6.0, 6.6, 8, ORANGE, "red_d")
    bolt(c, 1.15, 1.5, 0.78, ramp=["amber", "yellow", "white"], sep="red_d")
    return c


def ic_fuel_saver(c):
    lump = U(disc(6.0, 5.8, 4.8, 4.0), disc(10.2, 3.6, 2.2, 2.0))
    fill(c, lump, COAL, ball(5.2, 4.8, 5.4, 4.6, N=4, bias=0.3), sep="ink")
    dot(c, 3.8, 3.4, "grey", r=0.5)
    arrow = U(rect(9.4, 5.2, 11.6, 9.4), poly([(7.6, 8.6), (13.4, 8.6), (10.5, 13.2)]))
    fill(c, arrow, ORANGE, vgrad(5.2, 13.2, 3.4, 1.4), sep="red_d")
    return c


def ic_extra_slot(c):
    box = rect(0.8, 0.8, 13.2, 13.2)
    fill(c, box, ["rust", "clay", "orange"], vgrad(0.8, 13.2, 2.4, 1.0), sep="red_d")
    fill(c, rect(2.2, 2.2, 11.8, 11.8), ["clay", "orange", "amber"], vgrad(2.2, 11.8, 0.8, 2.0), sep="rust")
    plus = U(rect(6.0, 3.6, 8.0, 10.4), rect(3.6, 6.0, 10.4, 8.0))
    fill(c, plus, ["amber", "yellow", "white"], vgrad(3.6, 10.4, 1.0, 2.0), sep="rust")
    return c


def ic_big_trays(c):
    tray = rect(0.6, 8.2, 13.4, 10.8)
    fill(c, tray, COPPER, vgrad(8.2, 10.8, 3.2, 1.0), sep="rust")
    fill(c, rect(1.4, 10.8, 12.6, 12.0), ["red_d", "rust"], const(0.6), sep="red_d")
    for cx, cy, r, ramp in ((3.0, 6.0, 2.2, RED_FRUIT), (7.0, 5.0, 2.4, GREEN), (11.0, 6.2, 2.2, ["rust", "orange", "amber"])):
        fill(c, disc(cx, cy, r), ramp, ball(cx - 0.6, cy - 0.6, r + 0.2, N=len(ramp) - 1, bias=0.3), sep="ink")
    dot(c, 2.4, 5.2, "white", r=0.45)
    return c


# ---------------------------------------------------------------- tonics (items)

def it_growth_tonic(c):
    cork = rect(5.2, 0.4, 8.8, 2.0)
    fill(c, cork, ["brown_d", "brown", "tan"], vgrad(0.4, 2.0, 0.6, 2.0), sep="ink")
    body = U(disc(7.0, 9.4, 5.0, 4.2), rect(5.8, 2.0, 8.2, 5.4))
    fill(c, body, ["green_dd", "green_d", "green"], ball(5.6, 7.6, 5.4, 4.8, N=2, bias=0.35), sep="green_dd")
    leaf(c, 7.0, 10.8, -90, 4.6, 1.6, ["yellow", "white"], sep="green_dd")
    fill(c, I(seg((3.6, 7.0), (3.8, 10.2), 0.7), body), ["white"], const(0))
    return c


def it_lucky_charm(c):
    fill(c, D(disc(7.0, 1.0, 1.7, 1.4), disc(7.0, 1.0, 0.8, 0.6)), GOLD_D, const(2.0), sep="brown")
    cy = 7.6
    clover = U(disc(7.0, cy - 2.9, 1.9), disc(7.0, cy + 2.9, 1.9),
               disc(7.0 - 2.9, cy, 1.9), disc(7.0 + 2.9, cy, 1.9), disc(7.0, cy, 2.4))
    fill(c, clover, GREEN, ball(5.6, cy - 2.2, 5.2, 5.0, N=3, bias=0.35), sep="green_dd")
    fill(c, seg((7.2, cy + 3.8), (7.9, 13.4), 0.9), ["green_d", "green"], const(1.0), sep="green_dd")
    dot(c, 6.4, cy - 0.6, "yellow", r=0.6)
    return c


def it_energy_brew(c):
    mug = rect(2.4, 4.8, 9.6, 12.8)
    fill(c, mug, CREAM, vgrad(4.8, 12.8, 3.0, 1.0), sep="brown_d")
    handle = D(disc(10.2, 8.6, 2.5, 2.3), disc(10.2, 8.6, 1.3, 1.1))
    fill(c, handle, CREAM, const(1.0), sep="brown_d")
    fill(c, disc(6.0, 4.9, 3.6, 1.2), ["brown", "clay", "amber"], const(1.8), sep="brown_d")
    bolt(c, 2.7, 6.0, 0.5, ramp=SUN, sep="brown_d")
    fill(c, arc([(4.0, 3.6), (2.8, 2.0), (4.4, 0.4)], 0.8), ["grey_l", "white"], const(1.0))
    fill(c, arc([(8.2, 3.4), (7.0, 1.8), (8.6, 0.4)], 0.8), ["grey_l", "white"], const(1.0))
    return c


def it_rain_totem(c):
    cloud = U(disc(4.6, 2.8, 2.4, 2.0), disc(8.2, 2.0, 2.8, 2.4), disc(10.8, 3.4, 2.1, 1.8), rect(2.4, 2.6, 12.4, 4.4))
    fill(c, cloud, CLOUD, ball(6.0, 1.4, 5.8, 3.2, N=4, bias=0.3), sep="slate_d")
    body = rect(4.6, 4.8, 9.4, 13.4)
    fill(c, body, BROWN, hgrad(4.6, 9.4, 2.8, 0.4), sep="brown_d")
    for u, v in ((6.0, 7.0), (8.0, 7.0)):
        fill(c, disc(u, v, 0.8), ["ink"], const(0))
    fill(c, rect(6.0, 9.6, 8.0, 10.6), ["ink"], const(0))
    fill(c, rect(5.4, 12.2, 8.6, 13.4), ["brown_d"], const(0), sep=None)
    for cx, cy in ((2.2, 8.4), (11.8, 9.8)):
        fill(c, disc(cx, cy + 0.9, 0.9), ["blue", "cyan"], const(1.0), sep="blue_d")
        fill(c, poly([(cx - 0.9, cy + 0.6), (cx, cy - 0.9), (cx + 0.9, cy + 0.6)]), ["blue", "cyan"], const(1.0), sep="blue_d")
    return c


BRANCHES = {
    "branch_green_thumb": br_green_thumb, "branch_woodsman": br_woodsman, "branch_miner": br_miner,
    "branch_trader": br_trader, "branch_tinkerer": br_tinkerer,
}

SKILLS = {
    "quick_sprout": ic_quick_sprout, "bountiful": ic_bountiful, "star_grower": ic_star_grower,
    "deep_roots": ic_deep_roots, "seed_saver": ic_seed_saver,
    "sharp_edge": ic_sharp_edge, "timber": ic_timber, "light_swing": ic_light_swing,
    "prospector": ic_prospector, "ladder_sense": ic_ladder_sense, "geologist": ic_geologist,
    "steady_hands": ic_steady_hands,
    "haggler": ic_haggler, "bulk_buyer": ic_bulk_buyer, "charmer": ic_charmer, "merchants_pal": ic_merchants_pal,
    "overclock": ic_overclock, "fuel_saver": ic_fuel_saver, "extra_slot": ic_extra_slot, "big_trays": ic_big_trays,
}

TONICS = {
    "growth_tonic": it_growth_tonic, "lucky_charm": it_lucky_charm,
    "energy_brew": it_energy_brew, "rain_totem": it_rain_totem,
}


def main():
    art = sys.argv[1]
    icons, big = Sprites(art, "icons"), Sprites(art, "icons_big")
    items, items_big = Sprites(art, "items"), Sprites(art, "items_big")
    for name, fn in list(BRANCHES.items()) + [(k, v) for k, v in SKILLS.items()]:
        sid = "skill_" + name
        for sp, n in ((icons, 14), (big, 28)):
            c = fn(Canvas(n, dither=False))
            out, pad = finish(c.im)
            sp.save(sid, out, n // 2 + pad, n // 2 + pad)
    for name, fn in TONICS.items():
        for sp, n in ((items, 14), (items_big, 28)):
            c = fn(Canvas(n))
            out, pad = finish(c.im)
            sp.save(name, out, n // 2 + pad, n // 2 + pad)
    print(json.dumps({**icons.pivots, **big.pivots, **items.pivots, **items_big.pivots}))


main()
