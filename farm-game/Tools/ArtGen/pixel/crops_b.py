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


def lobe_leaf(cx, cy, r, ramp=LEAF, lobes=5, base=(0.0, 1.0), veins=True, ry=None, depth=0.22):
    """Broad lobed leaf (squash, melon, grape) seen from above: scalloped edge, lit
    upper-left, dark rim lower-right and light veins fanning from the stalk point
    (base is a unit direction from the centre to the stalk)."""
    ry = ry or r * 0.72
    bx, by = cx + base[0] * r * 0.75, cy + base[1] * ry * 0.75
    ba = math.atan2(base[1] * r, base[0] * ry)
    m = {}
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            nx = (x + 0.5 - cx) / r
            ny = (y + 0.5 - cy) / ry
            th = math.atan2(ny, nx) - ba
            rr = math.sqrt(nx * nx + ny * ny)
            edge = 1.0 - depth + depth * abs(math.cos(lobes * (th - math.pi) / 2))
            if rr > edge:
                continue
            if math.cos(th) > 0.96 and rr > 0.5:
                continue  # notch at the stalk
            nz = math.sqrt(max(0.0, 1 - rr * rr))
            v = lit(nx * 0.55, ny * 0.55, 0.45 + 0.55 * nz) * 1.25 - 0.05
            c = tone(ramp[1:], v, x, y)
            if rr > edge - 0.2 and v < 0.55:
                c = ramp[1]
            if rr > edge - 0.2 and v < 0.3:
                c = ramp[0]
            if veins:
                vx, vy = x + 0.5 - bx, y + 0.5 - by
                d = math.sqrt(vx * vx + vy * vy)
                if 1.0 < d < r * 1.3 and rr < edge - 0.12:
                    va = math.atan2(vy / ry, vx / r) - (ba + math.pi)
                    va = (va + math.pi) % (2 * math.pi) - math.pi
                    for k in (-2, -1, 0, 1, 2):
                        if abs(va - k * 0.62) * d < 0.6:
                            c = ramp[3] if c in (ramp[1], ramp[2]) else ramp[2]
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


def tendril(x, y, d=1, c="green_d"):
    """A tiny curly vine tendril (5x5), d = +1 curls right, -1 left."""
    rows = [".ccc.", "c...c", "c.c.c", "c..c.", ".c..."]
    m = {}
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "c":
                m[(x + (i - 2) * d, y + j - 4)] = c
    return m


# ---------------------------------------------------------------- shared small parts

MOUND = [
    ".....tTTt.....",
    "...tTTttTtbb..",
    "..tTttttttbbb.",
    ".bttttttbbbbBB",
    "..BBbbbbbBBBB.",
]
MOUND_KEY = {"T": "tan_d", "t": "brown_l", "b": "brown", "B": "brown_d"}


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


# ================================================================ pumpkin

PUMPKIN = ("red_d", "rust", "orange", "amber", "yellow")
UNRIPE = ("green_dd", "green_d", "green", "yellow")


def pumpkin_ball(cx, cy, rx, ry, ramp=PUMPKIN, ribs=(-0.6, -0.2, 0.2, 0.6), spec=True):
    def mod(nx, ny, nz, v, x, y):
        if abs(nx) < 0.14 and ny < -0.8:
            return None  # crease where the stem sits
        lon = math.asin(max(-1.0, min(1.0, nx / max(0.25, math.sqrt(1 - ny * ny))))) / (math.pi / 2)
        edges = (-1.0,) + tuple(ribs) + (1.0,)
        for a, b in zip(edges, edges[1:]):
            if a <= lon <= b:
                s = (lon - (a + b) / 2) / ((b - a) / 2)
                v += 0.16 * (1 - s * s) - 0.08
        for rb in ribs:
            if abs(lon - rb) < 0.06 + 0.05 * (1 - abs(ny)):
                return ramp[1] if v > 0.45 else ramp[0]
        if ny > 0.82:
            v -= 0.25
        return v
    m = ball(cx, cy, rx, ry, ramp[:4], mod, gain=1.3)
    if spec:
        # glossy dashes on the two upper-left segments
        for x, y in ((cx - rx * 0.6, cy - ry * 0.35), (cx - rx * 0.6, cy - ry * 0.2), (cx - rx * 0.25, cy - ry * 0.55)):
            q = (int(x), int(y))
            if q in m:
                m[q] = ramp[4]
    return m


