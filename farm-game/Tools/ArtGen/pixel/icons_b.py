"""Icons B (pixel-direct): navigation, menus, actions and tools.

icons/<name>      body fits 14x14, for HUD pills and buttons.
icons_big/<name>  body fits 28x28, redrawn with more detail (not an upscale).
Pivot = centre of the 14/28 body (+ the finish() pad).

Shapes are built from pixel sets (rects, polygons, ellipses, thick lines, char maps) and
shaded with Endesga-32 ramps lit from the top-left: bevel() for flat boxes and planks,
dome() for soft round things, cyl() for metal, with checker dither between ramp steps.
finish() adds the 1px ink outline and the hard drop shadow.
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, CLEAR, Sprites, new, from_map, finish  # noqa: E402,F401
import json  # noqa: E402
import math  # noqa: E402

_L = (-0.55, -0.7, 0.6)
LIGHT = tuple(v / math.sqrt(sum(u * u for u in _L)) for v in _L)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

WOOD = ["brown_d", "brown", "brown_l", "tan_d", "tan"]
PLANK = ["brown", "brown_l", "tan_d", "tan", "cream"]
LEAF = ["teal_dd", "green_dd", "green_d", "green", "yellow"]
METAL = ["slate", "grey_d", "grey", "grey_l", "white"]
IRON = ["slate_d", "slate", "grey_d", "grey", "grey_l"]
STONE = ["slate_d", "slate", "grey_d", "grey", "grey_l"]
GOLD = ["rust", "orange", "amber", "yellow", "white"]
COIN = ["clay", "orange", "amber", "yellow", "cream"]
REDS = ["purple", "red_d", "red", "pink", "white"]
BLUES = ["slate_d", "blue_d", "blue", "cyan", "white"]
GREENS = ["green_dd", "green_d", "green", "yellow", "white"]
PAPER = ["tan_d", "tan", "cream", "white"]
BURLAP = ["brown", "brown_l", "tan_d", "tan", "cream"]
CREAM = ["tan_d", "tan", "cream", "white", "white"]
PURPLE = ["slate_d", "purple", "magenta", "pink", "white"]
TEAL = ["teal_dd", "blue_d", "blue", "cyan", "white"]


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


# ---------------------------------------------------------------- pixel sets

def rect(x0, y0, x1, y1):
    return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def rrect(x0, y0, x1, y1, r=1):
    """Rect with staircase-cut corners (r=1 drops the 4 corner pixels)."""
    s = rect(x0, y0, x1, y1)
    for x, y in list(s):
        dx = min(x - x0, x1 - x)
        dy = min(y - y0, y1 - y)
        if dx < r and dy < r and dx + dy < r:
            s.discard((x, y))
    return s


def ell(cx, cy, rx, ry, k=1.0):
    s = set()
    for y in range(math.floor(cy - ry) - 1, math.ceil(cy + ry) + 2):
        for x in range(math.floor(cx - rx) - 1, math.ceil(cx + rx) + 2):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= k:
                s.add((x, y))
    return s


def poly(pts):
    """Pixels whose centre lies inside the polygon (vertices in pixel-centre space)."""
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    s = set()
    for y in range(math.floor(min(ys)), math.ceil(max(ys)) + 1):
        for x in range(math.floor(min(xs)), math.ceil(max(xs)) + 1):
            inside = False
            j = len(pts) - 1
            for i in range(len(pts)):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                    inside = not inside
                j = i
            on_edge = False
            for i in range(len(pts)):
                ax, ay = pts[i - 1]
                bx, by = pts[i]
                if seg_d(x, y, ax, ay, bx, by) < 0.3:
                    on_edge = True
            if inside or on_edge:
                s.add((x, y))
    return s


def seg_d(x, y, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy
    t = 0.0 if L2 == 0 else max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / L2))
    return math.hypot(x - ax - dx * t, y - ay - dy * t)


def thick(a, b, r):
    """Pixels within r of segment a-b (a capsule)."""
    s = set()
    for y in range(math.floor(min(a[1], b[1]) - r) - 1, math.ceil(max(a[1], b[1]) + r) + 2):
        for x in range(math.floor(min(a[0], b[0]) - r) - 1, math.ceil(max(a[0], b[0]) + r) + 2):
            if seg_d(x, y, a[0], a[1], b[0], b[1]) <= r:
                s.add((x, y))
    return s


def line(a, b):
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


def ring(cx, cy, r0, r1):
    return {(x, y) for (x, y) in ell(cx, cy, r1, r1) if (x - cx) ** 2 + (y - cy) ** 2 >= r0 * r0}


def arc_pts(cx, cy, r, a0, a1, step=None):
    """Integer pixels along a circular arc, angles in degrees (0 = right, 90 = down)."""
    out = []
    n = max(8, int(abs(a1 - a0) / 180 * math.pi * r * 3))
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        p = (round(cx + r * math.cos(a)), round(cy + r * math.sin(a)))
        if not out or out[-1] != p:
            out.append(p)
    return out


def mapset(rows, ch, x0=0, y0=0):
    return {(x0 + x, y0 + y) for y, row in enumerate(rows) for x, c in enumerate(row) if c in ch}


# ---------------------------------------------------------------- canvas

class Canvas:
    def __init__(self, n):
        self.w = self.h = n
        self.im = new(n, n)
        self.px = self.im.load()

    def inb(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def on(self, x, y):
        return self.inb(x, y) and self.px[x, y][3] > 0

    def put(self, x, y, c):
        if c is not None and self.inb(x, y):
            self.px[x, y] = col(c)

    def erase(self, s):
        for x, y in s:
            if self.inb(x, y):
                self.px[x, y] = CLEAR

    def paint(self, cols, sep=None):
        """Paint {pixel: colour}; edge pixels touching earlier (behind) pixels get the sep colour."""
        for (x, y), cc in cols.items():
            if sep is not None and any((x + dx, y + dy) not in cols and self.on(x + dx, y + dy) for dx, dy in N4):
                cc = sep
            self.put(x, y, cc)

    def fill(self, s, c, sep=None):
        self.paint({p: c for p in s}, sep)

    def pts(self, pts, c):
        for x, y in pts:
            self.put(x, y, c)

    def stamp(self, x0, y0, rows, key, flip=False):
        for y, row in enumerate(rows):
            if flip:
                row = row[::-1]
            for x, ch in enumerate(row):
                if ch not in ". ":
                    self.put(x0 + x, y0 + y, key[ch])

    def edge(self, s, c="ink", sides=N4):
        """Draw c on pixels of s that sit next to a pixel outside s (an inner outline)."""
        for x, y in s:
            if any((x + dx, y + dy) not in s for dx, dy in sides):
                self.put(x, y, c)

    # -- shading ----------------------------------------------------------
    def bevel(self, s, ramp, lit=None, sep=None, hi=True, soft=True, grad=0.0):
        """Flat face: light top/left rim, dark bottom/right rim, dithered inner shadow."""
        n = len(ramp) - 1
        lit = n - 1 if lit is None else lit
        ys = [p[1] for p in s]
        y0, y1 = min(ys), max(ys)
        cols = {}
        for (x, y) in s:
            u = (x, y - 1) not in s
            lf = (x - 1, y) not in s
            d = (x, y + 1) not in s
            r = (x + 1, y) not in s
            v = lit - grad * ((y - y0) / max(1, y1 - y0) - 0.5)
            if d or r:
                v = lit - 1
            elif u or lf:
                v = lit + 1 if hi else lit
            elif soft and ((x, y + 2) not in s or (x + 2, y) not in s):
                v = lit - 0.5
            cols[(x, y)] = tone(ramp, v, x, y)
        self.paint(cols, sep)
        return cols

    def dome(self, s, ramp, depth=3.0, gain=1.0, off=0.0, sep=None):
        """Auto-shade a pixel set as a soft dome lit from the top-left."""
        dist = {}
        q = [p for p in s if any((p[0] + dx, p[1] + dy) not in s for dx, dy in N4)]
        for p in q:
            dist[p] = 1
        i = 0
        while i < len(q):
            x, y = q[i]
            i += 1
            for dx, dy in N4:
                nb = (x + dx, y + dy)
                if nb in s and nb not in dist:
                    dist[nb] = dist[(x, y)] + 1
                    q.append(nb)
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
        self.paint(cols, sep)
        return cols

    def sphere(self, cx, cy, rx, ry, ramp, gain=1.0, off=0.0, sep=None, k=1.0, s=None):
        s = s if s is not None else ell(cx, cy, rx, ry, k)
        n = len(ramp) - 1
        cols = {}
        for (x, y) in s:
            nx, ny = (x - cx) / rx, (y - cy) / ry
            nz = math.sqrt(max(0.0, 1 - min(1.0, nx * nx + ny * ny)))
            lv = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
            v = ((lv + 0.3) / 1.3) * n * gain + off
            cols[(x, y)] = tone(ramp, v, x, y)
        self.paint(cols, sep)
        return s

    def cyl(self, s, ramp, lit=None, sep=None, peak=0.3, vert=False):
        """Metal cylinder: per-row span shading, bright band near the left, dark right edge."""
        n = len(ramp) - 1
        lit = n - 1.5 if lit is None else lit
        spans = {}
        for x, y in s:
            k, j = (x, y) if vert else (y, x)
            lo, hi_ = spans.get(k, (j, j))
            spans[k] = (min(lo, j), max(hi_, j))
        cols = {}
        for (x, y) in s:
            k, j = (x, y) if vert else (y, x)
            lo, hi_ = spans[k]
            t = 0.5 if hi_ == lo else (j - lo) / (hi_ - lo)
            v = lit + 1.3 - 3.2 * abs(t - peak) if t > peak else lit + 1.3 - 2.0 * (peak - t)
            cols[(x, y)] = tone(ramp, v, x, y)
        self.paint(cols, sep)
        return cols


def paint_map(c, rows, key, x0=0, y0=0):
    """Paint a char map. key values: colour name | ('bevel'|'dome'|'cyl', ramp, kwargs) |
    ('in', colour, region_char): flat colour that still counts as part of region_char's shape."""
    groups = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in ". ":
                continue
            groups.setdefault(ch, set()).add((x0 + x, y0 + y))
    regions = {}
    for ch, pts in groups.items():
        k = key[ch]
        if isinstance(k, tuple) and k[0] == "in":
            regions.setdefault(k[2], set()).update(pts)
        elif isinstance(k, tuple):
            regions.setdefault(ch, set()).update(pts)
    for ch in groups:
        k = key[ch]
        if isinstance(k, tuple) and k[0] != "in":
            kind, ramp = k[0], k[1]
            kw = k[2] if len(k) > 2 else {}
            s = regions[ch]
            if kind == "bevel":
                c.bevel(s, ramp, **kw)
            elif kind == "dome":
                c.dome(s, ramp, **kw)
            elif kind == "cyl":
                c.cyl(s, ramp, **kw)
    for ch, pts in groups.items():
        k = key[ch]
        if isinstance(k, str):
            c.fill(pts, k)
        elif k[0] == "in":
            c.fill(pts, k[1])


def sparkle(c, x, y, big=False, color="white", core="yellow"):
    if big:
        c.stamp(x - 2, y - 2, ["..w..", "..w..", "wwcww", "..w..", "..w.."], {"w": color, "c": core})
    else:
        c.stamp(x - 1, y - 1, [".w.", "wcw", ".w."], {"w": color, "c": core})


# ================================================================ icons

