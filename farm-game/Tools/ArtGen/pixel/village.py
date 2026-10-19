"""Village and field props (pixel-direct): farm vehicles (a green harvester tractor pulling a crate
trailer, and a red pickup truck, each heading south-east or north-east with two wheel frames, plus the
truck's produce load), three village cottages (thatch, brick and siding), a plank bridge, two signposts
and a blank field sign, two crates, a hay bale and a water trough.

Projection and ray-casting are the same as buildings.py: a world point (u, v, z) lands at
X = (u - v) * 16 and Y = (u + v) * 8 - z, so one tile is 32 px wide and z is in pixels. +u runs
down-right and +v down-left; the visible faces are the top (Zp), the +u face (Xp) and the +v face (Yp).
Each object is a union of convex solids written as half-space constraints n.X <= d, and every screen
pixel is ray-cast exactly. Light comes from the top-left; finish() adds the 1px ink outline and drop shadow.

Vehicles are modelled in their own frame (x along the heading, y across) and rotated into the world for
the other heading, so both directions share one drawing. Pivots: vehicles, cottages and the bridge sit on
the ground centre (0, 0, 0) of their footprint; the bridge pivot is its centre on the water surface; the
small deco objects use their ground contact point.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, Sprites, new, finish  # noqa: E402

CANVAS = 112           # working canvas in px
CX = CY = 56           # ground centre on the canvas (the pivot)
TAU = 2 * math.pi


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
    """Lambert shading of unit normal n against LIGHT, mapped onto a dark-to-light ramp."""
    nu = unit(n)
    i = nu[0] * LIGHT[0] + nu[1] * LIGHT[1] + nu[2] * LIGHT[2]
    v = ((i + 1.0) / 2.0) * (len(ramp) - 1)
    if crisp:
        return ramp[max(0, min(len(ramp) - 1, int(round(v))))]
    return tone(ramp, v, x, y)


def seam(t, step=4.0):
    """True on the one-pixel joint line of a board run along t."""
    f = (t * step) % 1.0
    return f < 0.12 or f > 0.88


def shingle(u, z, base, seam_c, hi):
    """Roof shingle courses (3 px rows, staggered joints). u runs along the ridge, z up the slope."""
    row = int(math.floor(z / 3.0))
    f = z - row * 3.0
    j = (u * 4.0 + (row % 2) * 0.5) % 1.0
    if f < 0.9 or j < 0.1 or j > 0.9:
        return seam_c
    if f < 1.6:
        return hi
    return base


# ---------------------------------------------------------------- convex solids (n.X <= d)

def plane3(p1, p2, p3, inside, tag):
    """Constraint for the plane through three points, normal pointing away from `inside`."""
    a = (p2[0] - p1[0], p2[1] - p1[1], p2[2] - p1[2])
    b = (p3[0] - p1[0], p3[1] - p1[1], p3[2] - p1[2])
    n = (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    w = (inside[0] - p1[0], inside[1] - p1[1], inside[2] - p1[2])
    if n[0] * w[0] + n[1] * w[1] + n[2] * w[2] > 0:
        n = (-n[0], -n[1], -n[2])
    n = unit(n)
    return (n, n[0] * p1[0] + n[1] * p1[1] + n[2] * p1[2], tag)


def box(x0, x1, y0, y1, z0, z1):
    return [((1.0, 0.0, 0.0), x1, "Xp"), ((-1.0, 0.0, 0.0), -x0, "Xn"),
            ((0.0, 1.0, 0.0), y1, "Yp"), ((0.0, -1.0, 0.0), -y0, "Yn"),
            ((0.0, 0.0, 1.0), z1, "Zp"), ((0.0, 0.0, -1.0), -z0, "Zn")]


def circle(r, n, cu=0.0, cv=0.0):
    return [(cu + r * math.cos(TAU * i / n), cv + r * math.sin(TAU * i / n)) for i in range(n)]


def extrude(poly, ia, ib, ic, c0, c1, lo="Zn", hi="Zp", side="S"):
    """Convex polygon (counter-clockwise) in the world axes (ia, ib), extruded along axis ic from
    c0 to c1. Side faces are tagged side+index; the low and high caps get the lo and hi tags.
    Axis indices: 0 = u (or local x), 1 = v (or local y), 2 = z."""
    cons = []
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        ex, ey = x1 - x0, y1 - y0
        m = math.hypot(ex, ey)
        n = [0.0, 0.0, 0.0]
        n[ia], n[ib] = ey / m, -ex / m
        cons.append((tuple(n), n[ia] * x0 + n[ib] * y0, "%s%d" % (side, i)))
    n = [0.0, 0.0, 0.0]
    n[ic] = 1.0
    cons.append((tuple(n), c1, hi))
    n = [0.0, 0.0, 0.0]
    n[ic] = -1.0
    cons.append((tuple(n), -c0, lo))
    return cons


def gable(du, dv, hl, hw, ze, pk, A="A", B="B", E1="E", E2="K"):
    """Gable roof centred on the origin, ridge along unit (du, dv), half-length hl, eaves at +-hw
    across the ridge at height ze, ridge pk above. A is the +across slope, B the -across slope."""
    nu, nv = -dv, du

    def P(s, a, z):
        return (s * du + a * nu, s * dv + a * nv, z)

    inside = (0.0, 0.0, ze + pk * 0.25)
    return [
        plane3(P(-hl, -hw, ze), P(-hl, hw, ze), P(-hl, 0.0, ze + pk), inside, E2),
        plane3(P(hl, -hw, ze), P(hl, hw, ze), P(hl, 0.0, ze + pk), inside, E1),
        plane3(P(-hl, hw, ze), P(hl, hw, ze), P(0.0, 0.0, ze + pk), inside, A),
        plane3(P(-hl, -hw, ze), P(hl, -hw, ze), P(0.0, 0.0, ze + pk), inside, B),
        ((0.0, 0.0, -1.0), -ze, "K"),
    ]


def rotate_cons(cons, f, g):
    """Local constraints (x along f, y along g) to world (u, v): the normal's (u, v) part is
    nx * f + ny * g, z and d are unchanged."""
    out = []
    for n, d, t in cons:
        out.append(((n[0] * f[0] + n[1] * g[0], n[0] * f[1] + n[1] * g[1], n[2]), d, t))
    return out


class Solid:
    def __init__(self, cons, mat, frame=None):
        self.cons = cons      # [(unit outward normal, d, tag)] with n.X <= d, in world (u, v, z)
        self.mat = mat        # fn(tag, normal, a, b, z, x, y) -> palette name or RGBA
        self.frame = frame    # (f, g) when the material should see local (a, b) = (f.X, g.X)


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
    im = new(CANVAS, CANVAS)
    px = im.load()
    for y in range(CANVAS):
        Y = y - CY
        for x in range(CANVAS):
            D = (x - CX) / 16.0
            best = None
            for s in solids:
                h = hit(s.cons, D, Y)
                if h is None or h[0] < -1e-6:
                    continue
                if best is None or h[0] >= best[0][0] - 1e-9:
                    best = (h, s)      # the nearest surface along the ray wins
            if best is None:
                continue
            (z, tag, nrm), s = best
            u = (Y + z) / 16.0 + D / 2.0
            v = (Y + z) / 16.0 - D / 2.0
            if s.frame:
                f, g = s.frame
                a, b = f[0] * u + f[1] * v, g[0] * u + g[1] * v
            else:
                a, b = u, v
            px[x, y] = col(s.mat(tag, nrm, a, b, z, x, y))
    return im


def emit(sp, name, solids):
    im = render(solids)
    bb = im.getbbox()
    if bb is None:
        raise SystemExit("empty render: " + name)
    x0, y0, x1, y1 = bb
    out, pad = finish(im.crop(bb))
    sp.save(name, out, CX - x0 + pad, CY - y0 + pad)
    sys.stderr.write("%s: body %dx%d\n" % (name, x1 - x0, y1 - y0))


HEADING = {"se": (1.0, 0.0), "ne": (0.0, -1.0)}   # local +x (forward) in world (u, v)
PHASE = (0.0, math.pi / 5)                        # spoke rotation per frame (half a spoke)

INK = ["ink", "slate_d", "slate", "grey_d"]
TIRE = ["ink", "slate_d", "slate", "grey_d"]
GREEN = ["green_dd", "green_d", "green", "yellow"]
RED = ["purple", "red_d", "red", "pink"]
WOOD = ["brown_d", "brown", "brown_l", "tan"]
CRATE = ["brown_d", "brown", "brown_l", "tan"]
GLASS = ["slate", "blue_d", "blue", "cyan"]
PLASTER = ["tan", "cream", "cream", "white"]
BRICK = ["red_d", "rust", "red", "clay"]
STONE = ["grey_d", "grey", "grey_l", "white"]


def dark_mat(tag, n, a, b, z, x, y):
    return lit(INK, n, x, y)


def lamp_mat(tag, n, a, b, z, x, y):
    return "yellow"


def tail_mat(tag, n, a, b, z, x, y):
    return "red"


def wood_mat(tag, n, a, b, z, x, y):
    if tag in ("Xp", "Xn", "Yp", "Yn") and z % 2.2 < 0.6:
        return "brown_d"                               # board joints
    return lit(WOOD, n, x, y)


def crate_mat(tag, n, a, b, z, x, y):
    if tag == "Zp":
        return "brown" if seam(a, 3.0) else "tan"
    if z % 1.8 < 0.6:
        return "brown_d"                               # slats
    return lit(CRATE, n, x, y)


def grille_mat(a_or_b, z, lo, hi):
    return lambda b: None


def gshade(tag, n, x, y):
    """Green body paint with flat faces: top light, left side mid, right side dark."""
    if tag == "Zp":
        return "green"
    if tag == "Yp":
        return "green_d"
    return "green_dd"


def wheel_mat(cx, cz, A, B, ph):
    """Wheel with a dark tyre ring, a slate disc with four thin grey spokes and a small hub.
    Caps (Yp/Ym) carry the face, S* the tread."""
    def f(tag, n, a, b, z, x, y):
        if tag[0] == "S":
            return lit(TIRE, n, x, y)
        dx = (a - cx) / A
        dz = (z - cz) / B
        rr = math.hypot(dx, dz)
        if rr > 0.8:
            return "ink"
        if rr < 0.2:
            return "grey_l"
        th = math.atan2(dz, dx) - ph
        if math.cos(4.0 * th) > 0.9:
            return "grey"
        return "slate"
    return f


def wheels(cx, cz, A, B, y0, ph):
    """A pair of wheels (both sides), axis across the vehicle (along y), at local (x, z)."""
    pts = [(cx + A * math.cos(TAU * i / 10), cz + B * math.sin(TAU * i / 10)) for i in range(10)]
    out = []
    for sgn in (1, -1):
        c0, c1 = (y0, y0 + 0.1) if sgn == 1 else (-(y0 + 0.1), -y0)
        out.append((extrude(pts, 0, 2, 1, c0, c1, lo="Ym", hi="Yp"), wheel_mat(cx, cz, A, B, ph)))
    return out


def build(parts, heading):
    f = HEADING[heading]
    g = (-f[1], f[0])
    return [Solid(rotate_cons(cons, f, g), mat, frame=(f, g)) for cons, mat in parts]


# ---------------------------------------------------------------- harvester (green farm tractor + crate trailer)

def tractor_hood(tag, n, a, b, z, x, y):
    if tag == "Xp" and abs(b) < 0.14 and 5.8 < z < 8.6:      # radiator grille
        return "ink" if int(math.floor((b + 0.2) * 14)) % 2 else "grey_d"
    return gshade(tag, n, x, y)


def tractor_cab(tag, n, a, b, z, x, y):
    if tag in ("Yp", "Yn") and -0.34 < a < -0.12 and 8.0 < z < 11.8:
        return lit(GLASS, n, x, y)
    if tag == "Xn" and abs(b) < 0.2 and 8.0 < z < 11.8:
        return lit(GLASS, n, x, y)
    return gshade(tag, n, x, y)


def harvester(heading, frame):
    ph = PHASE[frame]
    parts = [
        (box(-0.42, 0.34, -0.22, 0.22, 2.5, 5.0), dark_mat),          # chassis
        (box(0.0, 0.34, -0.2, 0.2, 5.0, 9.0), tractor_hood),           # engine bonnet
        (box(-0.42, -0.02, -0.24, 0.24, 5.0, 13.0), tractor_cab),      # cab
        (box(-0.46, 0.0, -0.27, 0.27, 13.0, 14.2), lambda t, n, a, b, z, x, y: gshade(t, n, x, y)),
        (box(0.12, 0.18, -0.12, -0.07, 9.0, 13.5), dark_mat),          # exhaust
        (box(0.34, 0.37, -0.15, -0.08, 5.5, 7.0), lamp_mat),           # headlamps
        (box(0.34, 0.37, 0.08, 0.15, 5.5, 7.0), lamp_mat),
        (box(-0.94, -0.44, -0.2, 0.2, 4.6, 8.4), wood_mat),            # trailer box
        (box(-0.8, -0.5, -0.14, 0.14, 8.4, 10.4), crate_mat),          # crates on the trailer
        (box(-0.94, -0.44, -0.2, 0.2, 3.2, 4.6), dark_mat),            # trailer frame
        (box(-0.46, -0.42, -0.04, 0.04, 2.6, 3.4), dark_mat),          # draw bar
    ]
    parts += wheels(-0.22, 5.5, 0.2, 5.5, 0.22, ph)                    # big rear wheels
    parts += wheels(0.2, 3.8, 0.14, 3.8, 0.22, ph)                     # small front wheels
    parts += wheels(-0.72, 3.0, 0.12, 3.0, 0.2, ph)                    # trailer wheels
    return build(parts, heading)


# ---------------------------------------------------------------- pickup truck (red, wooden side boards)

def truck_cab(tag, n, a, b, z, x, y):
    if tag in ("Yp", "Yn") and 0.12 < a < 0.46 and 7.6 < z < 10.6:
        return lit(GLASS, n, x, y)
    if tag == "Xp" and abs(b) < 0.24 and 8.4 < z < 10.8:       # windscreen above the bonnet
        return lit(GLASS, n, x, y)
    if tag == "Xn" and abs(b) < 0.22 and 7.6 < z < 10.6:
        return lit(GLASS, n, x, y)
    return lit(RED, n, x, y)


def truck_bonnet(tag, n, a, b, z, x, y):
    if tag == "Xp" and abs(b) < 0.2 and 5.2 < z < 7.4:          # grille
        return "ink" if int(math.floor((b + 0.2) * 12)) % 2 else "grey_d"
    return lit(RED, n, x, y)


def red_mat(tag, n, a, b, z, x, y):
    return lit(RED, n, x, y)


def truck(heading, frame):
    ph = PHASE[frame]
    parts = [
        (box(-0.9, 0.9, -0.28, 0.28, 2.5, 4.5), dark_mat),          # chassis
        (box(-0.9, 0.05, -0.3, 0.3, 4.0, 4.6), wood_mat),           # bed floor
        (box(-0.9, 0.05, -0.34, -0.3, 4.6, 9.0), wood_mat),         # bed side boards
        (box(-0.9, 0.05, 0.3, 0.34, 4.6, 9.0), wood_mat),
        (box(-0.96, -0.9, -0.34, 0.34, 4.0, 9.0), wood_mat),        # tailgate
        (box(0.05, 0.55, -0.3, 0.3, 4.5, 11.5), truck_cab),         # cab
        (box(0.55, 0.92, -0.3, 0.3, 4.5, 8.0), truck_bonnet),       # bonnet
        (box(0.05, 0.55, -0.32, 0.32, 11.5, 12.5), red_mat),        # cab roof
        (box(0.9, 0.96, -0.34, 0.34, 3.0, 4.6), dark_mat),          # bumpers
        (box(-0.96, -0.9, -0.34, 0.34, 2.8, 3.8), dark_mat),
        (box(0.9, 0.94, -0.22, -0.1, 6.0, 7.4), lamp_mat),          # headlamps
        (box(0.9, 0.94, 0.1, 0.22, 6.0, 7.4), lamp_mat),
        (box(-0.96, -0.92, -0.26, -0.1, 6.0, 7.4), tail_mat),       # tail lamps
        (box(-0.96, -0.92, 0.1, 0.26, 6.0, 7.4), tail_mat),
    ]
    parts += wheels(-0.5, 5.5, 0.2, 5.5, 0.3, ph)
    parts += wheels(0.55, 5.5, 0.2, 5.5, 0.3, ph)
    return build(parts, heading)


def produce_mat(ramp, top):
    def f(tag, n, a, b, z, x, y):
        if tag == "Zp":
            return top
        return lit(ramp, n, x, y, crisp=True)
    return f


def truck_load():
    """Crates and vegetables in the bed of the se truck, drawn on the same ground pivot."""
    parts = [
        (box(-0.84, -0.42, -0.26, 0.0, 4.6, 7.6), crate_mat),
        (box(-0.36, -0.06, -0.26, 0.0, 4.6, 7.6), crate_mat),
        (box(-0.84, -0.42, -0.26, 0.0, 7.6, 9.4), crate_mat),
    ]
    for cu, cv, ramp, top in ((-0.22, -0.1, ["red_d", "red", "red", "pink"], "red"),
                              (-0.16, 0.1, ["rust", "orange", "orange", "amber"], "orange"),
                              (-0.62, 0.02, ["green_dd", "green_d", "green", "green"], "green")):
        z1 = 8.8 if cu > -0.4 else 8.4
        parts.append((extrude(circle(0.07, 8, cu, cv), 0, 1, 2, 9.4, z1, lo="Zn", hi="Zp"),
                      produce_mat(ramp, top)))
    return build(parts, "se")


# ---------------------------------------------------------------- cottages (1x1 footprint)

def cottage_a():
    """Thatched roof, cream plaster with half-timbering."""
    WH, PK = 14.0, 10.0

    def wall(tag, n, u, v, z, x, y):
        if tag == "Zp":
            return "tan"
        if tag not in ("Yp", "Xp"):
            return "tan_d"
        s = u if tag == "Yp" else v
        if z < 1.2:
            return "grey_d" if (x + y) % 2 else "grey"
        if tag == "Yp":
            if 0.04 < s < 0.26 and z < 10.5:                          # door
                if abs(s - 0.15) < 0.012:
                    return "brown_d"
                if 0.2 < s < 0.23 and 4.6 < z < 5.4:
                    return "yellow"
                return lit(["brown", "brown", "brown_l", "tan"], n, x, y)
            if -0.36 < s < -0.14 and 6.2 < z < 11.0:                  # window
                if abs(s + 0.25) < 0.012 or abs(z - 8.6) < 0.5:
                    return "cream"
                return lit(GLASS, n, x, y)
        elif -0.12 < s < 0.12 and 7.0 < z < 10.0:                     # gable-side window
            return lit(GLASS, n, x, y)
        if abs(abs(s) - 0.48) < 0.05 or 9.2 < z < 10.0 or 12.3 < z < 13.0:
            return "brown"                                             # timbers
        return lit(PLASTER, n, x, y)

    def thatch(tag, n, u, v, z, x, y):
        if tag in ("A", "B"):
            if z % 2.6 < 0.6:
                return "brown_d"
            h = (int(math.floor(u * 9.0)) * 7 + int(z // 2.6) * 5) % 6
            if h == 0:
                return "tan_d"
            return lit(["brown", "brown_l", "tan", "cream"], n, x, y)
        if tag == "E":
            if abs(v) < 0.03:
                return "brown_d"
            return lit(["brown", "tan", "cream"], n, x, y)
        return "brown_d"

    def stone(tag, n, u, v, z, x, y):
        if z % 3.0 < 0.7:
            return "slate"
        return lit(STONE, n, x, y)

    return [
        Solid(box(-0.5, 0.5, -0.5, 0.5, 0.0, WH), wall),
        Solid(gable(1.0, 0.0, 0.62, 0.62, WH, PK), thatch),
        Solid(box(0.14, 0.3, -0.33, -0.17, WH + 3.0, WH + PK + 6.0), stone),
    ]


def cottage_b():
    """Blue slate roof, red brick walls, shuttered windows."""
    WH, PK = 14.0, 10.0

    def wall(tag, n, u, v, z, x, y):
        if tag == "Zp":
            return "grey"
        if tag not in ("Yp", "Xp"):
            return "red_d"
        s = u if tag == "Yp" else v
        if z < 1.2:
            return "slate"
        if tag == "Yp":
            if 0.04 < s < 0.24 and z < 10.5:                          # door
                if abs(s - 0.14) < 0.012:
                    return "brown_d"
                return lit(["brown", "brown", "brown_l", "tan"], n, x, y)
            if -0.36 < s < -0.14 and 6.0 < z < 11.0:                  # window and shutters
                if abs(s + 0.25) < 0.012 or abs(z - 8.5) < 0.5:
                    return "white"
                return lit(GLASS, n, x, y)
            if (-0.42 < s < -0.37 or -0.13 < s < -0.08) and 6.0 < z < 11.0:
                return lit(["slate_d", "blue_d", "blue", "cyan"], n, x, y)
        row = int(z // 3.0)
        if z - row * 3.0 < 0.8:
            return "tan"                                              # mortar course
        q = (s * 8.0 + (row % 2) * 0.5) % 1.0
        if q < 0.12:
            return "tan"                                              # head joint
        return lit(BRICK, n, x, y)

    def slate(tag, n, u, v, z, x, y):
        if tag in ("A", "B"):
            return shingle(u, z, "blue", "blue_d", "cyan")
        if tag == "E":
            if abs(z - WH) < 0.7 or abs(abs(v) - 0.5) < 0.1:
                return "white"
            return lit(BRICK, n, x, y)
        return "blue_d"

    def chimney(tag, n, u, v, z, x, y):
        row = int(z // 3.0)
        if z - row * 3.0 < 0.8:
            return "tan"
        return lit(BRICK, n, x, y)

    return [
        Solid(box(-0.5, 0.5, -0.5, 0.5, 0.0, WH), wall),
        Solid(gable(1.0, 0.0, 0.62, 0.62, WH, PK), slate),
        Solid(box(0.14, 0.3, -0.33, -0.17, WH + 3.0, WH + PK + 6.0), chimney),
    ]


def cottage_c():
    """Lap-siding walls, rust shingles, green door, flower boxes under the windows."""
    WH, PK = 14.0, 11.0

    def wall(tag, n, u, v, z, x, y):
        if tag == "Zp":
            return "tan"
        if tag not in ("Yp", "Xp"):
            return "tan_d"
        s = u if tag == "Yp" else v
        if z < 1.6:
            return lit(["slate", "grey_d", "grey"], n, x, y)
        if z % 2.4 < 0.6:
            return "tan_d"                                             # lap seams
        if tag == "Yp":
            if -0.1 < s < 0.14 and z < 10.0:                           # green door
                if abs(s - 0.02) < 0.01 or z > 9.0:
                    return "green_dd"
                if 0.08 < s < 0.1 and 4.6 < z < 5.4:
                    return "yellow"
                return lit(GREEN, n, x, y)
            for c in (-0.3, 0.3):                                      # windows
                if c - 0.09 < s < c + 0.09 and 6.6 < z < 11.0:
                    if abs(s - c) < 0.012 or abs(z - 8.8) < 0.5:
                        return "white"
                    return lit(GLASS, n, x, y)
        return lit(PLASTER, n, x, y)

    def flowers(tag, n, a, b, z, x, y):
        if tag == "Zp":
            h = (int(math.floor(a * 18.0)) * 5 + int(math.floor(b * 18.0)) * 3) % 7
            return {0: "pink", 3: "pink", 1: "red", 5: "yellow", 2: "green", 4: "green"}.get(h, "green_d")
        if z % 1.6 < 0.5:
            return "brown_d"
        return lit(["brown", "brown", "brown_l"], n, x, y)

    def shingles(tag, n, u, v, z, x, y):
        if tag in ("A", "B"):
            return shingle(u, z, "rust", "red_d", "clay")
        if tag == "E":
            if (v * v) + (z - (WH + 3.5)) ** 2 < 2.4 ** 2:
                return lit(GLASS, n, x, y)
            return lit(PLASTER, n, x, y)
        return "red_d"

    solids = [
        Solid(box(-0.5, 0.5, -0.5, 0.5, 0.0, WH), wall),
        Solid(gable(1.0, 0.0, 0.62, 0.62, WH, PK), shingles),
    ]
    for c in (-0.3, 0.3):
        solids.append(Solid(box(c - 0.12, c + 0.12, 0.5, 0.62, 4.0, 6.2), flowers))
    return solids


# ---------------------------------------------------------------- small world objects

def bridge():
    """Plank bridge running along v (up-right on screen), pivot on the water surface at its centre."""
    def deck(tag, n, u, v, z, x, y):
        if tag == "Zp":
            return "brown_d" if seam(u, 4.0) else lit(["brown", "brown_l", "tan", "tan"], n, x, y)
        return lit(["brown_d", "brown", "brown_l"], n, x, y)

    def timber(tag, n, u, v, z, x, y):
        return lit(["brown_d", "brown", "brown_l", "tan"], n, x, y)

    solids = [
        Solid(box(-0.36, 0.36, -1.0, 1.0, 5.0, 6.4), deck),
        Solid(box(-0.4, 0.4, -1.0, 1.0, 3.6, 5.0), timber),
    ]
    for pu, pv in ((0.28, -0.66), (-0.28, -0.66), (0.28, 0.66), (-0.28, 0.66)):
        solids.append(Solid(box(pu - 0.05, pu + 0.05, pv - 0.05, pv + 0.05, 0.0, 3.6), timber))
    for sgn in (1, -1):
        uc = sgn * 0.36
        for vp in (-0.9, 0.0, 0.9):
            solids.append(Solid(box(uc - 0.04, uc + 0.04, vp - 0.04, vp + 0.04, 6.4, 9.6), timber))
        solids.append(Solid(box(uc - 0.05, uc + 0.05, -1.0, 1.0, 8.6, 9.6), timber))
        solids.append(Solid(box(uc - 0.04, uc + 0.04, -1.0, 1.0, 7.0, 7.8), timber))
    return solids


def post_mat(tag, n, a, b, z, x, y):
    return lit(WOOD, n, x, y)


ARROW = [
    "...........",
    "...........",
]


def sign_board(icon, key):
    """Arrow board on the +v face: the icon is a 12 x 7 grid, one cell per screen pixel."""
    def f(tag, n, a, b, z, x, y):
        if tag == "Yp":
            cu = int(math.floor((a + 0.42) * 16.0))
            cz = int(math.floor(z - 12.0))
            if 0 <= cz < 7 and 0 <= cu < 12:
                ch = icon[6 - cz][cu]
                if ch != ".":
                    return key[ch]
            return lit(["tan_d", "tan", "cream"], n, x, y)
        return lit(WOOD, n, x, y)
    return f


TREE_ICON = [
    "....g.......",
    "...ggG......",
    "..gGggg.....",
    ".gGgggGg....",
    "..gGgGg.....",
    "....t.......",
    "....t.......",
]
PICK_ICON = [
    "..ssss......",
    ".sS..Ss.....",
    "sS....Ss....",
    "....bb......",
    "....bb......",
    "....bb......",
    "....bb......",
]
ICON_KEY = {"g": "green", "G": "green_d", "t": "brown", "s": "grey", "S": "slate", "b": "brown"}


def signpost(icon):
    """Post at the ground centre and an arrow board on its +v face, painted with an icon."""
    arrow = [(-0.42, 12.0), (0.2, 12.0), (0.36, 15.5), (0.2, 19.0), (-0.42, 19.0)]
    return [
        Solid(box(-0.04, 0.04, -0.04, 0.04, 0.0, 20.0), post_mat),
        Solid(extrude(arrow, 0, 2, 1, 0.04, 0.08, lo="Ym", hi="Yp"), sign_board(icon, ICON_KEY)),
    ]


def field_sign():
    """Blank board on a post: the board's +v face is left plain for the engine to letter."""
    def board(tag, n, u, v, z, x, y):
        return lit(["tan_d", "tan", "cream"], n, x, y)
    return [
        Solid(box(-0.04, 0.04, -0.04, 0.04, 0.0, 12.0), post_mat),
        Solid(box(-0.42, 0.42, 0.0, 0.06, 6.0, 12.0), board),
    ]