def crumble(m, seed, p=0.3, holes=0, hole_c="brown_d"):
    """Withered look: nibble edge pixels away and punch a few dark holes."""
    r = rng(seed)
    out = dict(m)
    for (x, y) in m:
        n = sum((x + dx, y + dy) in m for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if n < 4 and r.random() < p:
            del out[(x, y)]
    inner = [k for k in out if all((k[0] + dx, k[1] + dy) in out for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for k in r.sample(inner, min(holes, len(inner))):
        out[k] = hole_c
    return out


DEAD_LEAF = ("brown_d", "brown", "brown_l", "tan_d")
DEAD_STEM = ("brown_l", "brown")
DEAD_LINE = "brown_d"


def wilt_leaf(cx, cy, r, side, seed):
    """A lobe leaf that has dried and hangs: flattened, dropped and nibbled."""
    m = lobe_leaf(cx, cy + r * 0.25, r * 0.95, ramp=DEAD_LEAF, base=(-0.45 * side, -0.9), ry=r * 0.7, depth=0.32)
    return crumble(m, seed, 0.3, holes=2)


def pumpkin_stages():
    st = {}
    seed = (["wc", "cB"], {"w": "white", "c": "cream", "B": "tan_d"})
    st[0] = mound([(3, 1) + seed, (7, 0) + seed, (9, 2) + seed])

    cv = Cv()
    cv.paint(stem([(14, BY - 1), (14, BY - 5)]))
    cv.paint(leaf(14, BY - 5, -160, 7, 2.6))
    cv.paint(leaf(16, BY - 5, -20, 7, 2.6))
    st[1] = cv

    def grow(dead):
        cv = Cv()
        sl, sd = DEAD_STEM if dead else ("green_d", "green_dd")
        ln = DEAD_LINE if dead else LEAF_LINE
        if dead:
            cv.paint(stem([(14, BY - 1), (14, BY - 5), (17, BY - 8)], sl, sd))
            cv.paint(stem([(14, BY - 1), (7, BY - 2)], sl, w=1))
            cv.paint(stem([(15, BY - 1), (22, BY - 3)], sl, w=1))
            cv.paint(wilt_leaf(18, BY - 9, 6, 1, 11), ln)
            cv.paint(wilt_leaf(7, BY - 5, 5.5, -1, 12), ln)
            cv.paint(wilt_leaf(23, BY - 5, 5.5, 1, 13), ln)
            cv.paint(pumpkin_ball(11, BY - 1.5, 4, 2.6, ("brown_d", "brown", "brown_l", "tan_d"), ribs=(-0.3, 0.3), spec=False), "brown_d")
            return cv
        cv.paint(stem([(14, BY - 1), (14, BY - 9)], sl, sd))
        cv.paint(stem([(14, BY - 2), (7, BY - 4)], sl, w=1))
        cv.paint(stem([(15, BY - 2), (22, BY - 5)], sl, w=1))
        cv.paint(lobe_leaf(15, BY - 13, 6.5, base=(0, 1)), ln)
        cv.paint(lobe_leaf(7, BY - 8, 6, base=(0.6, 0.8)), ln)
        cv.paint(lobe_leaf(23, BY - 8, 6, base=(-0.6, 0.8)), ln)
        cv.paint(pumpkin_ball(11, BY - 1.5, 4, 2.8, UNRIPE, ribs=(-0.3, 0.3), spec=False), "ink")
        cv.map([".y.", "yay", ".y."], {"y": "yellow", "a": "orange"}, 20, BY - 4)
        return cv

    st[2] = grow(False)
    st["dead"] = grow(True)

    cv = Cv()
    cv.paint(lobe_leaf(15, BY - 17, 6.5, base=(0, 1)), LEAF_LINE)
    cv.paint(lobe_leaf(6, BY - 13, 6.5, base=(0.6, 0.8)), LEAF_LINE)
    cv.paint(lobe_leaf(24, BY - 13, 6.5, base=(-0.6, 0.8)), LEAF_LINE)
    cv.paint(tendril(26, BY - 5, 1), LEAF_LINE)
    cv.paint(pumpkin_ball(15, BY - 6, 12, 8.4), "ink")
    cv.map(["..ss", ".sS.", ".sS."], {"s": "tan_d", "S": "brown"}, 13, BY - 17)
    st[3] = cv
    return st


# ================================================================ sunflower

PETAL = ("orange", "amber", "yellow")
DISC = ("brown_d", "brown", "rust")


def flower_head(cx, cy, R, n=12, ry=None, petal=PETAL, disc=DISC, inner=0.5):
    """Sunflower head: pointed petals around a seeded disc. Returns a shape dict."""
    ry = ry or R
    m = {}
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - R) - 1, int(cx + R) + 2):
            nx = (x + 0.5 - cx) / R
            ny = (y + 0.5 - cy) / ry
            rr = math.sqrt(nx * nx + ny * ny)
            th = math.atan2(ny, nx)
            sec = (th / (2 * math.pi) * n + 0.5) % 1.0
            sv = abs(sec - 0.5) * 2  # 0 at petal centre, 1 between petals
            tip = 0.62 + 0.38 * (1 - sv) ** 1.4
            if rr > tip:
                continue
            if rr < inner:
                q = rr / inner
                dv = lit(nx / inner * 0.8, ny / inner * 0.8, math.sqrt(max(0, 1 - q * q))) * 1.3
                c = tone(disc, dv, x, y)
                if q < 0.75 and (x + y) % 2 == 0 and c != disc[2]:
                    c = disc[0] if c == disc[1] else disc[1]
                if q > 0.86:
                    c = disc[0]
                m[(x, y)] = c
                continue
            v = lit(nx * 0.6, ny * 0.6, 0.8) * 1.35 - 0.1
            c = tone(petal, v, x, y)
            if sv > 0.78 or rr < inner + 0.1:
                c = petal[0] if c != petal[0] else "rust"
            m[(x, y)] = c
    return m


def sunflower_stages():
    st = {}
    seed = (["wS", "SS"], {"w": "cream", "S": "slate_d"})
    st[0] = mound([(3, 1) + seed, (7, 0) + seed, (9, 2) + seed])

    cv = Cv()
    cv.paint(stem([(14, BY - 1), (14, BY - 7)]))
    cv.paint(leaf(14, BY - 7, -150, 6, 2.0))
    cv.paint(leaf(16, BY - 7, -30, 6, 2.0))
    st[1] = cv

    def grow(dead):
        cv = Cv()
        if dead:
            lr, sl, sd, ln = DEAD_LEAF[:3], "brown_l", "brown", DEAD_LINE
            cv.paint(stem([(14, BY - 1), (14, BY - 12), (16, BY - 18), (19, BY - 21)], sl, sd))
            for x0, y0, a, ln_, wd in ((14, BY - 4, 120, 8, 2.8), (16, BY - 8, 65, 8, 2.8), (15, BY - 13, 140, 6, 2.2), (17, BY - 17, 50, 6, 2.2)):
                cv.paint(crumble(leaf(x0, y0, a, ln_, wd, lr, bend=0.15), x0 + y0, 0.2), ln)
            cv.paint(crumble(ball(20, BY - 18.5, 3, 3.5, ("brown_d", "brown", "grey_d", "grey")), 5, 0.1), "ink")
            return cv
        cv.paint(stem([(14, BY - 1), (14, BY - 22)]))
        cv.paint(leaf(14, BY - 4, 168, 9, 3.4, bend=0.12), LEAF_LINE)
        cv.paint(leaf(16, BY - 8, 12, 9, 3.4, bend=0.12), LEAF_LINE)
        cv.paint(leaf(14, BY - 13, -165, 7, 2.8, bend=0.1), LEAF_LINE)
        cv.paint(leaf(16, BY - 17, -15, 6, 2.4, bend=0.1), LEAF_LINE)
        bud = ball(15, BY - 24, 3.6, 3.4, ("green_dd", "green_d", "green"))
        cv.paint(bud, "ink")
        cv.map(["y..y", ".yy."], {"y": "amber"}, 13, BY - 28)
        cv.set(12, BY - 25, "amber")
        cv.set(17, BY - 26, "yellow")
        return cv

    st[2] = grow(False)
    st["dead"] = grow(True)

    cv = Cv()
    cv.paint(stem([(14, BY - 1), (14, BY - 25)]))
    cv.paint(leaf(14, BY - 4, 168, 10, 3.8, bend=0.12), LEAF_LINE)
    cv.paint(leaf(16, BY - 9, 12, 10, 3.8, bend=0.12), LEAF_LINE)
    cv.paint(leaf(14, BY - 14, -168, 8, 3.0, bend=0.1), LEAF_LINE)
    cv.paint(leaf(16, BY - 18, -12, 7, 2.6, bend=0.1), LEAF_LINE)
    cv.paint(flower_head(15, BY - 27.5, 9.5, n=13), "ink")
    st[3] = cv
    return st


# ================================================================ cabbage

CAB_LEAF = ("teal_dd", "green_dd", "green_d", "green")
CAB_HEAD = ("green_d", "green", "yellow")


def cabbage_head(cx, cy, rx, ry, ramp=CAB_HEAD, line="green_d"):
    """Tight cabbage head: shaded ball with curved leaf-wrap seams."""
    def mod(nx, ny, nz, v, x, y):
        # seams: arcs of overlapping leaves from the top centre
        for k, off in ((1, -0.15), (-1, 0.2)):
            a = nx * k + 0.55 * (ny + 0.2) ** 2 + off
            if abs(a) < 0.07 and ny > -0.7:
                return line if v < 0.95 else ramp[1]
        return v
    return ball(cx, cy, rx, ry, ramp, mod, gain=1.35, bias=0.05)


def cabbage_stages():
    st = {}
    seed = (["oB"], {"o": "brown_d", "B": "brown_d"})
    st[0] = mound([(3, 1, ["o"], {"o": "brown_d"}), (6, 0, ["o"], {"o": "brown_d"}), (8, 2, ["o"], {"o": "brown_d"}),
                   (10, 1, ["o"], {"o": "brown_d"}), (5, 2, ["o"], {"o": "brown_d"})])
    st[0].map(["w"], {"w": "tan"}, BX - 7 + 3, BY - 3)
    del seed

    cv = Cv()
    cv.paint(stem([(14, BY - 1), (14, BY - 3)]))
    cv.paint(lobe_leaf(10, BY - 5, 3.6, CAB_LEAF, lobes=2, base=(0.8, 0.5), depth=0.1, veins=False))
    cv.paint(lobe_leaf(20, BY - 5, 3.6, CAB_LEAF, lobes=2, base=(-0.8, 0.5), depth=0.1, veins=False))
    st[1] = cv

    def grow(dead):
        cv = Cv()
        lf = DEAD_LEAF if dead else CAB_LEAF
        ln = DEAD_LINE if dead else LEAF_LINE
        sq = 0.75 if dead else 1.0
        d = 2 if dead else 0
        for cx, cy, r, b in ((8, BY - 7, 6, (0.7, 0.6)), (22, BY - 7, 6, (-0.7, 0.6)), (15, BY - 9, 6, (0, 1))):
            m = lobe_leaf(cx, cy + d, r, lf, lobes=3, base=b, depth=0.12, ry=r * 0.7 * sq)
            cv.paint(crumble(m, cx, 0.25, 2) if dead else m, ln)
        head = cabbage_head(15, BY - 5 + d * 0.5, 5, 4.2 * sq, ("brown_d", "brown", "tan_d") if dead else CAB_HEAD,
                            "brown_d" if dead else "green_d")
        cv.paint(head, ln)
        for cx, cy, r, b in ((9, BY - 2, 5.5, (0.8, -0.4)), (21, BY - 2, 5.5, (-0.8, -0.4))):
            m = lobe_leaf(cx, cy + d * 0.5, r, lf, lobes=3, base=b, depth=0.12, ry=r * 0.62 * sq)
            cv.paint(crumble(m, cy + cx, 0.25, 2) if dead else m, ln)
        return cv

    st[2] = grow(False)
    st["dead"] = grow(True)

    cv = Cv()
    for cx, cy, r, b in ((6, BY - 9, 6.5, (0.7, 0.6)), (24, BY - 9, 6.5, (-0.7, 0.6)), (15, BY - 13, 6.5, (0, 1))):
        cv.paint(lobe_leaf(cx, cy, r, CAB_LEAF, lobes=3, base=b, depth=0.12, ry=r * 0.75), LEAF_LINE)
    cv.paint(cabbage_head(15, BY - 9, 8, 7.2), LEAF_LINE)
    for cx, cy, r, b in ((7, BY - 3, 6.5, (0.8, -0.3)), (23, BY - 3, 6.5, (-0.8, -0.3))):
        cv.paint(lobe_leaf(cx, cy, r, CAB_LEAF, lobes=3, base=b, depth=0.12, ry=r * 0.6), LEAF_LINE)
    st[3] = cv
    return st


# ================================================================ eggplant

EGG = ("brown_d", "purple", "magenta")


def pod(x0, y0, ang, length, r0, r1, ramp=EGG, spec="pink", curve=0.0):
    """Elongated fruit hanging from (x0, y0): narrow at the stem end, fat at the tip.
    Shaded as a surface of revolution about its (slightly curved) axis."""
    a = math.radians(ang)
    dx, dy = math.cos(a), math.sin(a)
    px_, py_ = -dy, dx
    m = {}
    R = int(length + r1 + 3)
    best = {}
    for y in range(int(y0) - R, int(y0) + R + 1):
        for x in range(int(x0) - R, int(x0) + R + 1):
            rx, ry = x + 0.5 - x0, y + 0.5 - y0
            u = rx * dx + ry * dy
            v = rx * px_ + ry * py_
            t = u / length
            v -= curve * length * t * t
            if t < -0.05 or t > 1.0 + r1 / length:
                continue
            tt = min(max(t, 0.0), 1.0)
            rad = r0 + (r1 - r0) * (math.sin(min(1.0, tt / 0.75) * math.pi / 2) ** 1.5)
            if t > 1.0:
                # round cap
                e = (t - 1.0) * length
                if e * e + v * v > r1 * r1:
                    continue
                rad = math.sqrt(max(0.01, r1 * r1 - e * e))
                nu = e / r1
            else:
                nu = 0.0
            if abs(v) > rad:
                continue
            nv = v / max(rad, 0.01)
            nz = math.sqrt(max(0.0, 1 - nv * nv - nu * nu))
            nxs = nv * px_ + nu * dx
            nys = nv * py_ + nu * dy
            val = lit(nxs, nys, nz) * 1.35
            m[(x, y)] = tone(ramp, val, x, y)
            best[(x, y)] = val
    if spec and best:
        top = sorted(best.items(), key=lambda kv: -kv[1])[:2]
        for (q, _) in top:
            m[q] = spec
    return m


def calyx(x, y, c="green_d", d="green_dd"):
    """Little green cap with drooping sepals, centred on (x, y) = stem end of the fruit."""
    rows = [".cS.", "cccS", "c.dS", "c..d"]
    m = {}
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                m[(x + i - 2, y + j - 1)] = {"c": c, "S": d, "d": d}[ch]
    return m


def eggplant_stages():
    st = {}
    seed = (["c"], {"c": "cream"})
    st[0] = mound([(3, 1) + seed, (6, 0) + seed, (8, 2) + seed, (10, 1) + seed, (5, 2) + seed])

    cv = Cv()
    cv.paint(stem([(14, BY - 1), (14, BY - 6)], "purple", "brown_d"))
    cv.paint(leaf(14, BY - 6, -155, 6, 2.0))
    cv.paint(leaf(16, BY - 6, -25, 6, 2.0))
    st[1] = cv

    def bush(cv, h, k=1.0, lf=("green_dd", "green_d", "green"), sl="purple", sd="brown_d", ln=LEAF_LINE, dead=False):
        cv.paint(stem([(14, BY - 1), (14, BY - h)], sl, sd))
        cv.paint(stem([(14, BY - 6), (9, BY - 11)], sl, w=1))
        cv.paint(stem([(15, BY - 8), (20, BY - 13)], sl, w=1))
        spec = [(14, BY - 3, 160, 9, 3.4), (16, BY - 5, 20, 9, 3.4), (10, BY - 11, -150, 8, 3.2),
                (19, BY - 13, -30, 8, 3.2), (14, BY - h + 1, -110, 7, 2.8), (16, BY - h + 1, -70, 7, 2.8)]
        for i, (x0, y0, a, ln_, wd) in enumerate(spec):
            if dead:
                a = 90 + (a - 90) * 0.35 if a > -90 else 90 - (-90 - a) * 0.0 + (a + 90) * -0.3
                m = crumble(leaf(x0, y0 + 2, 120 if x0 < 15 else 60, ln_ * 0.85, wd * 0.8, lf, bend=0.1), i + 7, 0.25)
            else:
                m = leaf(x0, y0, a, ln_ * k, wd * k, lf, bend=0.08)
            cv.paint(m, ln)
        return cv

    def grow(dead):
        cv = Cv()
        if dead:
            bush(cv, 16, lf=DEAD_LEAF[:3], sl="brown_l", sd="brown", ln=DEAD_LINE, dead=True)
            return cv
        bush(cv, 20)
        cv.map([".m.", "mym", ".m."], {"m": "magenta", "y": "yellow"}, 19, BY - 9)
        cv.map([".p.", "pyp", ".p."], {"p": "magenta", "y": "yellow"}, 8, BY - 15)
        return cv

    st[2] = grow(False)
    st["dead"] = grow(True)

    cv = Cv()
    bush(cv, 24, k=1.1)
    cv.paint(pod(19, BY - 12, 80, 8, 1.8, 3.6, curve=-0.05), "ink")
    cv.paint(calyx(19, BY - 12), "ink")
    cv.paint(pod(9, BY - 14, 100, 7, 1.6, 3.0, curve=0.05), "ink")
    cv.paint(calyx(9, BY - 14), "ink")
    st[3] = cv
    return st


# ================================================================ melon

MELON = ("green_dd", "green_d", "green", "yellow")


def melon_ball(cx, cy, rx, ry, dark="green_dd", ramp=MELON, n=3):
    """Watermelon: shaded oval with jagged dark stripes running end to end."""
    def mod(nx, ny, nz, v, x, y):
        lat = math.asin(max(-1.0, min(1.0, ny / max(0.2, math.sqrt(max(0.04, 1 - nx * nx))))))
        jag = 0.12 if (x // 1) % 2 else -0.04
        s = math.sin((lat / (math.pi / 2)) * n * math.pi / 1.0 * 0.5 * 2 + jag * 3)
        if s > 0.55:
            return dark if v < 0.9 else ramp[1]
        return v
    return ball(cx, cy, rx, ry, ramp, mod, gain=1.3, bias=0.05)


def melon_stages():
    st = {}
    seed = (["S", "S"], {"S": "ink"})
    st[0] = mound([(3, 1) + seed, (7, 0) + seed, (10, 1) + seed])
    st[0].map(["w"], {"w": "grey"}, BX - 7 + 7, BY - 3)

    cv = Cv()
    cv.paint(stem([(14, BY - 1), (14, BY - 4)]))
    cv.paint(leaf(14, BY - 4, -165, 6, 2.5))
    cv.paint(leaf(16, BY - 4, -15, 6, 2.5))
    cv.paint(lobe_leaf(15, BY - 7, 3, LEAF, lobes=3, base=(0, 1), depth=0.3, veins=False))
    st[1] = cv

    def grow(dead):
        cv = Cv()
        if dead:
            cv.paint(stem([(14, BY - 1), (7, BY - 2)], "brown_l", w=1))
            cv.paint(stem([(15, BY - 1), (23, BY - 3)], "brown_l", w=1))
            cv.paint(wilt_leaf(8, BY - 5, 5.5, -1, 21), DEAD_LINE)
            cv.paint(wilt_leaf(22, BY - 5, 5.5, 1, 22), DEAD_LINE)
            cv.paint(wilt_leaf(15, BY - 7, 5.5, 1, 23), DEAD_LINE)
            cv.paint(melon_ball(17, BY - 1.5, 4, 2.6, "brown_d", ("brown_d", "brown", "brown_l", "tan_d")), "brown_d")
            return cv
        cv.paint(stem([(14, BY - 1), (6, BY - 4)], w=1))
        cv.paint(stem([(15, BY - 1), (24, BY - 4)], w=1))
        cv.paint(lobe_leaf(15, BY - 11, 6, base=(0, 1), depth=0.32), LEAF_LINE)
        cv.paint(lobe_leaf(7, BY - 7, 5.5, base=(0.6, 0.8), depth=0.32), LEAF_LINE)
        cv.paint(lobe_leaf(23, BY - 7, 5.5, base=(-0.6, 0.8), depth=0.32), LEAF_LINE)
        cv.paint(tendril(4, BY - 2, -1), LEAF_LINE)
        cv.paint(melon_ball(18, BY - 1.5, 4.5, 2.8, n=2), "ink")
        cv.map([".y.", "yay", ".y."], {"y": "yellow", "a": "orange"}, 9, BY - 3)
        return cv

    st[2] = grow(False)
    st["dead"] = grow(True)

    cv = Cv()
    cv.paint(lobe_leaf(15, BY - 15, 6.5, base=(0, 1), depth=0.32), LEAF_LINE)
    cv.paint(lobe_leaf(5, BY - 10, 6, base=(0.6, 0.8), depth=0.32), LEAF_LINE)
    cv.paint(lobe_leaf(25, BY - 10, 6, base=(-0.6, 0.8), depth=0.32), LEAF_LINE)
    cv.paint(tendril(3, BY - 2, -1), LEAF_LINE)
    cv.paint(melon_ball(15, BY - 5.5, 12.5, 7.6), "ink")
    cv.paint(stem([(9, BY - 12), (8, BY - 14)], "green_d", w=1), "ink")
    st[3] = cv
    return st


# ================================================================ grapes

WOOD = ("brown_d", "brown", "brown_l", "tan_d")
GRAPE = ("brown_d", "purple", "magenta")


def trellis():
    """Small wooden trellis: two posts, a top beam and a middle rail (front-facing)."""
    cv = Cv()
    top = BY - 33
    for x in (4, 24):
        for y in range(top, BY + 1):
            cv.set(x, y, "brown_l")
            cv.set(x + 1, y, "brown")
        cv.set(x, top - 1, "tan_d")
        cv.set(x + 1, top - 1, "brown_l")
        for y in (top + 7, top + 16, top + 25):
            cv.set(x, y, "tan_d")
    for x in range(2, 28):
        cv.set(x, top + 1, "tan_d")
        cv.set(x, top + 2, "brown_l")
        cv.set(x, top + 3, "brown")
        cv.set(x, BY - 16, "brown_l")
        cv.set(x, BY - 15, "brown")
    for x in (7, 13, 20):
        cv.set(x, top + 2, "brown")
    for x in (4, 5, 24, 25):
        cv.set(x, top + 4, "brown_d")
        cv.set(x, BY - 14, "brown_d")
    return cv


def bunch(x, y, rows=(4, 3, 3, 2, 1), ramp=GRAPE, spec="pink", sep="brown_d"):
    """Grape bunch hanging from (x, y): rows of 3px berries, narrowing downwards."""
    m = {}
    m[(x, y)] = "green_d"
    m[(x, y + 1)] = "green_dd"
    gy = y + 2
    for i, n in enumerate(rows):
        x0 = x - n * 3 / 2.0 + (0.5 if i % 2 else 0.0)
        for k in range(n):
            bx = x0 + k * 3 + 1.5
            berry = ball(bx, gy + 1.5, 1.7, 1.7, ramp, gain=1.4)
            for q, c in berry.items():
                if q in m and m[q] not in ramp and m[q] != spec:
                    continue
                m[q] = c
            m[(int(bx - 0.5), int(gy + 0.5))] = spec if i < 2 else ramp[2]
        gy += 2
    # separate berries: darken pixels that neighbour a berry of the next row
    return m


def grapes_stages():
    st = {}
    seed = (["c"], {"c": "tan"})
    cv = trellis()
    cv.over(mound([(3, 1) + seed, (6, 0) + seed, (8, 2) + seed, (10, 1) + seed]))
    st[0] = cv

    cv = trellis()
    cv.paint(stem([(14, BY - 1), (14, BY - 5)], "brown_l", "brown"))
    cv.paint(leaf(14, BY - 5, -150, 6, 2.2))
    cv.paint(leaf(16, BY - 5, -30, 6, 2.2))
    st[1] = cv

    def vine(cv, h, lf=LEAF, sl="brown_l", sd="brown", ln=LEAF_LINE, dead=False, full=False):
        top = BY - 33
        cv.paint(stem([(14, BY - 1), (13, BY - 8), (15, BY - 14), (14, BY - 20), (14, BY - h)], sl, sd))
        if h >= 30:
            cv.paint(stem([(14, top + 3), (8, top + 4), (3, top + 3)], sl, w=1))
            cv.paint(stem([(15, top + 3), (21, top + 4), (26, top + 3)], sl, w=1))
        spots = [(9, BY - 19, 4.5, (0.7, 0.6)), (20, BY - 18, 4.5, (-0.7, 0.6)), (11, BY - 9, 4, (0.8, 0.4))]
        if h >= 30:
            spots += [(7, top + 5, 5, (0.6, -0.6)), (22, top + 5, 5, (-0.6, -0.6)), (15, top + 3, 4.5, (0, 1))]
        if full:
            spots += [(19, BY - 7, 4, (-0.8, 0.4)), (3, top + 9, 3.5, (0.5, -0.8)), (26, top + 9, 3.5, (-0.5, -0.8))]
        for i, (cx, cy, r, b) in enumerate(spots):
            if dead:
                m = crumble(lobe_leaf(cx, cy + 2, r * 0.9, lf, lobes=5, base=(b[0] * 0.5, -0.9), depth=0.35, ry=r * 0.6), i + 40, 0.3, 1)
            else:
                m = lobe_leaf(cx, cy, r, lf, lobes=5, base=b, depth=0.3)
            cv.paint(m, ln)
        return cv

    def grow(dead):
        cv = trellis()
        if dead:
            vine(cv, 30, DEAD_LEAF, "brown", "brown_d", DEAD_LINE, dead=True)
            return cv
        vine(cv, 30)
        cv.paint(bunch(9, BY - 27, rows=(2, 2, 1), ramp=("green_dd", "green_d", "green"), spec="yellow"), "ink")
        cv.paint(bunch(20, BY - 27, rows=(2, 1), ramp=("green_dd", "green_d", "green"), spec="yellow"), "ink")
        return cv

    st[2] = grow(False)
    st["dead"] = grow(True)

    cv = trellis()
    vine(cv, 30, full=True)
    cv.paint(bunch(8, BY - 28), "ink")
    cv.paint(bunch(21, BY - 28, rows=(3, 3, 2, 1)), "ink")
    cv.paint(bunch(14, BY - 16, rows=(3, 2, 1)), "ink")
    st[3] = cv
    return st


# ================================================================ items
#
# items/<id> (14x14) and items_big/<id> (28x28): the harvested produce, drawn with the same
# primitives as the ripe stage-3 crops so they read as the same fruit. Like crops_a's icons the
# produce fills its box, is lit from the top-left with a white/cream glint and has its darkest
# ramp step bottom-right; finish() adds the outline and drop shadow. Pivot = centre.

def icon_pumpkin(n):
    cv = Cv(n, n)
    if n == 14:
        cv.paint(pumpkin_ball(7, 8.3, 7, 5.7, ribs=(-0.52, -0.17, 0.17, 0.52)))
        cv.map(["..sS", ".sS.", ".sS."], {"s": "tan_d", "S": "brown"}, 5, 0)
        cv.map(["gG", "G."], {"g": "green", "G": "green_d"}, 9, 1)
        return cv
    cv.paint(pumpkin_ball(14, 16.4, 14, 11.4, ribs=(-0.55, -0.2, 0.2, 0.55)))
    for q in ((6, 10), (7, 9), (8, 9), (9, 8), (5, 12)):
        cv.set(*q, "yellow")
    cv.paint(lobe_leaf(20.5, 4.5, 5.2, base=(-0.8, 0.6), depth=0.3), LEAF_LINE)
    cv.paint(tendril(6, 6, -1), LEAF_LINE)
    cv.map(["....tS", "...tsS", "..tsS.", "..ssS.", "..ssS.", "..ssS."], {"t": "tan", "s": "tan_d", "S": "brown"}, 10, 0)
    return cv


def icon_sunflower(n):
    cv = Cv(n, n)
    if n == 14:
        cv.paint(flower_head(7, 7, 7.1, n=11, inner=0.48))
        return cv
    cv.paint(flower_head(14, 14, 14.1, n=14, inner=0.46))
    return cv


def icon_cabbage(n):
    cv = Cv(n, n)
    if n == 14:
        cv.paint(lobe_leaf(3.5, 9.5, 3.6, CAB_LEAF, lobes=3, base=(0.8, -0.3), depth=0.12, veins=False))
        cv.paint(lobe_leaf(10.5, 9.5, 3.6, CAB_LEAF, lobes=3, base=(-0.8, -0.3), depth=0.12, veins=False))
        cv.paint(cabbage_head(7, 6.5, 6, 5.6), LEAF_LINE)
        return cv
    for cx, cy, b in ((6, 19.5, (0.8, -0.3)), (22, 19.5, (-0.8, -0.3))):
        cv.paint(lobe_leaf(cx, cy, 6.8, CAB_LEAF, lobes=3, base=b, depth=0.12, ry=6.8 * 0.72), LEAF_LINE)
    cv.paint(cabbage_head(14, 12.5, 11.5, 10.8), LEAF_LINE)
    return cv


def icon_eggplant(n):
    cv = Cv(n, n)
    if n == 14:
        cv.paint(pod(10, 4, 132, 6.5, 1.6, 3.6, curve=-0.04))
        cv.map([".gS", "ggS", "gSS", "S.."], {"g": "green_d", "S": "green_dd"}, 9, 1)
        cv.set(12, 0, "green_d")
        return cv
    cv.paint(pod(20, 8, 132, 13, 3.2, 7.2, curve=-0.04, spec=None))
    return cv


def icon_melon(n):
    cv = Cv(n, n)
    if n == 14:
        cv.paint(melon_ball(7, 7.6, 7, 6, n=3))
        cv.map([".g", "G."], {"g": "green", "G": "green_d"}, 7, 0)
        return cv
    cv.paint(melon_ball(14, 15, 14, 11.8, n=3))
    return cv


def grape_bunch(cx, top, rows, r, pitch, ramp=GRAPE, spec="pink", step=None):
    """Grape bunch for icons: rows of berries (r radius, pitch spacing) narrowing downwards,
    each berry separated from the ones above by the ramp's darkest step."""
    step = step or pitch * 0.8
    m = {}
    for i, k in enumerate(rows):
        x0 = cx - (k - 1) * pitch / 2.0
        cy = top + r + i * step
        for j in range(k):
            bx = x0 + j * pitch
            b = ball(bx, cy, r, r, ramp, gain=1.4)
            for q, c in b.items():
                m[q] = c
            # rim the lower-right of each berry so the next row reads as a separate ball
            m[(int(bx - r * 0.45), int(cy - r * 0.45))] = spec if i < len(rows) - 1 else ramp[2]
    return m


def icon_grapes(n):
    cv = Cv(n, n)
    if n == 14:
        cv.paint(grape_bunch(6.5, 2, (4, 3, 3, 2, 1), 1.75, 3.0, step=2.3))
        cv.map(["gG", "G."], {"g": "green", "G": "green_d"}, 7, 0)
        return cv
    cv.paint(grape_bunch(13, 4, (4, 4, 3, 2, 1), 3.3, 5.6, step=4.6))
    return cv


ICONS = {"pumpkin": icon_pumpkin, "sunflower": icon_sunflower, "cabbage": icon_cabbage,
         "eggplant": icon_eggplant, "melon": icon_melon, "grapes": icon_grapes}

# Seed packets: crops_a's design (crimped paper packet, tinted band with a picture of the produce,
# two printed lines at the bottom); the band tint gives each packet its own colour.
PAPER = ("tan_d", "tan", "cream", "white")
TINT = {"pumpkin": ("green", "green_d"), "sunflower": ("blue", "blue_d"), "cabbage": ("cyan", "blue"),
        "eggplant": ("grey_l", "grey"), "melon": ("magenta", "purple"), "grapes": ("amber", "clay")}


def ptone(ramp, v, x, y):
    """crops_a's tone(): ramp index v (fractional), checker-dithered between steps."""
    n = len(ramp) - 1
    v = max(0.0, min(float(n), v))
    b = int(v)
    f = v - b
    if b >= n or f < 0.3:
        return ramp[b]
    if f > 0.7:
        return ramp[b + 1]
    return ramp[b + 1] if (x + y) % 2 == 0 else ramp[b]


def seed_packet(cid, n):
    cv = Cv(n, n)
    light, dark = TINT[cid]
    x0, x1, y0, y1 = (2, 11, 1, 13) if n == 14 else (4, 23, 1, 27)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            v = 2.0 - (0.6 if x >= x1 - (1 if n == 14 else 2) else 0) - (0.5 if y >= y1 - 1 else 0) + (0.4 if x == x0 else 0)
            cv.set(x, y, ptone(PAPER, v, x, y))
    for x in range(x0, x1 + 1):  # crimped top
        cv.set(x, y0, "tan" if x % 2 else CLEAR)
        cv.set(x, y0 + 1, "tan_d")
    pic = ICONS[cid](14).im
    if n == 14:
        for y in range(y0 + 3, y0 + 10):
            for x in range(x0 + 1, x1):
                cv.set(x, y, ptone([dark, light], 1.0 if y < y0 + 8 else 0.0, x, y))
        cv.im.alpha_composite(pic.resize((7, 7), resample=0), (x0 + 1, y0 + 3))
    else:
        for y in range(y0 + 4, y0 + 20):
            for x in range(x0 + 2, x1 - 1):
                cv.set(x, y, ptone([dark, light], 1.0 if y < y0 + 17 else 0.5 if y < y0 + 19 else 0.0, x, y))
        cv.im.alpha_composite(pic, (x0 + 3, y0 + 4))
        cv.p = cv.im.load()
        for x in range(x0 + 3, x1 - 3, 2):
            cv.set(x, y1 - 3, "tan_d")
            cv.set(x + 1, y1 - 3, "tan_d")
    cv.p = cv.im.load()
    return cv


# ================================================================ main

CROPS = {
    "pumpkin": pumpkin_stages,
    "sunflower": sunflower_stages,
    "cabbage": cabbage_stages,
    "eggplant": eggplant_stages,
    "melon": melon_stages,
    "grapes": grapes_stages,
}


def main():
    art = sys.argv[1]
    sc, si, sb = Sprites(art, "crops"), Sprites(art, "items"), Sprites(art, "items_big")
    for cid, fn in CROPS.items():
        st = fn()
        for k in (0, 1, 2, 3, "dead"):
            trim_save(sc, f"{cid}_{k}", st[k])
        for S, n in ((si, 14), (sb, 28)):
            save_item(S, cid, ICONS[cid](n))
            save_item(S, f"seed_{cid}", seed_packet(cid, n))
    pivots = {}
    for s in (sc, si, sb):
        pivots.update(s.pivots)
    print(json.dumps(pivots))


if __name__ == "__main__":
    main()