def nav_farm(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            "......T......",
            ".....TTT.....",
            "....TTRTT....",
            "...TTRwRTT...",
            "..TTRwkwRTT..",
            ".TTRRwywRRTT.",
            "TTRRRRRRRRRTT",
            ".RRwwwwwwwRR.",
            ".RRwwdddwwRR.",
            ".RRwdwdwdwRR.",
            ".RRwddwddwRR.",
            ".RRwdwdwdwRR.",
            ".RRwwdddwwRR.",
            "ggggggggggggg",
        ]
        paint_map(c, rows, {"T": ("bevel", STONE, {"lit": 2.5}), "R": ("bevel", REDS, {"lit": 2}),
                            "w": "white", "d": "red_d", "k": "ink", "y": "amber",
                            "g": ("bevel", LEAF, {"lit": 2.5, "soft": False})}, x0=1)
        return c
    # big: barn with gambrel roof, silo behind on the right, grass strip
    silo = rect(19, 6, 25, 24)
    c.cyl(silo, METAL, lit=2.4)
    cap = ell(22, 6, 3.5, 3.3) - rect(18, 7, 26, 12)
    c.sphere(21.4, 5.2, 3.6, 3.6, REDS, s=cap, off=0.3)
    for y in (11, 17, 23):
        c.pts([(x, y) for x in range(19, 26)], "grey_d")
    roof = poly([(10, 1), (16, 4), (20.4, 12.5), (-0.4, 12.5), (4, 4)])
    gable = poly([(10, 4), (14.5, 6.4), (17.4, 12.5), (2.6, 12.5), (5.5, 6.4)])
    c.bevel(roof, STONE, lit=2.4, sep="ink")
    c.edge(roof, "grey_l", sides=((0, -1),))
    c.bevel(gable, REDS, lit=2)
    walls = rect(1, 12, 19, 24)
    c.bevel(walls | gable, REDS, lit=2)
    # white trim under the roof and at the wall corners
    c.pts([(x, 12) for x in range(1, 20)], "white")
    # board lines on the walls
    for x in (3, 5, 15, 17):
        c.pts([(x, y) for y in range(14, 24)], "red_d")
    # loft door with hay
    c.fill(rect(8, 6, 12, 10), "white")
    c.fill(rect(9, 7, 11, 10), "ink")
    c.stamp(9, 8, ["yay", "aYa", "YaY"], {"y": "yellow", "a": "amber", "Y": "clay"})
    # big X doors
    c.fill(rect(6, 15, 14, 24), "white")
    c.fill(rect(7, 16, 13, 24), "red_d")
    for a, b in (((7, 16), (13, 24)), ((13, 16), (7, 24))):
        c.pts(line(a, b), "white")
        c.pts([(x + 1, y) for x, y in line(a, b) if 7 <= x + 1 <= 13], "white")
    c.pts([(x, 15) for x in range(6, 15)], "white")
    # grass with tufts
    grass = rect(0, 25, 27, 27) | {(1, 24), (4, 24), (16, 24), (24, 24), (26, 24), (0, 24), (27, 24)}
    c.bevel(grass, LEAF, lit=2.5, soft=False)
    for x in (2, 6, 13, 20, 25):
        c.put(x, 26, "green_dd")
    return c


def nav_forest(n):
    c = Canvas(n)
    if n == 14:
        c.bevel(rect(6, 11, 7, 13), WOOD, lit=2)
        tiers = [(6.5, 4.5, 11.4, 6.6), (6.5, 2.0, 8.4, 5.1), (6.5, -0.6, 5.2, 3.4)]
    else:
        c.bevel(rect(12, 22, 15, 27), WOOD, lit=2)
        c.pts([(13, 24), (14, 26)], "brown")
        tiers = [(13.5, 11.0, 23.4, 12.4), (13.5, 6.0, 18.4, 9.8), (13.5, 2.0, 13.4, 7.4), (13.5, -0.6, 7.6, 4.6)]
    for i, (cx, top, base, hw) in enumerate(tiers):
        s = poly([(cx, top), (cx + hw, base), (cx - hw, base)])
        # droopy scalloped bottom edge
        step = 3 if n == 14 else 4
        for x in range(math.floor(cx - hw), math.ceil(cx + hw) + 1):
            if (x - int(cx)) % step == step - 1 and (x, round(base)) in s and abs(x - cx) < hw - 1:
                s.discard((x, round(base)))
        c.dome(s, LEAF, depth=2.5 if n == 14 else 4, off=0.45 + 0.1 * i, sep="green_dd" if i else None)
    if n == 28:
        # a little red mushroom at the base
        c.fill(rect(21, 24, 22, 27), "cream")
        cap = ell(21.5, 23.5, 2.6, 1.8) - rect(18, 25, 25, 27)
        c.sphere(21, 23, 2.8, 2.2, REDS, s=cap, off=0.6, sep="ink")
        c.pts([(20, 22), (22, 23)], "white")
    return c


def gem(c, x, y, cut):
    """3x3 cut gem for ore on rock faces."""
    if cut == "cyan":
        c.stamp(x, y, [".w.", "wcc", "cCC"], {"w": "white", "c": "cyan", "C": "blue"})
    else:
        c.stamp(x, y, [".w.", "wpp", "pRR"], {"w": "white", "p": "pink", "R": "red"})


def nav_mine(n):
    """Mine entrance: a rock mound with a dark tunnel framed in timber, a lantern and ore gems."""
    c = Canvas(n)
    if n == 14:
        mound = ell(6.5, 12, 6.9, 11.2) & rect(0, 0, 13, 12)
        c.dome(mound, STONE, depth=3, off=0.3)
        c.pts([(2, 7), (3, 9), (12, 9), (11, 2)], "grey_d")
        c.pts([(4, 3), (8, 2)], "grey_l")
        gem(c, 8, 2, "cyan")
        frame = mapset(["WWWWWWWWWW", "WWWiiiiWWW", "WWiiiiiiWW", "WWiiiiiiWW",
                        "WWiiiiiiWW", "WWiiiiiiWW", "WWiiiiiiWW", "WWiiiiiiWW"], "W", 2, 5)
        interior = mapset(["WWWWWWWWWW", "WWWiiiiWWW", "WWiiiiiiWW", "WWiiiiiiWW",
                           "WWiiiiiiWW", "WWiiiiiiWW", "WWiiiiiiWW", "WWiiiiiiWW"], "i", 2, 5)
        c.fill(interior, "ink")
        c.bevel(frame, WOOD, lit=2.5)
        c.stamp(5, 7, ["y"], {"y": "yellow"})
        return c
    mound = ell(13.5, 21, 13.8, 14.2) & rect(0, 0, 27, 27)
    c.dome(mound, STONE, depth=6, off=0.3)
    c.pts([(3, 12), (4, 13), (6, 9), (23, 10), (24, 8), (22, 26)], "grey_d")
    c.pts([(6, 8), (9, 7), (19, 8), (5, 16)], "grey_l")
    gem(c, 2, 17, "cyan")
    gem(c, 22, 14, "pink")
    outer = rect(4, 19, 23, 27) | (ell(13.5, 19, 9.5, 9.5) & rect(0, 0, 27, 19))
    inner = rect(7, 19, 20, 27) | (ell(13.5, 19, 6.5, 6.5) & rect(0, 0, 27, 19))
    frame = outer - inner
    c.fill(inner, "ink")
    c.pts([(x, 26) for x in range(8, 20)], "slate")
    c.pts([(x, 27) for x in range(8, 20) if x % 3 == 0], "grey_d")
    c.stamp(12, 15, [".k.", "kyk", "kwk", ".k."], {"k": "ink", "y": "yellow", "w": "white"})
    c.pts([(13, 14)], "grey_l")
    c.bevel(frame, WOOD, lit=2.6, sep="ink")
    c.pts([(x, 19) for x in range(4, 24) if (x, 19) in frame], "brown")
    return c


CANVAS = ["teal_dd", "green_dd", "green_d", "green", "yellow"]
LEATHER = ["brown_d", "brown", "brown_l", "tan_d", "tan"]


def bag(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            ".....hhhh.....",
            "....hh..hh....",
            "...FFFFFFFF...",
            "..FFFFFFFFFF..",
            "..FFFFFFFFFF..",
            "..BFFFFFFFFB..",
            "..BBBFkkFBBB..",
            ".sBBBByyBBBBs.",
            ".sBBBBBBBBBBs.",
            ".sBBPPPPPPBBs.",
            ".sBBPPPPPPBBs.",
            ".sBBPPPPPPBBs.",
            "..BBBBBBBBBB..",
            "...BBBBBBBB...",
        ]
        paint_map(c, rows, {"F": ("bevel", LEATHER, {"lit": 1.6}), "B": ("bevel", CANVAS, {"lit": 2.2}),
                            "P": ("bevel", CANVAS, {"lit": 2.8}), "s": ("bevel", LEATHER, {"lit": 1.4}),
                            "h": "brown", "y": "amber", "k": "brown_d"})
        c.put(6, 7, "yellow")
        c.pts([(4, 9), (9, 9)], "green_dd")
        return c
    # big: canvas backpack with a leather flap, buckle, pockets and a top loop
    c.pts(arc_pts(13.5, 5, 4.5, 180, 360), "brown")
    c.pts(arc_pts(13.5, 5, 3.5, 180, 360), "brown_l")
    side = rrect(1, 12, 5, 25, 2) | rrect(22, 12, 26, 25, 2)
    c.bevel(side, LEATHER, lit=1.6)
    c.pts([(x, 15) for x in range(2, 5)] + [(x, 15) for x in range(23, 26)], "brown_d")
    body = rrect(4, 4, 23, 27, 4)
    c.dome(body, CANVAS, depth=4, off=0.3, gain=0.95, sep="ink")
    pocket = rrect(8, 18, 19, 25, 2)
    c.bevel(pocket, CANVAS, lit=2.8, sep="green_dd")
    c.pts([(x, 19) for x in range(10, 18, 2)], "green_d")
    flap = rrect(5, 4, 22, 12, 4) | rrect(9, 10, 18, 15, 3)
    c.bevel(flap, LEATHER, lit=2.0, sep="ink")
    for x in range(7, 21, 2):
        c.put(x, 6, "brown")
    # straps down the front + buckle
    for x in (6, 20):
        c.fill(rect(x, 13, x + 1, 26), "brown")
        c.put(x, 13, "brown_l")
    c.stamp(11, 13, ["kkkkkk", "kyyyyk", "kyookk", "kkkkkk"], {"k": "ink", "y": "amber", "o": "yellow"})
    c.put(12, 14, "white")
    return c


def build(n):
    c = Canvas(n)
    if n == 14:
        handle_ = thick((1.5, 12.0), (8.0, 5.5), 0.9)
        c.bevel(handle_, WOOD, lit=2.5)
        c.pts([(1, 11), (2, 12), (2, 11), (3, 11), (2, 10)], "red")
        c.pts([(1, 12), (3, 12)], "red_d")
        head = thick((5.6, 2.6), (10.6, 7.6), 1.5) | {(5, 4), (4, 3), (3, 2)}
        head -= {(11, 9), (12, 8), (12, 9)}
        c.bevel(head, METAL, lit=2.2, sep="ink")
        c.pts([(4, 3), (3, 2)], "grey_d")
        return c
    handle_ = thick((3.0, 25.0), (15.5, 12.5), 1.6)
    c.bevel(handle_, WOOD, lit=2.6)
    grip_ = {p for p in handle_ if p[0] - p[1] < -15}
    c.bevel(grip_, REDS, lit=2.0)
    for k in range(4):
        c.pts([(x, y) for (x, y) in grip_ if (x + y) == 26 + 3 * k - 4], "red_d")
    H = (16.0, 12.0)
    pt = lambda d, u: (H[0] + D[0] * d + U[0] * u, H[1] + D[1] * d + U[1] * u)  # noqa: E731
    body = poly([pt(-2.5, 2.6), pt(6.4, 2.6), pt(6.4, -2.6), pt(-2.5, -2.6)])
    claw = poly([pt(-2.5, 2.6), pt(-6.0, 1.8), pt(-9.6, -0.6), pt(-9.0, -2.0), pt(-5.5, -1.2), pt(-2.5, -2.6)])
    head = (body | claw) & rect(0, 0, 27, 27)
    c.bevel(head, METAL, lit=2.3, sep="ink")
    face = {p for p in body if (p[0] - H[0]) * D[0] + (p[1] - H[1]) * D[1] > 4.9}
    c.fill(face, "grey_l")
    c.fill({p for p in face if (p[0] - H[0]) * D[0] + (p[1] - H[1]) * D[1] > 6.0}, "grey")
    split = [(round(x), round(y)) for x, y in (pt(-4.5, 0.0), pt(-5.5, -0.4), pt(-6.5, -0.8), pt(-7.4, -1.0))]
    c.pts(split, "slate")
    c.fill({p for p in head if -1.2 <= (p[0] - H[0]) * D[0] + (p[1] - H[1]) * D[1] <= 1.6 and (p[0] - H[0]) * U[0] + (p[1] - H[1]) * U[1] < -1.6}, "grey_d")
    c.pts([(round(x), round(y)) for x, y in (pt(-1.5, 1.6), pt(0, 1.6), pt(1.5, 1.6), pt(3, 1.6))], "white")
    sparkle(c, 24, 4, big=True)
    return c


