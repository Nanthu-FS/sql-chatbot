"""Farm buildings (pixel-direct, folder "buildings"): barn, silo, market stall, orders notice
board, well and workbench. Each also gets a <name>_snow winter variant (snow on roofs and tops).

Projection is the 2:1 tile grid: a world point (u, v, z) lands at X = (u - v) * 16 and
Y = (u + v) * 8 - z, so one tile is 32 px wide and z is in pixels. +u runs down-right and +v
down-left, so the visible faces are the top (T), the front-left face (+v, tag L) and the
front-right face (+u, tag R). Each building is a union of convex solids (boxes, prisms, gable
roofs, cones) written as half-space constraints n.X <= d. Every screen pixel is ray-cast through
them exactly (the view ray is (1, 1, 16) in these units), so slopes and edges stay crisp.
Light comes from the top-left; finish() then adds the usual 1px ink outline and hard drop shadow.

Pivot = the ground centre of the footprint (local (0, 0, 0), where the engine puts the tile centre).
That is 8 px above the bottom corner of a 1x1 footprint and 16 px above the bottom corner of the
2x2 barn footprint, i.e. bottom-centre minus the depth of the base.
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, Sprites, new, finish  # noqa: E402
import math  # noqa: E402

W, H = 96, 96          # working canvas
CX, CY = 48, 72        # ground-centre pixel on the canvas (the pivot)


def col(c):
    return E[c] if isinstance(c, str) else c


def unit(n):
    m = math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
    return (n[0] / m, n[1] / m, n[2] / m)


LIGHT = unit((-1.0, 0.45, 1.0))   # direction toward the light: up and to the left on screen


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


def lit(ramp, n, x, y, bias=0.0, crisp=False):
    """Colour for a surface with unit normal n: lambert against LIGHT, mapped onto the ramp.
    crisp=True gives flat bands (no dither), used on the faceted cylinders."""
    nu = unit(n)
    i = nu[0] * LIGHT[0] + nu[1] * LIGHT[1] + nu[2] * LIGHT[2]
    v = ((i + 1.0) / 2.0 + bias) * (len(ramp) - 1)
    if crisp:
        return ramp[max(0, min(len(ramp) - 1, int(round(v))))]
    return tone(ramp, v, x, y)


def seam(t, step=4.0):
    """True on the one-pixel joint line of a board or plank run along t."""
    f = (t * step) % 1.0
    return f < 0.12 or f > 0.88


def shingle(u, z, base, seam_c, hi):
    """Roof shingle courses (3 px rows, staggered joints). u runs along the roof, z up the slope."""
    row = int(math.floor(z / 3.0))
    f = z - row * 3.0
    j = (u * 4.0 + (row % 2) * 0.5) % 1.0
    if f < 0.9 or j < 0.1 or j > 0.9:
        return seam_c
    if f < 1.6:
        return hi
    return base


def const(c):
    return lambda tag, n, u, v, z, x, y: c


# ---------------------------------------------------------------- convex solids

def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def plane3(p1, p2, p3, inside, tag):
    """Constraint n.X <= d for the plane through three points, with n facing away from `inside`."""
    n = cross(sub(p2, p1), sub(p3, p1))
    if dot(n, sub(inside, p1)) > 0:
        n = (-n[0], -n[1], -n[2])
    n = unit(n)
    return (n, dot(n, p1), tag)


def box(u0, u1, v0, v1, z0, z1, R="R", L="L", T="T"):
    return [((1.0, 0.0, 0.0), u1, R), ((-1.0, 0.0, 0.0), -u0, "K"),
            ((0.0, 1.0, 0.0), v1, L), ((0.0, -1.0, 0.0), -v0, "K"),
            ((0.0, 0.0, 1.0), z1, T), ((0.0, 0.0, -1.0), -z0, "K")]


def circle(r, n, cu=0.0, cv=0.0, rot=0.0):
    return [(cu + r * math.cos(rot + 2 * math.pi * i / n), cv + r * math.sin(rot + 2 * math.pi * i / n))
            for i in range(n)]


def prism(poly, z0, z1, side="S", top="T"):
    """Convex polygon (counter-clockwise in u, v) extruded from z0 up to z1."""
    cons = []
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        ex, ey = x1 - x0, y1 - y0
        m = math.hypot(ex, ey)
        nx, ny = ey / m, -ex / m
        cons.append(((nx, ny, 0.0), nx * x0 + ny * y0, "%s%d" % (side, i)))
    cons.append(((0.0, 0.0, 1.0), z1, top))
    cons.append(((0.0, 0.0, -1.0), -z0, "K"))
    return cons


def cone(poly, zb, apex, cu=0.0, cv=0.0, side="C"):
    """Convex polygon base at height zb with the apex straight above (cu, cv)."""
    inside = (cu, cv, zb + 0.2 * (apex - zb))
    A = (cu, cv, apex)
    cons = []
    for i in range(len(poly)):
        p, q = poly[i], poly[(i + 1) % len(poly)]
        cons.append(plane3((p[0], p[1], zb), (q[0], q[1], zb), A, inside, "%s%d" % (side, i)))
    cons.append(((0.0, 0.0, -1.0), -zb, "K"))
    return cons


def gable(cu, cv, du, dv, hl, hw, ze, pk, A="A", B="B", E1="E", E2="K"):
    """Gable roof: ridge through (cu, cv) along unit (du, dv), half-length hl, eaves at +-hw
    across the ridge at height ze, ridge pk above. A is the +across slope, B the -across slope."""
    nu, nv = -dv, du

    def P(s, a, z):
        return (cu + s * du + a * nu, cv + s * dv + a * nv, z)

    inside = (cu, cv, ze + pk * 0.25)
    return [
        plane3(P(-hl, -hw, ze), P(-hl, hw, ze), P(-hl, 0.0, ze + pk), inside, E2),
        plane3(P(hl, -hw, ze), P(hl, hw, ze), P(hl, 0.0, ze + pk), inside, E1),
        plane3(P(-hl, hw, ze), P(hl, hw, ze), P(0.0, 0.0, ze + pk), inside, A),
        plane3(P(-hl, -hw, ze), P(hl, -hw, ze), P(0.0, 0.0, ze + pk), inside, B),
        ((0.0, 0.0, -1.0), -ze, "K"),
    ]


class Solid:
    def __init__(self, cons, mat):
        self.cons = cons      # [(unit outward normal, d, tag)] with n.X <= d
        self.mat = mat        # fn(tag, normal, u, v, z, x, y) -> palette name / RGBA


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
    im = new(W, H)
    px = im.load()
    for y in range(H):
        Y = y - CY
        for x in range(W):
            D = (x - CX) / 16.0
            best = None
            for s in solids:
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


def emit(sp, name, solids):
    im = render(solids)
    x0, y0, x1, y1 = im.getbbox()
    body = im.crop((x0, y0, x1, y1))
    out, pad = finish(body)
    sp.save(name, out, CX - x0 + pad, CY - y0 + pad)
    sys.stderr.write("%s: body %dx%d\n" % (name, body.width, body.height))


# ---------------------------------------------------------------- barn (2x2 footprint)

def barn(winter):
    WH, PK = 18.0, 15.0           # wall height, roof peak above the eaves
    RIDGE = WH + PK

    def walls(tag, n, u, v, z, x, y):
        if tag == "L":                                   # front-left wall, +v
            if z < 2.0:
                return "grey_d" if (x + y) % 2 else "grey"
            if z > WH - 1.5:
                return "cream"
            if abs(u) <= 0.4 and z <= 15.0:              # double barn door
                if abs(abs(u) - 0.4) < 0.05 or abs(z - 15.0) < 0.6:
                    return "cream"
                if abs(u) < 0.05:
                    return "red_d"
                t, s = (u + 0.4) / 0.8, z / 15.0
                if abs(t - s) < 0.08 or abs(t + s - 1.0) < 0.08:   # X brace
                    return "cream"
                return "red"
            return "red_d" if seam(u) else "red"
        if tag == "R":                                   # front-right wall, +u
            if z > WH - 1.5:
                return "tan"
            if z < 2.0:
                return "grey_d"
            if abs(v) <= 0.22 and 6.0 <= z <= 12.0:      # loft window
                if abs(abs(v) - 0.22) < 0.05 or abs(v) < 0.05 or z < 6.6 or z > 11.4:
                    return "cream"
                return "slate"
            return "purple" if seam(v) else "red_d"
        return "red_d"

    def roof(tag, n, u, v, z, x, y):
        if tag == "E":                                   # gable end, +u
            if z > WH + PK * (1.0 - abs(v)) - 1.2:
                return "cream"
            if abs(v) <= 0.2 and 20.0 <= z <= 26.0:      # hay hatch
                if abs(abs(v) - 0.2) < 0.05 or z < 20.6 or z > 25.4:
                    return "cream"
                return "brown_d" if int(z) % 3 else "brown"
            return "purple" if seam(v) else "red_d"
        if tag == "A":                                   # front slope, faces the viewer's left
            if winter:
                return shingle(u, z, "white", "grey_l", "white")
            return shingle(u, z, "grey_d", "slate", "grey")
        if tag == "B":                                   # back slope, seen above the ridge
            if winter:
                return shingle(u, z, "grey_l", "grey", "white")
            return shingle(u, z, "slate", "slate_d", "grey_d")
        return "slate"

    def cupola(tag, n, u, v, z, x, y):
        if tag == "cL":
            return "cream"
        if tag == "cR":
            return "tan"
        if tag.startswith("cc"):
            if winter:
                return lit(["grey", "grey_l", "white", "white"], n, x, y)
            return lit(["slate_d", "slate", "grey_d", "grey"], n, x, y)
        return "cream"

    return [
        Solid(box(-0.86, 0.86, -0.86, 0.86, 0.0, WH), walls),
        Solid(gable(0.0, 0.0, 1.0, 0.0, 0.94, 0.96, WH, PK), roof),
        Solid(box(-0.2, 0.2, -0.2, 0.2, RIDGE - 3.0, RIDGE + 6.0, R="cR", L="cL", T="cT"), cupola),
        Solid(cone(circle(0.3, 4, rot=math.pi / 4), RIDGE + 6.0, RIDGE + 16.0, side="cc"), cupola),
    ]


# ---------------------------------------------------------------- silo (1x1, tall tower)

def silo(winter):
    BRICK = ["purple", "red_d", "red", "rust", "clay"]
    METAL = ["slate", "grey_d", "grey", "grey_l", "white"]
    PATINA = ["teal_dd", "green_dd", "green_d", "green"]
    SNOWR = ["grey", "grey_l", "white", "white"]

    def ledge(tag, n, u, v, z, x, y):                    # brick base ring
        if tag == "T":
            return "white" if winter else "grey_l"
        if abs(u) < 0.16 and v > 0 and z < 6.0:          # cellar hatch, front-left
            return "cream" if (z < 0.6 or abs(abs(u) - 0.16) < 0.05) else "brown_d"
        row = int(z // 3.0)
        if z - row * 3.0 < 0.9:                          # mortar course
            return "tan"
        q = math.atan2(v, u) * 0.6 * 16.0 + (row % 2) * 3.0
        if q % 6.0 < 0.9:                                # head joint
            return "tan"
        return lit(BRICK, n, x, y, crisp=True)

    def wall(tag, n, u, v, z, x, y):                     # ribbed sheet metal
        if u > 0.4 and abs(v) < 0.1 and 9.0 < z < 38.0:  # ladder up the front-right
            if 0.05 < abs(v) < 0.09:
                return "brown_l"
            if z % 3.0 < 0.9:
                return "brown"
        if z % 6.0 < 0.9:
            return "grey_d"
        return lit(METAL, n, x, y, crisp=True)

    def cap(tag, n, u, v, z, x, y):                      # conical roof
        return lit(SNOWR, n, x, y, crisp=True) if winter else lit(PATINA, n, x, y, crisp=True)

    return [
        Solid(prism(circle(0.6, 14), 0.0, 8.0, side="S"), ledge),
        Solid(prism(circle(0.55, 14), 8.0, 40.0, side="W"), wall),
        Solid(cone(circle(0.66, 14), 40.0, 48.0, side="C"), cap),
        Solid(box(-0.03, 0.03, -0.03, 0.03, 47.0, 50.0), const("grey")),
    ]


# ---------------------------------------------------------------- market stall (1x1, awning)

PILE = {"tomato": ["red_d", "red", "red", "pink"],
        "carrot": ["rust", "orange", "orange", "amber"],
        "corn": ["brown", "amber", "yellow", "white"]}


def flat(ramp):
    return lambda tag, n, u, v, z, x, y: lit(ramp, n, x, y)


def market(winter):
    def counter(tag, n, u, v, z, x, y):
        if tag == "T":
            if winter:
                return "white" if (x + y) % 3 else "grey_l"
            return "brown_d" if seam(v) else "tan"
        if tag == "L":
            return "brown_d" if seam(u) else "brown_l"
        return "brown_d" if seam(v) else "brown"

    def crate(tag, n, u, v, z, x, y):
        if tag == "T":
            if winter:
                return "white"
            return "red" if (math.floor(u * 8) + math.floor(v * 8)) % 2 == 0 else "orange"
        if tag == "L":
            return "tan_d" if z % 2.5 < 0.9 else "tan"
        return "brown" if z % 2.5 < 0.9 else "brown_l"

    def post(tag, n, u, v, z, x, y):
        return "brown_d" if tag == "R" else ("tan_d" if tag == "T" else "brown")

    def canopy(tag, n, u, v, z, x, y):                   # red and cream awning stripes
        stripe = int(math.floor((u + 0.6) / 0.2)) % 2
        if tag == "CT":
            if winter:
                return "white" if stripe == 0 else "grey_l"
            return "red" if stripe == 0 else "cream"
        if tag == "CF":
            if z < 21.3:
                return "brown_d"
            return "red" if stripe == 0 else "cream"
        return "red_d" if stripe == 0 else "tan"

    canopy_cons = [((1.0, 0.0, 0.0), 0.6, "CR"), ((-1.0, 0.0, 0.0), 0.6, "K"),
                   ((0.0, 1.0, 0.0), 0.55, "CF"), ((0.0, -1.0, 0.0), 0.55, "K"),
                   ((0.0, 0.0, -1.0), -20.5, "K"),
                   plane3((0.0, -0.55, 26.5), (0.0, 0.55, 22.5), (1.0, 0.0, 24.5), (0.0, 0.0, 23.0), "CT")]
    solids = [Solid(canopy_cons, canopy)]
    for pu, pv in ((-0.38, -0.38), (0.38, -0.38), (-0.38, 0.38), (0.38, 0.38)):
        solids.append(Solid(box(pu - 0.04, pu + 0.04, pv - 0.04, pv + 0.04, 0.0, 20.5), post))
    solids.append(Solid(box(-0.42, 0.42, -0.3, 0.3, 0.0, 11.0), counter))
    solids.append(Solid(box(0.46, 0.74, -0.12, 0.16, 0.0, 7.0), crate))
    for cu, kind in ((-0.25, "tomato"), (0.02, "carrot"), (0.27, "corn")):
        solids.append(Solid(cone(circle(0.16, 8, cu, -0.08), 11.0, 16.5, cu, -0.08, side="P"),
                            flat(PILE[kind])))
    for i in range(6):                                   # scalloped valance
        cu = -0.5 + i * 0.2
        solids.append(Solid(prism(circle(0.1, 6, cu, 0.45), 19.3, 20.5, side="Q"),
                            flat(["red_d", "red", "red", "pink"] if i % 2 == 0 else ["tan", "cream", "cream", "white"])))
    return solids


# ---------------------------------------------------------------- orders notice board (1x1)

# (u0, u1, z0, z1, paper colour, has an exclamation mark)
PAPERS = [(-0.32, -0.08, 16.5, 22.0, "cream", False), (0.02, 0.28, 18.0, 23.0, "white", False),
          (-0.28, -0.06, 9.5, 14.5, "white", False), (0.06, 0.3, 9.0, 13.5, "cream", True)]


def orders(winter):
    def board(tag, n, u, v, z, x, y):
        if tag == "T":
            return "white" if winter else "brown_l"
        if tag == "R":
            return "brown_d"
        if abs(u) > 0.36 or z < 7.2 or z > 23.2:         # wooden frame
            return "brown"
        for u0, u1, z0, z1, c, mark in PAPERS:
            if u0 <= u <= u1 and z0 <= z <= z1:
                mu = (u0 + u1) / 2.0
                if abs(u - mu) < 0.05 and z > z1 - 1.0:
                    return "red"                          # pin
                if mark and abs(u - mu) < 0.05 and z0 + 1.5 < z < z1 - 1.5:
                    return "red"                          # exclamation mark
                if int(z - z0) in (2, 4) and u0 + 0.05 < u < u1 - 0.05:
                    return "grey_d"                       # ink lines
                return c
        return "tan_d" if (math.floor(u * 16) * 5 + math.floor(z) * 7) % 9 == 0 else "tan"

    def post(tag, n, u, v, z, x, y):
        return "brown_d" if tag == "R" else ("tan_d" if tag == "T" else "brown")

    def roof(tag, n, u, v, z, x, y):
        if tag == "A":
            if winter:
                return shingle(u, z, "white", "grey_l", "white")
            return shingle(u, z, "brown_l", "brown", "tan")
        if tag == "B":
            if winter:
                return shingle(u, z, "grey_l", "grey", "white")
            return shingle(u, z, "brown", "brown_d", "brown_l")
        return "brown"

    solids = [Solid(box(-0.42, 0.42, -0.02, 0.06, 7.0, 23.5), board)]
    for pu in (-0.36, 0.36):
        solids.append(Solid(box(pu - 0.04, pu + 0.04, -0.12, -0.02, 0.0, 23.5), post))
    solids.append(Solid(gable(0.0, 0.0, 1.0, 0.0, 0.52, 0.46, 23.5, 6.5), roof))
    return solids


# ---------------------------------------------------------------- stone well (1x1)

def well(winter):
    STONE = ["slate", "grey_d", "grey", "grey_l", "white"]
    d = (math.sqrt(0.5), -math.sqrt(0.5))        # ridge axis: screen-horizontal
    n2 = (math.sqrt(0.5), math.sqrt(0.5))

    def ring(tag, n, u, v, z, x, y):
        if tag == "T":
            r2 = u * u + v * v
            if r2 < 0.13:                         # the water inside the rim
                if winter:
                    return "white" if (x * 3 + y) % 7 == 0 else "cyan"
                if r2 > 0.10:
                    return "grey_l"
                if (x * 3 + y * 5) % 11 == 0:
                    return "cyan"
                return "blue" if (x + y) % 2 else "blue_d"
            return "white" if winter and (x + y) % 3 else ("grey_l" if (x + y) % 3 else "grey")
        q = math.atan2(v, u) * 0.56 * 16.0 + (int(z // 3.0) % 2) * 2.5
        if z % 3.0 < 0.9 or q % 5.0 < 0.9:          # mortar
            return "grey_d"
        return lit(STONE, n, x, y)

    def wood(tag, n, u, v, z, x, y):
        if winter and tag == "T":
            return "white"
        return "brown_d" if tag == "R" else ("tan" if tag == "T" else "brown")

    def roof(tag, n, u, v, z, x, y):
        if tag == "A":
            if winter:
                return shingle(u, z, "white", "grey_l", "white")
            return shingle(u, z, "brown_l", "brown", "tan")
        if tag == "B":
            if winter:
                return shingle(u, z, "grey_l", "grey", "white")
            return shingle(u, z, "brown", "brown_d", "brown_l")
        return "brown"

    def bucket(tag, n, u, v, z, x, y):
        if tag == "T":
            return "brown_d"
        if z % 3.0 < 0.9:
            return "grey_d"                        # iron hoops
        return lit(["brown_d", "brown", "brown_l", "tan"], n, x, y)

    beam = [(-0.44 * d[0] - 0.03 * n2[0], -0.44 * d[1] - 0.03 * n2[1]),
            (0.44 * d[0] - 0.03 * n2[0], 0.44 * d[1] - 0.03 * n2[1]),
            (0.44 * d[0] + 0.03 * n2[0], 0.44 * d[1] + 0.03 * n2[1]),
            (-0.44 * d[0] + 0.03 * n2[0], -0.44 * d[1] + 0.03 * n2[1])]
    solids = [Solid(prism(circle(0.62, 14), 0.0, 9.0, side="S"), ring)]
    for pu, pv in ((0.3, -0.3), (-0.3, 0.3)):
        solids.append(Solid(box(pu - 0.04, pu + 0.04, pv - 0.04, pv + 0.04, 9.0, 21.0), wood))
    solids.append(Solid(prism(beam, 18.5, 20.0, side="B"), wood))
    solids.append(Solid(box(-0.02, 0.02, -0.02, 0.02, 15.5, 18.5), wood))     # rope
    solids.append(Solid(prism(circle(0.13, 6), 9.8, 15.5, side="Bu"), bucket))  # hangs over the water
    solids.append(Solid(gable(0.0, 0.0, d[0], d[1], 0.52, 0.5, 21.0, 6.0), roof))
    return solids


# ---------------------------------------------------------------- workbench (1x1, with tools)

def workbench(winter):
    def top(tag, n, u, v, z, x, y):
        if tag == "T":
            if winter:
                return "white" if (x + y) % 4 else "grey_l"
            # tools lying flat on the bench top, drawn as decals in (u, v)
            if -0.56 <= u <= -0.2 and -0.1 <= v <= -0.04:              # hammer handle
                return "brown"
            if -0.27 <= u <= -0.13 and -0.22 <= v <= 0.0:              # hammer head
                return "slate" if (u < -0.22 or v > -0.02) else "grey_l"
            if -0.12 <= u <= 0.42 and -0.27 <= v <= -0.17:             # saw blade, toothed
                return "grey_l" if int(math.floor(u * 22)) % 2 == 0 else "grey"
            if 0.42 < u <= 0.56 and -0.31 <= v <= -0.12:               # saw handle
                return "red"
            if -0.5 <= u <= -0.32 and 0.1 <= v <= 0.18:                # screwdriver grip
                return "red_d"
            if -0.32 < u <= -0.02 and 0.12 <= v <= 0.15:               # screwdriver shaft
                return "grey"
            return "brown" if seam(v) else "tan"
        if tag == "L":
            return "brown_d" if seam(u, 3.0) else "brown_l"
        return "brown_d" if seam(v, 3.0) else "brown"

    def leg(tag, n, u, v, z, x, y):
        return "brown_d" if tag == "R" else ("tan_d" if tag == "T" else "brown")

    def apron(tag, n, u, v, z, x, y):
        return "brown_d" if tag == "R" else "brown"

    def vice(tag, n, u, v, z, x, y):
        if tag == "T":
            return "white" if winter else "grey"
        if tag == "L":
            return "brown" if abs(u - 0.47) < 0.02 else "slate"
        return "grey_d"

    solids = [
        Solid(box(-0.8, 0.8, -0.26, 0.26, 9.0, 11.0), top),
        Solid(box(0.38, 0.62, 0.18, 0.34, 11.0, 17.0), vice),
    ]
    for pu in (-0.72, 0.72):
        for pv in (-0.2, 0.2):
            solids.append(Solid(box(pu - 0.045, pu + 0.045, pv - 0.045, pv + 0.045, 0.0, 9.0), leg))
    solids.append(Solid(box(-0.74, 0.74, 0.16, 0.21, 6.0, 9.0), apron))
    solids.append(Solid(box(-0.74, 0.74, -0.21, -0.16, 6.0, 9.0), apron))
    return solids


BUILDINGS = [("barn", barn), ("silo", silo), ("market", market), ("orders", orders),
             ("well", well), ("workbench", workbench)]


def main():
    s = Sprites(sys.argv[1], "buildings")
    for name, fn in BUILDINGS:
        emit(s, name, fn(False))
        emit(s, name + "_snow", fn(True))
    s.done()


main()
