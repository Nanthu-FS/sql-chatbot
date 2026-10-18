"""Forest and mine machines plus the merchant cart (pixel-direct, folder "buildings").

One-tile footprint machines, pivot = ground centre of the tile (as in buildings.py): sawmill,
wood_chipper, charcoal_kiln, syrup_kettle, furnace, geode_anvil, logging_hut, tree_nursery.
Taller or wider: drill_rig (about 34x52) and merchant_cart (about 44x40, two tiles wide in look).

Projection and ray caster are those of buildings.py, copied here because importing that module runs
its main(). A world point (u, v, z) lands at X = (u - v) * 16 and Y = (u + v) * 8 - z. Every part is a
convex solid (extruded polygon, cone, lathe or gable) written as half-space constraints; each screen
pixel is ray-cast through them exactly, then _pix.finish() adds the 1px ink outline and drop shadow.
Materials read the surface normal: T is a top face, R the +u face (right), L the +v face (front-left).
Round parts use crisp palette bands; soft shading is checker-dithered.
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, Sprites, finish, from_map, new  # noqa: E402
import math  # noqa: E402

W, H = 128, 128      # working canvas
CX, CY = 64, 96      # ground centre of the one-tile footprint (the pivot)

STONE = ["slate", "grey_d", "grey", "grey_l"]
IRON = ["slate", "grey_d", "grey", "grey_l", "white"]
WOOD = ["brown_d", "brown", "brown_l", "tan"]
RED = ["red_d", "red", "pink"]
CREAM = ["tan", "cream", "white"]
BLUE = ["blue_d", "blue", "cyan"]
MAPLE = ["....r....", "...rrr...", "r..rrr..r", "rr.rrr.rr", ".rrrrrrr.", ".rrrrrrr.",
         "..rrrrr..", "...rrr...", "....r...."]


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
    """Lambert shading of normal n against LIGHT, mapped onto the ramp."""
    nu = unit(n)
    i = nu[0] * LIGHT[0] + nu[1] * LIGHT[1] + nu[2] * LIGHT[2]
    v = ((i + 1.0) / 2.0 + bias) * (len(ramp) - 1)
    if crisp:
        return ramp[max(0, min(len(ramp) - 1, int(round(v))))]
    return tone(ramp, v, x, y)


def face(n):
    if n[2] > 0.6:
        return "T"
    if n[2] < -0.6:
        return "D"
    if n[0] > 0.6:
        return "R"
    if n[1] > 0.6:
        return "L"
    return "S"


def seam(t, step=4.0):
    """True on the one-pixel joint line of a board or plank run along t."""
    f = (t * step) % 1.0
    return f < 0.12 or f > 0.88


# ---------------------------------------------------------------- convex solids

def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def plane3(p1, p2, p3, inside, tag):
    """Constraint n.X <= d for the plane through three points, facing away from `inside`."""
    n = cross(sub(p2, p1), sub(p3, p1))
    if dot(n, sub(inside, p1)) > 0:
        n = (-n[0], -n[1], -n[2])
    n = unit(n)
    return (n, dot(n, p1), tag)


def rect(u0, u1, v0, v1):
    return [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]


def ell(ra, rb, n, a=0.0, b=0.0):
    """n-sided ellipse polygon: radius ra on the first axis, rb on the second, centred on (a, b)."""
    return [(a + ra * math.cos(2 * math.pi * i / n), b + rb * math.sin(2 * math.pi * i / n)) for i in range(n)]


def arch(a, b, zb, n=8):
    """Half ellipse standing on the line z = zb, in (v, z) coordinates (canopies, bails)."""
    return [(a * math.cos(math.pi * i / n), zb + b * math.sin(math.pi * i / n)) for i in range(n + 1)]


def ccw(poly):
    area = 0.0
    k = len(poly)
    for i in range(k):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % k]
        area += x0 * y1 - x1 * y0
    return poly if area > 0 else poly[::-1]


def ext(poly, axis, lo, hi, tag="S"):
    """Convex 2D polygon extruded along one axis from lo to hi. The polygon is (u, v) for axis 'z',
    (v, z) for axis 'u' and (u, z) for axis 'v'."""
    poly = ccw(poly)
    k = len(poly)
    cons = []
    for i in range(k):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % k]
        ex, ey = x1 - x0, y1 - y0
        m = math.hypot(ex, ey)
        nx, ny = ey / m, -ex / m
        d = nx * x0 + ny * y0
        n = {"z": (nx, ny, 0.0), "u": (0.0, nx, ny), "v": (nx, 0.0, ny)}[axis]
        cons.append((n, d, tag))
    ax = {"z": (0.0, 0.0, 1.0), "u": (1.0, 0.0, 0.0), "v": (0.0, 1.0, 0.0)}[axis]
    cons.append((ax, hi, "P"))
    cons.append(((-ax[0], -ax[1], -ax[2]), -lo, "M"))
    return cons


def cone(poly, zb, apex, cu=0.0, cv=0.0):
    """Cone-like solid: base polygon at height zb, apex straight above or below (cu, cv)."""
    inside = (cu, cv, zb + 0.2 * (apex - zb))
    A = (cu, cv, apex)
    k = len(poly)
    cons = []
    for i in range(k):
        p, q = poly[i], poly[(i + 1) % k]
        cons.append(plane3((p[0], p[1], zb), (q[0], q[1], zb), A, inside, "C"))
    if apex > zb:
        cons.append(((0.0, 0.0, -1.0), -zb, "M"))
    else:
        cons.append(((0.0, 0.0, 1.0), zb, "P"))
    return cons


def lathe(cu, cv, prof, mat, n=14):
    """Stack of round prisms following a radius profile [(z, r), ...] (domes, kettles)."""
    out = []
    for (za, ra), (zb, rb) in zip(prof, prof[1:]):
        r = (ra + rb) / 2.0
        if zb - za > 1e-6 and r > 0.02:
            out.append(Solid(ext(ell(r, r, n, cu, cv), "z", za, zb), mat))
    return out


def sph(zc, rz, ruv, steps=8):
    """Radius profile of an ellipsoid centred on zc (for lathe)."""
    prof = []
    for i in range(steps + 1):
        t = -1.0 + 2.0 * i / steps
        prof.append((zc + rz * t, ruv * math.sqrt(max(0.0, 1.0 - t * t))))
    return prof


def gable(cu, cv, du, dv, hl, hw, ze, pk):
    """Gable roof: ridge through (cu, cv) along (du, dv), half-length hl, eaves at +-hw, ridge pk above ze."""
    nu, nv = -dv, du

    def P(s, a, z):
        return (cu + s * du + a * nu, cv + s * dv + a * nv, z)

    inside = (cu, cv, ze + pk * 0.25)
    return [
        plane3(P(-hl, -hw, ze), P(-hl, hw, ze), P(-hl, 0.0, ze + pk), inside, "E"),
        plane3(P(hl, -hw, ze), P(hl, hw, ze), P(hl, 0.0, ze + pk), inside, "E"),
        plane3(P(-hl, hw, ze), P(hl, hw, ze), P(0.0, 0.0, ze + pk), inside, "A"),
        plane3(P(-hl, -hw, ze), P(hl, -hw, ze), P(0.0, 0.0, ze + pk), inside, "B"),
        ((0.0, 0.0, -1.0), -ze, "M"),
    ]


class Solid:
    def __init__(self, cons, mat):
        self.cons = cons      # [(unit outward normal, d, tag)] meaning n.X <= d
        self.mat = mat        # fn(tag, normal, u, v, z, x, y) -> palette name or RGBA


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


def stamp_world(im, sprite, u, v, z):
    """Pastes a small character-map sprite centred on the projected point of (u, v, z)."""
    w, h = sprite.size
    ox = int(round(CX + (u - v) * 16.0 - w / 2.0))
    oy = int(round(CY + (u + v) * 8.0 - z - h / 2.0))
    out, src = im.load(), sprite.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                out[ox + x, oy + y] = src[x, y]


def emit(sp, name, solids, post=None):
    im = render(solids)
    if post:
        post(im)
    x0, y0, x1, y1 = im.getbbox()
    body = im.crop((x0, y0, x1, y1))
    out, pad = finish(body)
    sp.save(name, out, CX - x0 + pad, CY - y0 + pad)
    sys.stderr.write("%s: body %dx%d\n" % (name, body.width, body.height))


# ---------------------------------------------------------------- sawmill (1x1)

def sawmill():
    def deck(tag, n, u, v, z, x, y):
        f = face(n)
        if f == "T":
            return "brown" if seam(v, 3.0) else "tan"
        if f == "R":
            return "brown_d" if seam(v, 3.0) else "brown"
        return "brown_d" if seam(u, 3.0) else "brown_l"

    def log(tag, n, u, v, z, x, y):
        if face(n) == "R":                                  # cut end: growth rings
            d = math.hypot(v * 16.0, z - 15.4)
            return "cream" if int(d / 1.6) % 2 else "tan"
        if n[2] < 0.5 and int((u + 0.5) * 9.0) % 4 == 0:
            return "brown_d"                                # bark cracks
        return lit(WOOD, n, x, y, crisp=True)

    def blade(tag, n, u, v, z, x, y):
        if face(n) == "R":
            d = math.hypot(v * 16.0, z - 23.0)
            if d < 1.6:
                return "slate"                              # hub
            if d > 5.6:
                ang = math.atan2(z - 23.0, v * 16.0)
                return "grey_l" if math.floor((ang + math.pi) * 6.0 / math.pi) % 2 else "grey_d"   # teeth
            return "grey_l" if v < -0.05 and z > 23.0 else "grey"
        return lit(IRON, n, x, y, crisp=True)

    legs_a = [(-0.42, 0.0), (-0.30, 0.0), (-0.02, 11.0), (-0.14, 11.0)]    # (v, z) A-frame legs
    legs_b = [(0.30, 0.0), (0.42, 0.0), (0.14, 11.0), (0.02, 11.0)]
    S = [Solid(ext(rect(-0.44, 0.44, -0.44, 0.44), "z", 0.0, 2.0), deck)]    # floor
    for u0, u1 in ((-0.34, -0.25), (0.12, 0.20)):                          # two sawhorses
        S.append(Solid(ext(legs_a, "u", u0, u1), deck))
        S.append(Solid(ext(legs_b, "u", u0, u1), deck))
        S.append(Solid(ext(rect(u0, u1, -0.36, 0.36), "z", 9.0, 11.0), deck))
    S.append(Solid(ext(ell(0.26, 4.2, 14, 0.0, 15.4), "u", -0.44, 0.30), log))  # the log
    S.append(Solid(ext(ell(0.42, 7.0, 24, 0.0, 23.0), "u", 0.36, 0.40), blade))  # big round blade
    S.append(Solid(ext(rect(0.28, 0.33, 0.38, 0.46), "z", 2.0, 32.0), deck))   # blade frame
    S.append(Solid(ext(rect(0.28, 0.33, -0.46, -0.38), "z", 2.0, 32.0), deck))
    S.append(Solid(ext(rect(0.28, 0.42, -0.46, 0.46), "z", 30.0, 32.0), deck))
    for k in range(3):                                                      # stack of planks
        u0 = -0.40 + 0.02 * (k % 2)
        S.append(Solid(ext(rect(u0, u0 + 0.36, 0.22, 0.44), "z", 2.0 + 2.5 * k, 4.5 + 2.5 * k), deck))
    return S


# ---------------------------------------------------------------- wood chipper (1x1)

def wood_chipper():
    CHUTE = [(0.24, 14.0), (0.56, 3.0), (0.64, 4.5), (0.30, 16.0)]          # (u, z) chute sheet

    def body(tag, n, u, v, z, x, y):
        f = face(n)
        if f == "T":
            return "pink"
        if abs(z - 11.0) < 0.6:
            return "grey_d"                                  # rail
        if f == "L" and (x + y) % 6 == 0:
            return "grey_l"                                  # rivets
        return "red_d" if f == "R" else "red"

    def hopper(tag, n, u, v, z, x, y):
        if face(n) == "T":
            return "tan" if (x * 3 + y) % 4 == 0 else "brown_d"   # chips in the hopper
        return lit(["red_d", "red", "pink"], n, x, y, crisp=True)

    def chute(tag, n, u, v, z, x, y):
        return lit(["grey_d", "grey", "grey_l", "white"], n, x, y, crisp=True)

    def heap(tag, n, u, v, z, x, y):
        return lit(["brown", "brown_l", "tan", "cream"], n, x, y)

    def wheel(tag, n, u, v, z, x, y):
        p, q = (u + 0.04) / 0.36, (z - 9.5) / 6.5
        r = math.hypot(p, q)
        if r < 0.22:
            return "grey_l"                                  # hub
        if r > 0.82:
            return "slate"                                   # rim
        if abs(math.sin(2 * math.atan2(q, p))) < 0.3:
            return "grey_d"                                  # spokes
        return "grey"

    def frame(tag, n, u, v, z, x, y):
        return lit(["slate", "grey_d", "grey"], n, x, y, crisp=True)

    return [
        Solid(ext(rect(-0.42, 0.34, -0.40, 0.38), "z", 0.0, 3.0), frame),
        Solid(ext(rect(-0.36, 0.26, -0.28, 0.28), "z", 3.0, 19.0), body),
        Solid(ext(rect(-0.42, 0.12, -0.40, 0.26), "z", 19.0, 26.0), hopper),
        Solid(ext(CHUTE, "v", -0.22, 0.22), chute),
        Solid(cone(ell(0.24, 0.24, 9, 0.72, 0.0), 0.0, 6.0, 0.72, 0.0), heap),
        Solid(ext(ell(0.36, 6.5, 20, -0.04, 9.5), "v", 0.28, 0.36), wheel),
    ]


# ---------------------------------------------------------------- charcoal kiln (1x1)

def charcoal_kiln():
    SOOT = ["slate", "slate", "grey_d", "grey"]          # sooty clay, so it reads apart from the white beehive

    def stone(tag, n, u, v, z, x, y):
        if n[1] > 0.5 and abs(u) < 0.14 and 1.5 < z < 9.5:      # arched firing door, front-left
            if abs(u) > 0.115 or z < 2.4 or z > 8.8:
                return "tan"                                     # wooden door frame
            if z < 4.2:                                          # glowing embers
                return ["red", "orange", "amber", "yellow"][2 if (x + y) % 2 else 3]
            return "ink"
        return lit(SOOT, n, x, y)                            # dithered soot, no courses (not a skep)

    def crown(tag, n, u, v, z, x, y):
        if face(n) == "T":
            if math.hypot(u, v) < 0.1:
                return "red" if (x + y) % 2 else "orange"        # glowing smoke hole
            return "grey"
        return lit(SOOT, n, x, y, crisp=True)

    prof = [(0.0, 0.60), (5.0, 0.60), (8.0, 0.57), (11.0, 0.50), (14.0, 0.40), (17.0, 0.26),
            (19.0, 0.15), (20.0, 0.14)]
    S = lathe(0.0, 0.0, prof, stone, n=16)                  # beehive dome
    S.append(Solid(ext(ell(0.16, 0.16, 12), "z", 19.5, 23.0), crown))     # smoke stack
    return S


# ---------------------------------------------------------------- syrup kettle (1x1)

MAPLE_KEY = {"r": "red"}


def syrup_kettle():
    def base(tag, n, u, v, z, x, y):
        if face(n) == "T":
            return "grey_d"                                  # ash
        return lit(["slate", "grey_d", "grey", "grey_l"], n, x, y, crisp=True)

    def flame(tag, n, u, v, z, x, y):
        i = min(3, int(z / 2.2))
        if (x + y) % 2 and i < 3:
            i += 1
        return ["red", "orange", "amber", "yellow"][min(3, i)]

    def iron(tag, n, u, v, z, x, y):
        return lit(IRON, n, x, y, crisp=True)

    def rim(tag, n, u, v, z, x, y):
        if face(n) == "T":
            r = math.hypot(u, v)
            if r < 0.24:
                return "amber" if r < 0.14 else "brown_d"    # syrup inside
            return "grey_l"
        return lit(IRON, n, x, y, crisp=True)

    S = [Solid(ext(ell(0.34, 0.34, 12), "z", 0.0, 2.0), base)]          # fire ring
    S.append(Solid(cone(ell(0.2, 0.2, 10), 2.0, 8.0), flame))           # flame
    S += lathe(0.0, 0.0, sph(13.0, 9.0, 0.58, 10), iron, n=16)          # iron pot
    S.append(Solid(ext(ell(0.36, 0.36, 14), "z", 21.5, 23.0), rim))     # rim
    return S


def leaf_post(im):
    stamp_world(im, from_map(MAPLE, MAPLE_KEY), 0.0, 0.3, 14.0)


# ---------------------------------------------------------------- furnace (1x1)

def furnace():
    def stone(tag, n, u, v, z, x, y):
        f = face(n)
        if f == "T":
            return "grey_l"
        if n[1] > 0.6 and -0.30 < u < 0.12 and 3.0 < z < 13.0:  # firing door, front-left
            if u < -0.27 or u > 0.09 or z < 3.4 or z > 12.4:
                return "slate"                                   # iron frame
            d = math.hypot((u + 0.09) / 0.21, (z - 8.0) / 5.0)
            ramp = ["red", "orange", "amber", "yellow", "white"]
            i = max(0, 4 - int(d * 2.6))
            return ramp[i] if (x + y) % 3 else ramp[max(0, i - 1)]
        row = int(z // 3.0)
        if z - row * 3.0 < 0.8:
            return "grey_d"                                      # mortar course
        s = u if f == "L" else (v if f == "R" else u + v)
        if (s * 16.0 + (row % 2) * 4.0) % 8.0 < 0.9:
            return "grey_d"                                      # head joint
        return lit(STONE, n, x, y, crisp=True)

    def iron(tag, n, u, v, z, x, y):
        return lit(IRON, n, x, y, crisp=True)

    def flue(tag, n, u, v, z, x, y):
        if face(n) == "T":
            return "orange" if math.hypot(u - 0.18, v + 0.24) < 0.06 else "slate"   # hot flue mouth
        return lit(STONE, n, x, y, crisp=True)

    def ash(tag, n, u, v, z, x, y):
        return lit(["brown_d", "brown", "grey_d"], n, x, y)

    return [
        Solid(ext(rect(-0.42, 0.34, -0.40, 0.36), "z", 0.0, 19.0), stone),          # firebox
        Solid(ext(rect(-0.44, 0.36, -0.42, 0.38), "z", 16.0, 18.0), iron),         # iron band
        Solid(ext(rect(0.06, 0.28, -0.34, -0.14), "z", 19.0, 36.0), flue),          # chimney
        Solid(ext(rect(-0.34, 0.16, 0.36, 0.42), "z", 0.0, 2.6), ash),              # ash pit sill
    ]


# ---------------------------------------------------------------- geode anvil (1x1)

def geode_anvil():
    def stump(tag, n, u, v, z, x, y):
        if face(n) == "T":
            return "tan" if int(math.hypot(u, v) * 16.0) % 2 else "brown_l"      # tree rings
        if n[2] < 0.5 and int(z) % 3 == 0:
            return "brown_d"                                                       # bark grooves
        return lit(WOOD, n, x, y, crisp=True)

    def iron(tag, n, u, v, z, x, y):
        if face(n) == "T" and math.hypot(u - 0.2, v) < 0.035:
            return "ink"                                                            # hardy hole
        return lit(["slate", "grey_d", "grey", "grey_l"], n, x, y)                  # steel, no white glare

    def rock(tag, n, u, v, z, x, y):
        if abs((u - 0.02) + 0.035 * (z - 19.5)) < 0.02:
            return "slate_d"                                                        # crack
        return lit(["grey_d", "grey", "grey_l", "white"], n, x, y)

    def crystal(tag, n, u, v, z, x, y):
        if z > 27.0:
            return "pink"
        return lit(["purple", "magenta", "pink"], n, x, y, crisp=True)

    S = [Solid(ext(ell(0.24, 0.24, 12), "z", 0.0, 6.0), stump),
         Solid(ext(rect(-0.28, 0.28, -0.18, 0.18), "z", 6.0, 8.5), iron),           # foot
         Solid(ext(rect(-0.20, 0.20, -0.12, 0.12), "z", 8.5, 10.5), iron),         # waist
         Solid(ext(rect(-0.40, 0.40, -0.22, 0.22), "z", 10.5, 14.5), iron),         # face
         Solid(ext([(-0.40, 10.5), (-0.40, 14.5), (-0.84, 12.5)], "v", -0.14, 0.14), iron)]  # horn
    S += lathe(0.02, 0.0, sph(19.5, 5.0, 0.3, 8), rock, n=12)                       # geode, sits on the face
    for cu, cv, zb, za in ((0.0, 0.0, 21.0, 33.0), (0.11, -0.05, 20.5, 29.0), (-0.09, 0.06, 20.5, 28.0)):
        S.append(Solid(cone(ell(0.1, 0.1, 6, cu, cv), zb, za, cu, cv), crystal))  # purple crystals
    return S


# ---------------------------------------------------------------- drill rig (1x1, tall)

def drill_rig():
    def lattice(tag, n, u, v, z, x, y):
        f = face(n)
        if f == "T":
            return "tan"
        if z % 6.0 < 1.0:
            return "brown_d"                                 # rail
        t = u * 16.0 if f == "L" else v * 16.0
        if (z + t) % 6.0 < 1.0 or (z - t) % 6.0 < 1.0:
            return "brown_d"                                 # cross bracing
        return lit(WOOD, n, x, y, crisp=True)

    def steel(tag, n, u, v, z, x, y):
        return lit(IRON, n, x, y, crisp=True)

    def gear(tag, n, u, v, z, x, y):
        p, q = u / 0.26, (z - 26.0) / 5.0
        r = math.hypot(p, q)
        if r < 0.3:
            return "grey_l"                                  # axle
        if r > 0.8 and math.floor(math.atan2(q, p) * 8 / (2 * math.pi)) % 2 == 0:
            return "slate"                                   # teeth
        return "grey"

    def bit(tag, n, u, v, z, x, y):
        ang = math.atan2(v, u)
        return "grey_l" if math.floor(ang * 4 / (2 * math.pi) + (17.0 - z) / 3.0) % 2 else "grey_d"   # flutes

    def coal(tag, n, u, v, z, x, y):
        return lit(["ink", "slate_d", "slate", "grey_d"], n, x, y)

    def bin_wood(tag, n, u, v, z, x, y):
        if z % 4.0 < 0.7:
            return "grey_d"                                  # iron hoops
        return lit(WOOD, n, x, y, crisp=True)

    def pad(tag, n, u, v, z, x, y):
        if face(n) == "T" and (x + y) % 5:
            return "grey_l"
        return lit(["grey_d", "grey"], n, x, y)

    S = [Solid(ext(rect(-0.42, 0.42, -0.42, 0.42), "z", 0.0, 2.0), pad)]
    for i in range(6):                                       # stepped lattice tower
        z0 = 2.0 + 6.0 * i
        r = 0.34 - 0.03 * i
        S.append(Solid(ext(rect(-r, r, -r, r), "z", z0, z0 + 6.0), lattice))
    S.append(Solid(ext(rect(-0.22, 0.22, -0.22, 0.22), "z", 38.0, 40.0), lattice))      # crown deck
    S.append(Solid(ext(ell(0.20, 4.5, 14, 0.0, 43.5), "v", -0.04, 0.04), steel))        # sheave
    S.append(Solid(ext(rect(-0.025, 0.025, -0.025, 0.025), "z", 17.0, 43.5), steel))    # cable
    S.append(Solid(ext(ell(0.26, 5.0, 16, 0.0, 26.0), "v", 0.18, 0.30), gear))          # gear
    S.append(Solid(ext(rect(-0.12, 0.12, -0.12, 0.12), "z", 16.0, 18.0), steel))         # collar
    S.append(Solid(cone(ell(0.16, 0.16, 8), 16.0, 2.5), bit))                           # drill bit
    S.append(Solid(ext(rect(0.16, 0.42, -0.42, -0.10), "z", 0.0, 10.0), bin_wood))      # coal bin
    S.append(Solid(cone(ell(0.22, 0.22, 8, 0.29, -0.26), 10.0, 15.0, 0.29, -0.26), coal))  # coal heap
    return S


# ---------------------------------------------------------------- merchant cart (about two tiles wide)

def merchant_cart():
    def wheel(tag, n, u, v, z, x, y):
        p, q = (u + 0.1) / 0.34, (z - 7.0) / 7.0
        r = math.hypot(p, q)
        if r > 0.84:
            return "brown_d"                                 # tyre
        if r < 0.2:
            return "grey_l"                                  # hub
        if abs(math.sin(3 * math.atan2(q, p))) < 0.2:
            return "brown_l"                                 # spokes
        return "brown"

    def bed(tag, n, u, v, z, x, y):
        f = face(n)
        if f == "T":
            return "brown_l" if seam(v, 3.0) else "tan"
        band = 12.6 < z < 13.4
        if f == "R":
            return "yellow" if band else ("blue_d" if seam(v, 3.0) else "blue")
        return "yellow" if band else ("blue_d" if seam(u, 4.0) else "blue")

    def canopy(tag, n, u, v, z, x, y):
        if face(n) == "R" and math.hypot(v / 0.24, (z - 14.0) / 9.0) < 1.0:   # open front
            if math.hypot(v / 0.07, (z - 16.0) / 2.6) < 1.0:
                return "yellow"                              # lantern glow inside
            return "brown_d"
        s = int(math.floor((u + 0.8) / 0.16)) % 3
        return lit((RED, CREAM, BLUE)[s], n, x, y)           # striped canvas

    def crate(tag, n, u, v, z, x, y):
        if face(n) == "T":
            return "tan"
        if z % 2.0 < 0.5:
            return "brown"
        return lit(["brown_d", "brown", "brown_l", "tan_d"], n, x, y, crisp=True)

    def lantern(tag, n, u, v, z, x, y):
        if face(n) == "T":
            return "brown_d"
        return "yellow" if (x + y) % 2 else "amber"

    def wood(tag, n, u, v, z, x, y):
        return lit(WOOD, n, x, y, crisp=True)

    return [
        Solid(ext(ell(0.34, 7.0, 20, -0.1, 7.0), "v", 0.30, 0.38), wheel),       # near wheel
        Solid(ext(ell(0.34, 7.0, 20, -0.1, 7.0), "v", -0.38, -0.30), wheel),    # far wheel
        Solid(ext(rect(-0.9, 0.9, -0.3, 0.3), "z", 6.0, 14.0), bed),            # wagon bed
        Solid(ext(arch(0.34, 12.0, 14.0, 8), "u", -0.9, 0.82), canopy),         # canvas cover
        Solid(ext(rect(0.46, 0.62, 0.36, 0.56), "z", 0.0, 6.0), crate),         # crates
        Solid(ext(rect(0.50, 0.66, 0.40, 0.52), "z", 6.0, 11.0), crate),
        Solid(ext(rect(0.84, 0.96, -0.07, 0.07), "z", 15.0, 21.0), lantern),    # lantern
        Solid(ext(rect(0.82, 0.86, -0.01, 0.01), "z", 21.0, 26.0), wood),      # lantern pole
        Solid(ext(rect(-1.1, -0.9, -0.05, 0.05), "z", 3.0, 7.0), wood),         # tongue
    ]


# ---------------------------------------------------------------- logging hut (1x1)

def logging_hut():
    def walls(tag, n, u, v, z, x, y):
        f = face(n)
        if f == "L" and -0.12 < u < 0.08 and z < 9.0:           # plank door, front-left
            if abs(u - 0.05) < 0.02 and 3.5 < z < 5.0:
                return "tan"                                     # handle
            return "brown_d" if seam(u, 6.0) else "brown"
        if f == "R" and -0.20 < v < 0.02 and 5.0 < z < 9.5:     # window
            if abs(v + 0.09) < 0.02 or abs(z - 7.2) < 0.25 or v < -0.17 or v > -0.03 or z < 5.4 or z > 9.1:
                return "cream"
            return "blue_d" if (x + y) % 3 else "cyan"
        if n[2] < 0.5 and z % 3.0 < 0.9:
            return "brown_d"                                     # gap between logs
        if n[2] < 0.5 and z % 3.0 < 1.6:
            return "tan"                                         # lit top of each log
        return lit(WOOD[:3], n, x, y, crisp=True)

    def roof(tag, n, u, v, z, x, y):
        if face(n) == "R":
            return "brown_d" if seam(v, 4.0) else "brown"
        row = int(z / 2.0)
        if z - row * 2.0 < 0.6:
            return "brown_d"                                     # shingle course
        return lit(["brown", "brown_l", "tan"], n, x, y)

    def stump(tag, n, u, v, z, x, y):
        if face(n) == "T":
            return "tan" if int(math.hypot(u - 0.38, v - 0.30) * 16.0) % 2 else "brown_l"
        return lit(WOOD, n, x, y, crisp=True)

    def handle(tag, n, u, v, z, x, y):
        return lit(WOOD, n, x, y, crisp=True)

    def axe(tag, n, u, v, z, x, y):
        return lit(IRON, n, x, y, crisp=True)

    def stone(tag, n, u, v, z, x, y):
        if face(n) == "T":
            return "ink"
        return lit(["grey_d", "grey", "grey_l"], n, x, y, crisp=True)

    def log_end(uc, zc):
        def mat(tag, n, u, v, z, x, y):
            if face(n) == "L":                                   # round ends of the stacked logs
                d = math.hypot((u - uc) * 17.9, z - zc)
                if d < 3.2:
                    return "cream" if int(d / 1.5) % 2 else "tan"
                return "brown_d"
            return lit(WOOD, n, x, y, crisp=True)
        return mat

    S = [Solid(ext(rect(-0.34, 0.28, -0.31, 0.31), "z", 0.0, 13.0), walls),          # log walls
         Solid(gable(-0.03, 0.0, 1.0, 0.0, 0.31, 0.40, 13.0, 7.0), roof),            # roof
         Solid(ext(rect(0.10, 0.22, 0.12, 0.24), "z", 12.0, 19.0), stone),           # chimney
         Solid(ext(ell(0.17, 0.17, 10, 0.38, 0.30), "z", 0.0, 6.0), stump),          # stump
         Solid(ext(rect(0.37, 0.41, 0.27, 0.31), "z", 6.0, 14.0), handle),           # axe handle
         Solid(ext(rect(0.33, 0.45, 0.22, 0.36), "z", 11.0, 14.0), axe)]             # axe head
    for uc, zc, v0 in ((-0.22, 3.8, 0.36), (-0.02, 3.8, 0.36), (0.18, 3.8, 0.36),
                       (-0.12, 7.6, 0.36), (0.08, 7.6, 0.36)):
        S.append(Solid(ext(ell(0.16, 3.8, 10, uc, zc), "v", v0, 0.50), log_end(uc, zc)))   # stacked logs
    return S


# ---------------------------------------------------------------- tree nursery (1x1)

def tree_nursery():
    def bench(tag, n, u, v, z, x, y):
        f = face(n)
        if f == "T":
            return "brown_l" if seam(v, 3.0) else "tan"
        if f == "R":
            return "brown_d" if seam(v, 3.0) else "brown"
        return "brown_d" if seam(u, 3.0) else "brown_l"

    def clay(tag, n, u, v, z, x, y):
        if face(n) == "T":
            return "brown_d" if math.hypot(u, v) < 0.1 else "clay"              # soil in the pot
        return lit(["rust", "clay", "clay", "tan"], n, x, y, crisp=True)

    def leaf(tag, n, u, v, z, x, y):
        return lit(["green_dd", "green_d", "green", "green"], n, x, y)

    def sign(tag, n, u, v, z, x, y):
        if face(n) == "R":
            if abs(abs(v) - 0.26) < 0.03 or z < 13.4 or z > 19.6:
                return "brown"                                   # board frame
            if abs(z - 16.0) < 0.35 or abs(z - 17.9) < 0.35:
                return "green_d" if abs(v) < 0.18 else "cream"   # painted lines
            return "cream"
        return lit(WOOD, n, x, y, crisp=True)

    S = []
    for cu, cv in ((-0.4, -0.24), (0.4, -0.24), (-0.4, 0.24), (0.4, 0.24)):   # legs
        S.append(Solid(ext(rect(cu - 0.03, cu + 0.03, cv - 0.03, cv + 0.03), "z", 0.0, 8.0), bench))
    S.append(Solid(ext(rect(-0.42, 0.42, -0.30, 0.30), "z", 8.0, 10.0), bench))           # bench top
    S.append(Solid(ext(rect(-0.42, 0.42, -0.38, -0.2), "z", 16.0, 18.0), bench))          # back shelf
    for cu in (-0.40, 0.40):
        S.append(Solid(ext(rect(cu - 0.03, cu + 0.03, -0.37, -0.33), "z", 10.0, 16.0), bench))  # uprights

    def pot_with_sapling(cu, cv, zb, h):
        out = [Solid(ext(ell(0.10, 0.10, 10, cu, cv), "z", zb, zb + 2.0), clay),
               Solid(ext(ell(0.13, 0.13, 10, cu, cv), "z", zb + 2.0, zb + 5.0), clay)]
        out.append(Solid(cone(ell(0.17, 0.17, 8, cu, cv), zb + 5.0, zb + 5.0 + h * 0.6, cu, cv), leaf))
        out.append(Solid(cone(ell(0.10, 0.10, 7, cu, cv), zb + 5.0 + h * 0.4, zb + 5.0 + h, cu, cv), leaf))
        return out

    for i, cu in enumerate((-0.30, 0.0, 0.30)):                       # front row of saplings
        S += pot_with_sapling(cu, 0.06, 10.0, 12.0 + 2.0 * (i % 2))
    for i, cu in enumerate((-0.30, 0.0, 0.30)):                       # back row on the shelf
        S += pot_with_sapling(cu, -0.28, 18.0, 13.0 - 2.0 * (i % 2))
    S.append(Solid(ext(rect(0.36, 0.40, -0.04, 0.0), "z", 10.0, 16.0), bench))      # sign post
    S.append(Solid(ext([(-0.26, 13.0), (0.26, 13.0), (0.26, 20.0), (-0.26, 20.0)], "u", 0.40, 0.43), sign))
    return S


# ---------------------------------------------------------------- assembly

SPRITES = [("sawmill", sawmill), ("wood_chipper", wood_chipper), ("charcoal_kiln", charcoal_kiln),
           ("syrup_kettle", syrup_kettle), ("furnace", furnace), ("geode_anvil", geode_anvil),
           ("drill_rig", drill_rig), ("merchant_cart", merchant_cart), ("logging_hut", logging_hut),
           ("tree_nursery", tree_nursery)]
POST = {"syrup_kettle": leaf_post}


def main():
    s = Sprites(sys.argv[1], "buildings")
    for name, fn in SPRITES:
        emit(s, name, fn(), POST.get(name))
    s.done()


main()