def craft(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            "..........y...",
            ".........yoy..",
            "...MMMMMMMMMy.",
            "MMMMMMMMMMMMM.",
            ".MMMMMMMMMMMM.",
            "...MMMMMMMMM..",
            ".....MMMMM....",
            ".....MMMMM....",
            "....MMMMMMM...",
            "..WWWWWWWWWWW.",
            "..WWWWWWWWWWW.",
            "..WWWWWWWWWWW.",
            "..WWWWWWWWWWW.",
            "..WWWWWWWWWWW.",
        ]
        paint_map(c, rows, {"M": ("bevel", IRON, {"lit": 2.6}), "W": ("bevel", WOOD, {"lit": 2.2}),
                            "y": "yellow", "o": "white"})
        c.pts([(x, 9) for x in range(2, 13)], "tan")
        c.pts([(4, 11), (5, 11), (9, 12), (10, 12), (7, 10)], "brown")
        c.edge(rect(2, 9, 12, 13), "ink", sides=((0, -1),))
        c.paint({(x, 9): "ink" for x in range(4, 11)})
        c.pts([(x, 9) for x in (2, 3, 11, 12)], "tan")
        return c
    # big: anvil on a stump with sparks
    stump = rect(5, 19, 23, 27)
    c.cyl(stump, WOOD, lit=2.2, peak=0.25)
    top = ell(14, 19.5, 9.5, 2.2) & rect(4, 17, 24, 21)
    c.fill(top, "tan")
    c.pts(arc_pts(14, 19.5, 6.5, 20, 160), "tan_d")
    c.pts(arc_pts(14, 19.5, 3.5, 0, 180), "tan_d")
    c.put(14, 19, "brown_l")
    for x, y in ((7, 23), (8, 24), (19, 25), (20, 24), (13, 26)):
        c.put(x, y, "brown")
    anvil = poly([(5, 6), (24, 6), (24, 9), (20, 11), (18, 11), (18, 14), (21, 17.4), (7, 17.4), (10, 14), (10, 11), (7, 10), (0.5, 7.4)])
    c.bevel(anvil, IRON, lit=2.6, sep="ink")
    c.pts([(x, 6) for x in range(6, 24)], "grey_l")
    c.pts([(x, 7) for x in range(5, 23)], "grey")
    c.pts([(20, 7), (21, 7)], "slate")
    sparkle(c, 4, 3, big=True, core="amber")
    sparkle(c, 25, 2)
    c.put(1, 2, "yellow")
    c.put(27, 4, "amber")
    return c


def orders(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            "......MM......",
            ".BBBBMMMMBBBB.",
            ".BPPPPPPPPPPB.",
            ".BPPPPPPPPPPB.",
            ".BPggPllllPPB.",
            ".BPggPPPPPPPB.",
            ".BPPPPPPPPPPB.",
            ".BPggPllllPPB.",
            ".BPggPPPPPPPB.",
            ".BPPPPPPPPPPB.",
            ".BPeePlllPPPB.",
            ".BPeePPPPPPPB.",
            ".BPPPPPPPPPPB.",
            ".BBBBBBBBBBBB.",
        ]
        paint_map(c, rows, {"B": ("bevel", WOOD, {"lit": 2.4}), "P": ("bevel", PAPER, {"lit": 2, "soft": False, "hi": False}),
                            "M": ("bevel", METAL, {"lit": 2.5}), "g": "green", "l": "grey_d", "e": "tan_d"})
        c.pts([(4, 5), (4, 8)], "green_d")
        c.put(3, 4, "yellow")
        c.put(3, 7, "yellow")
        c.put(6, 0, "white")
        return c
    board = rrect(4, 3, 23, 27, 1)
    c.bevel(board, WOOD, lit=2.4)
    paper = rect(6, 6, 21, 25)
    c.bevel(paper, PAPER, lit=2, soft=False)
    c.pts([(21, y) for y in range(6, 26)], "tan")
    c.put(21, 25, "tan_d")
    clip = rrect(10, 1, 17, 6, 1)
    c.bevel(clip, METAL, lit=2.5, sep="ink")
    c.fill(rect(12, 2, 15, 2), "ink")
    # checklist: two ticks, one empty box
    for i, y in enumerate((9, 14, 19)):
        c.fill(rect(8, y, 11, y + 3), "ink")
        c.fill(rect(9, y + 1, 10, y + 2), "white")
        c.pts([(x, y + 1) for x in range(13, 20 - 2 * (i == 1))] + [(x, y + 3) for x in range(13, 18)], "grey")
        if i < 2:
            c.stamp(8, y - 1, ["....g", "...gg", "g.gg.", "ggg..", ".g..."], {"g": "green"})
    c.put(23, 26, "brown_d")
    return c


