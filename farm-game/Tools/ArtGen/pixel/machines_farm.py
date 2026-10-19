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
STONE = ["grey_d", "grey", "grey", "grey_l"]
BRICK = ["brown", "rust", "red", "clay"]
RED_ROOF = ["red_d", "red", "red", "pink"]
SLATE = ["slate", "grey_d", "grey", "grey_l"]
GRAPE = ["purple", "purple", "magenta", "purple", "pink"]


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
                px[X, Y] = col(key[ch])


def put(im, X, Y, c):
    if 0 <= X < W and 0 <= Y < H:
        im.load()[X, Y] = col(c)


def disc(im, cx, cy, r, c):
    for Y in range(int(cy - r) - 1, int(cy + r) + 2):
        for X in range(int(cx - r) - 1, int(cx + r) + 2):
            if (X + 0.5 - cx) ** 2 + (Y + 0.5 - cy) ** 2 <= r * r:
                put(im, X, Y, c)


def emit(sp, name, solids, paint=None):
    im = render(solids)
    if paint is not None:
        paint(im)
    x0, y0, x1, y1 = im.getbbox()
    body = im.crop((x0, y0, x1, y1))
    out, pad = finish(body)
    sp.save(name, out, CX - x0 + pad, CY - y0 + pad)
    sys.stderr.write("%s: body %dx%d\n" % (name, body.width, body.height))


# ---------------------------------------------------------------- windmill (33 x 57)

