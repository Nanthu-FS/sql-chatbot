import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); from _pix import E, CLEAR, Sprites, bayer, finish, from_map, new, rng  # noqa: E401,E402
"""Crops B: pumpkin, sunflower, cabbage, eggplant, melon, grapes.

Pixel-direct: plants are built from small shaded primitives (balls, leaves, lobed
leaves, stems) plus hand-drawn character maps for the tiny stages, then finished with
the shared ink outline and drop shadow (finish()).

Crop canvas: 30x40, plant meets the soil at the pixel boundary (BX, BY); the sprite is
trimmed symmetrically so the pivot stays horizontally centred.
Writes crops/<id>_{0,1,2,3,dead}, items/<id>, items/seed_<id>, items_big/<id>, items_big/seed_<id>.
"""
import json  # noqa: E402
import math  # noqa: E402

W, H = 30, 40
BX, BY = 15, 36  # stem columns are 14 and 15; row BY is the first row below the soil line

L3 = (-0.56, -0.66, 0.50)
_n = math.sqrt(sum(c * c for c in L3))
L3 = tuple(c / _n for c in L3)


def P(c):
    return E[c] if isinstance(c, str) else c


class Cv:
    """A small RGBA canvas with helpers that take palette names."""

    def __init__(self, w=W, h=H):
        self.w, self.h = w, h
        self.im = new(w, h)
        self.p = self.im.load()

    def inb(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def set(self, x, y, c):
        if c is not None and self.inb(x, y):
            self.p[x, y] = P(c)

    def get(self, x, y):
        return self.p[x, y] if self.inb(x, y) else CLEAR

    def on(self, x, y):
        return self.inb(x, y) and self.p[x, y][3] > 0

    def clear(self, x, y):
        if self.inb(x, y):
            self.p[x, y] = CLEAR

    def map(self, rows, key, ox=0, oy=0):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in ". ":
                    continue
                if ch == "_":
                    self.clear(ox + x, oy + y)
                else:
                    self.set(ox + x, oy + y, key[ch])

    def paint(self, m, line=None):
        """Draw a shape dict {(x, y): colour}. line: colour drawn on existing pixels that
        border the shape, so overlapping parts separate (the outer edge is left for finish())."""
        if line:
            for (x, y) in m:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    q = (x + dx, y + dy)
                    if q not in m and self.on(*q):
                        self.set(q[0], q[1], line)
        for (x, y), c in m.items():
            self.set(x, y, c)

    def over(self, other, ox=0, oy=0):
        for y in range(other.h):
            for x in range(other.w):
                c = other.p[x, y]
                if c[3] > 0:
                    self.set(ox + x, oy + y, c)

    def copy(self):
        c = Cv(self.w, self.h)
        c.over(self)
        return c


# ---------------------------------------------------------------- shading primitives

def tone(ramp, v, x, y):
    """Pick a ramp colour for light level v (0..1) with a checker band between steps."""
    v = max(0.0, min(0.999, v))
    f = v * (len(ramp) - 1)
    i = int(f)
    fr = f - i
    if i + 1 >= len(ramp):
        return ramp[-1]
    if fr > 0.64:
        return ramp[i + 1]
    if fr > 0.36 and (x + y) % 2 == 0:
        return ramp[i + 1]
    return ramp[i]


def lit(nx, ny, nz):
    return max(0.0, nx * L3[0] + ny * L3[1] + nz * L3[2])


def ball(cx, cy, rx, ry, ramp, mod=None, gain=1.25, bias=0.0):
    """Shaded ellipse. mod(nx, ny, nz, v, x, y) may return a new v or a colour."""
    m = {}
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            nx = (x + 0.5 - cx) / rx
            ny = (y + 0.5 - cy) / ry
            r2 = nx * nx + ny * ny
            if r2 > 1.0:
                continue
            nz = math.sqrt(1.0 - r2)
            v = lit(nx, ny, nz) * gain + bias
            if mod:
                r = mod(nx, ny, nz, v, x, y)
                if r is None:
                    continue
                if isinstance(r, (str, tuple)):
                    m[(x, y)] = r
                    continue
                v = r
            m[(x, y)] = tone(ramp, v, x, y)
    return m


LEAF = ("teal_dd", "green_dd", "green_d", "green")
LEAF_LINE = "teal_dd"


def leaf(x0, y0, ang, length, wd, ramp=("green_dd", "green_d", "green"), bend=0.0, rib=True, tipc=None):
    """Pointed leaf from (x0, y0) along angle ang (degrees, 0 = right, -90 = up).
    The half facing the light is bright, the other half mid with a dark rim; the midrib
    is drawn in the mid tone on the bright half."""
    a = math.radians(ang)
    dx, dy = math.cos(a), math.sin(a)
    px_, py_ = -dy, dx
    lit_sign = 1 if (px_ * -0.6 + py_ * -0.8) > 0 else -1
    m = {}
    R = int(length + wd + 3)
    for y in range(int(y0) - R, int(y0) + R + 1):
        for x in range(int(x0) - R, int(x0) + R + 1):
            rx, ry = x + 0.5 - x0, y + 0.5 - y0
            u = rx * dx + ry * dy
            t = u / length
            if t < 0 or t > 1:
                continue
            v = rx * px_ + ry * py_ - bend * length * t * t
            hw = wd * math.sin(math.pi * (t ** 0.7)) + 0.2
            if abs(v) > hw:
                continue
            s = v * lit_sign
            if s >= 0:
                c = ramp[2]
                if rib and s < 0.75 and wd >= 2.2 and t < 0.85:
                    c = ramp[1]
            else:
                c = ramp[0] if -s > hw - 1.0 else ramp[1]
            if tipc and t > 0.88:
                c = tipc
            m[(x, y)] = c
    return m


def lobe_leaf(cx, cy, r, ramp=LEAF, lobes=5, base=(0.0, 1.0), veins=True, ry=None):
    """Round lobed leaf (squash, melon, grape) with scalloped edge and veins fanning from
    the stalk point (base given as a unit direction from the centre)."""
    ry = ry or r * 0.8
    bx, by = cx + base[0] * r * 0.8, cy + base[1] * ry * 0.8
    m = {}
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            nx = (x + 0.5 - cx) / r
            ny = (y + 0.5 - cy) / ry
            th = math.atan2(ny, nx) - math.atan2(base[1], base[0])
            rr = math.sqrt(nx * nx + ny * ny)
            edge = 0.84 + 0.16 * abs(math.cos(lobes * th / 2))
            if rr > edge:
                continue
            # notch at the stalk
            if math.cos(th) > 0.97 and rr > 0.55:
                continue
            nz = math.sqrt(max(0.0, 1 - rr * rr)) * 0.8 + 0.2
            v = lit(nx * 0.7, ny * 0.7, nz) * 1.3 - 0.1
            c = tone(ramp[1:], v, x, y)
            if rr > edge - 0.18 and v < 0.5:
                c = ramp[1]
            if veins:
                vx, vy = x + 0.5 - bx, y + 0.5 - by
                d = math.sqrt(vx * vx + vy * vy)
                if 1.5 < d < r * 1.1:
                    va = math.atan2(vy, vx) - math.atan2(-base[1], -base[0])
                    va = (va + math.pi) % (2 * math.pi) - math.pi
                    for k in (-1.0, 0.0, 1.0):
                        if abs(va - k * 0.85) * d < 0.55:
                            c = ramp[1] if c != ramp[1] else ramp[0]
            m[(x, y)] = c
    return m


def line(x0, y0, x1, y1, c, w=1):
    """Pixel line (w = 1 or 2 px wide; the second column goes to the right)."""
    m = {}
    n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(n + 1):
        t = i / n
        x = int(round(x0 + (x1 - x0) * t))
        y = int(round(y0 + (y1 - y0) * t))
        for k in range(w):
            m[(x + k, y)] = c(x + k, y, t) if callable(c) else c
    return m


def stem(points, cl="green_d", cd="green_dd", w=2):
    """Polyline stem; with w=2 the left column is lit and the right one shaded."""
    m = {}
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / n
            x = int(round(x0 + (x1 - x0) * t))
            y = int(round(y0 + (y1 - y0) * t))
            if w == 1:
                m[(x, y)] = cl
            else:
                m[(x, y)] = cl
                m[(x + 1, y)] = cd
    return m


def recolor(m, table):
    return {k: table.get(v, v) for k, v in m.items()}


def shift(m, dx, dy):
    return {(x + dx, y + dy): c for (x, y), c in m.items()}


# ---------------------------------------------------------------- shared small parts

MOUND = [
    ".....tTTt.....",
    "...tTTttTtbb..",
    "..tTttttttbbb.",
    ".bttttttbbbbBB",
    "..BBbbbbbBBBB.",
]
MOUND_KEY = {"T": "tan", "t": "tan_d", "b": "brown_l", "B": "brown"}


def mound(seeds):
    """Stage 0: soil mound (14x5) with seeds; seeds = [(x, y, map rows, key)]."""
    cv = Cv()
    ox, oy = BX - 7, BY - 3
    cv.map(MOUND, MOUND_KEY, ox, oy)
    for sx, sy, rows, key in seeds:
        cv.map(rows, key, ox + sx, oy + sy)
    return cv


# ---------------------------------------------------------------- output

def trim_save(S, name, cv, ax=None, ay=None):
    """Trim symmetrically around BX (or the given anchor), finish and save."""
    ax = BX if ax is None else ax
    ay = BY if ay is None else ay
    pts = [(x, y) for y in range(cv.h) for x in range(cv.w) if cv.on(x, y)]
    x0 = min(x for x, _ in pts)
    x1 = max(x for x, _ in pts)
    y0 = min(y for _, y in pts)
    y1 = max(y for _, y in pts)
    m = min(x0, cv.w - 1 - x1, ax - 0)
    body = cv.im.crop((m, y0, cv.w - m, y1 + 1))
    assert body.width <= 30 and body.height <= 38, (name, body.size)
    im, pad = finish(body)
    S.save(name, im, ax - m + pad, ay - y0 + pad)
    return im


def save_item(S, name, cv):
    im, pad = finish(cv.im)
    S.save(name, im, cv.w // 2 + pad, cv.h // 2 + pad)
    return im


DEAD_RAMP = ["brown_d", "brown", "brown_l", "tan_d", "tan"]


def lum(c):
    c = P(c)
    return 0.3 * c[0] + 0.55 * c[1] + 0.15 * c[2]


def wither(cv, sag=1.0, seed=1, keep=()):
    """Withered copy of a plant: colours mapped to a brown/grey ramp by brightness, outer
    parts sag downwards, a few edge pixels crumble away."""
    keep = {P(k) for k in keep}
    r = rng(seed)
    out = Cv(cv.w, cv.h)
    pix = {}
    for y in range(cv.h):
        for x in range(cv.w):
            c = cv.p[x, y]
            if c[3] == 0:
                continue
            if c in keep:
                pix[(x, y)] = c
                continue
            l = lum(c)
            if l < 50:
                nc = "brown_d"
            elif l < 95:
                nc = "brown"
            elif l < 140:
                nc = "brown_l" if (x + y) % 2 else "grey_d"
            elif l < 185:
                nc = "tan_d"
            else:
                nc = "tan"
            h = max(0, BY - y)
            dxc = abs(x + 0.5 - BX)
            dy = int(round(sag * (h * 0.12 + (dxc / 15.0) ** 1.6 * min(h, 14) * 0.35)))
            pix[(x, y + dy)] = P(nc)
    # gaps left by sagging: fill from the pixel above when both sides are filled
    for (x, y), c in pix.items():
        out.set(x, y, c)
    for y in range(out.h - 1, 0, -1):
        for x in range(out.w):
            if not out.on(x, y) and out.on(x, y - 1) and out.on(x, y + 1):
                out.set(x, y, out.get(x, y - 1))
    edge = [(x, y) for y in range(out.h) for x in range(out.w) if out.on(x, y)
            and sum(out.on(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) <= 2
            and y < BY - 2 and P(out.get(x, y)) not in keep]
    for x, y in edge:
        if r.random() < 0.35:
            out.clear(x, y)
    return out
