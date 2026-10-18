"""Farm machines and buildings on a one-tile footprint (pixel-direct, folder "buildings"): windmill,
compost_bin, bakery_oven, preserves_jar, beehive, fruit_press, seed_maker, oil_press, market_cart,
harvest_shed and planter_shed.

Same projection and exact ray-cast as buildings.py: a world point (u, v, z) lands at X = (u - v) * 16 and
Y = (u + v) * 8 - z (u and v in tiles, z in pixels). +u runs down-right and +v down-left, so the visible
faces are the top (T), front-right (R, +u) and front-left (L, +v). Each solid is the convex hull of its
points, kept as half-spaces n.X <= d, and every screen pixel is ray-cast through them. A few small details
(windmill sails, sprout, bee, smoke, coin, scythe) are painted in screen space after the render.
Light comes from the top-left; finish() adds the 1px ink outline and the hard drop shadow.
Pivot = the ground centre of the one-tile footprint (local (0, 0, 0)), as buildings.py does it.
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, Sprites, new, finish  # noqa: E402
import math  # noqa: E402

W, H = 96, 96          # working canvas
CX, CY = 48, 72        # ground-centre pixel on the canvas (the pivot)

WOOD = ["brown_d", "brown", "brown_l", "tan"]
WOOD_LT = ["brown", "brown_l", "tan", "cream"]
STONE = ["grey_d", "grey", "grey_l", "cream"]
BRICK = ["brown", "rust", "red", "clay"]
RED_ROOF = ["red_d", "red", "red", "pink"]
SLATE = ["slate", "grey_d", "grey", "grey_l"]


def col(c):
    return E[c] if isinstance(c, str) else c


def unit(n):
    m = math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
    return (n[0] / m, n[1] / m, n[2] / m)


LIGHT = unit((-1.0, 0.45, 1.0))   # toward the light: up and to the left on screen


def tone(ramp, v, x, y):
    """Ramp colour at fractional level v, checker-dithered between the two nearest steps."""
    n = len(ramp) - 1
    v = max(0.0, min(float(n), v))
    b = int(v)
    f = v - b
    if b >= n or f < 0.3:
        return ramp[b]
    if f > 0.7:
        return ramp[b + 1]
    return ramp[b + 1] if (x + y) % 2 == 0 else ramp[b]


def lit(ramp, n, x, y, crisp=False):
    """Lambert against LIGHT mapped onto the ramp; crisp gives flat bands, else a checker dither."""
    nu = unit(n)
    i = nu[0] * LIGHT[0] + nu[1] * LIGHT[1] + nu[2] * LIGHT[2]
    v = ((i + 1.0) / 2.0) * (len(ramp) - 1)
    if crisp:
        return ramp[max(0, min(len(ramp) - 1, int(round(v))))]
    return tone(ramp, v, x, y)


def side_of(n):
    """Visible face of a normal: T (top), R (+u, front-right) or L (+v, front-left)."""
    if n[2] > 0.9:
        return "T"
    return "R" if n[0] > n[1] else "L"


def ring(cu, cv, r, k, z, rot=0.0):
    return [(cu + r * math.cos(rot + 2 * math.pi * i / k), cv + r * math.sin(rot + 2 * math.pi * i / k), z)
            for i in range(k)]


def bx(u0, u1, v0, v1, z0, z1):
    return [(u, v, z) for u in (u0, u1) for v in (v0, v1) for z in (z0, z1)]


def hull(pts):
    """Convex hull of 3D points as half-spaces (n, d, tag) with n.X <= d. Coplanar triangles merge."""
    n = len(pts)
    out = {}
    for i in range(n):
        for j in range(i + 1, n):
            for k in range(j + 1, n):
                a, b, c = pts[i], pts[j], pts[k]
                e1 = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
                e2 = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
                nn = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
                m = math.sqrt(nn[0] * nn[0] + nn[1] * nn[1] + nn[2] * nn[2])
                if m < 1e-9:
                    continue
                nn = (nn[0] / m, nn[1] / m, nn[2] / m)
                d = nn[0] * a[0] + nn[1] * a[1] + nn[2] * a[2]
                hi = lo = False
                for p in pts:
                    s = nn[0] * p[0] + nn[1] * p[1] + nn[2] * p[2] - d
                    if s > 1e-7:
                        hi = True
                    elif s < -1e-7:
                        lo = True
                    if hi and lo:
                        break
                if (hi and lo) or not (hi or lo):
                    continue
                if hi:
                    nn = (-nn[0], -nn[1], -nn[2])
                    d = -d
                out[(round(nn[0], 5), round(nn[1], 5), round(nn[2], 5), round(d, 4))] = (nn, d)
    return [(nn, d, "F") for nn, d in out.values()]


class Solid:
    def __init__(self, pts, mat):
        self.pts = pts                # the convex point set (for the screen bounding box)
        self.cons = hull(pts)
        self.mat = mat                # fn(tag, normal, u, v, z, x, y) -> palette name or RGBA
        self.bb = None


def hit(cons, D, Y):
    """Top-most point of a convex solid on the view ray of pixel offset (D, Y): (z, tag, normal).
    Along the ray u = (Y+z)/16 + D/2 and v = (Y+z)/16 - D/2, so every constraint is linear in z."""
    zlo, zhi, tag, nrm = -1e18, 1e18, None, None
    for n, d, t in cons:
        a, b, c = n
        k = (a + b) / 16.0 + c
        m = d - (a + b) * Y / 16.0 - (a - b) * D / 2.0
        if -1e-12 < k < 1e-12:
            if m < -1e-9:
                return None
        elif k > 0:
            z = m / k
            if z < zhi:
                zhi, tag, nrm = z, t, n
        else:
            z = m / k
            if z > zlo:
                zlo = z
    if tag is None or zlo > zhi + 1e-9:
        return None
    return zhi, tag, nrm


def render(solids):
    for s in solids:
        xs = [(p[0] - p[1]) * 16 + CX for p in s.pts]
        ys = [(p[0] + p[1]) * 8 - p[2] + CY for p in s.pts]
        s.bb = (min(xs) - 1, max(xs) + 1, min(ys) - 1, max(ys) + 1)
    im = new(W, H)
    px = im.load()
    for y in range(H):
        Y = y - CY
        for x in range(W):
            D = (x - CX) / 16.0
            best = None
            for s in solids:
                b = s.bb
                if x < b[0] or x > b[1] or y < b[2] or y > b[3]:
                    continue
                h = hit(s.cons, D, Y)
                if h is None or h[0] < -1e-6:
                    continue
                if best is None or h[0] >= best[0][0] - 1e-9:
                    best = (h, s)          # the nearest surface along the ray wins
            if best is None:
                continue
            (z, tag, nrm), s = best
            u = (Y + z) / 16.0 + D / 2.0
            v = (Y + z) / 16.0 - D / 2.0
            px[x, y] = col(s.mat(tag, nrm, u, v, z, x, y))
    return im


def stamp(im, rows, key, x0, y0):
    """Paint a small character map onto the canvas with its top-left corner at (x0, y0)."""
    px = im.load()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in ". ":
                continue
            X, Y = x0 + i, y0 + j
            if 0 <= X < W and 0 <= Y < H:
                c = key[ch]
                px[X, Y] = col(c)


def disc(im, cx, cy, r, c):
    px = im.load()
    for Y in range(int(cy - r) - 1, int(cy + r) + 2):
        for X in range(int(cx - r) - 1, int(cx + r) + 2):
            if 0 <= X < W and 0 <= Y < H and (X + 0.5 - cx) ** 2 + (Y + 0.5 - cy) ** 2 <= r * r:
                px[X, Y] = col(c)


def emit(sp, name, solids, paint=None):
    im = render(solids)
    if paint is not None:
        paint(im)
    x0, y0, x1, y1 = im.getbbox()
    body = im.crop((x0, y0, x1, y1))
    out, pad = finish(body)
    sp.save(name, out, CX - x0 + pad, CY - y0 + pad)
    sys.stderr.write("%s: body %dx%d\n" % (name, body.width, body.height))


# ---------------------------------------------------------------- windmill (34 x ~56)

def windmill():
    ROOF = ["brown_d", "brown", "brown_l", "tan"]
    HUB = (CX, CY - 36)

    def tower(tag, n, u, v, z, x, y):
        s = side_of(n)
        if s == "T":
            return "grey_l"
        row = int(z // 4)
        if z - row * 4 < 0.9:                                  # mortar course
            return "grey_d"
        if s == "L" and abs(u) < 0.12 and z < 9.5:             # arched wooden door, front-left
            if z < 0.9:
                return "brown_d"
            if abs(abs(u) - 0.12) < 0.02 or z > 8.4:
                return "tan"
            return "brown" if int(math.floor(u * 40)) % 2 else "brown_d"
        if s == "R" and abs(v) < 0.07 and 15.0 < z < 21.0:     # window, front-right
            if abs(abs(v) - 0.07) < 0.02 or abs(z - 18.0) < 0.6 or z > 20.2 or z < 15.8:
                return "cream"
            return "slate" if z > 18.0 else "cyan"
        th = math.atan2(v, u) / (2 * math.pi)
        if (th * 12 + (row % 2) * 0.5) % 1.0 < 0.1:           # stone head joints
            return "grey_d"
        return lit(STONE, n, x, y, crisp=True)

    def roof(tag, n, u, v, z, x, y):
        if z - int(z // 3) * 3 < 0.9:                          # shingle courses
            return "brown_d"
        return lit(ROOF, n, x, y, crisp=True)

    def sails(im):
        px = im.load()
        hx, hy = HUB
        for sgn in (-1, 1):                                    # four blades on the hub
            for t in range(4, 17):
                for s in range(-3, 3):
                    if t == 16 or s in (-3, 2):
                        c = "brown"
                    elif t % 4 == 0:
                        c = "brown_l"                          # rungs of the lattice
                    else:
                        c = "cream" if s < 0 else "tan"
                    for X, Y in ((hx + s, hy + sgn * t), (hx + sgn * t, hy + s)):
                        if 0 <= X < W and 0 <= Y < H:
                            px[X, Y] = E[c]
        disc(im, hx, hy, 3.6, "brown_d")
        disc(im, hx, hy, 2.2, "brown")
        disc(im, hx - 1, hy - 1, 1.0, "tan")
        px[hx, hy] = E["grey_d"]

    solids = [
        Solid(ring(0, 0, 0.42, 12, 0) + ring(0, 0, 0.34, 12, 30), tower),
        Solid(ring(0, 0, 0.5, 12, 30) + [(0, 0, 44)], roof),
    ]
    return solids, sails


# ---------------------------------------------------------------- compost bin (1x1, slatted box)

SPROUT = [
    "gG...Gg",
    "gGG.GGg",
    ".gGgGg.",
    "...G...",
    "...G...",
    "...G...",
]


def compost_bin():
    R, T, TOP = 0.42, 0.1, 13.0

    def wall(tag, n, u, v, z, x, y):
        s = side_of(n)
        if s == "T":
            return "tan"
        if (s == "L" and v < 0.3) or (s == "R" and u < 0.3):   # inside face of a back wall
            return "brown_d"
        if z < 1.0:
            return "brown_d"
        if 1.5 < z < 3.0 or 9.0 < z < 10.5:                    # hoops
            return "tan_d"
        t = u if s == "L" else v
        if (t * 6.0) % 1.0 < 0.22:                             # gaps between the slats
            return "brown_d"
        return lit(WOOD, n, x, y, crisp=True)

    def soil(tag, n, u, v, z, x, y):
        h = (x * 7 + y * 5 + int(math.floor(u * 9)) * 3) % 11
        if h == 0:
            return "tan"
        if h in (3, 4):
            return "green_dd"
        if h in (1, 6):
            return "brown"
        return "brown_d"

    solids = [
        Solid(bx(-R, R, R - T, R, 0, TOP), wall),
        Solid(bx(R - T, R, -R, R, 0, TOP), wall),
        Solid(bx(-R, R, -R, -R + T, 0, TOP), wall),
        Solid(bx(-R, -R + T, -R, R, 0, TOP), wall),
        Solid(bx(-0.33, 0.33, -0.33, 0.33, 0, 9), soil),
    ]

    def paint(im):
        stamp(im, SPROUT, {"g": "green", "G": "green_d"}, CX - 3, CY - 9 - 5)

    return solids, paint


# ---------------------------------------------------------------- bakery oven (1x1, brick dome)

def bakery_oven():
    def body(tag, n, u, v, z, x, y):
        s = side_of(n)
        if s == "T":
            return "tan"
        if s == "L" and abs(u) < 0.14 and 1.0 < z < 5.8:       # glowing mouth, front-left
            if abs(u) > 0.11 or z < 1.5 or z > 5.2:
                return "brown_d"
            r = math.hypot(u / 0.1, (z - 3.2) / 2.0)
            if r < 0.45:
                return "yellow"
            if r < 0.85:
                return "amber"
            return "orange" if r < 1.1 else "red_d"
        row = int(z // 2.6)
        if z - row * 2.6 < 0.7:
            return "tan"
        t = u if s == "L" else v
        if (t * 3.0 + (row % 2) * 0.5) % 1.0 < 0.14:
            return "tan"
        return lit(BRICK, n, x, y, crisp=True)

    def dome(tag, n, u, v, z, x, y):
        if n[2] > 0.9 and z > 16.0:
            return "grey_d"
        row = int(z // 2.4)
        if z - row * 2.4 < 0.7:
            return "tan"
        th = math.atan2(v, u) / (2 * math.pi)
        if (th * 14 + (row % 2) * 0.5) % 1.0 < 0.12:
            return "tan"
        return lit(BRICK, n, x, y, crisp=True)

    def chimney(tag, n, u, v, z, x, y):
        if z > 23.0:
            return "grey_d"
        row = int(z // 2.6)
        if z - row * 2.6 < 0.7:
            return "grey_l"
        return lit(["grey_d", "grey", "brown", "rust"], n, x, y, crisp=True)

    solids = [
        Solid(bx(-0.4, 0.4, -0.36, 0.36, 0, 8), body),
        Solid(ring(0, 0, 0.38, 12, 8) + ring(0, 0, 0.33, 12, 11) + ring(0, 0, 0.22, 12, 14)
              + ring(0, 0, 0.08, 12, 17), dome),
        Solid(bx(0.1, 0.26, -0.3, -0.14, 0, 24), chimney),
    ]

    def paint(im):
        disc(im, CX + 8, CY - 30, 2.6, "grey_l")               # smoke from the chimney
        disc(im, CX + 11, CY - 36, 2.2, "white")
        disc(im, CX + 8, CY - 40, 1.4, "grey_l")

    return solids, paint


# ---------------------------------------------------------------- preserves jar (shelf with jars)

def preserves_jar():
    def wood(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "tan"
        return lit(WOOD, n, x, y, crisp=True)

    def jar(cu, cv, z0, h, ramp):
        def mat(tag, n, u, v, z, x, y):
            s = side_of(n)
            if z > z0 + h - 1.4 or s == "T":                   # tan cloth lid
                return lit(WOOD_LT, n, x, y, crisp=True)
            if z0 + 2.2 < z < z0 + 4.4:                        # paper label
                return "cream"
            if z0 + h - 1.4 - 0.7 < z < z0 + h - 1.4:
                return "white"                                 # glass shoulder highlight
            return lit(ramp, n, x, y, crisp=True)
        return Solid(ring(cu, cv, 0.19, 10, z0) + ring(cu, cv, 0.2, 10, z0 + h - 1.4)
                     + ring(cu, cv, 0.22, 10, z0 + h), mat)

    solids = []
    for pu in (-0.4, 0.4):
        for pv in (-0.2, 0.2):
            solids.append(Solid(bx(pu - 0.04, pu + 0.04, pv - 0.04, pv + 0.04, 0, 30), wood))
    for z0 in (0.0, 11.0, 22.0):
        solids.append(Solid(bx(-0.46, 0.46, -0.24, 0.24, z0, z0 + 1.6), wood))
    jam = ["red_d", "red", "red", "pink"]
    pickle = ["green_dd", "green_d", "green", "green"]
    for cu in (-0.3, 0.0, 0.3):
        solids.append(jar(cu, 0.02, 1.6, 6.8, jam))
        solids.append(jar(cu, 0.02, 12.6, 6.8, pickle))
    for cu in (-0.14, 0.14):
        solids.append(jar(cu, 0.0, 23.6, 5.0, jam))
    return solids, None


# ---------------------------------------------------------------- beehive (white boxes)

BEE = [
    ".ww..",
    "kyyky",
    ".kk..",
]


def beehive():
    WHITE = ["grey", "grey_l", "white", "white"]

    def hive(tag, n, u, v, z, x, y):
        s = side_of(n)
        if s == "T":
            return "white"
        if abs(z - 8.0) < 0.6:
            return "grey"                                       # joint between the boxes
        if s == "L" and abs(u) < 0.12 and 1.2 < z < 3.2:       # entrance slot
            return "brown_d"
        if s == "L" and abs(u) < 0.16 and 0.0 < z < 1.2:
            return "tan"
        if z % 4.0 < 0.6:
            return "grey_l"                                    # board seams
        return lit(WHITE, n, x, y, crisp=True)

    def roof(tag, n, u, v, z, x, y):
        if abs(n[0]) > 0.9:
            return "cream"                                     # gable end
        if z % 2.4 < 0.5:
            return "brown_d"
        return lit(["brown_d", "brown", "brown_l", "tan"], n, x, y, crisp=True)

    solids = [
        Solid(bx(-0.36, 0.36, -0.36, 0.36, 0, 8), hive),
        Solid(bx(-0.34, 0.34, -0.34, 0.34, 8, 15), hive),
        Solid([(-0.42, -0.4, 15), (0.42, -0.4, 15), (0.42, 0.4, 15), (-0.42, 0.4, 15),
               (-0.42, 0.0, 20.5), (0.42, 0.0, 20.5)], roof),
    ]

    def paint(im):
        stamp(im, BEE, {"w": "white", "y": "yellow", "k": "ink"}, CX - 17, CY - 6)

    return solids, paint


# ---------------------------------------------------------------- fruit press (barrel and screw press)

def fruit_press():
    BCU, BCV = -0.1, 0.05

    def barrel(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            if math.hypot(u - BCU, v - BCV) < 0.26:             # grape mash
                return ["purple", "purple", "magenta", "purple", "pink"][(int(x) * 3 + int(y) * 5) % 5]
            return "brown_l"
        if 2.0 < z < 3.3 or 12.4 < z < 13.8:                   # iron hoops
            return "grey_d"
        th = math.atan2(v - BCV, u - BCU) / (2 * math.pi)
        if (th * 14) % 1.0 < 0.12:                             # stave seams
            return "brown_d"
        return lit(WOOD, n, x, y, crisp=True)

    def press(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "tan"
        return lit(WOOD, n, x, y, crisp=True)

    def screw(tag, n, u, v, z, x, y):
        return lit(SLATE, n, x, y, crisp=True)

    solids = [
        Solid(ring(BCU, BCV, 0.3, 12, 0) + ring(BCU, BCV, 0.37, 12, 8) + ring(BCU, BCV, 0.32, 12, 16), barrel),
        Solid(bx(-0.14, 0.3, -0.2, 0.2, 17.5, 19.5), press),                # press plate
        Solid(bx(0.32, 0.4, -0.26, -0.2, 0, 27), press),                    # frame posts
        Solid(bx(0.32, 0.4, 0.2, 0.26, 0, 27), press),
        Solid(bx(0.28, 0.42, -0.3, 0.3, 25, 28), press),                    # top beam
        Solid(bx(0.2, 0.24, -0.02, 0.02, 19.5, 25), screw),                 # screw
        Solid(bx(0.16, 0.28, -0.4, 0.4, 28, 30), press),                    # handle bar
    ]
    return solids, None


# ---------------------------------------------------------------- seed maker (cabinet, hopper, packets)

def seed_maker():
    def cabinet(tag, n, u, v, z, x, y):
        s = side_of(n)
        if s == "T":
            return "tan"
        if abs(z - 5.0) < 0.5 or abs(z - 9.5) < 0.5:           # drawer seams
            return "brown_d"
        if s == "L" and abs(u) < 0.03 and 2.0 < z < 3.2:       # knob
            return "grey_l"
        return lit(WOOD, n, x, y, crisp=True)

    def hopper(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            if abs(u) < 0.26 and abs(v) < 0.26:                # open mouth with seeds
                return "amber" if (int(x) * 3 + int(y) * 5) % 7 == 0 else ("tan" if (int(x) + int(y)) % 3 == 0 else "brown_d")
            return "grey_l"
        return lit(SLATE, n, x, y, crisp=True)

    def packet(colour):
        def mat(tag, n, u, v, z, x, y):
            if side_of(n) == "T":
                return "cream"
            if 10.0 < z < 12.0:
                return "cream"
            return colour
        return mat

    def shelf(tag, n, u, v, z, x, y):
        return "tan" if side_of(n) == "T" else "brown"

    solids = [
        Solid(bx(-0.34, 0.34, -0.3, 0.3, 0, 14), cabinet),
        Solid(bx(-0.2, 0.2, -0.18, 0.18, 14, 24), hopper),
        Solid([(-0.36, -0.34, 24), (0.36, -0.34, 24), (0.36, 0.34, 24), (-0.36, 0.34, 24),
               (-0.2, -0.18, 14), (0.2, -0.18, 14), (0.2, 0.18, 14), (-0.2, 0.18, 14)], hopper),
        Solid(bx(0.34, 0.46, -0.26, 0.26, 6, 8), shelf),
        Solid(bx(0.36, 0.42, -0.22, -0.1, 8, 15), packet("red")),
        Solid(bx(0.36, 0.42, -0.06, 0.06, 8, 16), packet("green")),
        Solid(bx(0.36, 0.42, 0.1, 0.22, 8, 14), packet("amber")),
    ]
    return solids, None


# ---------------------------------------------------------------- oil press (millstone wheel, oil jug)

def oil_press():
    WCU, WZ, WR = -0.1, 15.0, 12.0

    def wheel_face(tag, n, u, v, z, x, y):
        if abs(n[1]) < 0.8:
            return "grey_d"                                    # rim edge
        rr = math.hypot((u - WCU) * 16.0, z - WZ)
        if rr > WR - 1.6:
            return "grey_d"
        if rr < 2.4:
            return "brown_d"                                   # axle hole
        ang = math.atan2(z - WZ, (u - WCU) * 16.0) / (2 * math.pi)
        if (ang * 8) % 1.0 < 0.12:                             # radial grooves
            return "grey_d"
        return lit(SLATE, n, x, y, crisp=True)

    def jug(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "yellow" if (int(x) + int(y)) % 2 else "amber"
        if 6.0 < z < 7.4:
            return "brown_d"
        return lit(["orange", "amber", "amber", "yellow"], n, x, y, crisp=True)

    def plinth(tag, n, u, v, z, x, y):
        return "grey_l" if side_of(n) == "T" else lit(STONE, n, x, y)

    wheel = []
    for v in (-0.07, 0.07):
        wheel += [(WCU + (WR / 16.0) * math.cos(2 * math.pi * i / 20), v, WZ + WR * math.sin(2 * math.pi * i / 20))
                  for i in range(20)]
    solids = [
        Solid(bx(-0.42, 0.42, -0.36, 0.36, 0, 3), plinth),
        Solid(wheel, wheel_face),
        Solid(ring(0.26, 0.18, 0.1, 12, 3) + ring(0.26, 0.18, 0.135, 12, 9)
              + ring(0.26, 0.18, 0.12, 12, 13) + ring(0.26, 0.18, 0.07, 12, 16.5), jug),
    ]
    return solids, None


# ---------------------------------------------------------------- market cart (hand cart, awning)

def market_cart():
    def wood(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "tan"
        if (int(math.floor(v * 10)) % 3) == 0 and side_of(n) == "R":
            return "brown_d"
        return lit(WOOD, n, x, y, crisp=True)

    def wheel(tag, n, u, v, z, x, y):
        if abs(n[1]) < 0.8:
            return "brown_d"
        rr = math.hypot(u * 16.0, z - 7.0)
        if rr > 5.6:
            return "brown_d"
        if rr < 1.6:
            return "grey_d"
        ang = math.atan2(z - 7.0, u * 16.0) / (2 * math.pi)
        if (ang * 6) % 1.0 < 0.14:
            return "tan"
        return lit(WOOD, n, x, y, crisp=True)

    def canopy(tag, n, u, v, z, x, y):
        stripe = int(math.floor((v + 0.4) / 0.2)) % 2
        if side_of(n) == "T":
            return "red" if stripe == 0 else "cream"
        if z < 20.2:
            return "brown_d"
        return "red_d" if stripe == 0 else "tan"

    def produce_apple(tag, n, u, v, z, x, y):
        return lit(["red_d", "red", "red", "pink"], n, x, y, crisp=True)

    def corn(tag, n, u, v, z, x, y):
        return lit(["brown", "amber", "yellow", "white"], n, x, y, crisp=True)

    def wheel_pts(v):
        return [(0.4375 * math.cos(2 * math.pi * i / 14), v, 7.0 + 7.0 * math.sin(2 * math.pi * i / 14))
                for i in range(14)]

    solids = [
        Solid(wheel_pts(0.33) + wheel_pts(0.37), wheel),
        Solid(wheel_pts(-0.33) + wheel_pts(-0.37), wheel),
        Solid(bx(-0.3, 0.3, -0.24, 0.24, 7, 10), wood),                        # bed
        Solid(bx(-0.03, 0.03, -0.36, 0.36, 3.5, 5.5), wood),                  # axle
        Solid(bx(0.3, 0.5, 0.15, 0.2, 7, 9), wood),                           # handles
        Solid(bx(0.3, 0.5, -0.2, -0.15, 7, 9), wood),
        Solid(bx(-0.34, -0.28, -0.28, -0.22, 10, 24), wood),                  # awning posts
        Solid(bx(-0.34, -0.28, 0.22, 0.28, 10, 24), wood),
        Solid(bx(0.24, 0.28, -0.28, -0.22, 10, 21), wood),
        Solid(bx(0.24, 0.28, 0.22, 0.28, 10, 21), wood),
        Solid([(-0.4, -0.4, 24.0), (-0.4, 0.4, 24.0), (-0.4, -0.4, 22.5), (-0.4, 0.4, 22.5),
               (0.36, -0.4, 20.5), (0.36, 0.4, 20.5), (0.36, -0.4, 19.0), (0.36, 0.4, 19.0)], canopy),
        Solid(bx(-0.26, 0.02, -0.2, 0.06, 10, 14), wood),                     # apple crate
        Solid(ring(-0.14, -0.06, 0.06, 8, 14) + ring(-0.14, -0.06, 0.05, 8, 16.5), produce_apple),
        Solid(ring(-0.06, -0.14, 0.06, 8, 14) + ring(-0.06, -0.14, 0.05, 8, 16.5), produce_apple),
        Solid(ring(-0.2, 0.08, 0.06, 8, 14) + ring(-0.2, 0.08, 0.05, 8, 16.5), produce_apple),
        Solid(bx(0.06, 0.28, -0.2, 0.2, 10, 13), wood),                       # corn crate
        Solid(ring(0.17, 0.0, 0.12, 8, 13) + [(0.17, 0.0, 18.0)], corn),
    ]

    def paint(im):
        disc(im, CX + 15, CY - 14, 2.8, "amber")               # little coin sign on a bracket
        disc(im, CX + 15, CY - 14, 1.9, "yellow")
        stamp(im, ["g"], {"g": "brown"}, CX + 13, CY - 12)

    return solids, paint


# ---------------------------------------------------------------- harvest shed (wood, red roof)

def harvest_shed():
    def wall(tag, n, u, v, z, x, y):
        s = side_of(n)
        if s == "T":
            return "tan"
        if s == "L" and abs(u) < 0.13 and z < 10.0:            # door with an X brace
            if z < 0.9:
                return "brown_d"
            t, q = (u + 0.13) / 0.26, z / 10.0
            if abs(t - q) < 0.1 or abs(t + q - 1.0) < 0.1:
                return "tan"
            return "brown_d"
        if s == "R" and abs(v) < 0.1 and 6.0 < z < 11.0:       # window with cream frame
            if abs(abs(v) - 0.1) < 0.02 or abs(z - 8.5) < 0.5:
                return "cream"
            return "slate"
        if z < 1.0:
            return "brown_d"
        t = u if s == "L" else v
        if (t * 4.0) % 1.0 < 0.14:                             # plank joints
            return "brown_d"
        return lit(WOOD, n, x, y, crisp=True)

    def roof(tag, n, u, v, z, x, y):
        if abs(n[0]) > 0.9:
            return "tan"                                       # gable end
        if z - int(z // 2.6) * 2.6 < 0.7:
            return "red_d"
        return lit(RED_ROOF, n, x, y, crisp=True)

    def basket(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "brown_d"
        ang = math.atan2(v, u)
        if (int(z // 2) + int(math.floor(ang * 6.0 / math.pi))) % 2:
            return "tan"
        return "brown_l"

    def wheat(tag, n, u, v, z, x, y):
        return lit(["amber", "amber", "yellow", "cream"], n, x, y, crisp=True)

    def carrot(tag, n, u, v, z, x, y):
        return lit(["rust", "orange", "orange", "amber"], n, x, y, crisp=True)

    solids = [
        Solid(bx(-0.36, 0.36, -0.36, 0.36, 0, 13), wall),
        Solid([(-0.42, -0.4, 13), (0.42, -0.4, 13), (0.42, 0.4, 13), (-0.42, 0.4, 13),
               (-0.42, 0.0, 21.5), (0.42, 0.0, 21.5)], roof),
        Solid(ring(-0.2, 0.46, 0.13, 12, 0) + ring(-0.2, 0.46, 0.19, 12, 6), basket),
        Solid(ring(-0.2, 0.46, 0.14, 8, 6) + [(-0.2, 0.46, 11.0)], wheat),
        Solid(ring(0.24, 0.46, 0.13, 12, 0) + ring(0.24, 0.46, 0.19, 12, 6), basket),
        Solid(ring(0.24, 0.46, 0.14, 8, 6) + [(0.24, 0.46, 10.0)], carrot),
    ]

    def paint(im):
        px = im.load()
        for t in range(0, 22):                                  # scythe handle leaning on the wall
            X = CX + 19 - int(round(t * 0.4))
            Y = CY + 4 - t
            if 0 <= X < W and 0 <= Y < H:
                px[X, Y] = E["brown_l"]
        for X, Y in ((CX + 9, CY - 19), (CX + 6, CY - 22), (CX + 3, CY - 23), (CX + 1, CY - 22)):
            if 0 <= X < W and 0 <= Y < H:
                px[X, Y] = E["grey_l"]
                px[X, Y + 1] = E["grey"]

    return solids, paint


# ---------------------------------------------------------------- planter shed (green, seed sacks, can)

def planter_shed():
    def wall(tag, n, u, v, z, x, y):
        s = side_of(n)
        if s == "T":
            return "green"
        if s == "L" and abs(u) < 0.12 and z < 10.0:            # door
            if z < 0.9:
                return "green_dd"
            if abs(abs(u) - 0.12) < 0.02 or z > 9.2:
                return "cream"
            return "brown" if int(math.floor(u * 30)) % 2 else "brown_l"
        if s == "L" and z > 13.0:                              # gable: cream trim with a round vent
            if math.hypot(u / 0.09, (z - 17.5) / 2.2) < 1.0:
                return "brown_d"
            return "cream"
        if z < 1.0:
            return "green_dd"
        t = u if s == "L" else v
        if (t * 4.0) % 1.0 < 0.14:
            return "green_dd"
        return lit(["green_dd", "green_d", "green", "green"], n, x, y, crisp=True)

    def roof(tag, n, u, v, z, x, y):
        if abs(n[1]) > 0.9:                                    # gable face
            return "cream"
        if z - int(z // 2.6) * 2.6 < 0.7:
            return "slate"
        return lit(SLATE, n, x, y, crisp=True)

    def sack(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "tan"
        if 6.0 < z < 7.6:
            return "brown"
        return lit(["tan_d", "tan", "cream", "white"], n, x, y, crisp=True)

    def can(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "blue_d"
        if 1.5 < z < 2.6:
            return "grey"
        return lit(["blue_d", "blue", "blue", "cyan"], n, x, y, crisp=True)

    solids = [
        Solid(bx(-0.38, 0.36, -0.36, 0.38, 0, 13), wall),
        Solid([(-0.44, -0.42, 13), (0.44, -0.42, 13), (0.44, 0.42, 13), (-0.44, 0.42, 13),
               (0.0, -0.42, 21.0), (0.0, 0.42, 21.0)], roof),
        Solid(ring(-0.14, 0.44, 0.1, 10, 0) + ring(-0.14, 0.44, 0.13, 10, 4)
              + ring(-0.14, 0.44, 0.09, 10, 7) + ring(-0.14, 0.44, 0.04, 10, 8), sack),
        Solid(ring(0.12, 0.44, 0.1, 10, 0) + ring(0.12, 0.44, 0.13, 10, 4)
              + ring(0.12, 0.44, 0.09, 10, 7) + ring(0.12, 0.44, 0.04, 10, 8), sack),
        Solid(ring(0.34, 0.14, 0.1, 10, 0) + ring(0.34, 0.14, 0.1, 10, 6), can),
        Solid(bx(0.36, 0.44, 0.1, 0.18, 2.0, 4.0), can),                    # spout
    ]
    return solids, None


SPRITES = [("windmill", windmill), ("compost_bin", compost_bin), ("bakery_oven", bakery_oven),
           ("preserves_jar", preserves_jar), ("beehive", beehive), ("fruit_press", fruit_press),
           ("seed_maker", seed_maker), ("oil_press", oil_press), ("market_cart", market_cart),
           ("harvest_shed", harvest_shed), ("planter_shed", planter_shed)]


def main():
    s = Sprites(sys.argv[1], "buildings")
    for name, fn in SPRITES:
        solids, paint = fn()
        emit(s, name, solids, paint)
    s.done()


main()
