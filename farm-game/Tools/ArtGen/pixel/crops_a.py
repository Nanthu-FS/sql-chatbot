"""Crops A (pixel-direct): wheat, carrot, potato, tomato, corn, strawberry.

crops/<id>_0 seeds, _1 sprout, _2 growing, _3 ready, _dead withered (stage 2 shape).
  Pivot = soil contact, horizontally centred (placed on the tile diamond centre).
items/<id> 14x14 produce, items_big/<id> 28x28, items*/seed_<id> seed packets. Pivot = centre.

Everything is drawn in Endesga-32 with checker dither between ramp steps, lit from the
top-left, and finished with finish() (1px ink outline + hard drop shadow) like the tiles.
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, CLEAR, Sprites, new, from_map, finish  # noqa: E402
import json  # noqa: E402
import math  # noqa: E402

_L = (-0.55, -0.7, 0.6)
LIGHT = tuple(v / math.sqrt(sum(u * u for u in _L)) for v in _L)
LD = (-0.7071, -0.7071)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

LEAF = ["teal_dd", "green_dd", "green_d", "green", "yellow"]
DEAD = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
GOLD = ["brown", "clay", "amber", "yellow", "cream"]
SOIL = ["brown_d", "brown", "brown_l", "tan_d"]
ORANGE = ["red_d", "rust", "orange", "amber", "yellow"]
RED = ["purple", "red_d", "red", "pink", "white"]
SPUD = ["brown", "brown_l", "tan_d", "tan", "cream"]
KERNEL = ["clay", "orange", "amber", "yellow", "cream"]
WOOD = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
PAPER = ["tan_d", "tan", "cream", "white"]

CW, CH = 30, 41          # crop drawing canvas
GXB, GYB = 15, 38        # soil-contact boundary (pivot) on that canvas
MX, MY = 14.5, 37.5      # same point in pixel-centre space


def col(c):
    return E[c] if isinstance(c, str) else c


def tone(ramp, v, x, y):
    """Ramp colour for a fractional level v, checker-dithered between steps."""
    n = len(ramp) - 1
    v = max(0.0, min(float(n), v))
    b = int(v)
    f = v - b
    if b >= n or f < 0.3:
        return ramp[b]
    if f > 0.7:
        return ramp[b + 1]
    return ramp[b + 1] if (x + y) % 2 == 0 else ramp[b]


class Canvas:
    def __init__(self, w=CW, h=CH):
        self.w, self.h = w, h
        self.im = new(w, h)
        self.px = self.im.load()

    def inb(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def on(self, x, y):
        return self.inb(x, y) and self.px[x, y][3] > 0

    def put(self, x, y, c):
        if c is not None and self.inb(x, y):
            self.px[x, y] = col(c)

    def get(self, x, y):
        return self.px[x, y] if self.inb(x, y) else CLEAR

    def erase(self, x, y):
        if self.inb(x, y):
            self.px[x, y] = CLEAR

    def stamp(self, x0, y0, rows, key, flip=False):
        for y, row in enumerate(rows):
            if flip:
                row = row[::-1]
            for x, ch in enumerate(row):
                if ch not in ". ":
                    self.put(x0 + x, y0 + y, key[ch])


# ---------------------------------------------------------------- shapes

def ellipse(cx, cy, rx, ry, k=1.0):
    s = set()
    for y in range(math.floor(cy - ry) - 1, math.ceil(cy + ry) + 2):
        for x in range(math.floor(cx - rx) - 1, math.ceil(cx + rx) + 2):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= k:
                s.add((x, y))
    return s


def bez(p0, p1, p2, t):
    u = 1 - t
    return (u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0], u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1])


def bez_d(p0, p1, p2, t):
    u = 1 - t
    return (2 * u * (p1[0] - p0[0]) + 2 * t * (p2[0] - p1[0]), 2 * u * (p1[1] - p0[1]) + 2 * t * (p2[1] - p1[1]))


def ctrl(a, b, bend):
    dx, dy = b[0] - a[0], b[1] - a[1]
    ln = math.hypot(dx, dy) or 1.0
    return ((a[0] + b[0]) / 2 - dy / ln * bend * ln, (a[1] + b[1]) / 2 + dx / ln * bend * ln)


def curve_pts(a, b, bend=0.0):
    """Integer pixels along a quadratic curve from a to b, 8-connected, no repeats."""
    c1 = ctrl(a, b, bend)
    n = int(math.hypot(b[0] - a[0], b[1] - a[1]) * 3) + 2
    out = []
    for i in range(n + 1):
        x, y = bez(a, c1, b, i / n)
        p = (round(x), round(y))
        if not out or out[-1] != p:
            out.append(p)
    return out


def line_pts(a, b):
    x0, y0 = a
    x1, y1 = b
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    out = []
    while True:
        out.append((x0, y0))
        if (x0, y0) == (x1, y1):
            return out
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def stroke(c, pts, color, w=1):
    for x, y in pts:
        for i in range(w):
            c.put(x + i, y, color)


def stem(c, a, b, color, bend=0.0, w=1):
    pts = curve_pts(a, b, bend)
    stroke(c, pts, color, w)
    return pts


def leaf_shape(base, tip, hw, bend=0.0, prof="lance", fat=0.25):
    """{pixel: (side, nx, ny, t)} for a leaf swept along a curve with a width profile."""
    c1 = ctrl(base, tip, bend)
    ln = math.hypot(tip[0] - base[0], tip[1] - base[1])
    n = max(8, int(ln * 3))
    smp = []
    for i in range(n + 1):
        t = i / n
        x, y = bez(base, c1, tip, t)
        dx, dy = bez_d(base, c1, tip, t)
        dl = math.hypot(dx, dy) or 1.0
        if prof == "lance":
            w = hw * math.sin(math.pi * min(1.0, t ** 0.75))
        elif prof == "ovate":
            w = hw * math.sin(math.pi * t) ** 0.55
        elif prof == "blade":
            w = hw * (1 - t ** 1.6)
        elif prof == "round":
            w = hw * math.sin(math.pi * (0.15 + 0.85 * t)) ** 0.4
        else:
            w = hw
        smp.append((x, y, -dy / dl, dx / dl, w, t))
    xs = [s[0] for s in smp]
    ys = [s[1] for s in smp]
    out = {}
    for py in range(math.floor(min(ys) - hw) - 1, math.ceil(max(ys) + hw) + 2):
        for qx in range(math.floor(min(xs) - hw) - 1, math.ceil(max(xs) + hw) + 2):
            best = None
            for x, y, nx, ny, w, t in smp:
                d = math.hypot(qx - x, py - y) - w
                if best is None or d < best[0]:
                    best = (d, (qx - x) * nx + (py - y) * ny, nx, ny, t, w)
            if best[0] <= fat:
                out[(qx, py)] = best[1:]
    for x, y, nx, ny, w, t in smp:
        if t < 0.97:
            out.setdefault((round(x), round(y)), (0.0, nx, ny, t, w))
    return out


def paint_shape(c, colors, sep=None):
    """Paint {pixel: colour}; edges that overlap earlier (behind) pixels get the sep colour."""
    for (x, y), cc in colors.items():
        if sep is not None and any((x + dx, y + dy) not in colors and c.on(x + dx, y + dy) for dx, dy in N4):
            cc = sep
        c.put(x, y, cc)


def leaf(c, base, tip, hw, bend=0.0, ramp=LEAF, lit=3.0, prof="lance", vein=True, notch=0, sep=True, hi=True):
    shp = leaf_shape(base, tip, hw, bend, prof)
    if notch:
        drop = []
        for (x, y), (s, nx, ny, t, w) in shp.items():
            edge = sum((x + dx, y + dy) not in shp for dx, dy in N4)
            if edge and 0.15 < t < 0.9 and (x + 2 * y) % notch == 0 and abs(s) > 0.8:
                drop.append((x, y))
        for p in drop:
            del shp[p]
    cols = {}
    for (x, y), (s, nx, ny, t, w) in shp.items():
        face = nx * LD[0] + ny * LD[1]
        side = s * (1 if face >= 0 else -1)
        if vein and w >= 1.4 and abs(s) < 0.5 and t < 0.85:
            v = lit - 1
        elif side > 0.15 or (abs(face) < 0.2 and s * nx < 0):
            v = lit
        else:
            v = lit - 1
        if (x + 1, y) not in shp and (x, y + 1) not in shp:
            v -= 1
        elif hi and v >= lit and ((x - 1, y) not in shp or (x, y - 1) not in shp) and t > 0.35:
            v += 0.5
        cols[(x, y)] = tone(ramp, v, x, y)
    paint_shape(c, cols, ramp[max(0, int(lit) - 2)] if sep else None)
    return shp


def shade_set(c, s, ramp, depth=3.0, gain=1.0, off=0.0, sep=None, spec=None):
    """Auto-shade any pixel set as a soft dome lit from the top-left."""
    dist = {}
    q = [p for p in s if any((p[0] + dx, p[1] + dy) not in s for dx, dy in N4)]
    for p in q:
        dist[p] = 1
    i = 0
    while i < len(q):
        x, y = q[i]
        i += 1
        for dx, dy in N4:
            n = (x + dx, y + dy)
            if n in s and n not in dist:
                dist[n] = dist[(x, y)] + 1
                q.append(n)
    hgt = {p: depth * math.sqrt(max(0.0, 1 - (1 - min(1.0, (d - 0.5) / depth)) ** 2)) for p, d in dist.items()}
    n = len(ramp) - 1
    cols = {}
    for (x, y) in s:
        gx = (hgt.get((x + 1, y), 0) - hgt.get((x - 1, y), 0)) / 2
        gy = (hgt.get((x, y + 1), 0) - hgt.get((x, y - 1), 0)) / 2
        nl = math.sqrt(gx * gx + gy * gy + 1)
        lv = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / nl
        v = ((lv + 0.25) / 1.25) * n * gain + off
        cols[(x, y)] = tone(ramp, v, x, y)
    if spec:
        for p, cc in spec.items():
            if p in s:
                cols[p] = cc
    paint_shape(c, cols, sep)
    return cols


def sphere(c, cx, cy, rx, ry, ramp, gain=1.0, off=0.0, sep=None, spec=None, k=1.05):
    s = ellipse(cx, cy, rx, ry, k)
    n = len(ramp) - 1
    cols = {}
    for (x, y) in s:
        nx, ny = (x - cx) / rx, (y - cy) / ry
        nz = math.sqrt(max(0.0, 1 - min(1.0, nx * nx + ny * ny)))
        lv = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        v = ((lv + 0.3) / 1.3) * n * gain + off
        cols[(x, y)] = tone(ramp, v, x, y)
    if spec:
        for p, cc in spec.items():
            if p in s:
                cols[p] = cc
    paint_shape(c, cols, sep)
    return s


# ---------------------------------------------------------------- shared crop bits

def mound(c, rx=5.0, h=1.6, seeds=(), cx=MX):
    """Little heap of turned soil centred on the pivot, with optional seed pixels on top."""
    s = ellipse(cx, MY, rx, 2.1) | ellipse(cx, MY - h, rx - 1.6, 2.0)
    shade_set(c, s, SOIL, depth=2.5, gain=1.0, off=0.2)
    for x, y, cc in seeds:
        c.put(x, y, cc)
    return s


def soil_lip(c, rx=4.0, cx=MX):
    """Front lip of soil drawn over stems so plants look rooted."""
    s = {p for p in ellipse(cx, MY + 0.3, rx, 1.6) if p[1] >= GYB - 1}
    shade_set(c, s, SOIL, depth=1.5, gain=1.0, off=0.3)


def save_crop(sp, name, c):
    x0, y0, x1, y1 = c.im.getbbox()
    half = max(GXB - x0, x1 - GXB)
    left, right = GXB - half, GXB + half
    body = c.im.crop((left, y0, right, y1))
    assert body.height <= 38 and body.width <= 30, (name, body.size)
    out, pad = finish(body)
    sp.save(name, out, GXB - left + pad, GYB - y0 + pad)


# ---------------------------------------------------------------- wheat

EAR = [".l.", "lmd", "mld", "lmd", "mld", "lmd", ".md", ".d."]


def ear(c, x, y, key, lean=0):
    for i, row in enumerate(EAR):
        dx = 0 if lean == 0 else (lean * (len(EAR) - i)) // len(EAR)
        for j, ch in enumerate(row):
            if ch != ".":
                c.put(x + j + dx, y + i, key[ch])


def wheat(stage, dead=False):
    c = Canvas()
    if stage == 0:
        mound(c, seeds=[(11, 36, "yellow"), (12, 36, "amber"), (15, 35, "yellow"), (16, 35, "amber"), (14, 37, "yellow"), (17, 37, "amber")])
        return c
    if stage == 1:
        mound(c, rx=4.0, h=1.0)
        leaf(c, (14, 36), (10, 28), 1.1, -0.15, prof="blade")
        leaf(c, (15, 36), (19, 29), 1.1, 0.15, prof="blade")
        leaf(c, (14, 36), (15, 26), 1.2, 0.05, prof="blade")
        soil_lip(c, 3.0)
        return c
    ramp = DEAD if dead else LEAF
    if stage == 2:
        mound(c, rx=5.0, h=1.0)
        blades = [((13, 36), (5, 24), -0.12), ((16, 36), (24, 23), 0.12), ((14, 36), (9, 18), -0.08), ((15, 36), (21, 17), 0.08),
                  ((14, 36), (13, 14), 0.03), ((15, 36), (17, 15), -0.03)]
        if dead:
            blades = [((13, 36), (4, 31), -0.35), ((16, 36), (25, 31), 0.35), ((14, 36), (7, 24), -0.3), ((15, 36), (22, 23), 0.3),
                      ((14, 36), (11, 19), -0.15), ((15, 36), (19, 20), 0.15)]
        for b, t, bend in blades:
            leaf(c, b, t, 1.2, bend, ramp=ramp, prof="blade")
        key = {"l": "tan", "m": "tan_d", "d": "brown"} if dead else {"l": "yellow", "m": "green", "d": "green_d"}
        if dead:
            ear(c, 9, 15, key, -2)
            ear(c, 17, 16, key, 2)
        else:
            ear(c, 12, 7, key)
            ear(c, 7, 11, key, -1)
            ear(c, 18, 9, key, 1)
        soil_lip(c, 3.5)
        return c
    # stage 3: golden sheaf
    mound(c, rx=5.0, h=1.0)
    for b, t, bend in [((13, 36), (5, 27), -0.15), ((16, 36), (24, 27), 0.15)]:
        leaf(c, b, t, 1.2, bend, ramp=GOLD, lit=2.5, prof="blade")
    key = {"l": "yellow", "m": "amber", "d": "clay"}
    ears = [(5, 12, -1), (21, 12, 1), (9, 8, -1), (17, 8, 1), (13, 5, 0), (10, 15, 0), (17, 15, 0)]
    for x, y, lean in ears:
        stem(c, (14 + (1 if x > 13 else 0), 36), (x + 1 + lean, y + 8), "amber" if x < 14 else "clay", bend=0.04 * (x - 13))
    for x, y, lean in ears:
        ear(c, x, y, key, lean)
        c.put(x + 1 + lean, y - 1, "cream")
    soil_lip(c, 3.5)
    return c


# ---------------------------------------------------------------- carrot

def frond(c, base, tip, hw=2.0, bend=0.0, ramp=LEAF, lit=3.0):
    leaf(c, base, tip, hw, bend, ramp=ramp, lit=lit, prof="lance", notch=2, vein=True)


def carrot_top(c, w, h, ramp=ORANGE):
    """Orange shoulder of the root poking out of the mound."""
    s = ellipse(MX, GYB - 1 - h / 2 + 1, w / 2, h / 2 + 1)
    s = {p for p in s if p[1] <= GYB}
    shade_set(c, s, ramp, depth=2.0, gain=1.0, off=0.3)
    for p in sorted(s):
        x, y = p
        if y == GYB - 1 - h // 2 and (x + y) % 3 == 0 and (x + 1, y) in s and (x - 1, y) in s:
            c.put(x, y, ramp[1])
    return s


def carrot(stage, dead=False):
    c = Canvas()
    if stage == 0:
        mound(c, seeds=[(12, 36, "cream"), (16, 35, "cream"), (14, 37, "tan"), (18, 37, "cream")])
        return c
    if stage == 1:
        mound(c, rx=4.0, h=1.0)
        frond(c, (14, 36), (11, 29), 1.6, -0.1)
        frond(c, (15, 36), (18, 28), 1.6, 0.1)
        soil_lip(c, 3.0)
        return c
    ramp = DEAD if dead else LEAF
    if stage == 2:
        mound(c, rx=5.0, h=1.0)
        fr = [((14, 35), (6, 26), -0.12), ((15, 35), (23, 26), 0.12), ((14, 35), (10, 20), -0.06), ((15, 35), (19, 19), 0.06), ((14, 35), (14, 17), 0.0)]
        if dead:
            fr = [((14, 35), (5, 32), -0.3), ((15, 35), (24, 32), 0.3), ((14, 35), (8, 25), -0.3), ((15, 35), (21, 24), 0.3), ((14, 35), (13, 23), -0.15)]
        for b, t, bend in fr:
            frond(c, b, t, 2.2, bend, ramp=ramp)
        carrot_top(c, 6, 2, ORANGE if not dead else DEAD)
        return c
    mound(c, rx=6.0, h=1.0)
    fr = [((14, 32), (4, 21), -0.15), ((15, 32), (25, 21), 0.15), ((14, 32), (8, 14), -0.08), ((15, 32), (21, 13), 0.08), ((14, 32), (14, 10), 0.0)]
    for b, t, bend in fr:
        frond(c, b, t, 2.6, bend)
    carrot_top(c, 10, 5)
    return c


# ---------------------------------------------------------------- potato

def flower(c, x, y, petal="white", mid="yellow", shade="grey_l"):
    c.put(x, y - 1, petal)
    c.put(x - 1, y, petal)
    c.put(x + 1, y, shade)
    c.put(x, y + 1, shade)
    c.put(x, y, mid)


def bush(c, leaves, ramp=LEAF, lit=3.0, hw=2.4, prof="ovate", notch=0):
    for b, t, bend, *rest in leaves:
        leaf(c, b, t, rest[0] if rest else hw, bend, ramp=ramp, lit=lit, prof=prof, notch=notch)


def potato(stage, dead=False):
    c = Canvas()
    if stage == 0:
        mound(c, seeds=[])
        sphere(c, 14.5, 35.5, 2.6, 1.8, SPUD, sep="brown")
        c.put(13, 35, "brown_l")
        mound_front = {p for p in ellipse(MX, MY + 0.5, 5.0, 1.8) if p[1] >= 37}
        shade_set(c, mound_front, SOIL, depth=1.5, off=0.3)
        return c
    if stage == 1:
        mound(c, rx=4.0, h=1.0)
        stem(c, (14, 36), (14, 32), "green_d")
        leaf(c, (14, 33), (9, 29), 2.2, -0.2, prof="ovate")
        leaf(c, (15, 33), (20, 29), 2.2, 0.2, prof="ovate")
        leaf(c, (14, 33), (15, 27), 2.0, 0.0, prof="ovate")
        soil_lip(c, 3.0)
        return c
    ramp = DEAD if dead else LEAF
    if stage == 2:
        mound(c, rx=5.5, h=1.0)
        lv = [((13, 34), (3, 30), -0.2), ((16, 34), (26, 30), 0.2), ((13, 32), (5, 24), -0.15), ((16, 32), (24, 24), 0.15),
              ((14, 31), (10, 20), -0.1), ((15, 31), (19, 20), 0.1), ((14, 32), (14, 23), 0.0)]
        if dead:
            lv = [((13, 34), (3, 35), -0.35), ((16, 34), (26, 35), 0.35), ((13, 32), (5, 29), -0.3), ((16, 32), (24, 29), 0.3),
                  ((14, 31), (9, 25), -0.25), ((15, 31), (20, 25), 0.25), ((14, 32), (14, 27), 0.1)]
        bush(c, lv, ramp)
        soil_lip(c, 4.0)
        return c
    mound(c, rx=6.0, h=1.0)
    lv = [((13, 32), (2, 27), -0.2), ((16, 32), (27, 27), 0.2), ((13, 30), (4, 20), -0.15), ((16, 30), (25, 20), 0.15),
          ((14, 29), (9, 15), -0.1), ((15, 29), (20, 15), 0.1), ((14, 30), (14, 18), 0.0)]
    bush(c, lv, LEAF, hw=2.6)
    flower(c, 9, 15, "white", "yellow")
    flower(c, 20, 14, "white", "yellow")
    flower(c, 14, 17, "pink", "yellow", "magenta")
    for cx, cy in ((9.5, 35.5), (19.0, 35.0), (14.5, 37.0)):
        sphere(c, cx, cy, 3.0, 2.2, SPUD, sep="brown", spec={(int(cx) - 1, int(cy) - 1): "cream"})
        c.put(int(cx) + 1, int(cy), "brown_l")
    return c


# ---------------------------------------------------------------- tomato

def tomato_fruit(c, cx, cy, r, ripe=True):
    ramp = RED if ripe else ["green_dd", "green_d", "green", "yellow", "white"]
    sphere(c, cx, cy, r, r * 0.92, ramp, sep=ramp[1], spec={(round(cx - r * 0.45), round(cy - r * 0.45)): "white"})
    x, y = round(cx), round(cy - r * 0.92)
    c.put(x, y, "green_d")
    c.put(x - 1, y, "green")
    c.put(x + 1, y, "green_d")
    c.put(x, y - 1, "green_d")


def stake(c, top, x=19, ramp=WOOD):
    for y in range(top, GYB):
        c.put(x, y, ramp[3])
        c.put(x + 1, y, ramp[1])
    c.put(x, top, ramp[4])
    c.put(x + 1, top, ramp[2])


def tomato(stage, dead=False):
    c = Canvas()
    if stage == 0:
        mound(c, seeds=[(12, 36, "cream"), (13, 36, "tan"), (16, 35, "cream"), (17, 35, "tan"), (15, 37, "cream")])
        return c
    if stage == 1:
        mound(c, rx=4.0, h=1.0)
        stem(c, (14, 36), (14, 30), "green_d")
        leaf(c, (14, 31), (9, 28), 2.0, -0.2, prof="ovate", notch=3)
        leaf(c, (15, 31), (20, 27), 2.0, 0.2, prof="ovate", notch=3)
        soil_lip(c, 3.0)
        return c
    ramp = DEAD if dead else LEAF
    stake(c, 8 if stage == 3 else 12, ramp=WOOD)
    if stage == 2:
        mound(c, rx=5.5, h=1.0)
        stem(c, (14, 36), (17, 14), "brown" if dead else "green_d", bend=-0.08, w=1)
        if dead:
            lv = [((14, 33), (5, 34), -0.3), ((15, 31), (24, 33), 0.3), ((15, 26), (6, 28), -0.3), ((16, 23), (25, 26), 0.3),
                  ((16, 19), (9, 21), -0.3), ((17, 16), (23, 19), 0.3)]
        else:
            lv = [((14, 33), (5, 29), -0.2), ((15, 31), (24, 28), 0.2), ((15, 26), (6, 22), -0.2), ((16, 23), (25, 20), 0.2),
                  ((16, 19), (9, 14), -0.2), ((17, 16), (23, 12), 0.2)]
        bush(c, lv, ramp, hw=2.3, notch=3)
        if dead:
            sphere(c, 11.5, 30.5, 2.0, 1.8, ["brown_d", "brown", "brown_l", "tan_d"], sep="brown_d")
        else:
            tomato_fruit(c, 10.5, 25.5, 2.2, ripe=False)
            tomato_fruit(c, 20.5, 29.5, 2.2, ripe=False)
            flower(c, 13, 17, "yellow", "amber", "amber")
        c.put(19, 22, "cream")
        c.put(20, 22, "tan")
        soil_lip(c, 3.5)
        return c
    mound(c, rx=5.5, h=1.0)
    stem(c, (14, 36), (17, 10), "green_d", bend=-0.08)
    lv = [((14, 33), (4, 30), -0.2), ((15, 31), (25, 29), 0.2), ((15, 26), (5, 21), -0.2), ((16, 23), (26, 19), 0.2),
          ((16, 18), (8, 12), -0.2), ((17, 14), (24, 8), 0.2), ((17, 12), (14, 5), -0.1)]
    bush(c, lv, LEAF, hw=2.4, notch=3)
    c.put(19, 20, "cream")
    c.put(20, 20, "tan")
    for cx, cy, r in ((9.5, 25.5, 3.0), (21.5, 26.5, 3.0), (11.5, 15.5, 2.8), (22.0, 15.0, 2.6), (15.0, 31.0, 2.8)):
        tomato_fruit(c, cx, cy, r)
    return c


# ---------------------------------------------------------------- corn

def cob(c, x, y, h, husk=LEAF, ripe=True):
    """Corn ear: kernel column x..x+3, y..y+h, wrapped in husk leaves at the base."""
    if ripe:
        for j in range(h):
            for i in range(4):
                v = 3.2 - i * 0.7 - (0.6 if j > h - 3 else 0) + (0.4 if j < 2 else 0)
                if (i + j) % 2 == 0:
                    v -= 0.6
                c.put(x + i, y + j, tone(KERNEL, v, x + i, y + j))
        c.put(x, y, CLEAR)
        c.put(x + 3, y, CLEAR)
        c.put(x + 1, y - 1, "tan")
        c.put(x + 2, y - 2, "clay")
        c.put(x + 1, y - 2, "tan")
    leaf(c, (x + 1, y + h + 1), (x - 1, y + 2), 1.6, -0.15, ramp=husk, prof="blade")
    leaf(c, (x + 2, y + h + 1), (x + 5, y + (3 if ripe else 0)), 1.6, 0.15, ramp=husk, prof="blade")


def corn(stage, dead=False):
    c = Canvas()
    if stage == 0:
        mound(c, seeds=[(12, 36, "yellow"), (13, 36, "amber"), (16, 35, "yellow"), (17, 35, "amber"), (15, 37, "amber")])
        return c
    if stage == 1:
        mound(c, rx=4.0, h=1.0)
        leaf(c, (14, 36), (10, 27), 1.6, -0.2, prof="blade")
        leaf(c, (15, 36), (19, 28), 1.6, 0.2, prof="blade")
        soil_lip(c, 3.0)
        return c
    ramp = DEAD if dead else LEAF
    sc = ("brown", "brown_l") if dead else ("green_d", "green")
    if stage == 2:
        mound(c, rx=5.0, h=1.0)
        top = 12
        for y in range(top, GYB):
            c.put(14, y, sc[1])
            c.put(15, y, sc[0])
        if dead:
            lv = [((14, 32), (4, 36), -0.35), ((15, 29), (25, 34), 0.35), ((14, 24), (5, 29), -0.35), ((15, 20), (24, 26), 0.35), ((14, 15), (8, 20), -0.3)]
        else:
            lv = [((14, 32), (3, 27), -0.25), ((15, 29), (26, 24), 0.25), ((14, 24), (4, 17), -0.25), ((15, 20), (25, 13), 0.25), ((14, 15), (12, 6), -0.1)]
        for b, t, bend in lv:
            leaf(c, b, t, 1.7, bend, ramp=ramp, prof="blade")
        cob(c, 16, 21, 6, husk=ramp, ripe=False)
        soil_lip(c, 3.5)
        return c
    mound(c, rx=5.0, h=1.0)
    top = 7
    for y in range(top, GYB):
        c.put(14, y, "green")
        c.put(15, y, "green_d")
    lv = [((14, 33), (3, 29), -0.25), ((15, 30), (26, 26), 0.25), ((14, 25), (3, 19), -0.25), ((15, 21), (26, 14), 0.25), ((14, 15), (5, 8), -0.2)]
    for b, t, bend in lv:
        leaf(c, b, t, 1.8, bend, prof="blade")
    # tassel
    for x, y0, y1 in ((14, 2, 8), (12, 4, 7), (17, 4, 7)):
        for y in range(y0, y1):
            c.put(x, y, "amber" if (y % 2) else "tan")
    c.put(13, 7, "tan")
    c.put(16, 7, "tan")
    c.put(15, 3, "clay")
    cob(c, 17, 16, 9)
    soil_lip(c, 3.5)
    return c


# ---------------------------------------------------------------- strawberry

def berry(c, cx, cy, big=True):
    rows = ([".rr.", "rrrr", "rrrr", ".rr.", ".r.."] if not big else
            [".rrr.", "rrrrr", "rrrrr", ".rrr.", "..r.."])
    s = set()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "r":
                s.add((cx + i, cy + j))
    shade_set(c, s, RED, depth=2.0, off=0.2, sep="red_d")
    w = len(rows[0])
    for (sx, sy) in ((1, 1), (3, 2), (2, 3)) if big else ((1, 1), (2, 2)):
        c.put(cx + sx, cy + sy, "yellow")
    c.put(cx + w // 2 - 1, cy - 1, "green")
    c.put(cx + w // 2, cy - 1, "green_d")
    c.put(cx + w // 2 + (1 if big else 0), cy - 1, "green_d")


def strawberry(stage, dead=False):
    c = Canvas()
    if stage == 0:
        mound(c, seeds=[(12, 36, "yellow"), (16, 35, "yellow"), (14, 37, "pink"), (17, 37, "yellow")])
        return c
    if stage == 1:
        mound(c, rx=4.0, h=1.0)
        stem(c, (14, 36), (14, 32), "green_d")
        for b, t in (((14, 32), (10, 29)), ((15, 32), (19, 29)), ((14, 31), (15, 27))):
            leaf(c, b, t, 2.0, 0.0, prof="round", notch=3)
        soil_lip(c, 3.0)
        return c
    ramp = DEAD if dead else LEAF
    if stage == 2:
        mound(c, rx=5.5, h=1.0)
        if dead:
            lv = [((13, 35), (4, 36), -0.3), ((16, 35), (25, 36), 0.3), ((14, 33), (7, 30), -0.3), ((15, 33), (22, 30), 0.3), ((14, 32), (14, 27), 0.1)]
        else:
            lv = [((13, 35), (4, 31), -0.2), ((16, 35), (25, 31), 0.2), ((14, 33), (7, 25), -0.15), ((15, 33), (22, 25), 0.15), ((14, 32), (14, 23), 0.0)]
        bush(c, lv, ramp, hw=2.6, prof="round", notch=3)
        if not dead:
            flower(c, 9, 27)
            flower(c, 20, 26)
        soil_lip(c, 4.0)
        return c
    mound(c, rx=6.0, h=1.0)
    lv = [((13, 34), (3, 29), -0.2), ((16, 34), (26, 29), 0.2), ((14, 32), (6, 22), -0.15), ((15, 32), (23, 22), 0.15), ((14, 31), (14, 19), 0.0)]
    bush(c, lv, LEAF, hw=2.8, prof="round", notch=3)
    flower(c, 14, 20)
    for x, y, big in ((3, 30, True), (21, 30, True), (8, 31, True), (16, 32, True), (11, 25, False), (19, 24, False)):
        berry(c, x, y, big)
    return c


# ---------------------------------------------------------------- items

def item_canvas(size):
    return Canvas(size, size)


def item_wheat(n):
    c = item_canvas(n)
    k = n / 14
    key = {"l": "yellow", "m": "amber", "d": "clay"}
    if n == 14:
        for x0, y0, x1, y1 in ((7, 13, 2, 4), (7, 13, 7, 3), (7, 13, 11, 4)):
            stroke(c, line_pts((x0, y0), (x1, y1 + 6)), "tan")
        ear(c, 1, 1, key)
        ear(c, 6, 0, key)
        ear(c, 10, 1, key)
        for x in range(5, 10):
            c.put(x, 10, "red")
        return c
    for x0, x1, y1 in ((13, 4, 6), (13, 9, 3), (14, 14, 2), (14, 19, 3), (14, 24, 6)):
        stroke(c, line_pts((x0, 26), (x1 + 1, y1 + 12)), "amber")
        stroke(c, line_pts((x0 + 1, 26), (x1 + 2, y1 + 12)), "clay")
    for x, y in ((3, 6), (8, 3), (13, 2), (18, 3), (23, 6)):
        big_ear(c, x, y)
    for x in range(10, 19):
        c.put(x, 18, "red")
        c.put(x, 19, "red_d")
    return c


BIG_EAR = ["..l..", ".lmd.", "lmmdd", ".lmd.", "lmmdd", ".lmd.", "lmmdd", ".lmd.", "lmmdd", ".lmd.", "..d.."]


def big_ear(c, x, y):
    key = {"l": "yellow", "m": "amber", "d": "clay"}
    c.stamp(x - 1, y, BIG_EAR, key)
    c.put(x + 1, y - 1, "cream")
    c.put(x + 1, y - 2, "cream")


def item_carrot(n):
    c = item_canvas(n)
    k = n / 14
    # root: cone from top-right shoulder to bottom-left tip
    s = set()
    a = (9.5 * k, 4.5 * k)
    b = (1.5 * k, 12.5 * k)
    for y in range(n):
        for x in range(n):
            vx, vy = x - a[0], y - a[1]
            dx, dy = b[0] - a[0], b[1] - a[1]
            L2 = dx * dx + dy * dy
            t = max(0.0, min(1.0, (vx * dx + vy * dy) / L2))
            px_, py_ = a[0] + dx * t, a[1] + dy * t
            r = (3.0 * k) * (1 - t) ** 0.8 + 0.4
            if math.hypot(x - px_, y - py_) <= r and t > -0.05:
                s.add((x, y))
    s = {p for p in s if not (p[0] > 11.5 * k or p[1] < 1.5 * k)}
    shade_set(c, s, ORANGE, depth=2.5 * k, off=0.3)
    # ridges
    for i in range(3 if n == 14 else 6):
        t = 0.25 + i * (0.6 / (3 if n == 14 else 6))
        px_, py_ = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        x, y = round(px_), round(py_)
        if (x + 1, y + 1) in s:
            c.put(x + 1, y + 1, "rust")
        if (x + 2, y) in s and n == 28:
            c.put(x + 2, y, "rust")
    # leaves
    if n == 14:
        leaf(c, (10, 4), (13, 0), 1.2, 0.0, prof="blade", lit=3.0)
        leaf(c, (10, 4), (8, 0), 1.2, 0.0, prof="blade", lit=3.0)
        leaf(c, (11, 5), (13, 4), 1.0, 0.0, prof="blade")
    else:
        frond(c, (20, 9), (26, 1), 2.4, 0.1)
        frond(c, (19, 8), (14, 0), 2.4, -0.1)
        frond(c, (21, 10), (27, 9), 2.0, 0.0)
    return c


def item_potato(n):
    c = item_canvas(n)
    k = n / 14
    s = ellipse(6.5 * k, 7.0 * k, 6.0 * k, 4.6 * k) | ellipse(8.0 * k, 6.5 * k, 5.0 * k, 4.8 * k)
    s = {p for p in s if 0 <= p[0] < n and 0 <= p[1] < n}
    shade_set(c, s, SPUD, depth=3 * k, off=0.2)
    eyes = [(4, 6), (8, 5), (9, 9), (5, 9)] if n == 14 else [(6, 11), (14, 9), (19, 14), (10, 17), (16, 19), (7, 15)]
    for x, y in eyes:
        c.put(x, y, "brown_l")
        if n == 28:
            c.put(x + 1, y, "brown")
            c.put(x, y - 1, "tan")
    hl = [(4, 4), (5, 4)] if n == 14 else [(7, 7), (8, 7), (9, 7), (8, 6), (6, 8)]
    for p in hl:
        c.put(*p, "cream")
    return c


def item_tomato(n):
    c = item_canvas(n)
    k = n / 14
    r = 6.0 * k
    cx, cy = 7 * k - 0.5, 7.5 * k - 0.5
    spec = {(round(cx - r * 0.5), round(cy - r * 0.45)): "white"}
    if n == 28:
        spec.update({(round(cx - r * 0.5) + 1, round(cy - r * 0.45)): "white", (round(cx - r * 0.5), round(cy - r * 0.45) + 1): "white",
                     (round(cx - r * 0.5) + 1, round(cy - r * 0.45) + 1): "pink"})
    sphere(c, cx, cy, r + 0.4, r * 0.88, RED, spec=spec)
    if n == 14:
        calyx = ["..g..", "gGgGg", ".G.G."]
        c.stamp(5, 1, calyx, {"g": "green", "G": "green_d"})
        c.put(7, 0, "green_d")
    else:
        calyx = ["....gg....", "....gG....", "gg.gGGg.gg", ".gGGgGGgG.", "..GG..GG..", ".G......G."]
        c.stamp(9, 1, calyx, {"g": "green", "G": "green_d"})
    return c


def item_corn(n):
    c = item_canvas(n)
    k = n / 14
    # cob along the diagonal from bottom-left to top-right
    a = (4.0 * k, 10.0 * k)
    b = (10.5 * k, 2.5 * k)
    s = set()
    dx, dy = b[0] - a[0], b[1] - a[1]
    L2 = dx * dx + dy * dy
    for y in range(n):
        for x in range(n):
            t = ((x - a[0]) * dx + (y - a[1]) * dy) / L2
            if t < -0.15 or t > 1.1:
                continue
            tc = max(0.0, min(1.0, t))
            d = math.hypot(x - (a[0] + dx * tc), y - (a[1] + dy * tc))
            r = 2.6 * k * (1 - 0.35 * max(0.0, t) ** 2)
            if d <= r:
                s.add((x, y))
    cols = shade_set(c, s, KERNEL, depth=2.2 * k, off=0.3)
    # kernel grid: darken every other diagonal row
    for (x, y) in s:
        if (x - y) % 2 == 0 and ((x + y) % (3 if n == 28 else 2) == 0):
            cur = cols[(x, y)]
            i = KERNEL.index(cur) if cur in KERNEL else 2
            c.put(x, y, KERNEL[max(0, i - 1)])
    # husk
    if n == 14:
        leaf(c, (3, 13), (1, 5), 1.6, 0.1, prof="blade")
        leaf(c, (3, 13), (10, 11), 1.6, 0.1, prof="blade")
        leaf(c, (4, 12), (7, 6), 1.4, 0.0, prof="blade")
    else:
        leaf(c, (6, 27), (2, 10), 3.0, 0.1, prof="blade")
        leaf(c, (6, 27), (22, 23), 3.0, 0.1, prof="blade")
        leaf(c, (7, 25), (14, 14), 2.6, 0.0, prof="blade")
        for x, y in ((22, 4), (23, 3), (24, 2), (23, 5), (25, 3)):
            c.put(x, y, "tan")
    return c


def item_strawberry(n):
    c = item_canvas(n)
    k = n / 14
    s = set()
    for y in range(n):
        for x in range(n):
            # heart-ish cone: wide top, pointed bottom
            fy = (y - 3.0 * k) / (10.5 * k)
            if 0 <= fy <= 1:
                hw = (5.6 * k) * math.sqrt(max(0.0, 1 - fy ** 1.6)) * (1.0 if fy > 0.15 else 0.85 + fy)
                if abs(x - (n / 2 - 0.5)) <= hw:
                    s.add((x, y))
    shade_set(c, s, RED, depth=3 * k, off=0.25)
    seeds = [(4, 6), (8, 5), (6, 8), (10, 8), (5, 11), (8, 11)] if n == 14 else \
        [(6, 10), (11, 9), (16, 9), (21, 10), (8, 14), (13, 13), (18, 14), (10, 18), (15, 18), (20, 17), (12, 22), (16, 22), (6, 15)]
    for x, y in seeds:
        if (x, y) in s:
            c.put(x, y, "yellow")
            if n == 28 and (x + 1, y + 1) in s:
                c.put(x + 1, y + 1, "red_d")
    if n == 14:
        c.stamp(2, 0, ["....Gg....", ".gGgGGgGg.", "gG.G..G.Gg"], {"g": "green", "G": "green_d"})
    else:
        c.stamp(4, 0, ["........GG..........", "........Gg..........", "..gggGgGGGGgGggg....", ".gGGgGGgGgGGgGGGg...",
                       "gGG.gG..GGG..Gg.GGg.", "G....G...G....G...G."], {"g": "green", "G": "green_d"})
    return c


ITEMS = {"wheat": item_wheat, "carrot": item_carrot, "potato": item_potato, "tomato": item_tomato,
         "corn": item_corn, "strawberry": item_strawberry}
CROPS = {"wheat": wheat, "carrot": carrot, "potato": potato, "tomato": tomato, "corn": corn, "strawberry": strawberry}
TINT = {"wheat": ("amber", "clay"), "carrot": ("orange", "rust"), "potato": ("tan_d", "brown_l"),
        "tomato": ("red", "red_d"), "corn": ("yellow", "amber"), "strawberry": ("pink", "red")}


def seed_packet(cid, n):
    c = item_canvas(n)
    light, dark = TINT[cid]
    if n == 14:
        x0, x1, y0, y1 = 2, 11, 1, 13
    else:
        x0, x1, y0, y1 = 4, 23, 1, 27
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            v = 2.0 - (0.6 if x >= x1 - (1 if n == 14 else 2) else 0) - (0.5 if y >= y1 - 1 else 0) + (0.4 if x == x0 else 0)
            c.put(x, y, tone(PAPER, v, x, y))
    # crimped top
    for x in range(x0, x1 + 1):
        c.put(x, y0, "tan" if x % 2 else CLEAR)
        c.put(x, y0 + 1, "tan_d")
    # colour band + picture
    if n == 14:
        for y in range(y0 + 3, y0 + 10):
            for x in range(x0 + 1, x1):
                c.put(x, y, tone([dark, light], 1.0 if y < y0 + 8 else 0.0, x, y))
        mini = ITEMS[cid](14)
        sm = mini.im.resize((7, 7), resample=0)
        c.im.alpha_composite(sm, (x0 + 1, y0 + 3))
        c.px = c.im.load()
    else:
        for y in range(y0 + 4, y0 + 20):
            for x in range(x0 + 2, x1 - 1):
                c.put(x, y, tone([dark, light], 1.0 if y < y0 + 17 else 0.5 if y < y0 + 19 else 0.0, x, y))
        pic = ITEMS[cid](14)
        c.im.alpha_composite(pic.im, (x0 + 3, y0 + 4))
        c.px = c.im.load()
        for x in range(x0 + 3, x1 - 3, 2):
            c.put(x, y1 - 3, "tan_d")
            c.put(x + 1, y1 - 3, "tan_d")
    return c


def main():
    art = sys.argv[1]
    crops, items, big = Sprites(art, "crops"), Sprites(art, "items"), Sprites(art, "items_big")
    for cid, fn in CROPS.items():
        for st in range(4):
            save_crop(crops, f"{cid}_{st}", fn(st))
        save_crop(crops, f"{cid}_dead", fn(2, dead=True))
        for sp, n in ((items, 14), (big, 28)):
            body = ITEMS[cid](n).im
            out, pad = finish(body)
            sp.save(cid, out, n // 2 + pad, n // 2 + pad)
            body = seed_packet(cid, n).im
            out, pad = finish(body)
            sp.save(f"seed_{cid}", out, n // 2 + pad, n // 2 + pad)
    print(json.dumps({**crops.pivots, **items.pivots, **big.pivots}))


main()