def crate(full):
    parts = [Solid(box(-0.22, 0.22, -0.22, 0.22, 0.0, 5.5), crate_mat)]
    if not full:
        return parts

    def produce(ramp, top):
        return produce_mat(ramp, top)
    for cu, cv, ramp, top, h in ((-0.12, -0.08, ["rust", "orange", "orange", "amber"], "orange", 7.4),
                                 (0.08, -0.1, ["red_d", "red", "red", "pink"], "red", 8.0),
                                 (0.0, 0.1, ["green_dd", "green_d", "green", "green"], "green", 7.0),
                                 (0.12, 0.08, ["red_d", "red", "red", "pink"], "red", 7.6)):
        cons = extrude(circle(0.1, 8, cu, cv), 0, 1, 2, 5.0, h, lo="Zn", hi="Zp")
        parts.append(Solid(cons, produce(ramp, top)))
    return parts


def hay_bale():
    def side(tag, n, u, v, z, x, y):
        if tag == "Zp":
            rr = math.hypot(u, v) / 0.36
            f = (rr * 2.4 + math.atan2(v, u) / TAU) % 1.0
            return "brown" if f < 0.22 else lit(["tan", "cream", "cream", "white"], n, x, y)
        if z % 1.8 < 0.5:
            return "brown"
        return lit(["tan_d", "tan", "amber", "cream"], n, x, y)
    cons = extrude(circle(0.36, 14), 0, 1, 2, 0.0, 7.0, lo="Zn", hi="Zp")
    return [Solid(cons, side)]