def market(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            ".rrwwrrwwrrww.",
            "rrrwwrrwwrrwwr",
            "rrrwwrrwwrrwwr",
            ".r..w..r..w...",
            ".P.kkkkkkkk.P.",
            ".P.kkkkkkkk.P.",
            ".P.kkkkkkkk.P.",
            ".PaAkgGkoOkkP.",
            "CCCCCCCCCCCCCC",
            "CCCCCCCCCCCCCC",
            ".CCCCCCCCCCCC.",
            ".CCCCCCCCCCCC.",
            ".CCCCCCCCCCCC.",
            ".CC........CC.",
        ]
        paint_map(c, rows, {"r": "red", "w": "cream", "P": ("bevel", WOOD, {"lit": 2}),
                            "C": ("bevel", PLANK, {"lit": 2}), "k": "brown_d",
                            "a": "red", "A": "red_d", "g": "green", "G": "green_d", "o": "amber", "O": "orange"})
        c.pts([(x, 1) for x in range(0, 14) if c.px[x, 1] == E["red"]], "pink")
        c.pts([(x, 1) for x in range(0, 14) if c.px[x, 1] == E["cream"]], "white")
        c.pts([(x, 10) for x in range(1, 13)], "brown_l")
        c.pts([(x, 8) for x in range(0, 14)], "tan")
        return c
    # big: stall with scalloped awning, sign, produce crates
    back = rect(4, 9, 23, 17)
    c.fill(back, "brown_d")
    for x in range(5, 23, 3):
        c.pts([(x, y) for y in range(10, 17)], "brown")
    posts = rect(2, 6, 3, 27) | rect(24, 6, 25, 27)
    c.bevel(posts, WOOD, lit=2)
    aw = rect(0, 1, 27, 6)
    for x in range(28):
        stripe = (x // 4) % 2 == 0
        cols = ("pink", "red", "red", "red_d") if stripe else ("white", "cream", "cream", "tan")
        for y in range(1, 7):
            c.put(x, y, cols[0] if y == 1 else cols[3] if y == 6 else cols[1] if y < 4 else cols[2])
    for i in range(7):
        x0 = i * 4
        stripe = i % 2 == 0
        c.fill({(x0 + 1, 7), (x0 + 2, 7)}, "red_d" if stripe else "tan")
        c.put(x0, 7, "red" if stripe else "cream")
        c.put(x0 + 3, 7, "red" if stripe else "cream")
        c.fill({(x0 + 1, 8), (x0 + 2, 8)}, "red_d" if stripe else "tan_d")
    c.pts([(x, 0) for x in range(1, 27)], "red_d")
    c.edge(aw | rect(0, 7, 27, 8), "ink", sides=((0, -1),))
    c.pts([(x, 0) for x in range(2, 26)], "red")
    # produce on the counter
    c.sphere(7, 15.5, 2.2, 2.2, REDS, off=0.3)
    c.sphere(10.5, 15.5, 2.2, 2.2, REDS, off=0.3)
    c.put(7, 13, "green")
    c.put(10, 13, "green")
    c.sphere(15, 15.5, 2.2, 2.2, GOLD, off=-0.2)
    c.sphere(19.5, 15.0, 2.6, 2.6, GREENS, off=0.0)
    c.stamp(18, 12, ["gG"], {"g": "green", "G": "green_dd"})
    counter = rect(0, 18, 27, 27)
    c.bevel(counter, PLANK, lit=2, sep="ink")
    c.pts([(x, 18) for x in range(0, 28)], "tan")
    c.pts([(x, 19) for x in range(0, 28)], "brown")
    for y in (23,):
        c.pts([(x, y) for x in range(1, 27)], "brown_l")
    for x in (9, 18):
        c.pts([(x, y) for y in range(20, 27)], "brown_l")
    # price sign
    c.fill(rect(10, 21, 17, 26), "ink")
    c.bevel(rect(11, 22, 16, 25), PAPER, lit=2, soft=False)
    c.stamp(12, 22, [".aa.", "aYYa", "aYYa", ".aa."], {"a": "amber", "Y": "yellow"})
    return c


def coin(c, cx, cy, r, sep="ink", mark=True):
    s = ell(cx, cy, r, r)
    c.sphere(cx - 0.3, cy - 0.3, r + 0.3, r + 0.3, COIN, s=s, off=0.4, gain=0.95, sep=sep)
    if r >= 3:
        inner = ring(cx, cy, r - 1.6, r - 0.9)
        c.fill({p for p in inner if p in s and (p[0] - cx) + (p[1] - cy) > -0.5}, "orange")
        c.fill({p for p in inner if p in s and (p[0] - cx) + (p[1] - cy) <= -0.5}, "yellow")
    if mark:
        if r >= 4:
            x, y = round(cx), round(cy)
            c.pts([(x, y - 1), (x, y), (x, y + 1)], "orange")
            c.pts([(x - 1, y - 1), (x - 1, y), (x - 1, y + 1)], "cream")
        else:
            x, y = round(cx), round(cy)
            c.pts([(x, y)], "orange")
    return s


def shop(n):
    c = Canvas(n)
    if n == 14:
        sack = ell(6.5, 9.0, 6.0, 4.6) | ell(6.5, 7.0, 3.6, 3.0) | rect(5, 3, 8, 4)
        sack |= {(4, 1), (3, 1), (4, 2), (9, 1), (10, 1), (9, 2), (5, 2), (8, 2), (6, 2), (7, 2)}
        sack &= rect(0, 0, 13, 13)
        c.dome(sack, BURLAP, depth=3, off=0.6)
        c.pts([(5, 4), (6, 4), (7, 4), (8, 4)], "red")
        c.pts([(6, 5), (7, 5)], "red_d")
        c.pts([(5, 2), (8, 2)], "brown_l")
        c.stamp(4, 7, [".kkkk.", "kyyyak", "kywyAk", "kyyaAk", "kaAAAk", ".kkkk."],
                {"k": "ink", "y": "yellow", "w": "white", "a": "amber", "A": "orange"})
        return c
    # big: plump money bag with a coin badge and a coin stack in front
    sack = ell(13, 18, 11.5, 9.0) | ell(13, 13, 6.5, 6.0) | rect(10, 5, 16, 8)
    sack |= poly([(9, 5), (6, 1), (10, 2.5), (13, 1.5), (16, 2.5), (20, 1), (17, 5)])
    sack &= rect(0, 0, 27, 27)
    c.dome(sack, BURLAP, depth=5, off=0.55)
    for x, y in ((9, 3), (12, 3), (15, 3), (17, 3)):
        c.put(x, y, "tan_d")
    tie = rect(9, 7, 17, 8)
    c.bevel(tie, REDS, lit=2.2)
    c.fill({(13, 9), (12, 10), (14, 9), (12, 11)}, "red")
    c.put(12, 11, "red_d")
    c.pts([(7, 20), (8, 22), (18, 24), (6, 15), (20, 14), (11, 25)], "tan_d")
    c.fill(ell(12.5, 17.5, 5.2, 5.2), "ink")
    coin(c, 12.5, 17.5, 4.3)
    # coin stack
    for i, y in enumerate((25, 23, 21)):
        s = rrect(19, y, 26, y + 2, 1)
        c.fill(s, "ink")
        c.fill({(x, y) for x in range(20, 26)}, "yellow")
        c.fill({(x, y + 1) for x in range(20, 26)}, "amber")
        c.put(25, y + 1, "orange")
        c.put(20, y, "white")
    return c


def flat_coin(c, cx, cy, r):
    """Flat gold disc: amber rim, yellow face, orange lower-right shade, white glint, '$' stroke.
    Reads as money at 14px where the sphere shading of coin() turns into a blob."""
    c.fill(ell(cx, cy, r, r), "amber")
    face = ell(cx, cy, r - 1.2, r - 1.2)
    c.fill(face, "yellow")
    c.fill({p for p in face if (p[0] - cx) + (p[1] - cy) > 0.8 * r}, "orange")
    c.fill({p for p in face if (p[0] - cx) + (p[1] - cy) < -0.9 * r and abs((p[0] - cx) - (p[1] - cy)) < 0.7 * r}, "white")
    x, y = round(cx), round(cy)
    c.pts([(x, y + k) for k in range(-2, 3) if r >= 4 or k == 0], "orange")
    return ell(cx, cy, r, r)


def sell(n):
    """Coins dropping into a shipping crate."""
    c = Canvas(n)
    if n == 14:
        crate = rect(0, 6, 9, 13)
        flat_coin(c, 9.5, 4.5, 4.0)
        c.bevel(crate, PLANK, lit=1.8)
        c.pts([(x, 6) for x in range(0, 10)], "tan")
        for y in (9, 12):
            c.pts([(x, y) for x in range(1, 9)], "brown")
        c.pts([(1, y) for y in range(7, 13)] + [(8, y) for y in range(7, 13)], "brown_l")
        return c
    flat_coin(c, 7.5, 10, 4.8)
    crate = rect(1, 13, 20, 27)
    c.bevel(crate, PLANK, lit=1.8)
    for y in (13, 18, 23):
        c.pts([(x, y) for x in range(1, 21)], "tan")
        c.pts([(x, y + 4) for x in range(1, 21)], "brown")
    for x in (2, 3, 18, 19):
        c.pts([(x, y) for y in range(13, 28)], "brown_l" if x in (3, 18) else "tan_d")
    c.pts(line((4, 26), (17, 14)), "brown_l")
    c.edge(rect(1, 13, 20, 27), "ink", sides=((0, -1),))
    c.pts([(x, 13) for x in range(1, 21)], "tan")
    flat_coin(c, 20, 8, 6.4)
    sparkle(c, 4, 4, big=True)
    return c


def menu(n):
    c = Canvas(n)
    if n == 14:
        for y in (1, 6, 11):
            bar = rrect(0, y, 13, y + 2, 1)
            c.fill(bar, "cream")
            c.pts([(x, y) for x in range(1, 13)], "white")
            c.pts([(x, y + 2) for x in range(1, 13)], "tan")
            c.put(13, y + 1, "tan")
        return c
    for y in (2, 11, 20):
        bar = rrect(0, y, 27, y + 5, 2)
        c.fill(bar, "cream")
        c.pts([(x, y + 1) for x in range(1, 27)], "white")
        c.pts([(x, y + 4) for x in range(1, 27)], "tan")
        c.pts([(x, y + 5) for x in range(2, 26)], "tan_d")
        c.pts([(26, y + 2), (26, y + 3), (27, y + 3), (25, y + 4)], "tan_d")
        c.pts([(2, y + 2), (3, y + 2)], "white")
    return c


def gear_set(cx, cy, r_in, r_out, teeth, hole, tooth_w=0.45, off=0.0):
    s = set()
    for y in range(math.floor(cy - r_out) - 1, math.ceil(cy + r_out) + 2):
        for x in range(math.floor(cx - r_out) - 1, math.ceil(cx + r_out) + 2):
            d = math.hypot(x - cx, y - cy)
            a = math.atan2(y - cy, x - cx) + off
            tooth = math.cos(a * teeth) > math.cos(math.pi * tooth_w)
            if d <= r_in or (d <= r_out and tooth):
                if d > hole:
                    s.add((x, y))
    return s


def settings(n):
    c = Canvas(n)
    if n == 14:
        s = gear_set(6.5, 6.5, 4.6, 6.6, 8, 1.6, 0.42, math.pi / 8)
        c.dome(s, METAL, depth=2.0, off=0.4)
        c.edge(ring(6.5, 6.5, 2.2, 3.0) & s, "grey_d", sides=((1, 0), (0, 1)))
        return c
    s = gear_set(13.5, 13.5, 9.6, 13.4, 10, 3.6, 0.44, math.pi / 10)
    c.dome(s, METAL, depth=3.0, off=0.35)
    inner = ring(13.5, 13.5, 3.6, 6.4)
    c.fill({p for p in s if p in ring(13.5, 13.5, 6.4, 7.4)}, "grey_d")
    c.sphere(13.5, 13.5, 6.6, 6.6, METAL, s=inner, off=0.6)
    c.fill({p for p in ring(13.5, 13.5, 3.6, 4.4) if (p[0] - 13.5) + (p[1] - 13.5) > 0}, "grey_l")
    c.fill({p for p in ring(13.5, 13.5, 3.6, 4.4) if (p[0] - 13.5) + (p[1] - 13.5) <= 0}, "slate")
    return c


def trophy(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            "..GGGGGGGGGG..",
            "hhGGGGGGGGGGhh",
            "h.GGGGGGGGGG.h",
            "h..GGGGGGGG..h",
            ".h.GGGGGGGG.h.",
            "..hhGGGGGGhh..",
            "....GGGGGG....",
            ".....GGGG.....",
            "......GG......",
            "......GG......",
            "....GGGGGG....",
            "..BBBBBBBBBB..",
            "..BByyyyyyBB..",
            "..BBBBBBBBBB..",
        ]
        paint_map(c, rows, {"G": ("cyl", GOLD, {"lit": 2.4, "peak": 0.25}), "h": "amber",
                            "B": ("bevel", WOOD, {"lit": 1.6}), "y": "amber"})
        c.pts([(x, 0) for x in range(3, 11)], "orange")
        c.pts([(2, 0), (11, 0)], "yellow")
        c.put(4, 12, "yellow")
        c.stamp(5, 2, [".w.", "www", ".w."], {"w": "white"})
        return c
    cup = (rect(5, 3, 22, 8) | ell(13.5, 8, 8.6, 8.4)) & rect(0, 3, 27, 15)
    c.pts(arc_pts(5, 8, 3.6, 90, 270), "amber")
    c.pts(arc_pts(5, 8, 2.6, 90, 270), "orange")
    c.pts(arc_pts(22, 8, 3.6, -90, 90), "amber")
    c.pts(arc_pts(22, 8, 2.6, -90, 90), "orange")
    c.cyl(cup, GOLD, lit=2.4, peak=0.25, sep="ink")
    c.fill(ell(13.5, 4, 7.6, 1.1) & rect(0, 3, 27, 5), "orange")
    c.pts([(x, 4) for x in range(8, 19)], "clay")
    c.pts([(x, 3) for x in range(5, 23)], "yellow")
    stem = rect(12, 15, 15, 19)
    c.cyl(stem, GOLD, lit=2.2, peak=0.25)
    foot = poly([(9, 19), (18, 19), (20, 21), (7, 21)])
    c.cyl(foot, GOLD, lit=2.4, peak=0.25)
    base = rect(5, 22, 22, 27)
    c.bevel(base, WOOD, lit=1.8, sep="ink")
    c.fill(rect(9, 24, 18, 25), "amber")
    c.pts([(x, 24) for x in range(9, 18)], "yellow")
    c.put(18, 25, "orange")
    star = [
        "...w...",
        "..wwW..",
        "wwwwwWW",
        ".wwwwW.",
        ".wW.wW.",
    ]
    c.stamp(10, 6, star, {"w": "white", "W": "cream"})
    sparkle(c, 24, 2)
    sparkle(c, 2, 14)
    return c


def book(n):
    c = Canvas(n)
    if n == 14:
        c.fill(rect(2, 2, 13, 13), "blue_d")
        c.fill(rect(2, 1, 12, 12), "cream")
        for y in range(2, 12, 2):
            c.put(12, y, "tan")
        for x in range(3, 12, 2):
            c.put(x, 12, "tan")
        cover = rect(0, 0, 11, 11)
        c.bevel(cover, BLUES, lit=2.0)
        c.bevel(rect(0, 0, 1, 11), BLUES, lit=1.2)
        c.pts([(2, y) for y in range(0, 12)], "blue_d")
        c.stamp(4, 3, ["..y..", ".yyy.", "yyyyy", ".yyy.", ".y.y."], {"y": "yellow"})
        c.pts([(6, 3), (5, 4)], "white")
        c.pts([(7, 6), (8, 5), (7, 7)], "amber")
        c.pts([(11, 0), (10, 0), (11, 1), (11, 11), (10, 11), (11, 10)], "amber")
        c.pts([(8, 12), (8, 13), (9, 13)], "red")
        return c
    back = rect(3, 3, 26, 27)
    c.fill(back, "blue_d")
    pages = rect(3, 1, 25, 25)
    c.bevel(pages, CREAM, lit=1.6, hi=False)
    for y in range(3, 25, 2):
        c.pts([(24, y), (25, y)], "tan")
    for x in range(4, 25, 3):
        c.pts([(x, 24), (x + 1, 24)], "tan")
    cover = rrect(0, 0, 22, 23, 1)
    c.bevel(cover, BLUES, lit=2.0)
    spine = rect(0, 0, 3, 23)
    c.bevel(spine, BLUES, lit=1.3)
    c.pts([(4, y) for y in range(1, 23)], "blue_d")
    for y in (3, 20):
        c.pts([(x, y) for x in range(0, 4)], "amber")
        c.pts([(x, y + 1) for x in range(0, 4)], "orange")
    # gold corner caps
    for rows, x0, y0 in ((["yyy", ".yy", "..y"], 19, 1), (["..y", ".yy", "yyy"], 19, 20)):
        c.stamp(x0, y0, rows, {"y": "amber"})
    c.pts([(20, 1), (21, 1), (21, 2)], "yellow")
    # star medallion
    c.fill(ell(13, 11.5, 5.4, 5.4), "blue_d")
    c.fill(ring(13, 11.5, 4.4, 5.4), "amber")
    c.fill({p for p in ring(13, 11.5, 4.4, 5.4) if p[0] + p[1] < 24}, "yellow")
    star = [
        "....y....",
        "...yyy...",
        "yyyyyyyyy",
        ".yyyyyyO.",
        "..yyyyO..",
        "..yyOyO..",
        ".yO...yO.",
    ]
    c.stamp(9, 8, star, {"y": "yellow", "O": "amber"})
    c.pts([(13, 9), (12, 10)], "white")
    # ribbon bookmark
    c.fill(rect(16, 24, 17, 27), "red")
    c.put(17, 25, "red_d")
    c.px[16, 27] = CLEAR
    c.put(17, 27, "red_d")
    return c


def gift(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            "..yyy....yyy..",
            ".yyoyy..yyoyy.",
            ".yyyyykkyyyyy.",
            "...yyykkyyy...",
            "LLLLLLRRLLLLLL",
            "LLLLLLRRLLLLLL",
            ".BBBBBRRBBBBB.",
            ".BBBBBRRBBBBB.",
            ".BBBBBRRBBBBB.",
            ".BBBBBRRBBBBB.",
            ".BBBBBRRBBBBB.",
            ".BBBBBRRBBBBB.",
            ".BBBBBRRBBBBB.",
            ".BBBBBRRBBBBB.",
        ]
        paint_map(c, rows, {"y": ("bevel", GOLD, {"lit": 2.4, "soft": False}), "o": "orange", "k": "amber",
                            "L": ("bevel", REDS, {"lit": 2.4}), "B": ("bevel", REDS, {"lit": 1.8}),
                            "R": ("bevel", GOLD, {"lit": 2.6, "soft": False})})
        c.pts([(x, 6) for x in range(1, 13) if x not in (6, 7)], "red_d")
        c.pts([(6, 6), (7, 6)], "orange")
        c.put(6, 2, "yellow")
        return c
    box = rect(3, 13, 24, 27)
    c.bevel(box, REDS, lit=1.8)
    c.pts([(x, 13) for x in range(3, 25)], "red_d")
    c.pts([(x, 14) for x in range(4, 24) if x % 2], "red_d")
    lid = rect(1, 9, 26, 13)
    c.bevel(lid, REDS, lit=2.4, sep="ink")
    rib = rect(12, 9, 15, 27)
    c.bevel(rib, GOLD, lit=2.5, soft=False)
    c.pts([(x, 13) for x in range(12, 16)], "orange")
    c.pts([(x, 14) for x in range(12, 16)], "amber")
    # bow loops + tails
    for sx in (1, -1):
        cx = 13.5 + sx * 5.5
        loop = ell(cx, 4.5, 5.0, 3.6) | thick((13.5, 7), (cx, 4.5), 1.6)
        loop = {p for p in loop if (p[0] - 13.5) * sx >= 0}
        c.bevel(loop, GOLD, lit=2.5, sep="ink")
        hole = ell(cx + sx * 0.6, 4.4, 2.2, 1.2)
        c.fill(hole, "orange")
        c.pts([p for p in hole if p[1] < 4.4], "clay")
    tails = poly([(12, 8), (8, 13), (10, 13), (13, 9)]) | poly([(15, 8), (19, 13), (17, 13), (14, 9)])
    c.bevel(tails, GOLD, lit=2.2, soft=False)
    knot = rrect(11, 5, 16, 9, 1)
    c.bevel(knot, GOLD, lit=2.6, sep="ink")
    c.pts([(12, 6), (13, 6)], "white")
    sparkle(c, 24, 18)
    c.put(6, 18, "pink")
    c.put(7, 22, "pink")
    return c


def merchant(n):
    c = Canvas(n)
    if n == 14:
        top = (ell(6.5, 6.5, 6.3, 5.8) & rect(0, 0, 13, 6)) | rect(1, 5, 12, 7)
        c.dome(top, PURPLE, depth=3, off=0.1, gain=0.9)
        c.pts([(4, y) for y in range(1, 8) if (4, y) in top] + [(9, y) for y in range(1, 8) if (9, y) in top], "cream")
        c.pts([(x, 7) for x in range(1, 13)], "amber")
        bed = rect(0, 8, 13, 10)
        c.bevel(bed, WOOD, lit=2.4, sep="ink")
        for x0 in (0, 9):
            c.stamp(x0, 9, [".bbb.", "bBkBb", "bkgkb", "bBkBb", ".bbb."],
                    {"b": "brown", "B": "tan_d", "k": "brown_d", "g": "grey_l"})
        return c
    top = (ell(13, 13, 12.4, 12) & rect(0, 0, 27, 14)) | rect(1, 12, 25, 16)
    c.dome(top, PURPLE, depth=5, off=0.1, gain=0.9)
    for x in (6, 13, 20):
        c.pts([(x, y) for y in range(2, 17) if (x, y) in top], "cream")
        c.pts([(x + 1, y) for y in range(2, 17) if (x + 1, y) in top], "tan")
    c.fill(rect(1, 15, 25, 16), "amber")
    c.pts([(x, 16) for x in range(1, 26) if x % 3 == 1], "orange")
    c.pts([(13, y) for y in range(0, 2)], "brown")
    c.stamp(9, 6, [".y.", "yYy", ".y."], {"y": "yellow", "Y": "white"})
    c.stamp(16, 4, ["y"], {"y": "yellow"})
    c.stamp(17, 9, ["y"], {"y": "yellow"})
    bed = rect(0, 17, 27, 21)
    c.bevel(bed, WOOD, lit=2.4, sep="ink")
    c.pts([(x, 19) for x in range(1, 27)], "brown")
    c.fill(rect(24, 21, 27, 22), "brown")
    c.stamp(25, 8, [".k.", "kyk", "yoy", "kyk"], {"k": "ink", "y": "yellow", "o": "white"})
    c.pts([(26, 12), (26, 13), (26, 14)], "ink")
    for cx in (6.5, 19.5):
        rim = ring(cx, 22.5, 3.2, 5.0)
        c.bevel(rim, WOOD, lit=2.6, sep="ink")
        hub = ell(cx, 22.5, 3.2, 3.2)
        c.fill(hub, "brown_d")
        for a in range(0, 180, 45):
            r = math.radians(a)
            c.pts(line((round(cx - 3 * math.cos(r)), round(22.5 - 3 * math.sin(r))), (round(cx + 3 * math.cos(r)), round(22.5 + 3 * math.sin(r)))), "brown_l")
        c.fill(ell(cx, 22.5, 1.0, 1.0), "grey")
        c.put(round(cx - 0.5), 22, "grey_l")
    return c


def pennant(c, xc, y0, w, h, ramp):
    s = set()
    for dy in range(h):
        hw = (w / 2) * (1 - dy / h)
        for x in range(math.floor(xc - w / 2), math.ceil(xc + w / 2) + 1):
            if abs(x - xc) <= hw + 0.01:
                s.add((x, y0 + dy))
    c.bevel(s, ramp, lit=len(ramp) - 2.6, sep="ink")
    return s


def festival(n):
    c = Canvas(n)
    if n == 14:
        sag = lambda x: round(1 + 2.4 * math.sin(math.pi * x / 13))  # noqa: E731
        for xc, ramp in ((1.5, REDS), (6.5, GOLD), (11.5, BLUES)):
            top = sag(xc) + 1
            s = rect(round(xc - 1.5), top, round(xc + 1.5), top + 2) | rect(round(xc - 0.5), top + 3, round(xc + 0.5), top + 4)
            s = {p for p in s if 0 <= p[0] < 14}
            c.bevel(s, ramp, lit=len(ramp) - 2.4, soft=False)
        c.pts([(x, sag(x)) for x in range(14)], "brown")
        return c
    for y0, sag_h, flags in ((1, 5, ((3, REDS), (10, GOLD), (17, BLUES), (24, GREENS))),
                             (13, 4, ((6.5, PURPLE), (13.5, ["clay", "orange", "amber", "yellow", "white"]), (20.5, TEAL)))):
        sag = (lambda y0, sh: (lambda x: round(y0 + sh * math.sin(math.pi * x / 27))))(y0, sag_h)
        for xc, ramp in flags:
            top = sag(xc) + 1
            pennant(c, xc, top, 5.6 if y0 == 1 else 5.0, 8 if y0 == 1 else 7, ramp)
            c.put(round(xc), top + 2, "white")
        c.pts([(x, sag(x)) for x in range(28)], "brown")
    for p, cc in (((1, 12), "yellow"), ((26, 11), "pink"), ((2, 22), "cyan"), ((25, 24), "yellow"), ((9, 25), "pink"), ((18, 26), "green")):
        c.put(*p, cc)
    return c


def bell(n):
    c = Canvas(n)
    if n == 14:
        rows = [
            "......hh......",
            ".....GGGG.....",
            "....GGGGGG....",
            "...GGGGGGGG...",
            "...GGGGGGGG...",
            "...GGGGGGGG...",
            "...GGGGGGGG...",
            "..GGGGGGGGGG..",
            ".GGGGGGGGGGGG.",
            "GGGGGGGGGGGGGG",
            "RRRRRRRRRRRRRR",
            ".RRRRRRRRRRRR.",
            "......CC......",
            "......CC......",
        ]
        paint_map(c, rows, {"G": ("cyl", GOLD, {"lit": 2.3, "peak": 0.3}), "R": ("cyl", GOLD, {"lit": 1.6, "peak": 0.3}),
                            "C": ("bevel", GOLD, {"lit": 1.5}), "h": "orange"})
        c.pts([(4, 3), (4, 4)], "white")
        return c
    c.pts(arc_pts(13.5, 3.5, 2.2, 180, 360) + [(11, 4), (16, 4)], "orange")
    c.pts(arc_pts(13.5, 3.5, 1.6, 200, 340), "amber")
    body = poly([(9, 7), (18, 7), (19.5, 14), (21.5, 18), (24.5, 21), (2.5, 21), (5.5, 18), (7.5, 14)]) | ell(13.5, 9, 6.0, 5.0)
    rim = rrect(1, 20, 26, 23, 1)
    c.cyl(body, GOLD, lit=2.3, peak=0.3)
    c.cyl(rim, GOLD, lit=1.8, peak=0.3, sep="ink")
    c.pts([(x, 18) for x in range(5, 23)], "orange")
    c.pts([(x, 17) for x in range(6, 22)], "yellow")
    c.pts([(x, 12) for x in range(8, 20)], "orange")
    clap = ell(13.5, 25, 2.0, 1.8)
    c.sphere(13.0, 24.5, 2.4, 2.4, GOLD, s=clap, off=-0.3, sep="ink")
    c.pts([(10, 6), (9, 7), (9, 8), (8, 9), (8, 10)], "white")
    for pts in (((3, 9), (2, 10), (1, 11)), ((4, 13), (3, 14)), ((24, 9), (25, 10), (26, 11)), ((23, 13), (24, 14))):
        c.pts(pts, "white")
    sparkle(c, 22, 4)
    return c


def speaker(c, n):
    if n == 14:
        box = rect(0, 4, 2, 9)
        cone = poly([(3, 4), (6.4, 0.6), (6.4, 12.4), (3, 9)])
        c.bevel(box, METAL, lit=2.2)
        c.bevel(cone, METAL, lit=2.6, sep="grey_d")
        return
    box = rrect(1, 8, 6, 19, 1)
    cone = poly([(7, 8), (13.4, 1.6), (13.4, 25.4), (7, 19)])
    c.bevel(box, METAL, lit=2.2)
    c.bevel(cone, METAL, lit=2.6, sep="grey_d")
    c.pts([(12, y) for y in range(3, 25)], "grey_l")
    c.pts([(2, 9), (2, 10)], "white")


def wave_set(cx, cy, r0, r1, a):
    return {p for p in ring(cx, cy, r0, r1) if p[0] > cx and abs(math.degrees(math.atan2(p[1] - cy, p[0] - cx))) <= a}


def sound_on(n):
    c = Canvas(n)
    speaker(c, n)
    if n == 14:
        for r0, r1 in ((2.6, 4.0), (5.4, 6.8)):
            s = wave_set(6.4, 6.5, r0, r1, 52)
            c.fill(s, "white")
            c.edge(s, "grey_l", sides=((1, 0),))
        return c
    for r0, r1 in ((4.2, 6.2), (8.6, 10.6), (12.6, 14.6)):
        s = wave_set(13, 13.5, r0, r1, 48) & rect(0, 0, 27, 27)
        c.fill(s, "white")
        c.edge(s, "grey_l", sides=((1, 0), (0, 1)))
    return c


def sound_off(n):
    c = Canvas(n)
    speaker(c, n)
    if n == 14:
        x = thick((8.5, 4.5), (12.5, 8.5), 0.75) | thick((12.5, 4.5), (8.5, 8.5), 0.75)
        c.bevel(x, REDS, lit=2.4, soft=False)
        return c
    x = thick((16.5, 9.5), (25, 18), 1.4) | thick((25, 9.5), (16.5, 18), 1.4)
    c.bevel(x, REDS, lit=2.4)
    return c


def vibrate(n):
    c = Canvas(n)
    if n == 14:
        phone = rrect(4, 0, 9, 13, 1)
        c.bevel(phone, IRON, lit=2.0)
        c.fill(rect(5, 2, 8, 10), "blue")
        c.pts([(5, 4), (5, 5), (5, 6)], "cyan")
        c.pts([(6, 1), (7, 1)], "ink")
        c.pts([(6, 12), (7, 12)], "grey")
        c.pts([(5, 2), (5, 3), (6, 2)], "white")
        for pts in (((1, 4), (0, 5), (0, 6), (0, 7), (0, 8), (1, 9)), ((12, 4), (13, 5), (13, 6), (13, 7), (13, 8), (12, 9))):
            c.pts(pts, "yellow")
        c.pts([(2, 6), (2, 7), (11, 6), (11, 7)], "amber")
        return c
    phone = rrect(8, 1, 19, 26, 2)
    c.bevel(phone, IRON, lit=2.0)
    scr = rect(10, 4, 17, 21)
    c.fill(scr, "blue")
    c.pts([(x, 7) for x in range(10, 13)] + [(10, 8), (10, 9)], "cyan")
    c.pts([(10, 4), (11, 4), (10, 5), (12, 4), (10, 6)], "white")
    c.fill(rect(12, 2, 15, 2), "ink")
    c.fill(rrect(12, 23, 15, 24, 1), "grey")
    c.stamp(11, 9, ["..yy..", ".yyyy.", ".yyyy.", "yyyyyy", "..oo.."], {"y": "yellow", "o": "amber"})
    for r, cc in ((7.6, "yellow"), (10.6, "amber")):
        c.pts(arc_pts(13.5, 13.5, r, 180 - 32, 180 + 32), cc)
        c.pts(arc_pts(13.5, 13.5, r, -32, 32), cc)
    return c


CARD = ["brown", "brown_l", "tan_d", "tan", "cream"]


def box_open(c, n):
    """Open cardboard box; returns the front-face set (drawn last by caller)."""
    if n == 14:
        c.fill(rect(1, 7, 12, 8), "brown")
        c.bevel(poly([(1, 8), (-0.4, 5.6), (3, 5.6), (4, 8)]) & rect(0, 0, 13, 13), CARD, lit=2.4, soft=False)
        c.bevel(poly([(12, 8), (13.4, 5.6), (10, 5.6), (9, 8)]) & rect(0, 0, 13, 13), CARD, lit=1.8, soft=False)
        return rect(1, 9, 12, 13)
    c.fill(rect(3, 14, 24, 17), "brown")
    c.pts([(x, 14) for x in range(3, 25)], "brown_d")
    c.bevel(poly([(3, 17), (-0.4, 11), (6, 11), (9, 17)]), CARD, lit=2.4, sep="ink")
    c.bevel(poly([(24, 17), (27.4, 11), (21, 11), (18, 17)]), CARD, lit=1.7, sep="ink")
    return rect(3, 17, 24, 27)


def box_front(c, front, n):
    c.bevel(front, CARD, lit=2.2, sep="ink")
    if n == 14:
        c.pts([(x, 9) for x in range(1, 13)], "tan")
        c.pts([(x, 11) for x in range(4, 10)], "brown_l")
    else:
        c.pts([(x, 17) for x in range(3, 25)], "tan")
        c.fill(rect(7, 21, 14, 24), "cream")
        c.pts([(x, 22) for x in range(8, 13)], "grey")
        c.pts([(x, 23) for x in range(8, 11)], "grey")


def arrow(c, n, up, ramp):
    lit = 2.0 if ramp is GREENS else len(ramp) - 2.3
    if n == 14:
        head = ["......AA......", ".....AAAA.....", "....AAAAAA....", "...AAAAAAAA..."]
        if not up:
            head = head[::-1]
        s = mapset(head, "A", 0, 4 if not up else 0) | (rect(5, 4, 8, 9) if up else rect(5, 0, 8, 4))
        c.bevel(s, ramp, lit=lit, sep="ink")
        return
    if up:
        s = poly([(13.5, -0.4), (21.4, 7.5), (16.5, 7.5), (16.5, 18), (10.5, 18), (10.5, 7.5), (5.6, 7.5)])
    else:
        s = poly([(13.5, 17.4), (21.4, 9.5), (16.5, 9.5), (16.5, -0.4), (10.5, -0.4), (10.5, 9.5), (5.6, 9.5)])
    c.bevel(s, ramp, lit=lit, sep="ink")
    c.pts([(11, y) for y in range(9 if up else 0, 17 if up else 9)], ramp[3] if ramp is GREENS else ramp[-2])


def export(n):
    c = Canvas(n)
    front = box_open(c, n)
    arrow(c, n, True, GREENS)
    box_front(c, front, n)
    return c


def import_(n):
    c = Canvas(n)
    front = box_open(c, n)
    arrow(c, n, False, BLUES)
    box_front(c, front, n)
    return c


def badge(c, n, ramp):
    """Round button: mid colour, lit crescent top-left, shaded rim bottom-right, specular dot."""
    if n == 14:
        s = mapset(CIRC14, "x")
        cx = cy = 6.5
        r = 7.0
    else:
        s = ell(13.5, 13.5, 13.4, 13.4)
        cx = cy = 13.5
        r = 13.9
    cols = {}
    for (x, y) in s:
        dx, dy = (x - cx) / r, (y - cy) / r
        t = -(dx * 0.6 + dy * 0.8)
        rim = (1 - math.hypot(dx, dy)) * r
        v = 1.0
        if t > 0.3:
            v = 1.5
        if t > 0.55:
            v = 2.0
        if rim < (1.4 if n == 14 else 2.0) and t < 0.05:
            v = 0.0
        elif rim < (2.4 if n == 14 else 3.6) and t < -0.15:
            v = 0.5
        cols[(x, y)] = tone(ramp, v, x, y)
    c.paint(cols)
    if n == 14:
        c.pts([(3, 2), (2, 3)], ramp[3])
    else:
        c.pts([(6, 4), (5, 5), (7, 4), (4, 6)], ramp[3])
    return s


def info(n):
    c = Canvas(n)
    badge(c, n, BADGE_B)
    if n == 14:
        c.fill(rect(6, 2, 7, 3), "white")
        c.fill(rect(6, 5, 7, 10) | rect(5, 5, 5, 5) | rect(5, 10, 8, 10), "white")
        c.pts([(8, 3), (8, 6), (8, 7), (8, 8), (8, 9)], "blue_d")
        c.pts([(x, 11) for x in range(5, 9)], "blue_d")
        return c
    c.fill(rrect(12, 4, 15, 7, 1), "white")
    c.fill(rect(12, 10, 15, 21) | rect(10, 10, 11, 11) | rect(10, 20, 17, 22), "white")
    c.pts([(16, y) for y in range(11, 20)] + [(16, 5), (16, 6), (18, 21), (18, 22)], "blue_d")
    c.pts([(x, 23) for x in range(10, 19)] + [(13, 8), (14, 8), (15, 8)], "blue_d")
    return c


def close(n):
    c = Canvas(n)
    badge(c, n, BADGE_R)
    if n == 14:
        x = thick((4, 4), (9, 9), 0.8) | thick((9, 4), (4, 9), 0.8)
        c.fill({(p[0] + 1, p[1] + 1) for p in x}, "red_d")
        c.fill(x, "white")
        return c
    x = thick((8.5, 8.5), (18.5, 18.5), 1.7) | thick((18.5, 8.5), (8.5, 18.5), 1.7)
    c.fill({(p[0] + 1, p[1] + 1) for p in x}, "red_d")
    c.fill(x, "white")
    c.fill({p for p in x if (p[0] + 1, p[1] + 1) not in x}, "grey_l")
    return c


def P(x, y, n):
    """14-space pixel centre -> n-space pixel centre."""
    k = n / 14
    return ((x + 0.5) * k - 0.5, (y + 0.5) * k - 0.5)


def V(a, b, s=1.0):
    return (a[0] + b[0] * s, a[1] + b[1] * s)


U = (0.7071, -0.7071)    # along a bottom-left -> top-right handle
W = (-0.7071, -0.7071)   # up-left, perpendicular to it
D = (0.7071, 0.7071)     # down-right


def handle(c, a, b, n, r14=0.9, r28=1.7, ramp=WOOD, lit=2.6):
    s = thick(P(*a, n), P(*b, n), r14 if n == 14 else r28)
    s &= rect(0, 0, n - 1, n - 1)
    c.bevel(s, ramp, lit=lit)
    if n == 28:
        # wood grain flecks along the shaft
        pts = sorted(s, key=lambda p: p[0] - p[1])
        for i, (x, y) in enumerate(pts):
            if i % 7 == 3 and (x + 1, y + 1) in s and (x - 1, y - 1) in s:
                c.put(x, y, "brown_l")
    return s


def grip(c, s, n, k0, k1, ramp=REDS):
    """Wrap a band of a diagonal handle (x - y between k0 and k1 in 14-space) in coloured tape."""
    k = n / 14
    g = {p for p in s if k0 * k <= (p[0] - p[1]) + (n - 1) <= k1 * k}
    c.bevel(g, ramp, lit=2.0)
    for p in g:
        if (p[0] + p[1]) % (3 if n == 14 else 4) == 0:
            c.put(*p, ramp[1])
    return g


def tool_hoe(n):
    c = Canvas(n)
    k = n / 14
    s = handle(c, (1, 13), (9.8, 3.2), n)
    grip(c, s, n, 0, 3)
    blade = poly([P(9.0, 0.4, n), P(12.4, 0.4, n), P(12.9, 8.4, n), P(11.0, 8.4, n), P(10.6, 3.0, n)])
    blade &= rect(0, 0, n - 1, n - 1)
    c.bevel(blade, METAL, lit=2.2, sep="ink")
    bottom = max(p[1] for p in blade)
    c.fill({p for p in blade if p[1] >= bottom - (0 if n == 14 else 1)}, "white")
    neck = thick(P(9.2, 1.0, n), P(10.6, 3.4, n), 0.7 * k) & blade
    c.fill(neck, "grey_d")
    if n == 28:
        c.pts([(23, y) for y in range(6, 14)], "grey_l")
        sparkle(c, 4, 4, big=True)
    return c


def tool_axe(n):
    c = Canvas(n)
    s = handle(c, (1.5, 13), (10.5, 2), n)
    grip(c, s, n, 0, 3)
    H = (8.7, 4.3)
    head = poly([P(*V(V(H, U, -1.6), D, 1.4), n), P(*V(V(H, U, 1.6), D, 1.4), n),
                 P(*V(V(H, U, 1.8), W, 2.2), n), P(*V(V(H, U, 3.4), W, 5.0), n), P(*V(H, W, 6.0), n),
                 P(*V(V(H, U, -3.4), W, 5.0), n), P(*V(V(H, U, -1.8), W, 2.2), n)])
    head &= rect(0, 0, n - 1, n - 1)
    c.bevel(head, IRON, lit=2.6, sep="ink")
    far = max((p[0] + p[1]) for p in head)
    near = min((p[0] + p[1]) for p in head)
    c.fill({p for p in head if p[0] + p[1] <= near + (1 if n == 14 else 2)}, "grey_l")
    c.fill({p for p in head if p[0] + p[1] == near}, "white")
    c.fill({p for p in head if p[0] + p[1] >= far - (0 if n == 14 else 1)}, "slate")
    if n == 28:
        c.put(*[round(v) for v in P(*H, n)], "grey")
        sparkle(c, 3, 2, big=True)
    return c


def tool_pickaxe(n):
    c = Canvas(n)
    s = handle(c, (1, 13), (8.4, 5.6), n)
    grip(c, s, n, 0, 3)
    C = P(0.6, 13.4, n)
    R = 10.9 * n / 14
    head = set()
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - C[0], y - C[1])
            a = math.degrees(math.atan2(y - C[1], x - C[0]))
            t = (a + 98) / 106
            if 0 <= t <= 1:
                h = (0.55 + 1.15 * math.sin(math.pi * t) ** 0.8) * n / 14
                if abs(r - R) <= h:
                    head.add((x, y))
    c.bevel(head, IRON, lit=2.5, sep="ink")
    for p in head:
        r = math.hypot(p[0] - C[0], p[1] - C[1])
        if r > R + 0.6 * n / 14:
            c.put(*p, "grey_l")
    sock = thick(P(7.4, 5.0, n), P(9.6, 7.2, n), 0.9 * n / 14) & head
    c.bevel(sock, ["slate_d", "slate", "grey_d", "grey"], lit=2)
    if n == 28:
        sparkle(c, 3, 3, big=True)
    return c


