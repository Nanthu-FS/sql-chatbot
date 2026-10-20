"""Crafting materials and mine items (pixel-direct): woods, fibre and rope, metal parts, stone and
ores, bars, brick and glass, light sources, geodes, gems and a cave mushroom.

items/<id>      body 14x14: hard steps, no dither (crisp in the bags and HUD)
items_big/<id> body 28x28: the same silhouettes with more detail (grain, facets, glints), checker dither
Pivot = body centre (+ the finish() pad). Endesga-32 only, lit from the top-left, finished with
finish() (1px ink outline + hard drop shadow) like the rest of the art.

Every shape is written once in 14-unit space. A pixel is sampled at its centre
((x + 0.5) / k, (y + 0.5) / k) with k = n / 14, so the 28px sprite is the same drawing at twice
the resolution with extra detail, not an upscale.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, Sprites, finish, new  # noqa: E402

N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
_L = (-0.55, -0.7, 0.6)
_LN = math.sqrt(sum(c * c for c in _L))
LIGHT = tuple(c / _LN for c in _L)

# Ramps run dark to light; a level v picks ramp[round or dithered v].
BARK = ["brown_d", "brown", "brown_l", "tan"]
DARK_BARK = ["ink", "brown_d", "brown", "brown_l"]
FACE = ["tan_d", "tan", "cream", "white"]
DARK_FACE = ["brown", "brown_l", "tan_d", "tan"]
HEART = ["red_d", "red", "orange", "amber", "yellow"]
PLANK = ["brown", "brown_l", "tan_d", "tan", "cream"]
AMBER = ["rust", "orange", "amber", "yellow", "white"]
METAL = ["slate", "grey_d", "grey", "grey_l", "white"]
STONE = ["slate", "grey_d", "grey", "grey_l", "white"]
COAL = ["ink", "slate_d", "slate", "grey_d", "grey"]
CLAY = ["brown", "brown_l", "clay", "tan"]
BRICK = ["red_d", "rust", "red", "clay", "tan"]
COPPER_BAR = ["rust", "clay", "orange", "amber", "yellow"]
IRON_BAR = ["slate", "grey_d", "grey", "grey_l", "white"]
GOLD_BAR = ["brown", "clay", "amber", "yellow", "white"]
GLASS = ["blue_d", "blue", "cyan", "white"]
QUARTZ = ["grey", "grey_l", "white"]
FIRE = ["rust", "red", "orange", "amber", "yellow", "white"]
GRASS = ["green_dd", "green_d", "green", "yellow"]
GRASS2 = ["green_d", "green", "yellow", "cream"]
ROPE = ["brown_d", "brown", "brown_l", "tan", "cream"]
TIE = ["tan_d", "tan", "cream"]
AMETHYST = ["purple", "magenta", "pink", "white"]
EMERALD = ["teal_dd", "green_dd", "green_d", "green", "yellow"]
RUBY = ["purple", "red_d", "red", "pink", "white"]
DIAMOND = ["blue", "grey", "grey_l", "cyan", "white"]
MUSH_CAP = ["blue_d", "blue", "cyan", "white"]
MUSH_STEM = ["tan_d", "tan", "cream", "white"]
GEO_SHELL = ["slate", "grey_d", "tan_d", "tan", "cream"]
GEO_BASE = ["purple", "magenta", "pink", "white"]
GEO_EMBER = ["red_d", "red", "orange", "yellow", "white"]
GEO_CRYSTAL = ["blue_d", "blue", "cyan", "white"]
PRISM = [("red", "red_d"), ("orange", "rust"), ("yellow", "amber"), ("green", "green_d"),
         ("cyan", "blue"), ("blue", "blue_d"), ("magenta", "purple")]


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


# ---------------------------------------------------------------- shapes (14-unit space)

def disc(cx, cy, rx, ry=None):
    ry = rx if ry is None else ry
    return lambda u, v: ((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2 <= 1.0


def rect(x0, y0, x1, y1):
    """Pixel range [x0, x1) x [y0, y1) at 14px; same units at every size."""
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


# ---------------------------------------------------------------- levels (v indexes the ramp)

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


def rings(fx, cy, rxf, ry, N, marks=(0.3, 0.6), dark=1.0, pith=None):
    """Cut-face level: lit top-left, darker growth rings, optional dark pith."""
    def f(u, v):
        rho = math.hypot((u - fx) / rxf, (v - cy) / ry)
        val = N * (1.0 - 0.2 * rho) + 0.5 * (cy - v) / ry
        if any(abs(rho - m) < 0.09 for m in marks):
            val -= dark
        if pith is not None and rho < 0.16:
            val = pith
        return val
    return f


# ---------------------------------------------------------------- painting

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


def dot(c, u, v, cc, r=0.5):
    """Highlight or fleck: one pixel at 14px (put it on pixel centres x.5), a plus at 28px."""
    fill(c, disc(u, v, r), [cc], const(0))


def faceted(c, outline, facets, ramp, default=2.0, edge=None):
    """Gem body: each facet is (points, level). Pixels take the first facet that contains them;
    pixels on a facet boundary take the edge colour so the cuts read at 1x."""
    fs = [(poly(pts), lvl) for pts, lvl in facets]
    outer = poly(outline)
    k = c.k
    label, lev = {}, {}
    for (x, y) in cover(c, outer):
        u, v = (x + 0.5) / k, (y + 0.5) / k
        idx, lv = -1, default
        for i, (f, lvl) in enumerate(fs):
            if f(u, v):
                idx, lv = i, lvl
                break
        label[(x, y)] = idx
        lev[(x, y)] = lv
    cols = {}
    for p, idx in label.items():
        x, y = p
        if edge and c.dither is False and any(label.get((x + dx, y + dy), idx) != idx for dx, dy in N4):
            cols[p] = edge
        elif edge and c.dither and any(label.get((x + dx, y + dy), idx) != idx for dx, dy in N4):
            cols[p] = edge
        else:
            cols[p] = tone(ramp, lev[p], x, y, c.dither)
    paint(c, cols)


# ---------------------------------------------------------------- woods

def log(c, bark, face, face_lv, cy=7.0, r=4.1, x0=0.6, fx=10.4, rxf=2.7, gap=2.2, dense=False):
    """Log segment lying along x: bark cylinder on the left, cut end with rings on the right."""
    lx = x0 + r
    body = U(disc(lx, cy, r), rect(lx, cy - r, fx, cy + r))
    nb = len(bark) - 1

    def bark_lv(u, v):
        t = (v - (cy - r)) / (2 * r)
        val = nb * (1.0 - 0.9 * t) + 0.3
        if c.n > 14 and 0.2 < t < 0.9 and round(u * gap) % (3 if dense else 5) == 0:
            val -= 1.2
        return val

    fill(c, body, bark, bark_lv)
    fill(c, disc(fx, cy, rxf, r), face, face_lv, sep="brown_d")
    return c


def it_wood(c):
    return log(c, BARK, FACE, rings(10.4, 7.0, 2.7, 4.1, 3, marks=(0.35, 0.7), dark=0.9, pith=0.6))


def it_hardwood(c):
    return log(c, DARK_BARK, DARK_FACE, rings(10.2, 7.0, 2.6, 4.4, 3, marks=(0.3, 0.6, 0.85), dark=0.9, pith=0.0),
               r=4.4, fx=10.2, rxf=2.6, gap=2.6, dense=True)


def it_heartwood(c):
    def face(u, v):
        rho = math.hypot((u - 10.4) / 2.7, (v - 7.0) / 4.1)
        val = 4.0 - 2.2 * rho
        if any(abs(rho - m) < 0.09 for m in (0.4, 0.75)):
            val -= 0.8
        return val
    log(c, ["red_d", "rust", "brown", "clay"], HEART, face)
    dot(c, 1.5, 1.5, "orange")
    dot(c, 3.5, 0.5, "yellow")
    dot(c, 12.5, 12.5, "orange")
    return c


def it_plank(c):
    boards = [(1.0, 4.0, 1.0, 13.0), (5.0, 8.0, 0.0, 12.6), (9.0, 12.0, 1.4, 13.0)]
    for y0, y1, x0, x1 in boards:
        def lv(u, v, y0=y0, y1=y1, x1=x1):
            t = (v - y0) / (y1 - y0)
            val = 3.4 - 2.6 * t
            if u > x1 - 1.1:
                val = 4.0
            if c.n > 14 and abs(v - (y0 + 0.5 * (y1 - y0) + 0.25 * math.sin(u * 1.7))) < 0.12:
                val -= 1.2
            return val
        fill(c, rect(x0, y0, x1, y1), PLANK, lv, sep="brown_d")
        if c.n > 14:
            dot(c, 3.2, (y0 + y1) / 2, "grey_d")
            dot(c, 9.6, (y0 + y1) / 2, "grey_d")
    return c


def it_beam(c):
    top = poly([(0.8, 6.2), (3.4, 3.6), (13.2, 3.6), (10.2, 6.2)])
    front = rect(0.8, 6.2, 10.2, 11.4)
    end = poly([(10.2, 6.2), (13.2, 3.6), (13.2, 8.8), (10.2, 11.4)])
    fill(c, top, FACE, const(2.6), sep="brown_d")
    fill(c, front, BARK, vgrad(6.2, 11.4, 2.6, 0.4), sep="brown_d")
    if c.n > 14:
        for vv in (7.8, 9.6):
            fill(c, I(rect(0.8, vv, 10.2, vv + 0.4), front), ["brown_d"], const(0))
    fill(c, end, FACE, rings(11.7, 7.0, 1.6, 3.8, 3, marks=(0.35, 0.7), dark=0.9, pith=0.6), sep="brown_d")
    return c


# ---------------------------------------------------------------- fibre, rope, metal parts

def it_sap(c):
    handle = arc([(3.6, 5.2), (4.4, 2.6), (7.0, 1.5), (9.6, 2.6), (10.4, 5.2)], 0.9)
    fill(c, handle, ["grey_d", "grey_l"], const(1.0))
    body = poly([(2.4, 4.8), (11.6, 4.8), (10.2, 13.0), (3.8, 13.0)])
    fill(c, body, METAL, hgrad(2.4, 11.6, 3.4, 0.6), sep="slate_d")
    bands = lambda u, v: body(u, v) and (6.0 <= v < 6.9 or 10.0 <= v < 10.9)  # noqa: E731
    fill(c, bands, ["grey_d", "grey", "grey_l"], const(1.6))
    fill(c, disc(7.0, 4.8, 4.6, 1.3), ["tan", "cream", "yellow", "white"], hgrad(2.4, 11.6, 2.4, 1.2), sep="tan_d")
    dot(c, 5.5, 4.5, "white")
    return c


def it_fibre(c):
    base = (3.4, 12.8)
    tops = [(4.2, 1.0), (6.6, 0.6), (9.0, 1.4), (11.2, 3.2), (12.2, 6.2)]
    for i, top in enumerate(tops):
        ramp = GRASS if i % 2 == 0 else GRASS2
        bx, by = base
        tx, ty = top

        def lv(u, v, bx=bx, by=by, tx=tx, ty=ty):
            t = (by - v) / (by - ty)
            xline = bx + (tx - bx) * t
            return 2.2 if u < xline - 0.2 else 0.9
        fill(c, seg(base, top, 1.3), ramp, lv, sep="green_dd")
    band = poly([(3.6, 8.2), (9.9, 8.2), (7.3, 10.0), (3.3, 10.0)])
    fill(c, band, TIE, vgrad(8.2, 10.0, 2.0, 1.0), sep="brown_d")
    return c


def it_rope(c):
    cx, cy = 7.0, 7.4
    fill(c, disc(cx, cy + 1.0, 6.0, 4.6), ROPE, const(0.8))
    ring = D(disc(cx, cy, 6.0, 4.6), disc(cx, cy, 2.3, 1.6))

    def lv(u, v):
        nx, ny = (u - cx) / 6.0, (v - cy) / 4.6
        rr = max(1e-6, math.hypot(nx, ny))
        dotl = (nx * -0.707 + ny * -0.707) / rr
        val = 2.6 + 1.6 * dotl
        if c.n > 14 and ((u + v) * 1.3) % 1.0 < 0.3:
            val -= 0.9
        return val
    fill(c, ring, ROPE, lv, sep="brown_d")
    fill(c, disc(cx, cy, 2.3, 1.6), ["ink", "brown_d"], const(0.6), sep="ink")
    return c


def it_nails(c):
    nails = [((5.8, 6.8), (9.8, 13.2)), ((8.6, 3.2), (12.4, 9.4)), ((3.8, 3.0), (8.0, 10.6))]
    for head, tip in nails:
        fill(c, seg(head, tip, 1.0), ["slate", "grey", "grey_l"], const(1.6), sep="slate_d")
        hx, hy = head
        fill(c, disc(hx, hy, 1.7, 1.5), METAL, ball(hx - 0.4, hy - 0.4, 1.8, 1.6, N=4), sep="slate_d")
    return c


def tooth(cx, cy, a, r0, r1, w0, w1):
    """Trapezoid tooth on the ray at angle a: half-width w0 at the root (r0), w1 at the tip (r1)."""
    ux, uy = math.cos(a), math.sin(a)
    tx, ty = -uy, ux
    bx, by = cx + ux * r0, cy + uy * r0
    qx, qy = cx + ux * r1, cy + uy * r1
    return poly([(bx + tx * w0, by + ty * w0), (qx + tx * w1, qy + ty * w1),
                 (qx - tx * w1, qy - ty * w1), (bx - tx * w0, by - ty * w0)])


def it_gear(c):
    cx, cy = 7.0, 7.0
    teeth = [tooth(cx, cy, k * math.pi / 4, 3.6, 6.5, 1.5, 1.0) for k in range(8)]
    shape = U(disc(cx, cy, 4.9), *teeth)
    fill(c, shape, METAL, ball(6.0, 6.0, 6.4, 6.4, N=4, bias=0.4), sep="slate_d")
    fill(c, disc(cx, cy, 1.6), ["ink", "slate_d"], const(0.6), sep="slate_d")
    return c


# ---------------------------------------------------------------- stone and ores

ROCK = U(disc(7.0, 8.2, 5.4, 4.0), disc(5.6, 5.8, 3.4, 3.1), disc(10.0, 7.2, 3.0, 3.2))


# Ore-coloured rocks, like the mine's nodes (art round 3, style A).
PEACH = ["brown", "brown_l", "tan_d", "tan", "cream"]
TEAL = ["teal_dd", "blue_d", "blue", "cyan", "white"]
RUSTY = ["brown_d", "rust", "clay", "tan", "cream"]
GOLD_NUG = ["rust", "orange", "amber", "yellow", "white"]
DARK_STONE = ["slate_d", "slate", "grey_d", "grey", "grey_l"]
SLAB_LO = poly([(1.2, 8.6), (2.6, 6.8), (11.4, 6.6), (13.0, 8.6), (12.2, 11.6), (2.2, 11.8)])
SLAB_HI = poly([(3.4, 5.4), (4.8, 3.0), (9.6, 2.8), (10.8, 5.0), (9.8, 6.9), (4.0, 7.0)])


def it_stone(c):
    fill(c, SLAB_LO, STONE, ball(7.0, 8.4, 6.2, 3.4, N=4, bias=0.25), sep="slate_d")
    fill(c, SLAB_HI, STONE, ball(7.0, 4.6, 3.8, 2.4, N=4, bias=0.35), sep="slate_d")
    dot(c, 5.5, 3.5, "white")
    return c


def nugget(c, u, v, r, ramp, sep):
    fill(c, disc(u, v, r, r * 0.85), ramp, ball(u, v, r, r * 0.85, N=len(ramp) - 1, bias=0.35), sep=sep)
    dot(c, u - r * 0.35, v - r * 0.4, ramp[-1])


def it_copper_ore(c):
    fill(c, ROCK, PEACH, ball(6.4, 6.4, 6.0, 5.4, N=4, bias=0.25), sep="brown")
    for u, v in ((5.0, 6.0), (9.5, 7.5), (6.8, 9.6)):
        nugget(c, u, v, 1.5, TEAL, "teal_dd")
    return c


def it_iron_ore(c):
    fill(c, poly([(1.4, 7.0), (3.0, 4.2), (7.2, 4.0), (8.0, 7.0), (7.0, 11.8), (2.0, 11.6)]), RUSTY, ball(4.6, 7.4, 3.6, 4.0, N=4, bias=0.3), sep="brown_d")
    fill(c, poly([(6.6, 6.4), (8.4, 3.0), (12.2, 3.6), (13.0, 7.6), (11.6, 11.6), (7.4, 11.4)]), RUSTY, ball(9.8, 7.0, 3.4, 4.2, N=4, bias=0.3), sep="brown_d")
    for u, v in ((4.5, 6.5), (10.5, 5.5), (9.5, 9.5)):
        dot(c, u, v, "grey_l")
    return c


def it_gold_ore(c):
    fill(c, ROCK, DARK_STONE, ball(6.4, 6.4, 6.0, 5.4, N=4, bias=0.25), sep="slate_d")
    for u, v, r in ((5.4, 6.4, 1.8), (9.6, 8.0, 1.6)):
        nugget(c, u, v, r, GOLD_NUG, "brown_d")
    return c


def it_coal(c):
    lumps = [(9.4, 6.4, 3.6, 3.2), (5.0, 8.0, 3.9, 3.5), (9.0, 10.6, 2.8, 2.2)]
    for cx, cy, rx, ry in lumps:
        fill(c, disc(cx, cy, rx, ry), COAL, ball(cx, cy, rx, ry, N=4, bias=0.3, shift=-0.8), sep="ink")
        dot(c, cx - 0.5 * rx, cy - 0.5 * ry, "white", r=0.5)
    return c


def it_clay(c):
    lump = U(disc(7.0, 8.2, 5.6, 3.9), disc(5.4, 6.4, 2.8, 2.4))
    fill(c, lump, CLAY, ball(6.4, 7.0, 6.0, 4.6, N=3, bias=0.3), sep="brown_d")
    fill(c, D(disc(7.6, 7.4, 1.6, 1.1), disc(7.6, 7.4, 0.9, 0.55)), ["brown"], const(0), sep=None)
    dot(c, 4.5, 5.5, "tan")
    return c


def it_quartz(c):
    def crystal(cx, top, w, bot):
        h2 = top + 0.7 * w
        return poly([(cx, top), (cx + w / 2, h2), (cx + w / 2, bot), (cx - w / 2, bot), (cx - w / 2, h2)]), cx, w
    for cx, top, w, bot in ((3.6, 4.6, 3.6, 12.6), (10.6, 3.6, 3.8, 12.6), (7.0, 1.2, 5.2, 12.8)):
        sf, cxx, ww = crystal(cx, top, w, bot)

        def lv(u, v, cxx=cxx, ww=ww):
            return 2.6 - 2.2 * max(0.0, min(1.0, (u - cxx + ww / 2) / ww))
        fill(c, sf, QUARTZ, lv, sep="grey_d")
    dot(c, 6.5, 2.5, "white")
    dot(c, 10.5, 9.5, "cyan")
    return c


def ingot_bar(c, ramp, x0, y0, top_lv, front_lv, side_lv):
    """One iso bar: lit top face, front face with a vertical gradient, darker end face."""
    top = poly([(x0, y0), (x0 + 2, y0 - 2), (x0 + 11, y0 - 2), (x0 + 9, y0)])
    front = rect(x0, y0, x0 + 9, y0 + 3)
    side = poly([(x0 + 9, y0), (x0 + 11, y0 - 2), (x0 + 11, y0 + 1), (x0 + 9, y0 + 3)])
    fill(c, top, ramp, const(top_lv), sep="brown_d")
    fill(c, front, ramp, vgrad(y0, y0 + 3, front_lv[0], front_lv[1]), sep="brown_d")
    fill(c, side, ramp, const(side_lv), sep="brown_d")


def bars(c, ramp, top_lv, front_lv, side_lv):
    """Two ingots stacked, so the metal reads as bars rather than a crate."""
    ingot_bar(c, ramp, 0.5, 9.8, top_lv, front_lv, side_lv)
    ingot_bar(c, ramp, 2.0, 5.6, top_lv, front_lv, side_lv)
    return c


def it_copper_bar(c):
    return bars(c, COPPER_BAR, 3.4, (2.6, 1.4), 0.7)


def it_iron_bar(c):
    bars(c, IRON_BAR, 3.4, (2.6, 1.4), 0.6)
    dot(c, 5.5, 4.5, "white")
    return c


def it_gold_bar(c):
    bars(c, GOLD_BAR, 3.6, (2.8, 1.8), 1.0)
    dot(c, 5.5, 4.5, "white")
    dot(c, 6.5, 10.5, "white")
    return c


CRYSTAL_NUG = ["teal_dd", "blue_d", "blue", "cyan", "white"]
CRYSTAL_BAR = ["teal_dd", "blue_d", "blue", "cyan", "white"]


def it_crystalite_ore(c):
    fill(c, ROCK, DARK_STONE, ball(6.4, 6.4, 6.0, 5.4, N=4, bias=0.25), sep="slate_d")
    for u, v, r in ((5.0, 6.0, 1.9), (9.4, 8.2, 1.7), (7.2, 10.0, 1.2)):
        nugget(c, u, v, r, CRYSTAL_NUG, "teal_dd")
    return c


def it_crystal_bar(c):
    bars(c, CRYSTAL_BAR, 3.6, (2.8, 1.8), 1.0)
    dot(c, 5.5, 4.5, "white")
    dot(c, 6.5, 10.5, "white")
    return c


def it_brick(c):
    top = poly([(1, 5), (3, 2.6), (11, 2.6), (9, 5)])
    front = rect(1, 5, 9, 12)
    side = poly([(9, 5), (11, 2.6), (11, 9.4), (9, 12)])
    fill(c, top, BRICK, const(3.4), sep="rust")
    fill(c, front, BRICK, vgrad(5, 12, 2.6, 1.6), sep="rust")
    fill(c, side, BRICK, const(0.6), sep="rust")
    mortar = U(seg((1.0, 8.4), (9.0, 8.4), 0.9), seg((5.0, 5.0), (5.0, 8.4), 0.9),
               seg((3.0, 8.4), (3.0, 12.0), 0.9), seg((7.0, 8.4), (7.0, 12.0), 0.9))
    fill(c, I(mortar, front), ["tan"], const(0))
    return c


def it_glass(c):
    fill(c, rect(1, 1, 13, 13), ["slate", "grey_d", "grey", "grey_l"], vgrad(1, 13, 2.8, 0.6))
    pane = rect(2, 2, 12, 12)
    fill(c, pane, GLASS, lambda u, v: 3.0 - 2.4 * max(0.0, min(1.0, (u + v - 4.0) / 16.0)))
    fill(c, I(seg((3.2, 7.2), (7.2, 3.2), 0.9), pane), ["white"], const(0))
    fill(c, I(seg((6.4, 11.0), (11.0, 6.4), 0.7), pane), ["white"], const(0))
    return c


# ---------------------------------------------------------------- light

def it_torch(c):
    flame = poly([(7.4, 0.6), (9.8, 3.4), (10.0, 5.8), (7.6, 6.6), (5.2, 5.6), (5.0, 3.4)])
    core = poly([(7.4, 3.0), (8.8, 4.6), (8.2, 6.2), (6.6, 6.2), (6.0, 4.6)])
    stick = rect(6, 7, 9, 13.6)
    wrap = rect(5.6, 6.2, 9.4, 7.8)
    fill(c, stick, BARK, hgrad(6, 9, 2.6, 0.6), sep="brown_d")
    fill(c, wrap, ["brown_d", "brown", "tan_d"], vgrad(6.2, 7.8, 1.0, 2.0), sep="brown_d")

    def fire(u, v):
        rho = math.hypot((u - 7.5) / 2.6, (v - 5.6) / 5.0)
        return 5.0 * (1.0 - 0.85 * rho)
    fill(c, flame, FIRE, fire, sep="rust")
    fill(c, core, ["amber", "yellow", "white"], const(2.0))
    return c


def it_lantern(c):
    handle = arc([(5.2, 4.6), (5.6, 2.2), (7.0, 1.3), (8.4, 2.2), (8.8, 4.6)], 0.9)
    fill(c, handle, ["slate", "grey_d"], const(0.8))
    cap = poly([(3.8, 4.6), (10.2, 4.6), (8.8, 6.2), (5.2, 6.2)])
    fill(c, cap, METAL, hgrad(3.8, 10.2, 3.0, 1.0), sep="ink")
    body = poly([(4.4, 6.2), (9.6, 6.2), (10.4, 10.8), (3.6, 10.8)])

    def glow(u, v):
        rho = math.hypot((u - 7.0) / 3.6, (v - 8.6) / 2.8)
        return 3.2 * (1.0 - 0.75 * rho)
    fill(c, body, ["orange", "amber", "yellow", "white"], glow, sep="ink")
    fill(c, I(rect(6.5, 6.2, 7.5, 10.8), body), ["slate"], const(0))
    fill(c, I(rect(4.8, 6.2, 5.4, 10.8), body), ["slate"], const(0))
    fill(c, I(rect(8.6, 6.2, 9.2, 10.8), body), ["slate"], const(0))
    base = poly([(3.2, 10.8), (10.8, 10.8), (9.6, 12.6), (4.4, 12.6)])
    fill(c, base, METAL, vgrad(10.8, 12.6, 2.0, 0.8), sep="ink")
    return c


def it_support_beam(c):
    top = rect(0.6, 1.6, 13.4, 4.4)
    post_l = poly([(2.0, 4.4), (5.0, 4.4), (4.6, 13.2), (1.6, 13.2)])
    post_r = poly([(9.0, 4.4), (12.0, 4.4), (12.4, 13.2), (9.4, 13.2)])
    fill(c, top, BARK, vgrad(1.6, 4.4, 3.0, 1.2), sep="brown_d")
    fill(c, post_l, BARK, hgrad(1.6, 5.0, 2.4, 0.8), sep="brown_d")
    fill(c, post_r, BARK, hgrad(9.0, 12.4, 2.4, 0.8), sep="brown_d")
    knee_l = seg((4.2, 4.4), (1.9, 9.0), 1.3 if c.n > 14 else 1.6)
    knee_r = seg((9.8, 4.4), (12.1, 9.0), 1.3 if c.n > 14 else 1.6)
    fill(c, knee_l, ["brown_d", "brown"], const(1.0), sep="brown_d")
    fill(c, knee_r, ["brown_d", "brown"], const(1.0), sep="brown_d")
    for u, v in ((2.6, 2.6), (11.4, 2.6)):
        dot(c, u, v, "grey_d")
    return c


def it_rope_ladder(c):
    for x in (2.6, 10.6):
        fill(c, seg((x, 0.6), (x, 13.4), 1.5), ["tan_d", "tan", "cream"],
             lambda u, v: 2.0 if math.floor(v * 1.6) % 2 == 0 else 1.0)
    for y in (2.4, 5.6, 8.8, 12.0):
        fill(c, rect(3.0, y, 10.2, y + 1.3), BARK, vgrad(y, y + 1.3, 2.6, 1.0), sep="brown_d")
    return c


# ---------------------------------------------------------------- geodes

def geode(c, inner_ramp, inner_lv, spikes):
    shell = disc(7.0, 7.4, 5.8, 5.6)
    inner = I(disc(8.6, 6.2, 3.4, 3.1), shell)
    fill(c, D(shell, inner), GEO_SHELL, ball(6.0, 6.0, 6.6, 6.2, N=4, bias=0.25), sep="slate_d")
    fill(c, inner, inner_ramp, ball(8.2, 5.6, 3.6, 3.4, N=len(inner_ramp) - 1, bias=0.35, shift=inner_lv),
         sep="slate_d")
    top = len(inner_ramp) - 1
    for bx, by, h in spikes:
        fill(c, poly([(bx - 0.8, by), (bx, by - h), (bx + 0.8, by)]), inner_ramp, const(top))
    return c


def it_geode(c):
    return geode(c, GEO_BASE, 0.0, ((7.8, 6.8, 2.6), (9.4, 5.2, 2.6), (10.4, 7.2, 2.0)))


def it_ember_geode(c):
    c = geode(c, GEO_EMBER, 0.0, ((7.8, 6.8, 2.6), (9.4, 5.2, 2.6), (10.4, 7.2, 2.0)))
    dot(c, 12.5, 2.5, "orange")
    return c


def it_crystal_geode(c):
    c = geode(c, GEO_CRYSTAL, 0.0, ((7.8, 6.8, 2.6), (9.4, 5.2, 2.6), (10.4, 7.2, 2.0)))
    dot(c, 1.5, 12.5, "cyan")
    return c


# ---------------------------------------------------------------- gems

def it_amethyst(c):
    faceted(c, [(7, 0.6), (10.6, 3.2), (10.6, 10.8), (7, 13.4), (3.4, 10.8), (3.4, 3.2)],
            [([(7, 0.6), (3.4, 3.2), (7, 5.6)], 2.6),
             ([(7, 0.6), (10.6, 3.2), (7, 5.6)], 1.4),
             ([(3.4, 3.2), (7, 5.6), (7, 13.4), (3.4, 10.8)], 1.6),
             ([(7, 5.6), (10.6, 3.2), (10.6, 10.8), (7, 13.4)], 0.5)], AMETHYST, default=1.2,
            edge="purple")
    dot(c, 5.5, 4.5, "white")
    return c


def it_emerald(c):
    faceted(c, [(4.0, 1.8), (10.0, 1.8), (12.6, 4.4), (12.6, 9.6), (10.0, 12.2), (4.0, 12.2), (1.4, 9.6), (1.4, 4.4)],
            [([(4.4, 4.2), (9.6, 4.2), (9.6, 9.8), (4.4, 9.8)], 3.0),
             ([(4.0, 1.8), (10.0, 1.8), (9.6, 4.2), (4.4, 4.2)], 2.8),
             ([(1.4, 4.4), (4.0, 1.8), (4.4, 4.2), (4.4, 9.8), (1.4, 9.6)], 2.0),
             ([(9.6, 4.2), (10.0, 1.8), (12.6, 4.4), (12.6, 9.6), (10.0, 12.2), (9.6, 9.8)], 0.9),
             ([(1.4, 9.6), (4.4, 9.8), (9.6, 9.8), (10.0, 12.2), (4.0, 12.2)], 1.2)],
            EMERALD, default=2.0, edge="teal_dd")
    dot(c, 5.5, 5.5, "yellow")
    return c


def it_ruby(c):
    faceted(c, [(3.6, 1.6), (10.4, 1.6), (13.0, 5.0), (7.0, 12.8), (1.0, 5.0)],
            [([(3.6, 1.6), (10.4, 1.6), (8.6, 4.6), (5.4, 4.6)], 3.4),
             ([(1.0, 5.0), (3.6, 1.6), (5.4, 4.6)], 2.6),
             ([(10.4, 1.6), (13.0, 5.0), (8.6, 4.6)], 1.2),
             ([(1.0, 5.0), (5.4, 4.6), (7.0, 12.8)], 2.0),
             ([(5.4, 4.6), (8.6, 4.6), (7.0, 12.8)], 1.6),
             ([(8.6, 4.6), (13.0, 5.0), (7.0, 12.8)], 0.6)], RUBY, default=1.6, edge="purple")
    dot(c, 4.5, 2.5, "white")
    return c


def it_diamond(c):
    faceted(c, [(2.2, 4.6), (4.8, 1.4), (9.2, 1.4), (11.8, 4.6), (7.0, 12.8)],
            [([(4.8, 1.4), (9.2, 1.4), (7.0, 4.6)], 4.0),
             ([(2.2, 4.6), (4.8, 1.4), (7.0, 4.6)], 3.2),
             ([(9.2, 1.4), (11.8, 4.6), (7.0, 4.6)], 2.2),
             ([(2.2, 4.6), (7.0, 4.6), (7.0, 12.8)], 2.6),
             ([(7.0, 4.6), (11.8, 4.6), (7.0, 12.8)], 1.2)], DIAMOND, default=2.0, edge="grey")
    dot(c, 5.5, 2.5, "white")
    return c


def it_prismatic_shard(c):
    outline = poly([(7.0, 0.6), (10.8, 3.8), (9.6, 12.6), (4.4, 12.6), (3.2, 3.8)])
    k = c.k
    cols = {}
    for (x, y) in cover(c, outline):
        u, v = (x + 0.5) / k, (y + 0.5) / k
        t = (u - 3.2) / 7.6 * 0.6 + (v - 0.6) / 12.0 * 0.4
        idx = max(0, min(len(PRISM) - 1, int(t * len(PRISM))))
        cols[(x, y)] = PRISM[idx][0] if u < 7.0 else PRISM[idx][1]
    paint(c, cols)
    dot(c, 6.5, 2.5, "white")
    dot(c, 5.5, 5.5, "white")
    return c


def it_cave_mushroom(c):
    cap = I(disc(7.0, 6.6, 5.9, 4.6), lambda u, v: v <= 6.8)
    fill(c, cap, MUSH_CAP, ball(6.0, 5.4, 5.8, 4.6, N=3, bias=0.3), sep="blue_d")
    for u, v in ((4.5, 4.5), (8.5, 3.5), (10.5, 5.5)):
        dot(c, u, v, "white", r=0.5 if c.n == 14 else 0.75)
    fill(c, rect(4.8, 6.4, 9.2, 12.8), MUSH_STEM, hgrad(4.8, 9.2, 3.0, 1.4), sep="tan_d")
    return c


def it_resin(c):
    drop = U(disc(7.0, 8.8, 4.7, 4.3), poly([(7.0, 1.2), (3.4, 7.6), (10.6, 7.6)]))
    fill(c, drop, AMBER, ball(6.4, 7.6, 5.4, 5.4, N=4, bias=0.15), sep="rust")
    fill(c, disc(7.2, 9.0, 2.4, 2.1), ["orange", "amber", "yellow"], const(1.6))
    dot(c, 5.5, 6.5, "white")
    return c



# ---------------------------------------------------------------- fixtures (sold and placed on the farm)

def fixture_sprinkler(c, ramp):
    base = poly([(4.2, 9.0), (9.8, 9.0), (10.6, 12.6), (3.4, 12.6)])
    fill(c, base, ramp, hgrad(3.4, 10.6, 1.2, 3.2), sep="brown_d")
    fill(c, rect(3.0, 8.0, 11.0, 9.4), ramp, const(3.6), sep="brown_d")
    head = U(disc(7.0, 6.4, 2.2, 1.6), rect(6.2, 4.6, 7.8, 8.0))
    fill(c, head, ramp, hgrad(4.8, 9.2, 1.0, 3.4), sep="brown_d")
    for u, v in ((3.5, 4.5), (10.5, 4.5), (5.5, 2.5), (8.5, 2.5)):
        dot(c, u, v, "cyan")
    return c


def it_copper_sprinkler(c):
    return fixture_sprinkler(c, COPPER_BAR)


def it_iron_sprinkler(c):
    return fixture_sprinkler(c, IRON_BAR)


def it_gold_sprinkler(c):
    return fixture_sprinkler(c, GOLD_BAR)


def it_lightning_rod(c):
    rod = U(poly([(7.0, 0.6), (6.0, 3.6), (8.0, 3.6)]), rect(6.2, 3.6, 7.8, 10.8))
    fill(c, rod, METAL, hgrad(6.0, 8.0, 0.6, 3.4), sep="slate_d")
    plate = poly([(3.6, 10.4), (10.4, 10.4), (11.4, 12.8), (2.6, 12.8)])
    fill(c, plate, COPPER_BAR, hgrad(2.6, 11.4, 1.2, 3.2), sep="brown_d")
    dot(c, 3.5, 2.5, "yellow")
    dot(c, 10.5, 2.5, "yellow")
    return c


def it_scarecrow(c):
    fill(c, disc(7.0, 6.0, 2.0, 1.9), FACE, ball(6.4, 5.6, 2.2, 2.0, N=3, bias=0.3), sep="tan_d")
    hat = U(rect(4.8, 0.8, 9.2, 3.4), rect(2.8, 3.0, 11.2, 4.2))
    fill(c, hat, BARK, vgrad(0.8, 4.2, 3.0, 1.0), sep="brown_d")
    dot(c, 6.5, 5.5, "ink")
    dot(c, 8.5, 5.5, "ink")
    fill(c, rect(1.6, 8.0, 12.4, 9.2), BARK, vgrad(8.0, 9.2, 2.4, 1.0), sep="brown_d")
    fill(c, rect(5.8, 9.2, 8.2, 12.6), BARK, hgrad(5.8, 8.2, 2.2, 0.8), sep="brown_d")
    for u, v in ((1.5, 10.5), (12.5, 10.5), (4.5, 12.5), (9.5, 12.5)):
        dot(c, u, v, "amber")
    return c


# ---------------------------------------------------------------- minerals (mine rewards)

AGATE = ["rust", "clay", "tan", "cream", "white"]
AURORA = ["blue", "cyan", "green", "yellow", "pink", "white"]
CELESTINE = ["blue_d", "blue", "cyan", "white"]
CALCITE = ["tan", "cream", "white"]
FIRE_OPAL = ["red_d", "red", "orange", "amber", "yellow", "white"]
PURPLE_CUBE = ["purple", "magenta", "pink", "white"]
GREEN_CUBE = ["green_dd", "green_d", "green", "yellow"]
GARNET = ["purple", "red_d", "red", "pink"]
JASPER = ["brown_d", "brown", "red_d", "red", "clay"]
SUNSTONE = ["rust", "orange", "amber", "yellow"]
MOON = ["slate", "grey", "grey_l", "white"]
JET = ["slate_d", "slate", "grey_d", "grey"]
OBSIDIAN = ["ink", "slate_d", "slate", "grey_d", "grey", "grey_l"]
OPAL = ["purple", "magenta", "pink", "cyan", "white"]
SAPPHIRE = ["blue_d", "blue", "cyan", "white"]


def it_agate(c):
    shape = disc(7.0, 7.2, 5.6, 4.8)

    def lv(u, v):
        rho = math.hypot((u - 6.2) / 5.4, (v - 6.4) / 4.4)
        return 2.4 - 1.2 * rho + (1.2 if int(rho * 3.0) % 2 == 0 else 0.0)
    fill(c, shape, AGATE, lv, sep="rust")
    dot(c, 4.5, 4.5, "white")
    return c


def it_aurora_quartz(c):
    for cx, top, w, bot in ((3.6, 4.6, 3.6, 12.6), (10.6, 3.6, 3.8, 12.6), (7.0, 1.2, 5.2, 12.8)):
        h2 = top + 0.7 * w
        sf = poly([(cx, top), (cx + w / 2, h2), (cx + w / 2, bot), (cx - w / 2, bot), (cx - w / 2, h2)])
        fill(c, sf, AURORA, lambda u, v, cx=cx, w=w: 1.0 + 4.0 * (u - cx + w / 2) / w, sep="blue")
    dot(c, 6.5, 2.5, "white")
    dot(c, 10.5, 9.5, "white")
    return c


def it_celestine(c):
    for cx, top, bot in ((4.0, 3.0, 12.6), (7.4, 1.0, 12.8), (10.6, 4.2, 12.4)):
        blade = poly([(cx, top), (cx + 1.3, top + 2.0), (cx + 1.3, bot), (cx - 1.3, bot), (cx - 1.3, top + 2.0)])
        fill(c, blade, CELESTINE, hgrad(cx - 1.3, cx + 1.3, 1.0, 3.0), sep="blue_d")
    dot(c, 6.5, 3.5, "white")
    return c


def it_calcite(c):
    big = poly([(6.0, 1.6), (10.8, 6.0), (6.0, 10.4), (1.2, 6.0)])
    small = poly([(10.6, 7.4), (13.0, 9.2), (10.6, 11.0), (8.2, 9.2)])
    fill(c, big, CALCITE, lambda u, v: 2.0 - 1.2 * (u - 1.2) / 9.6, sep="tan")
    fill(c, small, CALCITE, const(1.4), sep="tan")
    dot(c, 4.5, 4.5, "white")
    return c


def cube(c, cx, top, w, side, ramp, lv_top, lv_left, lv_right, sep):
    """Isometric cube: top diamond of half-width w, side faces `side` tall, drawn left, right, top."""
    h = w / 2.0
    tp = poly([(cx, top), (cx + w, top + h), (cx, top + 2 * h), (cx - w, top + h)])
    lf = poly([(cx - w, top + h), (cx, top + 2 * h), (cx, top + 2 * h + side), (cx - w, top + h + side)])
    rt = poly([(cx, top + 2 * h), (cx + w, top + h), (cx + w, top + h + side), (cx, top + 2 * h + side)])
    fill(c, lf, ramp, const(lv_left), sep=sep)
    fill(c, rt, ramp, const(lv_right), sep=sep)
    fill(c, tp, ramp, const(lv_top), sep=sep)


def it_fluorite(c):
    cube(c, 8.0, 2.6, 3.6, 4.4, PURPLE_CUBE, 2.4, 1.4, 0.4, "purple")
    cube(c, 4.0, 7.6, 2.6, 3.4, GREEN_CUBE, 2.8, 1.8, 0.8, "green_dd")
    return c


def it_fire_opal(c):
    faceted(c, [(7.0, 0.8), (11.6, 3.2), (12.6, 7.4), (9.6, 12.6), (4.4, 12.6), (1.4, 7.4), (2.4, 3.2)],
            [([(7.0, 0.8), (2.4, 3.2), (7.0, 6.2)], 4.2),
             ([(7.0, 0.8), (11.6, 3.2), (7.0, 6.2)], 2.6),
             ([(2.4, 3.2), (7.0, 6.2), (4.4, 12.6), (1.4, 7.4)], 2.8),
             ([(11.6, 3.2), (12.6, 7.4), (9.6, 12.6), (7.0, 6.2)], 1.4),
             ([(7.0, 6.2), (9.6, 12.6), (4.4, 12.6)], 2.0)],
            FIRE_OPAL, default=2.0, edge="red_d")
    dot(c, 5.0, 3.5, "white")
    return c


def it_garnet(c):
    faceted(c, [(4.0, 1.6), (10.0, 1.6), (12.6, 4.2), (12.6, 9.8), (10.0, 12.4), (4.0, 12.4), (1.4, 9.8), (1.4, 4.2)],
            [([(4.6, 4.4), (9.4, 4.4), (9.4, 9.6), (4.6, 9.6)], 2.6),
             ([(4.0, 1.6), (10.0, 1.6), (9.4, 4.4), (4.6, 4.4)], 3.0),
             ([(1.4, 4.2), (4.0, 1.6), (4.6, 4.4)], 2.0),
             ([(1.4, 4.2), (4.6, 4.4), (4.6, 9.6), (1.4, 9.8)], 1.4),
             ([(10.0, 1.6), (12.6, 4.2), (9.4, 4.4)], 1.0),
             ([(9.4, 4.4), (12.6, 4.2), (12.6, 9.8), (9.4, 9.6)], 0.6)],
            GARNET, default=1.6, edge="purple")
    dot(c, 5.5, 3.0, "pink")
    return c


def it_jasper(c):
    shape = U(disc(7.0, 7.4, 5.4, 4.8), disc(5.2, 5.6, 3.0, 3.0))
    fill(c, shape, JASPER, ball(6.2, 6.0, 5.8, 5.2, N=4, bias=0.3), sep="brown_d")
    for u, v in ((4.5, 9.5), (9.5, 10.5), (10.5, 5.5), (7.5, 8.5)):
        dot(c, u, v, "cream")
    return c


def it_sunstone(c):
    fill(c, disc(7.0, 7.2, 5.6, 5.0), SUNSTONE, ball(6.2, 6.0, 5.8, 5.2, N=3, bias=0.3), sep="rust")
    for u, v in ((4.5, 4.5), (9.5, 8.5), (6.5, 10.5)):
        dot(c, u, v, "white")
    return c


def it_moonstone(c):
    shape = disc(7.0, 7.0, 5.6, 5.2)
    fill(c, shape, MOON, ball(6.2, 6.0, 5.8, 5.4, N=3, bias=0.3), sep="grey_d")
    fill(c, I(seg((3.2, 9.6), (10.8, 5.2), 1.2), shape), ["cyan"], const(0))
    dot(c, 4.5, 4.5, "white")
    return c


def it_jet(c):
    shape = U(disc(7.0, 7.4, 5.4, 4.8), disc(5.0, 5.8, 2.8, 2.6))
    fill(c, shape, JET, ball(6.2, 6.0, 5.6, 5.0, N=3, bias=0.2), sep="ink")
    dot(c, 4.5, 4.5, "grey_l")
    return c


def it_obsidian(c):
    faceted(c, [(7.0, 0.8), (11.6, 4.0), (10.4, 12.6), (4.2, 13.0), (2.2, 4.6)],
            [([(7.0, 0.8), (2.2, 4.6), (7.0, 7.0)], 3.4),
             ([(2.2, 4.6), (4.2, 13.0), (7.0, 7.0)], 2.2),
             ([(7.0, 0.8), (11.6, 4.0), (7.0, 7.0)], 1.6),
             ([(11.6, 4.0), (10.4, 12.6), (7.0, 7.0)], 0.8),
             ([(10.4, 12.6), (4.2, 13.0), (7.0, 7.0)], 1.0)],
            OBSIDIAN, default=1.2, edge="purple")
    dot(c, 5.0, 3.5, "white")
    return c


def mica_sheet(c, y0, lv):
    top = poly([(2.0, y0), (8.0, y0 - 2.4), (12.0, y0 - 0.6), (6.0, y0 + 1.8)])
    edge = poly([(2.0, y0), (6.0, y0 + 1.8), (6.0, y0 + 2.8), (2.0, y0 + 1.0)])
    fill(c, top, METAL, const(lv), sep="slate_d")
    fill(c, edge, METAL, const(0.6), sep="slate_d")


def it_mica(c):
    mica_sheet(c, 10.4, 1.4)
    mica_sheet(c, 7.4, 2.4)
    mica_sheet(c, 4.4, 3.4)
    return c


def it_opal(c):
    faceted(c, [(7.0, 1.2), (11.4, 3.4), (12.4, 7.2), (9.4, 12.4), (4.6, 12.4), (1.6, 7.2), (2.6, 3.4)],
            [([(7.0, 1.2), (2.6, 3.4), (7.0, 6.4)], 3.0),
             ([(7.0, 1.2), (11.4, 3.4), (7.0, 6.4)], 4.0),
             ([(2.6, 3.4), (7.0, 6.4), (4.6, 12.4), (1.6, 7.2)], 1.0),
             ([(11.4, 3.4), (12.4, 7.2), (9.4, 12.4), (7.0, 6.4)], 2.0),
             ([(7.0, 6.4), (9.4, 12.4), (4.6, 12.4)], 3.0)],
            OPAL, default=2.0, edge="magenta")
    dot(c, 5.0, 3.0, "white")
    return c


def it_star_sapphire(c):
    shape = disc(7.0, 7.0, 5.8, 5.2)
    fill(c, shape, SAPPHIRE, ball(6.2, 6.0, 5.8, 5.2, N=3, bias=0.3), sep="blue_d")
    rays = U(*[seg((7.0 - 3.8 * math.cos(a), 7.0 - 3.8 * math.sin(a)),
                   (7.0 + 3.8 * math.cos(a), 7.0 + 3.8 * math.sin(a)), 0.8)
               for a in (0.0, math.pi / 3, 2 * math.pi / 3)])
    fill(c, I(rays, shape), ["white"], const(0))
    return c


MINERALS = {
    "mineral_agate": it_agate, "mineral_aurora_quartz": it_aurora_quartz, "mineral_calcite": it_calcite,
    "mineral_celestine": it_celestine, "mineral_fire_opal": it_fire_opal, "mineral_fluorite": it_fluorite,
    "mineral_garnet": it_garnet, "mineral_jasper": it_jasper, "mineral_jet": it_jet, "mineral_mica": it_mica,
    "mineral_moonstone": it_moonstone, "mineral_obsidian": it_obsidian, "mineral_opal": it_opal,
    "mineral_star_sapphire": it_star_sapphire, "mineral_sunstone": it_sunstone,
    "copper_sprinkler": it_copper_sprinkler, "iron_sprinkler": it_iron_sprinkler,
    "gold_sprinkler": it_gold_sprinkler, "lightning_rod": it_lightning_rod, "scarecrow": it_scarecrow,
}


ITEMS = {
    "wood": it_wood, "hardwood": it_hardwood, "heartwood": it_heartwood, "resin": it_resin,
    "sap": it_sap, "plank": it_plank, "beam": it_beam, "fibre": it_fibre, "rope": it_rope,
    "nails": it_nails, "gear": it_gear, "stone": it_stone, "coal": it_coal,
    "copper_ore": it_copper_ore, "iron_ore": it_iron_ore, "gold_ore": it_gold_ore,
    "quartz": it_quartz, "clay": it_clay, "copper_bar": it_copper_bar, "iron_bar": it_iron_bar,
    "gold_bar": it_gold_bar, "crystalite_ore": it_crystalite_ore, "crystal_bar": it_crystal_bar, "brick": it_brick, "glass": it_glass, "torch": it_torch,
    "lantern": it_lantern, "support_beam": it_support_beam, "rope_ladder": it_rope_ladder,
    "geode": it_geode, "ember_geode": it_ember_geode, "crystal_geode": it_crystal_geode,
    "amethyst": it_amethyst, "emerald": it_emerald, "ruby": it_ruby, "diamond": it_diamond,
    "prismatic_shard": it_prismatic_shard, "cave_mushroom": it_cave_mushroom,
}
ITEMS.update(MINERALS)


def main():
    art = sys.argv[1]
    small, big = Sprites(art, "items"), Sprites(art, "items_big")
    for name, fn in ITEMS.items():
        for sp, n in ((small, 14), (big, 28)):
            c = fn(Canvas(n))
            out, pad = finish(c.im)
            sp.save(name, out, n // 2 + pad, n // 2 + pad)
    print(json.dumps({**small.pivots, **big.pivots}))


main()
