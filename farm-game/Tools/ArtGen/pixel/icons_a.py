"""Icons A (pixel-direct): HUD currencies and meters, time of day, seasons, weather, crop
status, quality stars, storage, and small UI glyphs (lock, check, arrows, timer...).

icons/<name>      body 14x14 (hand-tuned for 1x reading on the wood panel and dark HUD pill)
icons_big/<name>  body 28x28 (redrawn with more detail, not an upscale)
Pivot = centre of the body (+ the finish() pad).

Endesga-32 only, checker dither between ramp steps, lit from the top-left, finished with
finish() (1px ink outline + hard drop shadow) like every other sprite.
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, CLEAR, Sprites, new, finish  # noqa: E402
import json  # noqa: E402
import math  # noqa: E402

_L = (-0.55, -0.7, 0.6)
LIGHT = tuple(v / math.sqrt(sum(u * u for u in _L)) for v in _L)
LD = (-0.7071, -0.7071)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

GOLD = ["brown", "clay", "amber", "yellow", "white"]
SILVER = ["slate", "grey_d", "grey", "grey_l", "white"]
IRID = ["purple", "purple", "purple", "magenta", "pink"]
RED = ["purple", "red_d", "red", "pink", "white"]
BLUE = ["slate", "blue_d", "blue", "cyan", "white"]
GREEN = ["teal_dd", "green_dd", "green_d", "green", "yellow"]
CLOUD = ["grey_d", "grey", "grey_l", "white", "white"]
GLOOM = ["slate_d", "slate", "grey_d", "grey", "grey_l"]
WOOD = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
DEAD = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
ORANGE = ["red_d", "rust", "orange", "amber", "yellow"]
SUN = ["clay", "orange", "amber", "yellow", "white"]
PETAL = ["purple", "magenta", "pink", "white", "white"]
CREAM = ["brown_l", "tan_d", "tan", "cream", "white"]
MOON = ["tan_d", "tan", "cream", "white", "white"]
DRIED = ["brown", "brown_l", "tan_d", "tan", "cream"]


def col(c):
    return E[c] if isinstance(c, str) else c


DITHER = [True]


def tone(ramp, v, x, y):
    """Ramp colour for a fractional level v, checker-dithered between steps (big icons only;
    14px icons round to the nearest step so they stay crisp at 1x)."""
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
        self.w = self.h = self.n = n
        self.k = n / 14
        self.im = new(n, n)
        self.px = self.im.load()

    def inb(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def on(self, x, y):
        return self.inb(x, y) and self.px[x, y][3] > 0

    def put(self, x, y, c):
        if c is not None and self.inb(x, y):
            self.px[x, y] = col(c) if c != CLEAR else CLEAR

    def erase(self, x, y):
        if self.inb(x, y):
            self.px[x, y] = CLEAR

    def stamp(self, x0, y0, rows, key):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch not in ". ":
                    if key[ch] is None:
                        self.erase(x0 + x, y0 + y)
                    else:
                        self.put(x0 + x, y0 + y, key[ch])

    def fill(self, s, c):
        for x, y in s:
            self.put(x, y, c)


# ---------------------------------------------------------------- shapes (continuous coords, pixel centres)

def clip(s, n):
    return {(x, y) for x, y in s if 0 <= x < n and 0 <= y < n}


def disc(cx, cy, r, n, ry=None):
    ry = r if ry is None else ry
    s = set()
    for y in range(n):
        for x in range(n):
            if ((x + 0.5 - cx) / r) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0 + 0.35 / max(r, ry):
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


def poly(pts, n, k=1.0):
    pts = [(x * k, y * k) for x, y in pts]
    return {(x, y) for y in range(n) for x in range(n) if pip(x + 0.5, y + 0.5, pts)}


def seg_d(px, py, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    L2 = dx * dx + dy * dy or 1e-9
    t = max(0.0, min(1.0, ((px - a[0]) * dx + (py - a[1]) * dy) / L2))
    return math.hypot(px - (a[0] + dx * t), py - (a[1] + dy * t))


def capsule(a, b, w, n):
    return {(x, y) for y in range(n) for x in range(n) if seg_d(x + 0.5, y + 0.5, a, b) <= w / 2}


def rect(x0, y0, x1, y1):
    """Inclusive integer rect."""
    return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def star_pts(cx, cy, R, r, pts=5, rot=-90.0):
    out = []
    for i in range(pts * 2):
        a = math.radians(rot + i * 180.0 / pts)
        rr = R if i % 2 == 0 else r
        out.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return out


def edges(s):
    return {p for p in s if any((p[0] + dx, p[1] + dy) not in s for dx, dy in N4)}


def dist_in(s):
    """BFS distance (1 = edge) of every pixel to the outside of s."""
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


# ---------------------------------------------------------------- painting

def paint(c, cols, sep=None):
    """Paint {pixel: colour}; edges touching earlier (behind) pixels get the sep colour."""
    for (x, y), cc in cols.items():
        if sep is not None and any((x + dx, y + dy) not in cols and c.on(x + dx, y + dy) for dx, dy in N4):
            cc = sep
        c.put(x, y, cc)


def dome(c, s, ramp, depth=3.0, gain=1.0, off=0.0, sep=None, spec=None):
    """Auto-shade any pixel set as a soft dome lit from the top-left."""
    dist = dist_in(s)
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
    paint(c, cols, sep)
    return cols


def ball(c, cx, cy, rx, ry, ramp, gain=1.0, off=0.0, sep=None, spec=None, s=None):
    s = disc(cx, cy, rx, c.n, ry) if s is None else s
    n = len(ramp) - 1
    cols = {}
    for (x, y) in s:
        nx, ny = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
        nz = math.sqrt(max(0.0, 1 - min(1.0, nx * nx + ny * ny)))
        lv = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        v = ((lv + 0.3) / 1.3) * n * gain + off
        cols[(x, y)] = tone(ramp, v, x, y)
    if spec:
        for p, cc in spec.items():
            if p in s:
                cols[p] = cc
    paint(c, cols, sep)
    return s


def bevel(c, s, ramp, base=2.0, hi=1.0, lo=1.0, grad=0.0, sep=None, width=1):
    """Flat chunky fill: top/left edge band lighter, bottom/right edge band darker,
    optional diagonal gradient across the body (checker dither)."""
    n = len(ramp) - 1
    xs = [p[0] for p in s]
    ys = [p[1] for p in s]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    span = max(1, (x1 - x0) + (y1 - y0))
    cols = {}
    for (x, y) in s:
        t = ((x - x0) + (y - y0)) / span - 0.5
        v = base - grad * t
        lit = any((x - d, y) not in s or (x, y - d) not in s for d in range(1, width + 1))
        drk = any((x + d, y) not in s or (x, y + d) not in s for d in range(1, width + 1))
        if drk:
            v = base - lo
        elif lit:
            v = base + hi
        cols[(x, y)] = tone(ramp, min(n, v), x, y)
    paint(c, cols, sep)
    return cols


def inner_line(c, s, color):
    """Ink-ish separation line on the pixels of s that touch other painted pixels."""
    for (x, y) in s:
        if any((x + dx, y + dy) not in s and c.on(x + dx, y + dy) for dx, dy in N4):
            c.put(x, y, color)


def ring_outline(c, s, color="ink"):
    """Draw a 1px outline just outside s (inside the canvas) so an overlay reads on top."""
    for (x, y) in list(s):
        for dx, dy in N4:
            m = (x + dx, y + dy)
            if m not in s:
                c.put(m[0], m[1], color)


# ---------------------------------------------------------------- currency

def coin_face(c, cx, cy, r, rw, emblem=True):
    n = c.n
    s = disc(cx, cy, r, n)
    for (x, y) in s:
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        d = math.hypot(dx, dy) or 1e-6
        lit = (dx * LD[0] + dy * LD[1]) / d
        if d > r - rw:
            v = 2.0 + 1.25 * lit
        elif d > r - rw - 1:
            v = 2.0 - 1.0 * lit
        else:
            v = 2.35 - 0.45 * (dx + dy) / r
        c.put(x, y, tone(GOLD, v, x, y))
    if emblem:
        if n == 14 and r < 5.5:
            pass
        elif r < 7:
            st = [".y.", "yyy", ".y."] if r < 5 else ["..y..", ".yyy.", "yyyyy", ".yyy.", ".y.y."]
            w = len(st[0])
            ox, oy = round(cx - w / 2), round(cy - len(st) / 2)
            em = {(ox + i, oy + j) for j, row in enumerate(st) for i, ch in enumerate(row) if ch == "y"}
            for (x, y) in em:
                for ddx, ddy in ((1, 0), (0, 1), (1, 1)):
                    if (x + ddx, y + ddy) not in em:
                        c.put(x + ddx, y + ddy, "clay")
            for (x, y) in em:
                c.put(x, y, "yellow")
        else:
            em = poly(star_pts(cx, cy - 0.3, r * 0.5, r * 0.22), n)
            for (x, y) in em:
                for ddx, ddy in ((1, 0), (0, 1), (1, 1)):
                    if (x + ddx, y + ddy) not in em and (x + ddx, y + ddy) in s:
                        c.put(x + ddx, y + ddy, "clay")
            for (x, y) in em:
                lt = (x - 1, y) not in em or (x, y - 1) not in em
                c.put(x, y, "white" if lt and (x + y) % 2 == 0 else "yellow")
    # glint on the lit rim
    gx, gy = cx - r * 0.62, cy - r * 0.62
    c.put(int(gx), int(gy) + 1, "white")
    c.put(int(gx) + 1, int(gy), "white")
    if r >= 7:
        c.put(int(gx), int(gy) + 2, "white")
        c.put(int(gx) + 2, int(gy), "white")
        c.put(int(gx) + 1, int(gy) + 1, "white")
    return s


def ic_coin(n):
    c = Canvas(n)
    if n == 14:
        put_map(c, COIN14)
    else:
        coin_face(c, 14, 14, 13.2, 2)
    return c


def coin_edge(c, cx, ytop, rx, ry, th, sep="brown"):
    """A coin lying flat: elliptical top face + milled side band, painted over what is behind."""
    n = c.n
    top = disc(cx, ytop + ry, rx, n, ry)
    body = set()
    for t in range(th + 1):
        body |= {(x, y + t) for (x, y) in top}
    body = clip(body, n)
    side = body - top
    cols = {}
    for (x, y) in side:
        u = (x + 0.5 - cx) / rx
        v = 1.7 - 0.9 * u
        if th >= 3 and x % 2 == 0:
            v -= 0.8
        cols[(x, y)] = tone(GOLD, v, x, y)
    for (x, y) in top:
        dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - ytop - ry) / ry
        d = math.hypot(dx, dy) or 1e-6
        if d > 0.72:
            v = 3.0 if dy < 0.1 else 2.0
        else:
            v = 2.6 - 0.6 * dx
        cols[(x, y)] = tone(GOLD, v, x, y)
    paint(c, cols, sep)
    xs = sorted(x for (x, y) in top if y == min(yy for _, yy in top))
    if xs:
        c.put(xs[0], ytop, "white")
    return body


def ic_coins(n):
    c = Canvas(n)
    if n == 14:
        m = compose(["."] * 14, STACK14, 0, 0)
        m = compose(m, STACK14[:6], 6, 4, sep="B")
        m = compose(m, FACE8, 5, 6, sep="B")
        put_map(c, m, ox=0, oy=0)
        return c
    for yt in (19, 16, 13, 10, 7, 4):
        coin_edge(c, 9.5, yt, 8.5, 3.0, 3)
    for yt in (19, 16, 13):
        coin_edge(c, 19.5, yt, 8.0, 3.0, 3)
    s = disc(19.0, 19.5, 8.3, n)
    for p in s:
        c.erase(*p)
    coin_face(c, 19.0, 19.5, 8.3, 1, emblem=True)
    edge_over(c, s, "brown")
    return c


def ic_gem(n):
    c = Canvas(n)
    k = n / 14
    TL, TR = (3.4, 1.0), (10.6, 1.0)
    GL, GR = (0.0, 5.0), (14.0, 5.0)
    CL, CR = (4.6, 5.0), (9.4, 5.0)
    B = (7.0, 14.0)
    facets = [
        ([TL, GL, CL], 3.6),
        ([TL, TR, CR, CL], 3.0),
        ([TR, GR, CR], 2.0),
        ([GL, CL, B], 2.6),
        ([CL, CR, B], 2.0),
        ([CR, GR, B], 1.0),
    ]
    if n == 28:
        # extra crown facets for the big gem
        M = (7.0, 1.0)
        facets = [
            ([TL, GL, CL], 3.5),
            ([TL, M, CL], 3.0),
            ([M, CR, CL], 2.6),
            ([M, TR, CR], 3.0),
            ([TR, GR, CR], 2.0),
            ([GL, CL, B], 2.7),
            ([CL, (7.0, 5.0), B], 2.3),
            ([(7.0, 5.0), CR, B], 1.8),
            ([CR, GR, B], 1.0),
        ]
    done = set()
    for pts, v in facets:
        s = poly(pts, n, k) - done
        for (x, y) in s:
            c.put(x, y, tone(BLUE, v, x, y))
        done |= s
    if n == 14:
        for p in ((4, 2), (3, 3), (2, 4)):
            c.put(*p, "white")
        c.put(8, 2, "white")
    else:
        for i in range(5):
            c.put(7 - i, 3 + i, "white")
        c.put(15, 4, "white")
        c.put(16, 3, "white")
        c.put(5, 12, "white")
        c.put(6, 13, "white")
        # girdle line
        for x in range(1, 27):
            if (x, 10) in done:
                c.put(x, 10, "cyan" if x < 14 else "blue")
    return c


# ---------------------------------------------------------------- energy / xp / level / heart

BOLT = [(8.0, 0.0), (12.4, 0.0), (8.8, 5.0), (12.6, 5.0), (4.6, 14.0), (6.3, 7.9), (2.4, 7.9)]


def bolt(c, ox, oy, sc, sep=None):
    pts = [(ox + x * sc, oy + y * sc) for x, y in BOLT]
    s = poly(pts, c.n)
    cols = {}
    for (x, y) in s:
        v = 3.0
        if (x + 1, y) not in s or (x, y + 1) not in s:
            v = 2.0
        elif (x - 1, y) not in s or (x, y - 1) not in s:
            v = 4.0
        if y >= oy + 9.5 * sc and v > 2:
            v = 2.5
        cols[(x, y)] = tone(SUN, v, x, y)
    paint(c, cols, sep)
    return s


def ic_energy(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            ".......wyya",
            "......wyyya",
            ".....wyyya.",
            "....wyyya..",
            "...wyyyaa..",
            "..wyyyyyyyya",
            ".wyyyyyyyya.",
            "aaaayyyyaa..",
            "....yyyaa...",
            "...yyyaa....",
            "...yyaa.....",
            "..yyaa......",
            "..yaa.......",
            "..aa........",
        ]
        c.stamp(1, 0, rows, {"w": "white", "y": "yellow", "a": "amber"})
        return c
    bolt(c, 0.6, 0.0, 2.0)
    return c


def apple(c, cx, cy, r, sep=None):
    s = disc(cx - r * 0.42, cy, r * 0.68, c.n, r * 0.9) | disc(cx + r * 0.42, cy, r * 0.68, c.n, r * 0.9) | \
        disc(cx, cy + r * 0.12, r * 0.85, c.n, r * 0.85)
    s = {p for p in s if not (abs(p[0] + 0.5 - cx) < r * 0.2 and p[1] + 0.5 < cy - r * 0.62)}
    ball(c, cx, cy, r * 1.05, r * 0.95, RED, s=s, sep=sep, off=0.1)
    return s


def ic_energy_food(n):
    c = Canvas(n)
    if n == 14:
        m = compose(APPLE14, BOLT14, 8, 7, sep="k")
        put_map(c, m, ox=0, oy=1)
        return c
    s = apple(c, 12.0, 14.5, 11.5)
    for x, y in ((7, 7), (8, 6), (6, 8), (7, 8), (6, 9)):
        c.put(x, y, "white")
    c.put(9, 6, "pink")
    for y in range(1, 6):
        c.put(12, y, "brown_l" if y < 3 else "brown")
        c.put(13, y, "brown")
    for p, cc in (((14, 2), "green"), ((15, 1), "green"), ((16, 1), "green"), ((17, 1), "green_d"), ((15, 2), "green"),
                  ((16, 2), "green_d"), ((17, 2), "green_d"), ((18, 2), "green_d"), ((14, 3), "green_d"), ((15, 3), "green_d"),
                  ((16, 3), "green_dd")):
        c.put(*p, cc)
    sc = 1.05
    pts = [(14.2 + x * sc, 13.0 + y * sc) for x, y in BOLT]
    bs = poly(pts, n)
    for p in bs:
        c.erase(*p)
    edge_over(c, bs, "ink")
    bolt(c, 14.2, 13.0, sc)
    return c


SMALL_BOLT = [
    "..wya",
    ".wya.",
    "wyyya",
    "..ya.",
    ".ya..",
    ".a...",
]


def star_fill(c, cx, cy, R, r, ramp, glint=True, off=0.0, rot=-90.0):
    s = poly(star_pts(cx, cy, R, r, rot=rot), c.n)
    dome(c, s, ramp, depth=max(1.5, R * 0.45), off=off)
    if glint:
        hx, hy = round(cx - R * 0.22), round(cy - R * 0.32)
        c.put(hx - 1, hy, "white")
        c.put(hx, hy - 1, "white")
        if R > 8:
            c.put(hx - 1, hy - 1, "white")
            c.put(hx - 2, hy + 1, "white")
    return s


STAR14 = [
    "......y......",
    ".....yyy.....",
    ".....yyya....",
    "....ywyya....",
    "yyyyywyyyyyaa",
    ".yyyyyyyyyaa.",
    "..yyyyyyyaa..",
    "...yyyyyaa...",
    "...yyyyyaa...",
    "..yyyaaayaa..",
    "..yyaa..aaa..",
    ".yaa......aa.",
]


def ic_xp(n):
    """XP: a glossy green orb with a white glint. Round on purpose, so it never reads as a quality star."""
    c = Canvas(n)
    if n == 14:
        s = disc(6.5, 6.5, 6.2, n)
        ball(c, 6.5, 6.5, 6.2, 6.2, GREEN, gain=1.0, off=0.1, s=s, spec={(4, 3): "white", (4, 4): "white", (3, 4): "white"})
        return c
    s = disc(13.0, 15.0, 10.6, n)
    ball(c, 13.0, 15.0, 10.6, 10.6, GREEN, gain=1.0, off=0.1, s=s)
    for p in ((8, 10), (9, 9), (8, 11), (10, 8), (7, 12), (11, 7), (12, 7)):
        c.put(*p, "white")
    # four-point sparkle sitting just outside the orb, top right
    c.put(23, 4, "white")
    for p in ((22, 4), (24, 4), (23, 3), (23, 5)):
        c.put(*p, "yellow")
    return c


def rosette(c, cx, cy, r, bumps, br):
    n = c.n
    s = disc(cx, cy, r, n)
    for i in range(bumps):
        a = 2 * math.pi * i / bumps
        s |= disc(cx + (r - br * 0.4) * math.cos(a), cy + (r - br * 0.4) * math.sin(a), br, n)
    return clip(s, n)


def ic_level(n):
    c = Canvas(n)
    if n == 14:
        put_map(c, LEVEL14)
        return c
    ribbon(c, [(1.5, 0.0), (8.5, 0.0), (17.0, 12.5), (10.0, 12.5)], BLUE, "white")
    ribbon(c, [(19.5, 0.0), (26.5, 0.0), (18.0, 12.5), (11.0, 12.5)], RED, "white")
    s = disc(14.0, 18.6, 9.3, n)
    for p in s:
        c.erase(*p)
    coin_face(c, 14.0, 18.6, 9.3, 2)
    edge_over(c, s, "ink")
    for x in range(12, 17):
        if not c.on(x, 8):
            continue
        c.put(x, 8, "ink")
    return c


def heart_set(cx, cy, w, n):
    """Heart of total width w centred on (cx, cy)."""
    r = w * 0.27
    lx, rx = cx - w * 0.235, cx + w * 0.235
    ty = cy - w * 0.12
    s = disc(lx, ty, r, n) | disc(rx, ty, r, n)
    s |= poly([(cx - w * 0.5 + 0.05, ty + 0.2), (cx + w * 0.5 - 0.05, ty + 0.2), (cx, cy + w * 0.5)], n)
    s |= poly([(lx, ty - 0.5), (rx, ty - 0.5), (cx + w * 0.4, ty + w * 0.2), (cx, cy + w * 0.45), (cx - w * 0.4, ty + w * 0.2)], n)
    return s


HEART14 = [
    "..rrr...rrr..",
    ".rwprr.rrrrR.",
    "rwpprrrrrrrRR",
    "rpprrrrrrrrRR",
    "rprrrrrrrrrRR",
    "rrrrrrrrrrRRR",
    ".rrrrrrrrRRR.",
    "..rrrrrrRRR..",
    "...rrrrRRR...",
    "....rrRRR....",
    ".....rRR.....",
    "......R......",
]


def ic_heart(n):
    c = Canvas(n)
    if n == 14:
        c.stamp(0, 1, HEART14, {"r": "red", "R": "red_d", "p": "pink", "w": "white"})
        return c
    s = heart_set(14, 14.3, 27.6, n)
    dome(c, s, RED, depth=5.0, off=0.2)
    for p in ((6, 6), (5, 7), (6, 7), (7, 6), (5, 8), (8, 5)):
        c.put(*p, "white")
    for p in ((7, 7), (6, 8), (9, 5)):
        c.put(*p, "pink")
    return c


# ---------------------------------------------------------------- time of day

def ic_clock(n):
    c = Canvas(n)
    k = n / 14
    cx, cy, r = 7.0 * k, 7.8 * k, 5.6 * k
    # bells + feet
    for bx in (2.3, 11.7):
        s = disc(bx * k, 2.4 * k, 2.1 * k, n)
        bevel(c, s, GOLD, base=2.6, hi=1.0, lo=1.0)
    for fx, d in ((3.0, -1), (11.0, 1)):
        s = capsule((fx * k, 11.5 * k), ((fx + d * 1.2) * k, 13.4 * k), 1.6 * k, n)
        c.fill(s, "brown")
    body = disc(cx, cy, r, n)
    for p in body:
        c.erase(*p)
    for (x, y) in body:
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        d = math.hypot(dx, dy) or 1e-6
        lit = (dx * LD[0] + dy * LD[1]) / d
        c.put(x, y, tone(RED, 2.0 + 1.1 * lit, x, y))
    face = disc(cx, cy, r - (1.3 if n == 14 else 2.4), n)
    for (x, y) in face:
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        v = 3.1 - 0.5 * (dx + dy) / r
        if (x - 1, y) not in face or (x, y - 1) not in face:
            v = 2.0
        c.put(x, y, tone(CREAM, v, x, y))
    ix, iy = int(cx), int(cy)
    if n == 14:
        for y in range(iy - 3, iy + 1):
            c.put(ix, y, "ink")
        for x in range(ix, ix + 3):
            c.put(x, iy, "ink")
        c.put(ix, iy, "slate")
    else:
        for a in range(12):
            ang = a * math.pi / 6
            rr = r - 3.4
            px_, py_ = cx + rr * math.cos(ang), cy + rr * math.sin(ang)
            c.put(int(px_), int(py_), "tan_d" if a % 3 else "brown")
        for y in range(iy - 7, iy + 1):
            c.put(ix, y, "ink")
            c.put(ix - 1, y, "slate") if y < iy - 1 else None
        for x in range(ix, ix + 6):
            c.put(x, iy, "ink")
            c.put(x, iy + 1, "slate")
        c.put(ix, iy, "red")
        c.put(ix - 1, iy, "red_d")
        # hammer between the bells
        for p in ((13, 1), (14, 1), (13, 2), (14, 2), (13, 3), (14, 3)):
            c.put(*p, "grey" if p[0] == 13 else "grey_d")
        c.put(13, 0, "grey_l")
        c.put(14, 0, "grey")
    return c


def sun_rays(c, cx, cy, r0, r1, rays, w, ramp=SUN, v=2.4, rot=0.0):
    n = c.n
    for i in range(rays):
        a = rot + 2 * math.pi * i / rays
        a0 = (cx + r0 * math.cos(a), cy + r0 * math.sin(a))
        a1 = (cx + r1 * math.cos(a), cy + r1 * math.sin(a))
        s = capsule(a0, a1, w, n)
        for (x, y) in s:
            lit = -(math.cos(a) + math.sin(a)) * 0.7
            c.put(x, y, tone(ramp, v + 0.8 * lit, x, y))


def ic_sun(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            "......o......",
            "..o...o...o..",
            "...o.....o...",
            "....yyyyy....",
            "...ywwyyya...",
            "...wyyyyya...",
            "oo.ykyyky.aoo",
            "...ypyyypa...",
            "...yykkyaa...",
            "....aaaaa....",
            "...o.....o...",
            "..o...o...o..",
            "......o......",
        ]
        put_map(c, rows)
        return c
    tri_rays(c, 14, 14, 9.0, 13.9, 8, 6.0)
    tri_rays(c, 14, 14, 9.0, 12.2, 8, 4.0, rot=math.pi / 8, v=2.0)
    s = disc(14, 14, 9.0, n)
    for p in s:
        c.erase(*p)
    ball(c, 14, 14, 9.4, 9.4, SUN, off=0.45, s=s)
    for p in ((9, 9), (10, 8), (9, 10), (11, 8)):
        c.put(*p, "white")
    for x, y in ((11, 13), (11, 14), (17, 13), (17, 14)):
        c.put(x, y, "ink")
    for x, y in ((9, 16), (10, 16), (19, 16), (18, 16)):
        c.put(x, y, "pink")
    for x, y in ((12, 17), (13, 18), (14, 18), (15, 18), (16, 17)):
        c.put(x, y, "ink")
    return c


def ic_moon(n):
    c = Canvas(n)
    k = n / 14
    if n == 14:
        put_map(c, MOON14)
        return c
    outer = disc(6.6 * k, 7.0 * k, 6.4 * k, n)
    cut = disc(10.2 * k, 4.6 * k, 5.2 * k, n)
    s = outer - cut
    ball(c, 6.6 * k, 7.0 * k, 6.6 * k, 6.6 * k, MOON, s=s, off=0.4)
    for x, y in ((5, 15), (6, 16), (7, 16), (8, 15)):
        c.put(x, y, "brown")
    for x, y in ((4, 18), (5, 18)):
        c.put(x, y, "pink")
    for x, y in ((9, 21), (10, 21), (8, 22)):
        c.put(x, y, "tan_d")
    for x, y in ((12, 9), (13, 10)):
        c.put(x, y, "tan_d")
    for cx, cy, R in ((21.0, 17.5, 4.2), (24.0, 8.0, 2.6)):
        st = poly(star_pts(cx, cy, R, R * 0.45, pts=4, rot=-90), n)
        for (x, y) in st:
            c.put(x, y, "yellow" if (x + 0.5 - cx) + (y + 0.5 - cy) < 0.5 else "amber")
        c.put(int(cx), int(cy), "white")
    return c


def ic_sunrise(n):
    c = Canvas(n)
    k = n / 14
    hz = 9.4 * k
    cx = 7.0 * k
    for deg in (-162, -126, -90, -54, -18):
        a = math.radians(deg)
        if n == 14:
            s = capsule((cx + 5.9 * math.cos(a), hz + 5.9 * math.sin(a)), (cx + 7.0 * math.cos(a), hz + 7.0 * math.sin(a)), 1.5, n)
        else:
            ux, uy = math.cos(a), math.sin(a)
            s = poly([(cx + 11.4 * ux - 2.2 * uy, hz + 11.4 * uy + 2.2 * ux), (cx + 15.6 * ux, hz + 15.6 * uy),
                      (cx + 11.4 * ux + 2.2 * uy, hz + 11.4 * uy - 2.2 * ux)], n)
        for (x, y) in s:
            if y + 0.5 < hz:
                c.put(x, y, tone(SUN, 2.4 - 0.5 * math.sin(a), x, y))
    sr = 4.9 * k
    s = {p for p in disc(cx, hz, sr, n) if p[1] + 0.5 < hz}
    for (x, y) in s:
        t = (hz - (y + 0.5)) / sr
        v = 1.5 + 2.4 * t - 0.4 * (x + 0.5 - cx) / sr
        if (x - 1, y) not in s or (x, y - 1) not in s:
            v += 0.6
        c.put(x, y, tone(SUN, v, x, y))
    c.put(int(cx - 2.4 * k), int(hz - 3.2 * k), "white")
    if n == 28:
        c.put(int(cx - 2.4 * k) + 1, int(hz - 3.2 * k) - 1, "white")
        c.put(int(cx - 2.4 * k) - 1, int(hz - 3.2 * k) + 1, "white")
    # rolling hills in front of the sun
    ground = set()
    for x in range(n):
        top = hz - 0.4 * k + 0.9 * k * math.sin((x + 0.5) / n * math.pi * 2.0 + 0.6)
        for y in range(n):
            if top <= y + 0.5 <= hz + 4.4 * k:
                ground.add((x, y))
    cols = {}
    for (x, y) in ground:
        v = 3.0
        if (x, y - 1) not in ground:
            v = 4.0
        elif (x, y - 2) not in ground:
            v = 3.0
        else:
            v = 2.0 if y + 0.5 > hz + 2.2 * k else 2.6
        cols[(x, y)] = tone(GREEN, v, x, y)
    paint(c, cols)
    return c


# ---------------------------------------------------------------- seasons

def ic_spring(n):
    c = Canvas(n)
    if n == 14:
        blossom(c, 7.0, 7.3, 6.9, 0.55)
    else:
        blossom(c, 14.0, 14.6, 13.8, 0.52, notch=0.11)
    return c


def leaf_set(base, tip, hw, n, bend=0.0):
    """Leaf: lens swept from base to tip with half-width hw, optionally bowed."""
    s = {}
    L = math.hypot(tip[0] - base[0], tip[1] - base[1])
    ux, uy = (tip[0] - base[0]) / L, (tip[1] - base[1]) / L
    for y in range(n):
        for x in range(n):
            px_, py_ = x + 0.5 - base[0], y + 0.5 - base[1]
            t = (px_ * ux + py_ * uy) / L
            if t < 0 or t > 1:
                continue
            off = -px_ * uy + py_ * ux
            off -= bend * L * math.sin(math.pi * t)
            w = hw * math.sin(math.pi * t) ** 0.7
            if abs(off) <= w + 0.15:
                s[(x, y)] = (t, off / max(w, 0.01))
    return s


def paint_leaf(c, shp, ramp, lit=3.0, vein=True, sep=None):
    cols = {}
    for (x, y), (t, side) in shp.items():
        v = lit if side < 0 else lit - 1
        if vein and abs(side) < 0.25 and 0.1 < t < 0.85:
            v = lit - 1.5
        if (x + 1, y) not in shp and (x, y + 1) not in shp:
            v -= 0.8
        elif ((x - 1, y) not in shp or (x, y - 1) not in shp) and side < 0:
            v += 0.6
        cols[(x, y)] = tone(ramp, v, x, y)
    paint(c, cols, sep)


def ic_summer(n):
    """Sunflower: ring of yellow petals round a brown seed disc, a green stalk and one leaf."""
    c = Canvas(n)
    if n == 14:
        cx, cy, dp, rp, rc, P = 6.5, 5.2, 3.1, 2.0, 2.3, 8
    else:
        cx, cy, dp, rp, rc, P = 14.0, 11.4, 8.0, 3.5, 6.0, 10
    petals = set()
    for i in range(P):
        a = 2 * math.pi * i / P - math.pi / 2
        petals |= disc(cx + dp * math.cos(a), cy + dp * math.sin(a), rp, n)
    centre = disc(cx, cy, rc, n)
    petals = clip(petals, n) - centre
    dome(c, petals, SUN, depth=2.6 if n == 14 else 4.2, off=0.2)
    edge_over(c, centre, "amber")
    ball(c, cx, cy, rc, rc, WOOD, gain=0.9, off=0.2, s=centre)
    if n == 28:
        seeds = [(12, 9), (15, 8), (13, 12), (16, 11), (11, 13), (14, 14), (17, 14), (13, 16), (16, 17), (10, 11)]
        for x, y in seeds:
            if (x, y) in centre:
                c.put(x, y, "brown_d")
        c.put(12, 6, "brown_l")
        c.put(10, 8, "brown_l")
    # stalk
    top = cy + dp + rp - 1
    if n == 14:
        stalk = capsule((cx, top), (cx, n - 1), 1.4, n)
        for p in stalk:
            c.put(*p, "green_d")
        leaf = leaf_ovate((cx - 0.5, 12.2), (0.8, 8.6), 2.0, n)
    else:
        stalk = capsule((cx, top), (cx - 0.5, n - 1), 2.6, n)
        for p in stalk:
            c.put(*p, "green_d")
        leaf = leaf_ovate((cx - 1.0, 24.0), (1.0, 16.0), 4.0, n)
    cols = {}
    for (x, y), (t, side) in leaf.items():
        v = 3.0 if side < 0 else 2.0
        if (x + 1, y) not in leaf or (x, y + 1) not in leaf:
            v -= 0.9
        cols[(x, y)] = tone(GREEN, v, x, y)
    paint(c, cols)
    edge_over(c, set(leaf), "ink")
    return c


MAPLE = [(0.0, -1.0), (0.17, -0.62), (0.36, -0.72), (0.3, -0.22), (0.72, -0.52), (0.66, -0.33), (0.98, -0.28),
         (0.78, -0.02), (0.88, 0.08), (0.42, 0.32), (0.5, 0.48), (0.09, 0.36), (0.07, 0.62)]


def maple_pts(cx, cy, R):
    right = MAPLE
    left = [(-x, y) for x, y in reversed(MAPLE[1:])]
    pts = right + left
    return [(cx + x * R, cy + y * R) for x, y in pts]


MAPLE14 = [
    "......o......",
    ".....ooo.....",
    "..o..ooo..o..",
    "..oo.ooo.oo..",
    "o.ooooooooo.o",
    "ooooooooooooo",
    ".ooooooooooo.",
    "..ooooooooo..",
    "..ooooooooo..",
    ".oooooooooooo",
    "....ooooo....",
    "......o......",
    "......o......",
]


def ic_fall(n):
    c = Canvas(n)
    k = n / 14
    if n == 14:
        rows = [
            "......y......",
            ".....yaa.....",
            "..y..yao..o..",
            "..yy.yao.oo..",
            "y.yaayaoaoo.o",
            "yyaaaaaoooooo",
            ".yaaaaaooooo.",
            "..aaoaaoooo..",
            "..aaaoaooo...",
            ".yaaaaoooorr.",
            "....aaorr....",
            "......u......",
            ".....u.......",
        ]
        rows = [
            "......y.......",
            ".....yao......",
            "..y..yao..o...",
            "..yy.yao.oo...",
            "y.yaayaoaoor.r",
            "yyaaaaaooooorr",
            ".yaaaaoooooor.",
            "..yaaoaooorr..",
            "...aaaaooro...",
            "..yaaaaoorrrr.",
            "....aaaurr....",
            "......u.......",
            ".....u........",
        ]
        c.stamp(0, 0, rows, {"y": "yellow", "a": "orange", "o": "orange", "r": "rust", "u": "brown"})
        # proper shading pass over the stamped silhouette
        s = {(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch in "yaor"}
        cols = {}
        for (x, y) in s:
            t = (x + y) / 22.0
            v = 3.3 - 2.4 * t
            if (x + 1, y) not in s or (x, y + 1) not in s:
                v -= 0.7
            cols[(x, y)] = tone(ORANGE, v, x, y)
        paint(c, cols)
        for (x, y) in ((6, 3), (6, 4), (6, 5), (6, 6), (6, 7), (6, 8), (6, 9), (4, 6), (5, 7), (8, 6), (7, 7)):
            if (x, y) in s:
                c.put(x, y, "rust" if y > 5 else "orange")
        return c
    pts = maple_pts(14.0, 13.0, 13.0)
    s = poly(pts, n)
    cols = {}
    for (x, y) in s:
        t = (x + y) / 44.0
        v = 3.4 - 2.6 * t
        if (x + 1, y) not in s or (x, y + 1) not in s:
            v -= 0.8
        elif (x - 1, y) not in s or (x, y - 1) not in s:
            v += 0.6
        cols[(x, y)] = tone(ORANGE, v, x, y)
    paint(c, cols)
    # veins
    for a, L in ((-90, 11), (-30, 10), (-150, 10), (35, 6), (145, 6)):
        r = math.radians(a)
        for i in range(1, L):
            x, y = int(14 + i * math.cos(r)), int(16 + i * math.sin(r))
            if (x, y) in s and (x + 1, y) in s and (x, y + 1) in s:
                c.put(x, y, "rust" if y > 12 else "orange")
    for i in range(6):
        c.put(14 - (i // 3), 21 + i, "brown")
        c.put(15 - (i // 3), 21 + i, "brown_d" if i > 1 else None)
    return c


def ic_winter(n):
    c = Canvas(n)
    if n == 14:
        put_map(c, SNOW14)
        return c
    cx = cy = 14.0
    s = set()
    for i in range(6):
        a = -math.pi / 2 + i * math.pi / 3
        ux, uy = math.cos(a), math.sin(a)
        tip = (cx + 12.6 * ux, cy + 12.6 * uy)
        s |= capsule((cx, cy), tip, 2.3, 28)
        for d, L in ((6.5, 4.2), (9.8, 3.0)):
            bx, by = cx + d * ux, cy + d * uy
            for sgn in (-1, 1):
                b = a + sgn * math.pi / 3
                s |= capsule((bx, by), (bx + L * math.cos(b), by + L * math.sin(b)), 1.9, 28)
    hexa = poly([(cx + 3.6 * math.cos(math.pi / 6 + i * math.pi / 3), cy + 3.6 * math.sin(math.pi / 6 + i * math.pi / 3)) for i in range(6)], 28)
    s |= hexa
    cols = {}
    for (x, y) in s:
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        v = 3.4 - 0.08 * (dx + dy)
        if (x + 1, y) not in s or (x, y + 1) not in s:
            v = 2.0
        cols[(x, y)] = tone(["blue_d", "blue", "cyan", "grey_l", "white"], v, x, y)
    paint(c, cols)
    for (x, y) in hexa:
        if (x, y) not in edges(hexa):
            c.put(x, y, "cyan" if (x + y) % 2 else "white")
    c.put(14, 14, "blue")
    return c


# ---------------------------------------------------------------- weather

def cloud_set(n, k, ox=0.0, oy=0.0, sc=1.0):
    def d(x, y, r):
        return disc((x * sc + ox) * k, (y * sc + oy) * k, r * sc * k, n)
    s = d(4.0, 7.4, 3.0) | d(7.8, 5.2, 3.8) | d(11.2, 7.6, 2.6)
    s |= poly([(1.2 * sc + ox, 8.0 * sc + oy), (13.6 * sc + ox, 8.0 * sc + oy),
               (13.6 * sc + ox, 10.4 * sc + oy), (1.2 * sc + ox, 10.4 * sc + oy)], n, k)
    s = {p for p in s if p[1] + 0.5 <= (10.4 * sc + oy) * k}
    return clip(s, n)


def paint_cloud(c, s, ramp=CLOUD, sep=None, off=0.0):
    ys = [p[1] for p in s]
    y0, y1 = min(ys), max(ys)
    cols = {}
    for (x, y) in s:
        t = (y - y0) / max(1, y1 - y0)
        v = 3.3 - 1.5 * t + off
        if (x, y - 1) not in s or ((x - 1, y) not in s and t < 0.6):
            v += 0.7
        if (x, y + 1) not in s or (x + 1, y) not in s and t > 0.5:
            v -= 0.8
        cols[(x, y)] = tone(ramp, v, x, y)
    paint(c, cols, sep)


def ic_sunny(n):
    c = Canvas(n)
    k = n / 14
    if n == 14:
        sun_rays(c, 7, 7, 4.9, 7.0, 8, 1.6, v=2.2)
    else:
        tri_rays(c, 14, 14, 9.4, 14.0, 8, 6.4)
        tri_rays(c, 14, 14, 9.4, 12.4, 8, 4.4, rot=math.pi / 8, v=2.0)
    s = disc(7 * k, 7 * k, 4.3 * k, n)
    for p in s:
        c.erase(*p)
    ball(c, 7 * k, 7 * k, 4.6 * k, 4.6 * k, SUN, off=0.35, s=s)
    if n == 14:
        c.put(5, 5, "white")
        c.put(4, 6, "white")
    else:
        for p in ((10, 9), (9, 10), (10, 10), (11, 9), (9, 11)):
            c.put(*p, "white")
    return c


def ic_cloudy(n):
    c = Canvas(n)
    back = cloud(c, GLOOM, ox=3.6, oy=-1.2, sc=0.72, off=0.6)
    front_c = Canvas(n)
    front = cloud(front_c, CLOUD, ox=-0.4, oy=3.2, sc=0.86)
    for p in front:
        c.put(*p, front_c.px[p])
    edge_over(c, front, "grey_d")
    return c


def ic_rain(n):
    c = Canvas(n)
    cloud(c, CLOUD, oy=-1.0, off=-0.4)
    if n == 14:
        for x, y in ((3, 10), (8, 10), (12, 10), (6, 12), (10, 12)):
            c.put(x, y, "cyan")
            c.put(x - 1, y + 1, "blue")
    else:
        for x, y in ((6, 20), (13, 19), (20, 20), (9, 24), (16, 24), (23, 23)):
            c.put(x, y, "white")
            c.put(x, y + 1, "cyan")
            c.put(x - 1, y + 1, "cyan")
            c.put(x - 1, y + 2, "blue")
            c.put(x - 2, y + 2, "blue")
            c.put(x - 2, y + 3, "blue_d")
    return c


def ic_storm(n):
    c = Canvas(n)
    cloud(c, GLOOM, oy=-1.0, off=0.2)
    if n == 14:
        bs = {(x + 6, y + 6) for y, row in enumerate(STORM_BOLT) for x, ch in enumerate(row) if ch != "."}
        for (x, y) in bs:
            c.erase(x, y)
        edge_over(c, bs, "ink")
        c.stamp(6, 6, STORM_BOLT, {"w": "white", "y": "yellow", "a": "amber"})
        for x, y in ((2, 11), (3, 13), (11, 11), (10, 13)):
            c.put(x, y, "cyan")
        return c
    bs = poly([(12.2 + x * 1.15, 11.4 + y * 1.15) for x, y in BOLT], n)
    for p in bs:
        c.erase(*p)
    edge_over(c, bs, "ink")
    bolt(c, 12.2, 11.4, 1.15)
    for x, y in ((5, 21), (8, 24), (22, 22), (20, 25)):
        c.put(x, y, "cyan")
        c.put(x - 1, y + 1, "blue")
    return c


STORM_BOLT = [
    "..wya",
    ".wya.",
    "wyyya",
    "aaya.",
    "..ya.",
    ".ya..",
    ".a...",
]


def flake_small(c, x, y, cc="white", arm="cyan"):
    c.put(x, y, cc)
    for dx, dy in N4:
        c.put(x + dx, y + dy, arm)


def ic_snow(n):
    c = Canvas(n)
    cloud(c, CLOUD, oy=-1.0, off=0.1)
    if n == 14:
        for x, y in ((2, 11), (7, 12), (11, 11)):
            flake_small(c, x, y, "white", "grey_l")
    else:
        for x, y in ((6, 21), (14, 20), (22, 21), (10, 25), (18, 25)):
            flake_small(c, x, y, "white", "grey_l")
            c.put(x - 1, y - 1, "white")
            c.put(x + 1, y + 1, "cyan")
    return c


# ---------------------------------------------------------------- crop status

def drop_set(cx, top, bot, r, n):
    """Water drop: round bottom of radius r centred at bot - r, pointed top at top."""
    cy = bot - r
    s = disc(cx, cy, r, n)
    s |= poly([(cx, top), (cx + r * 0.92, cy - r * 0.38), (cx - r * 0.92, cy - r * 0.38)], n)
    return s


def paint_drop(c, s, cx, cy, r, sep=None):
    ball(c, cx - r * 0.1, cy - r * 0.2, r * 1.15, r * 1.5, BLUE, s=s, off=0.35, sep=sep)


def ic_water(n):
    c = Canvas(n)
    k = n / 14
    s = drop_set(7.0 * k, 0.0, 14.0 * k, 5.2 * k, n)
    paint_drop(c, s, 7.0 * k, 8.8 * k, 5.2 * k)
    if n == 14:
        for p in ((4, 7), (4, 8), (4, 9), (5, 6)):
            c.put(*p, "white")
    else:
        for p in ((8, 13), (8, 14), (7, 15), (7, 16), (7, 17), (7, 18), (8, 19), (9, 12)):
            c.put(*p, "white")
        c.put(8, 15, "cyan")
        c.put(8, 16, "cyan")
    return c


def ic_thirsty(n):
    c = Canvas(n)
    k = n / 14
    s = drop_set(5.2 * k, 1.0 * k, 14.0 * k, 4.4 * k, n)
    paint_drop(c, s, 5.2 * k, 9.6 * k, 4.4 * k)
    if n == 14:
        for p in ((3, 8), (3, 9), (4, 7)):
            c.put(*p, "white")
        bang = rect(11, 0, 12, 7) | rect(11, 10, 12, 11)
        bang -= {(11, 7), (12, 7)}
        bang |= {(11, 7)}
        for (x, y) in bang:
            c.put(x, y, "pink" if x == 11 and y < 9 else "red")
        c.put(12, 6, "red_d")
        c.put(12, 7, "red_d")
        c.put(12, 11, "red_d")
    else:
        for p in ((5, 15), (5, 16), (5, 17), (5, 18), (6, 14), (6, 19)):
            c.put(*p, "white")
        bang = poly([(20.4, 0.4), (26.6, 0.4), (25.0, 16.0), (22.0, 16.0)], n) | disc(23.5, 21.6, 2.8, n)
        bevel(c, bang, RED, base=2.0, hi=1.0, lo=1.0)
        c.put(21, 1, "white")
        c.put(21, 2, "white")
    return c


def sparkle(c, cx, cy, R, r, ramp=SUN, v=3.2):
    s = poly(star_pts(cx, cy, R, r, pts=4, rot=-90), c.n)
    cols = {}
    for (x, y) in s:
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        d = math.hypot(dx, dy) / R
        vv = v + 1.0 - 2.2 * d - 0.2 * (dx + dy) / R
        cols[(x, y)] = tone(ramp, vv, x, y)
    paint(c, cols)
    return s


def ic_ready(n):
    c = Canvas(n)
    k = n / 14
    if n == 14:
        rows = [
            ".....y.......",
            ".....y.......",
            "....yyy......",
            "....ywa......",
            "..yywwwaa....",
            "yyywwwwwaaa..",
            "..aawwwaa....",
            "....ywa......",
            "....yaa......",
            ".....a.......",
            ".....a.......",
        ]
        rows = [
            ".....y........",
            ".....y........",
            "....yya.......",
            "....ywa.......",
            "...ywwwa......",
            "yyywwwwwaaa...",
            "...awwwa......",
            "....ywa....w..",
            "....yaa...wyw.",
            ".....a.....w..",
            ".....a........",
        ]
        c.stamp(0, 1, rows, {"y": "yellow", "w": "white", "a": "amber"})
        return c
    sparkle(c, 11.0, 12.5, 11.5, 2.9)
    sparkle(c, 22.5, 21.5, 5.0, 1.5)
    sparkle(c, 22.0, 4.5, 3.6, 1.2)
    return c


def ic_withered(n):
    """Wilted flower: a bent brown stalk carrying a dead head of dry brown petals that hangs
    down. Drawn as a flower (not a leaf) so it reads as dead bloom at 1x and 2x."""
    c = Canvas(n)
    if n == 14:
        head, R, r, sw = (8.5, 4.2), 2.9, 2.0, 1.3
        stalk_pts = [(6.0, 13.0), (6.6, 9.0), (8.0, 7.0)]
    else:
        head, R, r, sw = (17.0, 9.0), 5.6, 3.8, 2.4
        stalk_pts = [(9.0, 27.0), (11.5, 19.0), (14.5, 13.0)]
    # petals fanned over the lower half so the head hangs; the top stays open
    angles = (0, 40, 90, 140, 180)
    stalk = set()
    for a, b in zip(stalk_pts, stalk_pts[1:]):
        stalk |= capsule(a, b, sw, n)
    split = (stalk_pts[0][0] + head[0]) / 2
    paint(c, {p: ("brown_l" if p[0] < split else "brown") for p in stalk})
    petals = set()
    for ang in angles:
        a = math.radians(ang)
        petals |= disc(head[0] + R * math.cos(a), head[1] + R * math.sin(a), r, n)
    centre = disc(head[0], head[1], R * 0.55, n)
    petals = clip(petals, n) - centre
    dome(c, petals, DRIED, depth=max(1.5, r * 0.9))
    ball(c, head[0], head[1], R * 0.55, R * 0.55, ["brown_d", "brown_d", "brown", "brown"], gain=0.9, s=centre)
    return c


def ic_quality(ramp, n, sep):
    c = Canvas(n)
    if n == 14:
        s = poly(star_pts(7.0, 7.5, 6.2, 2.8), n)
        dome(c, s, ramp, depth=1.6, off=0.4)
        c.put(6, 5, "white")
        c.put(5, 6, "white")
        return c
    s = poly(star_pts(14.0, 15.0, 12.6, 5.6), n)
    dome(c, s, ramp, depth=3.4, off=0.35)
    for p in ((12, 9), (11, 10), (12, 10), (10, 11), (13, 8)):
        c.put(*p, "white")
    return c


# ---------------------------------------------------------------- buildings / storage

def ic_storage(n):
    c = Canvas(n)
    k = n / 14
    x0, x1 = int(1 * k), n - int(1 * k) - 1
    y0, y1 = int(1 * k), n - 1
    fw = 2 if n == 14 else 3
    box = rect(x0, y0, x1, y1)
    plank_h = 3 if n == 14 else 4
    # recessed panel planks (darker)
    for (x, y) in box:
        v = 1.8
        if (y - y0 - fw) % plank_h == plank_h - 1:
            v = 0.8
        if n == 28 and (x * 5 + (y // plank_h) * 7) % 13 == 0:
            v -= 0.5
        c.put(x, y, tone(WOOD, v, x, y))
    # frame + X brace (lighter boards)
    ix0, ix1, iy0, iy1 = x0 + fw, x1 - fw, y0 + fw, y1 - fw
    bw = 2.2 if n == 14 else 3.6
    brace = capsule((ix0 + 0.5, iy0 + 0.5), (ix1 + 0.5, iy1 + 0.5), bw, n)
    brace = {p for p in brace if ix0 <= p[0] <= ix1 and iy0 <= p[1] <= iy1}
    for (x, y) in brace:
        v = 3.2
        if (x + 1, y + 1) not in brace and (x + 1, y) not in brace:
            v = 2.0
        elif (x - 1, y - 1) not in brace:
            v = 3.8
        c.put(x, y, tone(WOOD, v, x, y))
    frame = {(x, y) for (x, y) in box if x < ix0 or x > ix1 or y < iy0 or y > iy1}
    for (x, y) in frame:
        v = 3.0
        if x == x0 or y == y0:
            v = 3.9
        if x == x1 or y == y1:
            v = 1.6
        if (x == ix0 - 1 and iy0 <= y <= iy1) or (y == iy0 - 1 and ix0 <= x <= ix1):
            v = 2.4
        c.put(x, y, tone(WOOD, v, x, y))
    # nails
    for cx_, cy_ in ((x0 + fw // 2, y0 + fw // 2), (x1 - fw // 2, y0 + fw // 2), (x0 + fw // 2, y1 - fw // 2), (x1 - fw // 2, y1 - fw // 2)):
        c.put(cx_, cy_, "grey_l" if n == 28 else "brown")
        if n == 28:
            c.put(cx_ + 1, cy_ + 1, "slate")
    if n == 28:
        for x in range(x0 + 4, x1 - 3, 5):
            c.put(x, y0 + 1, "brown_l")
    return c


def ic_silo(n):
    c = Canvas(n)
    if n == 14:
        put_map(c, SILO14)
        return c
    x0, x1 = 6, 21
    top = 9
    w = x1 - x0 + 1
    body = rect(x0, top, x1, n - 1)
    for (x, y) in body:
        u = (x - x0) / (w - 1)
        v = 2.2 - 1.4 * u
        if u < 0.12:
            v = 3.0
        elif u > 0.85:
            v = 0.9
        if (y - top) % 5 == 4:
            v -= 0.8 if u > 0.3 else 0.4
        c.put(x, y, tone(RED, v, x, y))
    # white trim band under the dome
    for x in range(x0, x1 + 1):
        u = (x - x0) / (w - 1)
        for y in (top, top + 1):
            c.put(x, y, tone(CREAM, 3.4 - 2.0 * u + (0.4 if y == top else 0), x, y))
    # dome
    dcx = (x0 + x1 + 1) / 2
    dome_s = {p for p in disc(dcx, top + 0.5, w / 2 + 0.6, n, 8.4) if p[1] < top}
    ball(c, dcx - 0.5, top - 1.0, w / 2 + 1.5, 9.0, SILVER, s=dome_s, off=0.3)
    for y in range(0, 2):
        for x in range(13, 15):
            c.put(x, y, "grey" if x == 13 else "grey_d")
    c.put(13, 0, "grey_l")
    # door with cream frame
    for y in range(19, 28):
        for x in range(10, 17):
            if x in (10, 16) or y == 19:
                c.put(x, y, "cream" if x == 10 or y == 19 else "tan")
            else:
                c.put(x, y, tone(WOOD, 1.6 if (x + y) % 3 else 1.0, x, y))
    for i in range(5):
        c.put(11 + i, 20 + i * 2 if i < 4 else 27, "tan_d") if False else None
    for x, y in ((11, 20), (12, 21), (13, 22), (14, 23), (15, 24), (15, 20), (14, 21), (12, 23), (11, 24)):
        c.put(x, y, "tan_d")
    c.put(15, 26, "amber")
    # window
    for x, y in ((12, 13), (13, 13), (14, 13), (12, 14), (13, 14), (14, 14), (12, 15), (13, 15), (14, 15)):
        c.put(x, y, "slate_d")
    c.put(12, 13, "blue")
    c.put(13, 13, "cyan")
    # ladder on the right
    for y in range(top + 2, n - 1):
        c.put(19, y, "grey")
        c.put(21, y, "grey_d")
        if y % 3 == 0:
            c.put(20, y, "grey_l")
    return c


# ---------------------------------------------------------------- lock / signs / glyphs

def lock_body(c, x0, y0, x1, y1):
    s = rect(x0, y0, x1, y1)
    s -= {(x0, y0), (x1, y0), (x0, y1), (x1, y1)}
    bevel(c, s, GOLD, base=2.6, hi=1.0, lo=1.4, grad=0.6)
    return s


def shackle(c, cx, cy, r_out, r_in, left_len, right_len, n):
    s = {(x, y) for (x, y) in disc(cx, cy, r_out, n) if y + 0.5 <= cy} - disc(cx, cy, r_in, n)
    lx0, lx1 = int(round(cx - r_out)), int(round(cx - r_in)) - 1
    rx0, rx1 = int(round(cx + r_in)), int(round(cx + r_out)) - 1
    for y in range(int(cy), int(cy) + left_len):
        s |= {(x, y) for x in range(lx0, lx1 + 1)}
    for y in range(int(cy), int(cy) + right_len):
        s |= {(x, y) for x in range(rx0, rx1 + 1)}
    s = clip(s, n)
    cols = {}
    for (x, y) in s:
        v = 2.4
        if (x - 1, y) not in s or (x, y - 1) not in s:
            v = 3.6
        if (x + 1, y) not in s or (x, y + 1) not in s:
            v = 1.3
        cols[(x, y)] = tone(SILVER, v, x, y)
    paint(c, cols)
    return s


def keyhole(c, cx, y, big):
    if not big:
        c.put(cx, y, "ink")
        c.put(cx + 1, y, "ink")
        c.put(cx, y + 1, "ink")
        c.put(cx + 1, y + 1, "ink")
        c.put(cx, y + 2, "brown_d")
        c.put(cx + 1, y + 2, "brown_d")
    else:
        for p in ((cx - 1, y), (cx, y), (cx + 1, y), (cx - 1, y + 1), (cx, y + 1), (cx + 1, y + 1), (cx - 1, y + 2), (cx, y + 2),
                  (cx + 1, y + 2), (cx, y + 3), (cx, y + 4), (cx, y + 5), (cx - 1, y + 4), (cx + 1, y + 4), (cx - 1, y + 5), (cx + 1, y + 5)):
            c.put(*p, "ink")
        c.put(cx - 1, y, "brown_d")


def ic_lock(n):
    c = Canvas(n)
    if n == 14:
        shackle(c, 7.0, 6.0, 4.6, 2.2, 2, 2, n)
        lock_body(c, 1, 6, 12, 13)
        keyhole(c, 6, 8, False)
        c.put(2, 7, "white")
        return c
    shackle(c, 14.0, 12.0, 9.0, 4.6, 2, 2, n)
    lock_body(c, 2, 12, 25, 27)
    keyhole(c, 14, 16, True)
    for p in ((3, 13), (4, 13), (3, 14)):
        c.put(*p, "white")
    for x in range(3, 25):
        if x % 3 == 0:
            c.put(x, 25, "clay")
    return c


def ic_unlock(n):
    c = Canvas(n)
    if n == 14:
        shackle(c, 9.4, 4.2, 4.0, 1.8, 2, 4, n)
        lock_body(c, 1, 7, 12, 13)
        keyhole(c, 6, 9, False)
        c.put(2, 8, "white")
        return c
    shackle(c, 19.0, 8.6, 8.2, 4.0, 3, 6, n)
    lock_body(c, 2, 14, 25, 27)
    keyhole(c, 14, 17, True)
    for p in ((3, 15), (4, 15), (3, 16)):
        c.put(*p, "white")
    return c


def ic_warning(n):
    c = Canvas(n)
    k = n / 14
    tri = poly([(7.0, 0.2), (14.0, 13.6), (0.0, 13.6)], n, k)
    cols = {}
    for (x, y) in tri:
        v = 3.0
        if (x + 1, y) not in tri or (x, y + 1) not in tri:
            v = 1.6
        elif (x - 1, y) not in tri:
            v = 4.0
        cols[(x, y)] = tone(SUN, v, x, y)
    paint(c, cols)
    if n == 14:
        for y in range(4, 9):
            c.put(6, y, "ink")
            c.put(7, y, "ink")
        c.put(6, 10, "ink")
        c.put(7, 10, "ink")
        c.put(6, 11, "ink")
        c.put(7, 11, "ink")
        c.put(6, 8, CLEAR)
        c.put(6, 8, "brown_d")
    else:
        bang = poly([(12.0, 8.0), (16.0, 8.0), (15.2, 19.0), (12.8, 19.0)], n)
        bang |= disc(14.0, 22.6, 2.0, n)
        for p in bang:
            c.put(*p, "ink")
        for y in range(9, 18):
            c.put(15 if y < 14 else 14, y, "brown_d") if False else None
    return c


def glyph_fill(c, s, ramp, sep=None):
    return bevel(c, s, ramp, base=2.6, hi=1.2, lo=1.3, grad=0.4, sep=sep, width=1 if c.n == 14 else 2)


def ic_check(n):
    c = Canvas(n)
    k = n / 14
    w = 3.3 * k
    s = capsule((2.0 * k, 7.6 * k), (5.4 * k, 11.2 * k), w, n) | capsule((5.4 * k, 11.2 * k), (12.2 * k, 2.4 * k), w, n)
    glyph_fill(c, s, GREEN)
    return c


def ic_cross(n):
    c = Canvas(n)
    k = n / 14
    w = 3.4 * k
    s = capsule((2.4 * k, 2.4 * k), (11.6 * k, 11.6 * k), w, n) | capsule((11.6 * k, 2.4 * k), (2.4 * k, 11.6 * k), w, n)
    glyph_fill(c, s, RED)
    return c


def ic_plus(n):
    c = Canvas(n)
    k = n / 14
    a, b = int(4.5 * k), int(9.5 * k) - 1
    lo, hi = int(1 * k), n - int(1 * k) - 1
    s = rect(a, lo, b, hi) | rect(lo, a, hi, b)
    s -= {(a, lo), (b, lo), (a, hi), (b, hi), (lo, a), (lo, b), (hi, a), (hi, b)}
    glyph_fill(c, s, GREEN)
    return c


def ic_minus(n):
    c = Canvas(n)
    k = n / 14
    a, b = int(4.5 * k), int(9.5 * k) - 1
    lo, hi = int(1 * k), n - int(1 * k) - 1
    s = rect(lo, a, hi, b)
    glyph_fill(c, s, RED)
    return c


def arrow_set(n, k):
    head = poly([(7.0, 0.2), (13.8, 7.2), (0.2, 7.2)], n, k)
    shaft = rect(int(4.5 * k), int(6.5 * k), int(9.5 * k) - 1, n - 1 - (0 if n == 14 else 1))
    return head | shaft


def ic_arrow_up(n):
    c = Canvas(n)
    s = arrow_set(n, n / 14)
    glyph_fill(c, s, GREEN)
    return c


def ic_arrow_down(n):
    c = Canvas(n)
    s = arrow_set(n, n / 14)
    s = {(x, n - 1 - y) for (x, y) in s}
    glyph_fill(c, s, RED)
    return c


def ic_timer(n):
    c = Canvas(n)
    k = n / 14
    m = 2 if n == 14 else 3
    x0, x1 = int(1 * k), n - int(1 * k) - 1
    # glass bulbs
    glass = poly([(2.4, 2.0), (11.6, 2.0), (11.0, 4.6), (7.9, 7.0), (11.0, 9.4), (11.6, 12.0), (2.4, 12.0), (3.0, 9.4), (6.1, 7.0), (3.0, 4.6)], n, k)
    cols = {}
    for (x, y) in glass:
        v = 2.4 if (x + 1, y) not in glass else 3.0
        if (x - 1, y) not in glass:
            v = 4.0
        cols[(x, y)] = tone(["grey_d", "grey", "grey_l", "white", "white"], v, x, y)
    paint(c, cols)
    # sand: top half partly full, stream, pile at the bottom
    for (x, y) in glass:
        yy = (y + 0.5) / k
        inner = (x - 1, y) in glass and (x + 1, y) in glass
        if not inner:
            continue
        if 4.0 <= yy < 7.0:
            c.put(x, y, tone(SUN, 2.6 if (x + 1, y) in glass and (x + 2, y) in glass else 1.8, x, y))
        if yy >= 9.6 and yy < 12.0 - 0.5 / k:
            c.put(x, y, tone(SUN, 2.6 if (x + 1, y) in glass and (x + 2, y) in glass else 1.8, x, y))
    for y in range(int(7 * k), int(9.6 * k)):
        c.put(int(7 * k) - 1 if n == 14 else 13, y, "amber")
        if n == 28:
            c.put(14, y, "amber")
    # caps
    for yy0 in (0, n - m):
        cap = rect(x0, yy0, x1, yy0 + m - 1)
        for (x, y) in cap:
            v = 3.0 if y == yy0 else 1.6
            if x == x1:
                v -= 0.8
            if x == x0:
                v += 0.6
            c.put(x, y, tone(WOOD, v, x, y))
    # side posts (big only)
    if n == 28:
        for y in range(m, n - m):
            c.put(x0 + 1, y, "brown_l")
            c.put(x0 + 2, y, "brown")
            c.put(x1 - 1, y, "brown")
            c.put(x1 - 2, y, "brown_d")
        for p in ((7, 5), (7, 6), (8, 4)):
            c.put(*p, "white")
    else:
        c.put(3, 3, "white")
    return c


# ---------------------------------------------------------------- main

KEY = {"k": "ink", "w": "white", "c": "cream", "n": "tan", "N": "tan_d", "y": "yellow", "a": "amber", "o": "orange",
       "l": "clay", "u": "rust", "b": "brown_l", "B": "brown", "z": "brown_d", "r": "red", "R": "red_d", "p": "pink",
       "m": "magenta", "M": "purple", "g": "green", "G": "green_d", "h": "green_dd", "t": "teal_dd", "i": "cyan",
       "e": "blue", "E": "blue_d", "s": "grey_l", "S": "grey", "d": "grey_d", "D": "slate", "x": "slate_d"}


def put_map(c, rows, key=None, ox=None, oy=None):
    """Stamp a hand-drawn character map (KEY letters), centred on the canvas unless placed."""
    key = KEY if key is None else key
    h, w = len(rows), max(len(r) for r in rows)
    ox = (c.n - w) // 2 if ox is None else ox
    oy = (c.n - h) // 2 if oy is None else oy
    c.stamp(ox, oy, rows, key)
    return {(ox + x, oy + y) for y, r in enumerate(rows) for x, ch in enumerate(r) if ch not in ". "}


def compose(base, top, ox, oy, sep=None, n=14):
    """Lay map `top` over map `base` at (ox, oy); `sep` draws a line on base pixels it overlaps."""
    rows = [list(r.ljust(n, ".")) for r in base] + [list("." * n) for _ in range(n - len(base))]
    cells = {(x + ox, y + oy) for y, r in enumerate(top) for x, ch in enumerate(r) if ch != "."}
    for y, r in enumerate(top):
        for x, ch in enumerate(r):
            if ch != ".":
                rows[y + oy][x + ox] = ch
    if sep:
        for (x, y) in cells:
            for dx, dy in N4:
                m = (x + dx, y + dy)
                if m not in cells and 0 <= m[0] < n and 0 <= m[1] < n and rows[m[1]][m[0]] != ".":
                    rows[m[1]][m[0]] = sep
    return ["".join(r) for r in rows]


def edge_over(c, s, color):
    """Separation line on already-painted pixels that touch shape s (s sits in front of them)."""
    for (x, y) in list(s):
        for dx, dy in N4:
            m = (x + dx, y + dy)
            if m not in s and c.on(*m):
                c.put(m[0], m[1], color)


COIN14 = [
    "....yyyy....",
    "..yyyyyyya..",
    ".ywwaaaaaal.",
    ".ywaaaaaaal.",
    "yyaaayyaaaal",
    "yyaaayylaaal",
    "yyaaayylaaal",
    "yaaaayylaaal",
    ".aaaaallaal.",
    ".aaaaaaaall.",
    "..llaaaall..",
    "....llll....",
]


STACK14 = [".yyyyyy.", "ywaaaaay", "lyyyyyyl", ".llllll.", "ayyyyyyl", ".llllll.", "ayyyyyyl", ".llllll.",
           "ayyyyyyl", ".llllll."]


FACE8 = ["..yyyy..", ".ywyyyy.", "ywaaaaal", "yaaayaal", "yaaayaal", "yaaaaall", ".laaaal.", "..llll.."]


LEVEL14 = [
    ".iee....rrR.",
    ".iiee..rrRR.",
    "..iee..rrR..",
    "..iiee.rRR..",
    "...ieyyyR...",
    "..yyaaaayl..",
    ".yaaawaaaal.",
    ".yaayyyaaal.",
    "yaayyyyyaall",
    "yaaayyyaaall",
    "yaaayayaaall",
    ".yaaaaaaall.",
    ".laaaaaalll.",
    "...llllll...",
]


def ribbon(c, pts, ramp, stripe):
    s = poly(pts, c.n)
    a = ((pts[0][0] + pts[1][0]) / 2, (pts[0][1] + pts[1][1]) / 2)
    b = ((pts[2][0] + pts[3][0]) / 2, (pts[2][1] + pts[3][1]) / 2)
    cols = {}
    for (x, y) in s:
        v = 2.0
        if (x - 1, y) not in s:
            v = 3.0
        elif (x + 1, y) not in s:
            v = 1.0
        if seg_d(x + 0.5, y + 0.5, a, b) < 0.9:
            cols[(x, y)] = stripe
            continue
        cols[(x, y)] = tone(ramp, v, x, y)
    paint(c, cols, ramp[0])
    return s


MOON14 = [
    "...wwc........",
    "..wcc.........",
    ".wcc..........",
    "wccc.......y..",
    "wcc.......yay.",
    "wcc........y..",
    "wccBB.........",
    "wcccc.........",
    "wpcccc......c.",
    "ncccccc...cN..",
    ".nccccccccN...",
    "..NnccccnN....",
    "....NNNN......",
]


def blossom(c, cx, cy, R, pr, notch=0.0):
    n = c.n
    k = n / 14
    seg = 2 * math.pi / 5
    cols = {}
    for y in range(n):
        for x in range(n):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            th = math.atan2(dy, dx) + math.pi / 2
            a = (th + seg / 2) % seg - seg / 2
            lim = R * (pr + (1 - pr) * math.cos(a * 5 / 2) ** 0.5)
            if notch and abs(a) < notch and d > lim - 1.6 * k:
                continue
            if d > lim:
                continue
            lit = -(dx + dy) / max(d, 0.1)
            if d < 1.6 * k:
                cc = "yellow" if dx + dy < 0 else "amber"
            elif abs(a) > seg / 2 - 0.22 / k and d < lim - 0.5:
                cc = "magenta"
            elif n == 14:
                cc = "white" if (lit > 0.5 and d > lim - 1.6) else ("pink" if lit > -0.6 or d < lim - 1.5 else "magenta")
            else:
                v = 2.0 + 0.25 * lit
                if d < 4.6:
                    v = 1.2 + (d - 1.6 * k) * 0.3
                edge = d > lim - 1.2
                if edge and lit > 0.45:
                    v = 3.0
                elif edge and lit < -0.3:
                    v = 1.0
                elif d > lim - 2.4 and lit > 0.6:
                    v = 2.5
                cols[(x, y)] = tone(PETAL, v, x, y)
                continue
            cols[(x, y)] = E[cc]
    paint(c, cols)
    if n == 28:
        for i in range(5):
            a = -math.pi / 2 + i * seg
            for rr in (3.6, 4.8):
                c.put(int(cx + rr * math.cos(a)), int(cy + rr * math.sin(a)), "yellow" if rr > 4 else "amber")
        c.put(int(cx) - 1, int(cy) - 1, "white")


SNOW14 = [
    "......w......",
    "....w.w.w....",
    ".....www.....",
    ".w....w....w.",
    "wiw...w...wiw",
    ".wwww.w.wwww.",
    "....wwiww....",
    ".wwww.w.wwww.",
    "wiw...w...wiw",
    ".w....w....w.",
    ".....www.....",
    "....w.w.w....",
    "......w......",
]


def tri_rays(c, cx, cy, r0, r1, count, w0, ramp=SUN, v=2.4, rot=0.0):
    n = c.n
    for i in range(count):
        a = rot + 2 * math.pi * i / count
        ux, uy = math.cos(a), math.sin(a)
        px_, py_ = -uy, ux
        pts = [(cx + r0 * ux + w0 / 2 * px_, cy + r0 * uy + w0 / 2 * py_), (cx + r1 * ux, cy + r1 * uy),
               (cx + r0 * ux - w0 / 2 * px_, cy + r0 * uy - w0 / 2 * py_)]
        s = poly(pts, n)
        for (x, y) in s:
            lit = -(ux + uy) * 0.7
            side = ((x + 0.5 - cx) * px_ + (y + 0.5 - cy) * py_)
            c.put(x, y, tone(ramp, v + 0.7 * lit + (0.4 if side < 0 else -0.3), x, y))


PUFFS = [(7.4, 4.6, 3.7), (11.0, 6.9, 2.7), (3.7, 7.3, 2.8), (7.6, 8.0, 2.6)]


def cloud(c, ramp=CLOUD, ox=0.0, oy=0.0, sc=1.0, sep=None, off=0.0, base=10.4):
    """Puffy cloud: overlapping shaded puffs (back to front) with a flat base."""
    n = c.n
    k = n / 14
    allp = set()
    first = True
    for (px_, py_, r) in PUFFS:
        cx, cy, rr = (px_ * sc + ox) * k, (py_ * sc + oy) * k, r * sc * k
        s = {p for p in disc(cx, cy, rr, n) if p[1] + 0.5 <= (base * sc + oy) * k}
        s = clip(s, n)
        cols = {}
        for (x, y) in s:
            nx, ny = (x + 0.5 - cx) / rr, (y + 0.5 - cy) / rr
            lv = -(nx + ny) * 0.7
            v = 2.9 + 0.9 * lv + off
            if y + 1 >= (base * sc + oy) * k - 0.01:
                v = 1.4 + off
            cols[(x, y)] = tone(ramp, v, x, y)
        if first:
            paint(c, cols, sep)
            first = False
        else:
            paint(c, {p: cc for p, cc in cols.items() if p not in allp}, None)
            for p, cc in cols.items():
                if p in allp:
                    c.put(*p, cc)
            # separation arc where this puff sits on earlier ones
            if n > 14:
                for (x, y) in s:
                    if any((x + dx, y + dy) in allp and (x + dx, y + dy) not in s for dx, dy in N4) and y + 1 < (base * sc + oy) * k - 0.5:
                        c.put(x, y, ramp[2])
        allp |= s
    return allp


def leaf_ovate(base, tip, hw, n):
    """Broad leaf: widest a third of the way up, pointed tip. {pixel: (t, side)}."""
    s = {}
    L = math.hypot(tip[0] - base[0], tip[1] - base[1])
    ux, uy = (tip[0] - base[0]) / L, (tip[1] - base[1]) / L
    for y in range(n):
        for x in range(n):
            px_, py_ = x + 0.5 - base[0], y + 0.5 - base[1]
            t = (px_ * ux + py_ * uy) / L
            if t < 0 or t > 1:
                continue
            off = -px_ * uy + py_ * ux
            w = hw * math.sin(math.pi * t ** 0.72) ** 0.75
            if abs(off) <= w + 0.2:
                s[(x, y)] = (t, off / max(w, 0.01))
    return s


APPLE14 = [
    ".....z.gg..",
    ".....zgg...",
    "..rrrzrrrr.",
    ".rwprrrrrrr",
    "rwpprrrrrrR",
    "rpprrrrrrrR",
    "rrrrrrrrrRR",
    "rrrrrrrrrRR",
    "rrrrrrrrRRM",
    ".rrrrrrRRM.",
    ".RrrrrRRRM.",
    "..RRM.RMM..",
]


BOLT14 = [
    "..wya",
    ".wya.",
    "wyyya",
    "..ya.",
    ".ya..",
    ".a...",
]


SILO14 = [
    "...sd...",
    ".wsssSd.",
    "wssssSSd",
    "wssssSSd",
    "ccccnnNN",
    "prrrrrRR",
    "prriirRR",
    "pRrRRRRR",
    "prrrrrRR",
    "prrrrrRR",
    "pRccccRR",
    "prcBBcRR",
    "prcBzcRR",
    "prcBBcRR",
]


ICONS = {
    "coin": ic_coin, "coins": ic_coins, "gem": ic_gem, "energy": ic_energy, "energy_food": ic_energy_food,
    "xp": ic_xp, "level": ic_level, "heart": ic_heart, "clock": ic_clock, "sun": ic_sun, "moon": ic_moon,
    "sunrise": ic_sunrise, "season_spring": ic_spring, "season_summer": ic_summer, "season_fall": ic_fall,
    "season_winter": ic_winter, "weather_sunny": ic_sunny, "weather_cloudy": ic_cloudy, "weather_rain": ic_rain,
    "weather_storm": ic_storm, "weather_snow": ic_snow, "water": ic_water, "thirsty": ic_thirsty, "ready": ic_ready,
    "withered": ic_withered,
    "quality_silver": lambda n: ic_quality(SILVER, n, "grey_d"),
    "quality_gold": lambda n: ic_quality(GOLD, n, "clay"),
    "quality_iridium": lambda n: ic_quality(IRID, n, "purple"),
    "storage": ic_storage, "silo": ic_silo, "lock": ic_lock, "unlock": ic_unlock, "warning": ic_warning,
    "check": ic_check, "cross": ic_cross, "plus": ic_plus, "minus": ic_minus, "arrow_up": ic_arrow_up,
    "arrow_down": ic_arrow_down, "timer": ic_timer,
}


def main():
    art = sys.argv[1]
    small, big = Sprites(art, "icons"), Sprites(art, "icons_big")
    for name, fn in ICONS.items():
        for sp, n in ((small, 14), (big, 28)):
            DITHER[0] = n > 14
            body = fn(n).im
            assert body.size == (n, n), (name, body.size)
            out, pad = finish(body)
            sp.save(name, out, n // 2 + pad, n // 2 + pad)
    print(json.dumps({**small.pivots, **big.pivots}))


main()