def tool_scythe(n):
    c = Canvas(n)
    k = n / 14
    s = thick(P(10.2, 13.2, n), P(11.6, 0.8, n), 0.8 * k) & rect(0, 0, n - 1, n - 1)
    c.bevel(s, WOOD, lit=2.6)
    if n == 28:
        peg = thick(P(11.6, 7.6, n), P(12.8, 7.2, n), 0.7 * k)
        c.bevel(peg, WOOD, lit=2.2, sep="ink")
    o = P(8.2, 10.2, n)
    i = P(8.6, 13.2, n)
    blade = {p for p in ell(o[0], o[1], 9.6 * k, 9.4 * k) if p not in ell(i[0], i[1], 10.0 * k, 9.8 * k)}
    blade = {p for p in blade if p[0] <= P(11.6, 0, n)[0] + 0.5 and p[1] <= P(0, 7.5, n)[1] and 0 <= p[0] and 0 <= p[1]}
    c.bevel(blade, METAL, lit=2.3, sep="ink")
    top = {}
    for x, y in blade:
        top[x] = min(top.get(x, 99), y)
    for x, y in top.items():
        c.put(x, y + (1 if n == 28 else 0), "white")
    c.fill(thick(P(10.8, 1.4, n), P(12.2, 1.2, n), 0.8 * k), "grey_d")
    if n == 28:
        sparkle(c, 5, 20)
    return c