def trough():
    def wood(tag, n, u, v, z, x, y):
        if tag == "Zp":
            return "brown_l"
        if z % 2.0 < 0.5:
            return "brown_d"
        return lit(WOOD, n, x, y)

    def water(tag, n, u, v, z, x, y):
        if tag == "Zp":
            if (int(math.floor(u * 14.0)) * 3 + int(math.floor(v * 14.0))) % 7 == 0:
                return "white"
            return "cyan" if (int(math.floor(u * 14.0)) + int(math.floor(v * 14.0))) % 2 else "blue"
        return "blue_d"

    return [
        Solid(box(-0.36, 0.36, 0.13, 0.2, 0.0, 6.0), wood),
        Solid(box(-0.36, 0.36, -0.2, -0.13, 0.0, 6.0), wood),
        Solid(box(0.29, 0.36, -0.2, 0.2, 0.0, 6.0), wood),
        Solid(box(-0.36, -0.29, -0.2, 0.2, 0.0, 6.0), wood),
        Solid(box(-0.29, 0.29, -0.13, 0.13, 0.0, 4.4), water),
    ]


# ---------------------------------------------------------------- main

def main():
    art = sys.argv[1]
    veh = Sprites(art, "vehicles")
    bld = Sprites(art, "buildings")
    deco = Sprites(art, "deco")

    for heading in ("se", "ne"):
        for frame in (0, 1):
            emit(veh, f"harvester_{heading}_{frame}", harvester(heading, frame))
    # The pickup truck and its load are hand-pixeled in farm_hand.py (art round 5).

    emit(bld, "cottage_a", cottage_a())
    emit(bld, "cottage_b", cottage_b())
    emit(bld, "cottage_c", cottage_c())

    emit(deco, "bridge", bridge())
    emit(deco, "sign_forest", signpost(TREE_ICON))
    emit(deco, "sign_mine", signpost(PICK_ICON))
    emit(deco, "field_sign", field_sign())
    emit(deco, "crate_empty", crate(False))
    emit(deco, "crate_full", crate(True))
    emit(deco, "hay_bale", hay_bale())
    emit(deco, "trough", trough())

    pivots = {}
    for sp in (veh, bld, deco):
        pivots.update(sp.pivots)
    print(json.dumps(pivots))


main()