def windmill():
    ROOF = ["brown_d", "brown", "brown_l", "tan"]
    HX, HY = CX, CY - 34                       # hub, on the front of the cap

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
        return lit(["grey_d", "grey", "grey_l", "cream"], n, x, y, crisp=True)

    def roof(tag, n, u, v, z, x, y):
        if z - int(z // 3) * 3 < 0.9:                          # shingle courses
            return "brown_d"
        return lit(ROOF, n, x, y, crisp=True)

    def sails(im):
        for sgn in (-1, 1):                                    # four blades on the hub
            for t in range(4, 15):
                for s in range(-3, 3):
                    if t == 14 or s in (-3, 2):
                        c = "brown"
                    elif t % 4 == 0:
                        c = "brown_l"                          # rungs of the lattice
                    else:
                        c = "cream" if s < 0 else "tan"
                    put(im, HX + s, HY + sgn * t, c)
                    put(im, HX + sgn * t, HY + s, c)
        disc(im, HX, HY, 3.6, "brown_d")
        disc(im, HX, HY, 2.2, "brown")
        disc(im, HX - 1, HY - 1, 1.0, "tan")
        put(im, HX, HY, "grey_d")

    solids = [
        Solid(ring(0, 0, 0.42, 12, 0) + ring(0, 0, 0.34, 12, 30), tower),
        Solid(ring(0, 0, 0.5, 12, 30) + [(0, 0, 52)], roof),
    ]
    return solids, sails


# ---------------------------------------------------------------- compost bin (27 x 26, slatted box)

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


# ---------------------------------------------------------------- bakery oven (30 x 47, brick dome)

def bakery_oven():
    def mouth_top(X):
        return 3.6 + math.sqrt(max(0.0, 4.6 * 4.6 - X * X))

    def body(tag, n, u, v, z, x, y):
        s = side_of(n)
        if s == "T":
            return "tan"
        if s == "L":
            X = u * 16.0                                       # offset from the mouth's centre
            if abs(X) < 4.6 and 1.2 < z < mouth_top(X):        # arched mouth, front-left
                if abs(X) > 3.6 or z < 2.0 or z > mouth_top(X) - 0.9:
                    return "brown_d"
                r = math.hypot(X / 2.4, (z - 3.2) / 2.2)
                if r < 0.5:
                    return "yellow"
                if r < 0.95:
                    return "amber"
                return "orange"
        row = int(z // 2.6)
        if z - row * 2.6 < 0.7:
            return "tan"
        t = u if s == "L" else v
        if (t * 3.0 + (row % 2) * 0.5) % 1.0 < 0.14:
            return "tan"
        return lit(BRICK, n, x, y, crisp=True)

    def dome(tag, n, u, v, z, x, y):
        if n[2] > 0.9 and z > 20.0:
            return "grey_d"                                    # soot on the crown
        row = int(z // 2.4)
        if z - row * 2.4 < 0.7:
            return "tan"
        th = math.atan2(v, u) / (2 * math.pi)
        if (th * 14 + (row % 2) * 0.5) % 1.0 < 0.12:
            return "tan"
        return lit(BRICK, n, x, y, crisp=True)

    def chimney(tag, n, u, v, z, x, y):
        if z > 25.0:
            return "grey_d"
        row = int(z // 2.6)
        if z - row * 2.6 < 0.7:
            return "grey_l"
        return lit(["grey_d", "grey", "brown", "rust"], n, x, y, crisp=True)

    solids = [
        Solid(bx(-0.44, 0.44, -0.4, 0.4, 0, 9), body),
        Solid(ring(0, 0, 0.42, 12, 9) + ring(0, 0, 0.42, 12, 11.5) + ring(0, 0, 0.39, 12, 14)
              + ring(0, 0, 0.33, 12, 16.5) + ring(0, 0, 0.24, 12, 19) + ring(0, 0, 0.12, 12, 21)
              + ring(0, 0, 0.04, 12, 22.5), dome),
        Solid(bx(0.16, 0.38, -0.36, -0.14, 0, 25), chimney),
    ]

    def paint(im):                                             # smoke from the chimney
        disc(im, CX + 10, CY - 30, 2.8, "grey_l")
        disc(im, CX + 14, CY - 37, 2.4, "white")
        disc(im, CX + 11, CY - 42, 1.6, "grey_l")

    return solids, paint


# ---------------------------------------------------------------- preserves jar (three-shelf rack, 9 jars)

def preserves_jar():
    def wood(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "tan"
        return lit(WOOD, n, x, y, crisp=True)

    def jar(cu, cv, z0, h, ramp):
        top = z0 + h - 1.4

        def mat(tag, n, u, v, z, x, y):
            s = side_of(n)
            if z > top or s == "T":                            # brown screw-on lid
                return lit(WOOD, n, x, y, crisp=True)
            if z0 + 2.6 < z < z0 + 3.8:                        # thin paper label
                return "cream"
            if z > top - 0.8:
                return "white"                                 # glass shoulder
            return lit(ramp, n, x, y, crisp=True)
        return Solid(ring(cu, cv, 0.21, 10, z0) + ring(cu, cv, 0.21, 10, top)
                     + ring(cu, cv, 0.24, 10, z0 + h), mat)

    solids = []
    for pu in (-0.6, 0.6):
        for pv in (-0.3, 0.3):
            solids.append(Solid(bx(pu - 0.035, pu + 0.035, pv - 0.035, pv + 0.035, 0, 31), wood))
    for z0 in (0.0, 11.0, 22.0):
        solids.append(Solid(bx(-0.62, 0.62, -0.32, 0.32, z0, z0 + 1.6), wood))
    jam = ["red_d", "red", "red", "pink"]
    pickle = ["green_dd", "green_d", "green", "green"]
    for cu in (-0.44, 0.0, 0.44):
        solids.append(jar(cu, 0.0, 1.6, 6.6, jam))
        solids.append(jar(cu, 0.0, 12.6, 6.6, pickle))
        solids.append(jar(cu, 0.0, 23.6, 6.6, jam))
    return solids, None


# ---------------------------------------------------------------- beehive (two white boxes, 30 x 29)

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
            return "grey"                                      # joint between the boxes
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


# ---------------------------------------------------------------- fruit press (grape barrel and screw press)

def fruit_press():
    BCU, BCV = -0.2, 0.2                                       # barrel axis
    TU0, TU1, TV0, TV1 = 0.14, 0.46, -0.36, 0.06               # grape tub

    def barrel(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            if math.hypot(u - BCU, v - BCV) < 0.27:            # grape mash
                return GRAPE[(int(x) * 3 + int(y) * 5) % 5]
            return "brown_l"
        if 2.0 < z < 3.4 or 13.4 < z < 14.8:                   # iron hoops
            return "grey_d"
        th = math.atan2(v - BCV, u - BCU) / (2 * math.pi)
        if (th * 14) % 1.0 < 0.12:                             # stave seams
            return "brown_d"
        return lit(WOOD, n, x, y, crisp=True)

    def tub(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            if TU0 + 0.05 < u < TU1 - 0.05 and TV0 + 0.05 < v < TV1 - 0.05:
                return GRAPE[(int(x) * 5 + int(y) * 3) % 5]
            return "tan"
        if (v * 5.0) % 1.0 < 0.14 if side_of(n) == "L" else (u * 5.0) % 1.0 < 0.14:
            return "brown_d"                                   # plank joints
        return lit(WOOD, n, x, y, crisp=True)

    def timber(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "tan"
        return lit(WOOD, n, x, y, crisp=True)

    def iron(tag, n, u, v, z, x, y):
        return lit(SLATE, n, x, y, crisp=True)

    solids = [
        Solid(ring(BCU, BCV, 0.34, 12, 0) + ring(BCU, BCV, 0.38, 12, 9) + ring(BCU, BCV, 0.34, 12, 18), barrel),
        Solid(bx(TU0, TU1, TV0, TV1, 0, 12), tub),                                # grape tub
        Solid(bx(0.25, 0.34, -0.46, -0.36, 0, 27), timber),                        # press posts
        Solid(bx(0.25, 0.34, 0.1, 0.2, 0, 27), timber),
        Solid(bx(0.24, 0.35, -0.48, 0.2, 26, 30), timber),                         # beam
        Solid(bx(0.16, 0.44, -0.34, 0.04, 14, 15.5), timber),                     # press plate
        Solid(bx(0.27, 0.33, -0.2, -0.14, 15.5, 27), iron),                        # screw
    ]

    def paint(im):                                             # a bunch of grapes on the barrel lid
        for X, Y, c in ((-9, -19, "purple"), (-6, -21, "magenta"), (-3, -19, "purple"), (-7, -17, "magenta")):
            disc(im, CX + X, CY + Y, 1.8, c)

    return solids, paint


# ---------------------------------------------------------------- seed maker (cabinet, round hopper, packets)

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
            if math.hypot(u, v) < 0.3:                         # open mouth, seeds inside
                return "amber" if (int(x) * 3 + int(y) * 5) % 7 == 0 else ("tan" if (int(x) + int(y)) % 3 == 0 else "brown_d")
            return "grey_l"
        return lit(SLATE, n, x, y, crisp=True)

    def packet(ramp):
        def mat(tag, n, u, v, z, x, y):
            if side_of(n) == "T" or 9.0 < z < 11.5:
                return "cream"
            return lit(ramp, n, x, y, crisp=True)
        return mat

    solids = [
        Solid(bx(-0.4, 0.4, -0.34, 0.34, 0, 14), cabinet),
        Solid(ring(0, 0, 0.42, 12, 26) + ring(0, 0, 0.15, 12, 14), hopper),
        Solid(bx(0.42, 0.5, -0.26, -0.1, 6, 14), packet(["red_d", "red", "red", "pink"])),
        Solid(bx(0.42, 0.5, -0.06, 0.1, 6, 15), packet(["green_dd", "green_d", "green", "green"])),
        Solid(bx(0.42, 0.5, 0.14, 0.3, 6, 13), packet(["orange", "amber", "amber", "yellow"])),
    ]
    return solids, None


# ---------------------------------------------------------------- oil press (millstone wheel, oil jug)

def oil_press():
    WCU, WZ, WR = -0.12, 16.0, 12.5

    def wheel_face(tag, n, u, v, z, x, y):
        if abs(n[1]) < 0.8:
            return "grey_d"                                    # thickness of the wheel
        rr = math.hypot((u - WCU) * 16.0, z - WZ)
        if rr > WR - 1.8:
            return "grey_l" if (z - WZ) > -4.0 else "grey"     # rim band
        if rr < 2.6:
            return "brown_d"                                   # axle hole
        ang = math.atan2(z - WZ, (u - WCU) * 16.0) / (2 * math.pi)
        if (ang * 8) % 1.0 < 0.12:                             # radial grooves
            return "grey_d"
        return lit(["grey_d", "grey", "grey", "grey_l"], n, x, y, crisp=True)

    def jug(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "amber"                                     # oil surface
        if 6.0 < z < 7.4:
            return "cream"                                     # paper label
        return lit(["brown", "rust", "clay", "tan"], n, x, y, crisp=True)   # earthenware, not a flame

    def cork(tag, n, u, v, z, x, y):
        return "brown_d"

    def hub(tag, n, u, v, z, x, y):
        return lit(WOOD, n, x, y, crisp=True)

    # squat bottle: a stack of frustums (the hull of a concave profile would fill in the neck)
    JUG = [(0.0, 0.15), (5.0, 0.17), (9.0, 0.16), (12.0, 0.11), (13.5, 0.07), (15.5, 0.07)]

    def plinth(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "grey_l"
        return lit(STONE, n, x, y)

    wheel = []
    for v in (-0.1, 0.1):
        wheel += [(WCU + (WR / 16.0) * math.cos(2 * math.pi * i / 24), v, WZ + WR * math.sin(2 * math.pi * i / 24))
                  for i in range(24)]
    solids = [
        Solid(bx(-0.42, 0.42, -0.36, 0.36, 0, 3), plinth),
        Solid(wheel, wheel_face),
        Solid([(WCU + 0.2 * math.cos(2 * math.pi * i / 10), vv, WZ + 4.0 * math.sin(2 * math.pi * i / 10))
               for vv in (0.10, 0.22) for i in range(10)], hub),                     # axle hub on the wheel
        *[Solid(ring(0.26, 0.2, ra, 12, za) + ring(0.26, 0.2, rb, 12, zb), jug)
          for (za, ra), (zb, rb) in zip(JUG, JUG[1:])],
        Solid(ring(0.26, 0.2, 0.08, 12, 15.5) + ring(0.26, 0.2, 0.08, 12, 17.5), cork),   # cork
    ]
    return solids, None


# ---------------------------------------------------------------- market cart (hand cart, striped awning)

def market_cart():
    def wood(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "tan"
        if int(math.floor(v * 10)) % 3 == 0 and side_of(n) == "R":
            return "brown_d"
        return lit(WOOD, n, x, y, crisp=True)

    def wheel(tag, n, u, v, z, x, y):
        if abs(n[1]) < 0.8:
            return "brown_d"
        rr = math.hypot(u * 16.0, z - 6.0)
        if rr > 5.4:
            return "brown_d"
        if rr < 1.5:
            return "grey_d"
        ang = math.atan2(z - 6.0, u * 16.0) / (2 * math.pi)
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

    def apple(tag, n, u, v, z, x, y):
        return lit(["red_d", "red", "red", "pink"], n, x, y, crisp=True)

    def corn(tag, n, u, v, z, x, y):
        return lit(["brown", "amber", "yellow", "white"], n, x, y, crisp=True)

    def wheel_pts(v):
        return [(0.375 * math.cos(2 * math.pi * i / 14), v, 6.0 + 6.0 * math.sin(2 * math.pi * i / 14))
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
        Solid(ring(-0.14, -0.06, 0.06, 8, 14) + ring(-0.14, -0.06, 0.05, 8, 16.5), apple),
        Solid(ring(-0.06, -0.14, 0.06, 8, 14) + ring(-0.06, -0.14, 0.05, 8, 16.5), apple),
        Solid(ring(-0.2, 0.08, 0.06, 8, 14) + ring(-0.2, 0.08, 0.05, 8, 16.5), apple),
        Solid(bx(0.06, 0.28, -0.2, 0.2, 10, 13), wood),                       # corn crate
        Solid(ring(0.17, 0.0, 0.12, 8, 13) + [(0.17, 0.0, 18.0)], corn),
    ]

    def paint(im):                                             # a little coin sign on a bracket
        disc(im, CX + 15, CY - 14, 2.8, "amber")
        disc(im, CX + 15, CY - 14, 1.9, "yellow")
        put(im, CX + 14, CY - 13, "brown")

    return solids, paint


# ---------------------------------------------------------------- harvest shed (wood walls, red roof)

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
        if (int(z // 2) + int(math.floor(math.atan2(v, u) * 6.0 / math.pi))) % 2:
            return "tan"
        return "brown_l"

    def wheat(tag, n, u, v, z, x, y):
        return lit(["amber", "amber", "yellow", "cream"], n, x, y, crisp=True)

    def carrot(tag, n, u, v, z, x, y):
        return lit(["rust", "orange", "orange", "amber"], n, x, y, crisp=True)

    bA, bB = (-0.27, 0.42), (0.27, 0.42)
    solids = [
        Solid(bx(-0.36, 0.36, -0.36, 0.36, 0, 13), wall),
        Solid([(-0.42, -0.4, 13), (0.42, -0.4, 13), (0.42, 0.4, 13), (-0.42, 0.4, 13),
               (-0.42, 0.0, 21.5), (0.42, 0.0, 21.5)], roof),
        Solid(ring(bA[0], bA[1], 0.15, 12, 0) + ring(bA[0], bA[1], 0.2, 12, 7), basket),
        Solid(ring(bA[0], bA[1], 0.19, 8, 7) + [(bA[0], bA[1], 13.0)], wheat),
        Solid(ring(bB[0], bB[1], 0.15, 12, 0) + ring(bB[0], bB[1], 0.2, 12, 7), basket),
        Solid(ring(bB[0], bB[1], 0.19, 8, 7) + [(bB[0], bB[1], 12.0)], carrot),
    ]

    def paint(im):                                             # scythe leaning on the right wall
        for Y in range(-21, 7):
            X = 11 + round((Y + 21) * 5 / 27.0)
            put(im, CX + X, CY + Y, "brown_l")
        blade = [(11, -21), (12, -22), (13, -22), (14, -22), (15, -21), (16, -20), (17, -19),
                 (18, -18), (19, -17), (19, -16), (18, -15)]
        for X, Y in blade:
            put(im, CX + X, CY + Y, "grey_l")
            put(im, CX + X, CY + Y + 1, "grey")

    return solids, paint


# ---------------------------------------------------------------- planter shed (green walls, seed sacks, can)

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
        if s == "L" and z > 13.0:                              # gable: cream trim, round vent
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
        if 7.0 < z < 8.4:
            return "brown"                                     # tie
        return lit(["tan_d", "tan", "cream", "white"], n, x, y, crisp=True)

    def can(tag, n, u, v, z, x, y):
        if side_of(n) == "T":
            return "blue_d"
        if 1.5 < z < 2.6:
            return "grey"
        return lit(["blue_d", "blue", "blue", "cyan"], n, x, y, crisp=True)

    def sack_pts(cu, cv):
        return (ring(cu, cv, 0.12, 10, 0) + ring(cu, cv, 0.15, 10, 4)
                + ring(cu, cv, 0.12, 10, 7.5) + ring(cu, cv, 0.05, 10, 9.5))

    solids = [
        Solid(bx(-0.38, 0.36, -0.36, 0.38, 0, 13), wall),
        Solid([(-0.44, -0.42, 13), (0.44, -0.42, 13), (0.44, 0.42, 13), (-0.44, 0.42, 13),
               (0.0, -0.42, 21.0), (0.0, 0.42, 21.0)], roof),
        Solid(sack_pts(-0.14, 0.46), sack),
        Solid(sack_pts(0.14, 0.44), sack),
        Solid(ring(0.36, 0.12, 0.12, 10, 0) + ring(0.36, 0.12, 0.12, 10, 8), can),
        Solid(bx(0.4, 0.52, 0.12, 0.2, 2.0, 4.0), can),                    # spout
    ]
    return solids, None


# All of these are hand-pixeled in farm_hand.py now (art round 5); the ray-cast models stay for
# comparison.
SPRITES = []


def main():
    s = Sprites(sys.argv[1], "buildings")
    for name, fn in SPRITES:
        solids, paint = fn()
        emit(s, name, solids, paint)
    s.done()


main()