def tool_sickle(n):
    c = Canvas(n)
    k = n / 14
    o = P(7.4, 6.4, n)
    i = P(8.6, 7.6, n)
    blade = {p for p in ell(o[0], o[1], 5.9 * k, 5.9 * k) if p not in ell(i[0], i[1], 4.9 * k, 4.9 * k)}
    cut = P(9.0, 8.0, n)
    blade = {p for p in blade if not (p[0] > cut[0] and p[1] > cut[1] - 1 * k) and not (p[1] > P(0, 9.6, n)[1])}
    blade &= rect(0, 0, n - 1, n - 1)
    handle(c, (1.6, 13.2), (4.4, 9.0), n, 1.0, 1.9)
    c.bevel(blade, METAL, lit=2.3, sep="ink")
    inner = {p for p in blade if any((p[0] + dx, p[1] + dy) in ell(i[0], i[1], 4.9 * k, 4.9 * k) for dx, dy in N4)}
    c.fill(inner, "white")
    fer = thick(P(3.6, 10.0, n), P(5.0, 8.6, n), 0.9 * k)
    c.bevel(fer, GOLD, lit=1.8, sep="ink")
    if n == 28:
        sparkle(c, 24, 3, big=True)
    return c


def tool_water_can(n):
    c = Canvas(n)
    if n == 14:
        c.pts(arc_pts(2.6, 8.5, 2.4, 90, 270), "grey_d")
        spout = thick((8.5, 11.0), (11.6, 4.8), 0.9)
        c.bevel(spout, BLUES, lit=2.0)
        rose = rrect(11, 2, 13, 5, 1)
        c.bevel(rose, METAL, lit=2.4, sep="ink")
        body = rrect(2, 5, 9, 13, 1)
        c.cyl(body, BLUES, lit=2.0, peak=0.25, sep="ink")
        c.pts([(x, 5) for x in range(3, 9)], "cyan")
        c.pts([(4, 5), (5, 5)], "slate_d")
        c.pts([(x, 9) for x in range(2, 10)], "blue_d")
        c.pts([(3, 9)], "blue")
        c.put(12, 7, "cyan")
        c.put(13, 9, "cyan")
        return c
    for r, cc in ((6.2, "grey_d"), (5.2, "grey"), (4.4, None)):
        if cc:
            c.pts(arc_pts(5.5, 17.0, r, 90, 270), cc)
    c.pts(arc_pts(5.5, 17.0, 5.2, 180, 240), "grey_l")
    spout = thick((18, 22), (23, 10), 1.6)
    c.bevel(spout, BLUES, lit=2.0)
    rose = poly([(21, 8.5), (24, 4.5), (27, 7.5), (23.5, 11)])
    c.bevel(rose, METAL, lit=2.4, sep="ink")
    for p in ((24, 6), (25, 7), (23, 8), (24, 8)):
        c.put(*p, "grey_d")
    body = rrect(5, 9, 20, 27, 3)
    c.cyl(body, BLUES, lit=2.0, peak=0.25, sep="ink")
    top = ell(12.5, 10.0, 5.6, 1.6) & body
    c.fill(top, "cyan")
    c.fill(ell(10.5, 10.0, 2.4, 0.9), "slate_d")
    c.pts([(x, 18) for x in range(5, 21)], "blue_d")
    c.pts([(x, 17) for x in range(6, 20)], "cyan")
    c.pts([(x, 25) for x in range(6, 20)], "blue_d")
    for y in (13, 21):
        c.put(8, y, "white")
    for p in ((26, 12), (27, 14), (25, 15), (26, 18)):
        c.put(*p, "cyan")
    c.put(26, 13, "white")
    return c


def seeds(n):
    c = Canvas(n)
    if n == 14:
        pk = rect(1, 1, 10, 13)
        c.bevel(pk, PAPER, lit=2.0, hi=False)
        c.pts([(x, 1) for x in range(1, 11) if x % 2 == 0], "tan")
        for x in range(1, 11, 2):
            c.px[x, 1] = CLEAR
        c.pts([(x, 2) for x in range(1, 11)], "tan_d")
        win = rect(2, 4, 9, 9)
        c.bevel(win, GREENS, lit=1.6, soft=False)
        c.stamp(3, 4, ["..gg..", ".gGGg.", "...G..", "bbbbbb"], {"g": "yellow", "G": "white", "b": "brown"})
        c.fill(rect(2, 8, 9, 9), "brown")
        c.pts([(x, 8) for x in range(2, 10)], "brown_l")
        c.pts([(x, 11) for x in range(3, 9)], "tan_d")
        for x, y in ((11, 11), (12, 8)):
            c.fill({(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)}, "amber", sep="ink")
            c.put(x, y, "yellow")
            c.put(x + 1, y + 1, "clay")
        return c
    pk = rect(3, 1, 21, 26)
    c.bevel(pk, PAPER, lit=2.0, hi=False)
    for x in range(3, 22):
        if x % 2 == 1:
            c.px[x, 1] = CLEAR
        else:
            c.put(x, 1, "tan")
    c.pts([(x, 3) for x in range(3, 22)], "tan_d")
    win = rect(5, 6, 19, 18)
    c.bevel(win, GREENS, lit=1.7, sep="ink")
    c.fill(rect(5, 15, 19, 18), "brown")
    c.pts([(x, 15) for x in range(5, 20)], "brown_l")
    sprout = [
        "..yy.....yy..",
        ".yYYy...yYYy.",
        "yYyyYy.yYyyYy",
        ".yyyyYyYyyyy.",
        "......YY.....",
        "......YY.....",
        "......YY.....",
    ]
    c.stamp(6, 7, sprout, {"y": "yellow", "Y": "white"})
    for x, y in ((7, 16), (15, 17), (11, 17)):
        c.fill({(x, y), (x + 1, y)}, "amber")
    c.pts([(x, 21) for x in range(6, 19)], "tan_d")
    c.pts([(x, 23) for x in range(6, 15)], "tan_d")
    for x, y in ((22, 22), (24, 25), (20, 25), (1, 24)):
        seed = {(x, y), (x + 1, y), (x + 2, y), (x, y + 1), (x + 1, y + 1), (x + 2, y + 1), (x + 1, y - 1)}
        c.fill(seed, "amber", sep="ink")
        c.put(x + 1, y - 1, "yellow")
        c.put(x, y, "yellow")
        c.put(x + 2, y + 1, "clay")
    return c


def fertilizer(n):
    c = Canvas(n)
    SACK = ["brown", "brown_l", "tan_d", "tan", "cream"]
    SOIL = ["brown_d", "brown", "brown_l", "tan_d"]
    if n == 14:
        body = rrect(1, 5, 12, 13, 2) | rect(2, 4, 11, 6)
        c.dome(body, SACK, depth=2.5, off=0.5)
        rim = rrect(1, 3, 12, 6, 1)
        c.bevel(rim, SACK, lit=3.0, sep="ink", soft=False)
        c.fill(rect(3, 4, 10, 4), "brown_d")
        c.pts([(x, 3) for x in range(4, 10, 2)], "brown_l")
        c.stamp(3, 0, [".gg..gg.", "gGGggGGg", "..gGGg..", "...GG..."], {"g": "green", "G": "green_d"})
        c.put(4, 0, "yellow")
        c.stamp(5, 8, [".gg.", "gGGG", ".GG."], {"g": "green", "G": "green_d"})
        c.stamp(5, 7, [".gg.", "gggg", ".gG."], {"g": "green", "G": "green_d"})
        c.put(6, 7, "yellow")
        return c
    body = rrect(3, 10, 24, 27, 4) | rect(4, 8, 23, 12)
    c.dome(body, SACK, depth=4, off=0.5)
    rim = rrect(2, 6, 25, 11, 2)
    c.bevel(rim, SACK, lit=3.0, sep="ink")
    for x in range(4, 24, 3):
        c.put(x, 9, "brown_l")
    soil = ell(13.5, 7.6, 9.0, 1.9) & rect(0, 6, 27, 9)
    c.dome(soil, SOIL, depth=1.5, off=0.3)
    for p in ((8, 7), (12, 8), (17, 7), (20, 8)):
        c.put(*p, "tan_d")
    sprout = [
        "..gg......gg..",
        ".gGGg....gGGg.",
        "gGggGg..gGggGg",
        "gGgggGggGgggGg",
        ".gGGGgGGgGGGg.",
        "......sS......",
        "......sS......",
    ]
    c.stamp(7, 0, sprout, {"g": "green", "G": "green_d", "s": "green", "S": "green_dd"})
    c.pts([(9, 2), (19, 2)], "yellow")
    # printed badge: big plus over a little sprout
    badge = ell(13.5, 18.5, 5.4, 5.2)
    c.fill(badge, "cream", sep="ink")
    plus = rect(13, 15, 14, 22) | rect(10, 18, 17, 19)
    c.fill({(x + 1, y + 1) for x, y in plus}, "green_d")
    c.fill(plus, "green")
    c.pts([(13, 15), (10, 18)], "yellow")
    for p in ((5, 24), (21, 25), (6, 14)):
        c.put(*p, "brown_l")
    return c


def sprinkler(n):
    c = Canvas(n)
    if n == 14:
        spray = {p for p in ring(6.5, 9.5, 5.0, 6.9) if p[1] <= 8}
        c.fill(spray, "cyan")
        for (x, y) in spray:
            r = math.hypot(x - 6.5, y - 9.5)
            if r < 5.9:
                c.put(x, y, "blue")
            elif (x + y) % 3 == 0 and y < 6:
                c.put(x, y, "white")
        c.pts([(0, 10), (13, 10), (0, 11)], "cyan")
        c.pts([(13, 11)], "blue")
        base = ell(6.5, 12.5, 4.5, 1.6)
        c.bevel(base, LEAF, lit=2.4)
        stem = rect(6, 7, 7, 11)
        c.bevel(stem, METAL, lit=2.4, sep="ink")
        head = rrect(4, 5, 9, 7, 1)
        c.bevel(head, GOLD, lit=1.8, sep="ink")
        c.pts([(5, 6), (8, 6)], "clay")
        return c
    spray = {p for p in ring(13.5, 18, 10.4, 13.8) if p[1] <= 17}
    c.fill(spray, "cyan")
    for (x, y) in spray:
        r = math.hypot(x - 13.5, y - 18)
        if r < 11.4:
            c.put(x, y, "blue")
        elif r > 12.9 and (x + y) % 4 != 0:
            c.put(x, y, "white")
        elif 11.8 <= r <= 12.4 and (x + 2 * y) % 5 == 0:
            c.put(x, y, "white")
    for x, y in ((0, 19), (1, 22), (27, 19), (26, 22)):
        c.fill({(x, y), (x, y + 1)}, "cyan")
        c.put(x, y, "white")
    base = ell(13.5, 24.5, 9.5, 3.0)
    c.dome(base, LEAF, depth=2, off=0.6)
    stem = rect(12, 13, 15, 23)
    c.cyl(stem, METAL, lit=2.3, peak=0.3)
    c.cyl(rect(10, 20, 17, 22), METAL, lit=2.0, sep="ink")
    head = rrect(8, 9, 19, 13, 2)
    c.cyl(head, GOLD, lit=2.0, peak=0.3, sep="ink")
    cap = rrect(11, 6, 16, 9, 1)
    c.cyl(cap, GOLD, lit=2.4, peak=0.3, sep="ink")
    for x in (10, 13, 16):
        c.fill({(x, 11), (x + 1, 11)}, "clay")
    c.put(12, 7, "white")
    # jets feeding the spray
    c.pts([(9, 8), (8, 7), (18, 8), (19, 7)], "cyan")
    return c


def harvest(n):
    c = Canvas(n)
    if n == 14:
        c.pts(arc_pts(6.5, 6.5, 5.5, 185, 355), "brown")
        c.sphere(4.0, 5.2, 2.6, 2.4, REDS, off=0.3)
        c.sphere(9.4, 4.8, 2.8, 2.6, GREENS, off=-0.2)
        c.stamp(4, 2, ["g"], {"g": "green_d"})
        car = thick((6.5, 6.0), (7.5, 2.0), 0.8)
        c.bevel(car, ["red_d", "rust", "orange", "amber"], lit=2.0, sep="ink")
        c.stamp(7, 0, ["gg"], {"g": "green"})
        basket = poly([(0, 7), (13, 7), (11.5, 13), (1.5, 13)])
        c.fill(basket, "tan_d")
        for (x, y) in basket:
            if (x // 2 + y) % 2 == 0:
                c.put(x, y, "brown_l")
            if y == 13 or x in (min(q[0] for q in basket if q[1] == y),):
                pass
        c.edge(basket, "brown", sides=((1, 0), (0, 1)))
        c.bevel(rect(0, 6, 13, 7), PLANK, lit=2.6, sep="ink")
        return c
    c.pts(arc_pts(13.5, 13, 11.5, 182, 358), "brown")
    c.pts(arc_pts(13.5, 13, 10.5, 182, 358), "brown_l")
    c.pts(arc_pts(13.5, 13, 10.0, 200, 260), "tan")
    c.sphere(7.5, 10.5, 4.6, 4.4, REDS, off=0.3)
    c.stamp(6, 5, [".gG.", "gGgG"], {"g": "green", "G": "green_d"})
    c.sphere(20.5, 10.5, 5.0, 4.6, GREENS, off=-0.2)
    for p in ((18, 9), (20, 8), (22, 10), (19, 12)):
        c.put(*p, "green_d")
    car = thick((13.0, 13.0), (15.5, 3.5), 1.6)
    c.bevel(car, ["red_d", "rust", "orange", "amber"], lit=2.0, sep="ink")
    for y in (6, 9, 11):
        c.put(14, y, "rust")
    c.stamp(14, 0, [".gg.", "gGgG", ".gG."], {"g": "green", "G": "green_d"})
    c.sphere(11, 13.5, 2.2, 2.0, ["purple", "magenta", "pink", "white"], off=0.2, sep="ink")
    basket = poly([(0, 15), (27, 15), (24.4, 27), (2.6, 27)])
    for (x, y) in basket:
        cc = "tan_d" if (x // 3 + y // 2) % 2 == 0 else "brown_l"
        if y % 2 == 1 and x % 3 == 0:
            cc = "brown"
        c.put(x, y, cc)
    c.edge(basket, "brown", sides=((1, 0), (0, 1)))
    c.pts([(x, 26) for x in range(3, 25)], "brown")
    rim = rrect(0, 14, 27, 17, 1)
    c.bevel(rim, PLANK, lit=2.5, sep="ink")
    for x in range(1, 27, 3):
        c.put(x, 16, "brown_l")
    return c


def minecart(c, x0, y0, big):
    """Ore-filled mine cart. 14px version is 14 wide x 13 tall from (x0, y0); big is 17 x 14."""
    if not big:
        ore = ell(x0 + 6.5, y0 + 4.2, 5.6, 3.4)
        c.dome(ore, STONE, depth=2, off=0.45)
        c.stamp(x0 + 2, y0 + 1, ["...rR.....", "aA.Rr..cC.", "Aa.....Cc.", "....gG...."],
                {"r": "pink", "R": "red", "c": "cyan", "C": "blue", "a": "yellow", "A": "amber", "g": "green", "G": "green_d"})
        body = poly([(x0, y0 + 5), (x0 + 13, y0 + 5), (x0 + 11.6, y0 + 10.4), (x0 + 1.4, y0 + 10.4)])
        c.bevel(body, WOOD, lit=2.2, sep="ink")
        c.pts([(x, y0 + 5) for x in range(x0, x0 + 14)], "grey_l")
        c.pts([(x, y0 + 6) for x in range(x0 + 1, x0 + 13)], "grey_d")
        c.pts([(x0 + 3, y) for y in range(y0 + 7, y0 + 11)] + [(x0 + 10, y) for y in range(y0 + 7, y0 + 11)], "grey_d")
        for x in (x0 + 2, x0 + 9):
            c.fill(rrect(x, y0 + 10, x + 2, y0 + 12, 1) | {(x + 1, y0 + 10)}, "slate", sep="ink")
            c.put(x + 1, y0 + 11, "grey_l")
        return
    ore = ell(x0 + 8, y0 + 4.5, 7.2, 4.4)
    c.dome(ore, STONE, depth=3, off=0.45)
    c.stamp(x0 + 2, y0 + 1, ["....wr........", "..aArR...wc....", ".aYAARr..cCC...", "..AA..rR.CC.gG.", "..........gGG.."],
            {"w": "white", "r": "pink", "R": "red", "c": "cyan", "C": "blue", "a": "yellow", "Y": "white", "A": "amber",
             "g": "green", "G": "green_d"})
    body = poly([(x0, y0 + 6), (x0 + 16, y0 + 6), (x0 + 14.4, y0 + 12.4), (x0 + 1.6, y0 + 12.4)])
    c.bevel(body, WOOD, lit=2.2, sep="ink")
    c.pts([(x, y0 + 6) for x in range(x0, x0 + 17)], "grey_l")
    c.pts([(x, y0 + 7) for x in range(x0, x0 + 17)], "grey")
    c.pts([(x, y0 + 8) for x in range(x0 + 1, x0 + 16)], "grey_d")
    for x in (x0 + 4, x0 + 12):
        c.pts([(x, y) for y in range(y0 + 8, y0 + 13)], "grey_d")
        c.put(x, y0 + 10, "grey_l")
    c.pts([(x, y0 + 10) for x in range(x0 + 6, x0 + 11) if x % 2], "brown")
    for x in (x0 + 2, x0 + 11):
        wheel = ell(x + 1.5, y0 + 13, 1.9, 1.9)
        c.fill(wheel, "slate", sep="ink")
        c.put(x + 1, y0 + 12, "grey_l")
        c.put(x + 2, y0 + 13, "grey_d")


CIRC14 = [
    "....xxxxxx....",
    "..xxxxxxxxxx..",
    ".xxxxxxxxxxxx.",
    ".xxxxxxxxxxxx.",
    "xxxxxxxxxxxxxx",
    "xxxxxxxxxxxxxx",
    "xxxxxxxxxxxxxx",
    "xxxxxxxxxxxxxx",
    "xxxxxxxxxxxxxx",
    "xxxxxxxxxxxxxx",
    ".xxxxxxxxxxxx.",
    ".xxxxxxxxxxxx.",
    "..xxxxxxxxxx..",
    "....xxxxxx....",
]


BADGE_B = ["blue_d", "blue", "cyan", "white"]


BADGE_R = ["red_d", "red", "pink", "white"]


WATER = ["blue_d", "blue", "cyan", "white"]


ICONS = {
    "nav_farm": nav_farm, "nav_forest": nav_forest, "nav_mine": nav_mine, "bag": bag, "build": build,
    "craft": craft, "orders": orders, "market": market, "shop": shop, "sell": sell, "menu": menu,
    "settings": settings, "trophy": trophy, "book": book, "gift": gift, "merchant": merchant, "festival": festival,
    "bell": bell, "sound_on": sound_on, "sound_off": sound_off, "vibrate": vibrate, "export": export,
    "import": import_, "info": info, "close": close, "tool_hoe": tool_hoe, "tool_water_can": tool_water_can,
    "tool_axe": tool_axe, "tool_pickaxe": tool_pickaxe, "tool_scythe": tool_scythe, "tool_sickle": tool_sickle,
    "seeds": seeds, "fertilizer": fertilizer, "sprinkler": sprinkler, "harvest": harvest,
}


def main():
    art = sys.argv[1]
    small, big = Sprites(art, "icons"), Sprites(art, "icons_big")
    for name, fn in ICONS.items():
        for sp, n in ((small, 14), (big, 28)):
            body = fn(n).im
            out, pad = finish(body)
            sp.save(name, out, n // 2 + pad, n // 2 + pad)
    print(json.dumps({**small.pivots, **big.pivots}))


main()
