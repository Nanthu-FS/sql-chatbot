"""Field fixtures that stand on the corner between four farm plots (pixel-direct, folder "deco"):
copper, iron and gold sprinklers, a scarecrow with a snow-covered twin, and a lightning rod.

Each fixture is drawn around its pivot (0, 0): the centre of its round iso foot, which is the ground
point of the corner where four plots meet. That is the footprint-centre pivot buildings.py uses, so
the object sits on the vertex and not on a tile edge. Round parts are 2:1 iso ellipses. Shading is
lambert against a top-left key light, checker-dithered between palette steps with bayer(), and
finish() adds the 1px ink outline and the hard drop shadow.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E402
import math  # noqa: E402
from _pix import E, Sprites, bayer, finish, new  # noqa: E402

W, H = 48, 72          # working canvas
OX, OY = 24, 56        # the pivot on the canvas

LX, LY, LZ = -0.55, -0.6, 0.6          # key light: up, left and toward the viewer
LLEN = math.sqrt(LX * LX + LY * LY + LZ * LZ)

COPPER = ["brown", "rust", "clay", "orange", "tan"]
IRON = ["slate", "grey_d", "grey", "grey_l", "white"]
GOLD = ["rust", "clay", "orange", "amber", "yellow", "cream"]
DIRT = ["brown", "tan_d", "tan"]


def shade(ramp, t, x, y):
    """Palette colour at t in 0..1 along ramp (0 = darkest), checker-dithered between the two steps."""
    t = max(0.0, min(1.0, t))
    f = t * (len(ramp) - 1)
    b = min(int(f), len(ramp) - 2)
    level = int(round((f - b) * 16))
    return E[ramp[b + 1] if bayer(x, y, level) else ramp[b]]


def lambert(nx, ny, nz):
    return (nx * LX + ny * LY + nz * LZ) / LLEN


def to_t(i):
    """Map a lambert term (-1..1) onto a ramp position, keeping the shadow side dark but not black."""
    return (i + 0.25) / 1.25


def tone_sphere(ramp, u, v, rx, ry, x, y):
    nx, ny = u / float(rx), v / float(ry)
    nz = math.sqrt(max(0.0, 1.0 - nx * nx - ny * ny))
    return shade(ramp, to_t(lambert(nx, ny, nz)), x, y)


def tone_cyl(ramp, u, r, x, y):
    nx = max(-1.0, min(1.0, u / float(r)))
    nz = math.sqrt(1.0 - nx * nx)
    return shade(ramp, to_t(lambert(nx, 0.0, nz)), x, y)


class Art:
    """Working canvas. (u, v) are offsets from the pivot: u to the right, v down, as on screen."""

    def __init__(self):
        self.im = new(W, H)
        self.px = self.im.load()

    def put(self, u, v, c):
        x, y = OX + u, OY + v
        if c is None or not (0 <= x < W and 0 <= y < H):
            return
        self.px[x, y] = E[c] if isinstance(c, str) else c

    def ellipse(self, cu, cv, rx, ry, fn):
        """Filled 2:1 iso ellipse. fn(u, v, x, y) returns a colour, or None to leave the pixel alone."""
        for v in range(cv - ry, cv + ry + 1):
            for u in range(cu - rx, cu + rx + 1):
                if ((u - cu) / float(rx)) ** 2 + ((v - cv) / float(ry)) ** 2 <= 1.0:
                    self.put(u, v, fn(u, v, OX + u, OY + v))

    def blit(self, rows, key, cu, cv):
        """Character map: map cell (col, row) lands at offset (col - cu, row - cv); '.' is empty."""
        for r, row in enumerate(rows):
            for c, ch in enumerate(row):
                if ch != ".":
                    self.put(c - cu, r - cv, key[ch])

    def save(self, sp, name):
        x0, y0, x1, y1 = self.im.getbbox()
        body = self.im.crop((x0, y0, x1, y1))
        out, pad = finish(body)
        sp.save(name, out, OX - x0 + pad, OY - y0 + pad)


def line(a, p0, p1, c):
    (u0, v0), (u1, v1) = p0, p1
    n = max(abs(u1 - u0), abs(v1 - v0))
    for i in range(n + 1):
        t = i / float(n)
        a.put(int(round(u0 + (u1 - u0) * t)), int(round(v0 + (v1 - v0) * t)), c)


def foot(a, rx, ry, top_fn, side_a, side_b):
    """Round iso foot: a flat top with a 1px darker rim on the front (lit from the left)."""
    a.ellipse(0, 1, rx, ry, lambda u, v, x, y: side_a if u < rx // 2 else side_b)
    a.ellipse(0, 0, rx, ry, top_fn)


# ---------------------------------------------------------------- copper sprinkler (~13x16)

def copper_sprinkler(a):
    foot(a, 4, 2, lambda u, v, x, y: shade(COPPER, 0.8 - 0.4 * u / 4.0, x, y), "rust", "brown")
    for v in (-2, -3, -4):                               # short pipe
        for u in (-1, 0, 1):
            a.put(u, v, tone_cyl(COPPER, u, 1.5, OX + u, OY + v))
    for v in (-5, -4):                                   # head body under the flared top
        for u in range(-3, 4):
            a.put(u, v, tone_cyl(COPPER, u, 3.5, OX + u, OY + v))
    a.ellipse(0, -6, 3, 2, lambda u, v, x, y: shade(COPPER, 0.95 - 0.5 * u / 3.0, x, y))  # head top
    a.put(0, -8, "clay")                                 # nozzle
    a.put(0, -9, "ink")
    for s in (-1, 1):                                    # the spray: a fan of droplets on each side
        for u, v, c in ((2, -11, "cyan"), (3, -12, "white"), (4, -10, "cyan"), (5, -8, "cyan"),
                        (6, -6, "blue"), (5, -11, "blue")):
            a.put(s * u, v, c)


# ---------------------------------------------------------------- iron sprinkler (~15x16)

def iron_sprinkler(a):
    # rotary iron sprinkler: a stem on a round foot, a spray arm with a nozzle at each tip, a cap in the middle
    foot(a, 6, 2, lambda u, v, x, y: shade(IRON, 0.85 - 0.5 * u / 6.0, x, y), "slate", "grey_d")
    for v in range(-7, 0):                               # stem, 3px wide, rooted in the foot
        for u in (-1, 0, 1):
            a.put(u, v, tone_cyl(IRON, u, 1.5, OX + u, OY + v))
    for u in range(-7, 8):                               # spray arm: two rows of iron pipe
        a.put(u, -8, "grey_l" if abs(u) < 6 else "grey")
        a.put(u, -7, "grey" if abs(u) < 6 else "grey_d")
    for s in (-1, 1):                                    # nozzle at each tip, outlet facing up
        for v, c in ((-8, "grey"), (-9, "grey_l"), (-10, "ink")):
            a.put(s * 8, v, c)
        a.put(s * 7, -9, "grey_d")
    for u, v, c in ((-1, -9, "grey_l"), (0, -9, "white"), (1, -9, "grey"), (0, -10, "grey_l")):
        a.put(u, v, c)                                   # rotating cap at the centre of the arm
    for s in (-1, 1):                                    # water falling in arcs off each nozzle
        for u, v, c in ((9, -7, "cyan"), (10, -5, "cyan"), (11, -3, "blue"), (12, -1, "cyan")):
            a.put(s * u, v, c)


# ---------------------------------------------------------------- gold sprinkler (~15x17)

def gold_sprinkler(a):
    foot(a, 6, 2, lambda u, v, x, y: shade(GOLD, 0.8 - 0.4 * u / 6.0, x, y), "clay", "rust")
    for v in (-1, -2):                                   # neck
        for u in range(-2, 3):
            a.put(u, v, tone_cyl(GOLD, u, 2.5, OX + u, OY + v))
    for u in range(-3, 4):                               # collar under the ball
        a.put(u, -3, tone_cyl(GOLD, u, 3.5, OX + u, OY - 3))
    a.ellipse(0, -8, 4, 4, lambda u, v, x, y: tone_sphere(GOLD, u, v, 4, 4, x, y))   # ball
    for u, v in ((-2, -10), (-1, -11), (-3, -8)):        # glints on the top-left
        a.put(u, v, "white")
    # blue gem set in the front of the ball
    a.put(0, -10, "cyan")
    for u in (-1, 0, 1):
        a.put(u, -9, "cyan" if u == 0 else "blue")
    for u in (-2, -1, 0, 1, 2):
        a.put(u, -8, "blue_d" if u == 2 else ("cyan" if u == -1 else "blue"))
    for u in (-1, 0, 1):
        a.put(u, -7, "blue_d" if u == 1 else "blue")
    a.put(0, -6, "blue_d")
    a.put(0, -13, "amber")                               # finial
    a.put(0, -14, "yellow")


# ---------------------------------------------------------------- scarecrow (~21x35)

def centred(s, w=21):
    return s.center(w, ".")


SCARE = [
    centred("hhhhhhh"), centred("hHhhhhd"), centred("hhhhhhd"), centred("bbbbbbb"),    # hat
    centred("LLLLLLLLLLLLLLL"), centred("BBBBBBBBBBBBBBBBB"), centred("ddddddddddddddddd"),   # brim
    centred("fffffffff"), centred("ffefffeff"), centred("ffffffffF"),                 # burlap head
    centred("fffxxxfff"), centred("ffffffffF"), centred("FFFFFFFFF"),
    "ss" + "rrrr" + "rrrrrrrrr" + "rrrr" + "ss",                                       # outstretched arms
    "kk" + "RRRR" + "rrrrrrrrr" + "RRRR" + "kk",
    centred("rrrrrrrrR"), centred("rxxxxrrrR"), centred("rpppprrrR"),                # patched shirt
    centred("rxxxxrrrR"), centred("rrrrrnnnR"), centred("kkkkkkkkk"),
    centred("sSsSsSsSsSsSsSs"), centred("k" + "sSsSsSsSsSsSsSs" + "k"),              # straw skirt
    centred("sSsknNnwnNnksSs"), centred("ksSnNnwnNnSsk"), centred("sSsnNnwnNnsSs"),  # legs and pole
    centred("nNnwnNn"), centred("nNnwnNn"), centred("xxxwxxx"), centred("w"),
]
SCARE_KEY = {
    "h": "brown", "H": "brown_l", "d": "brown_d", "b": "red_d", "L": "brown_l", "B": "brown",
    "f": "cream", "F": "tan", "e": "ink", "x": "brown_d",
    "r": "red", "R": "red_d", "p": "tan", "n": "blue_d", "N": "slate",
    "s": "amber", "S": "yellow", "k": "clay", "w": "brown",
    "W": "white", "G": "grey_l",
}
SNOW_ROWS = set(range(0, 7)) | {13}      # hat, brim and the top of the arms


def snow_map(rows):
    """Snow on every upward-facing edge in the hat and arm rows; some cells dither to grey_l."""
    out = []
    for r, row in enumerate(rows):
        cells = list(row)
        for c, ch in enumerate(row):
            if ch == "." or r not in SNOW_ROWS:
                continue
            above = rows[r - 1][c] if r > 0 else "."
            if above == ".":
                cells[c] = "G" if (c + r) % 3 == 0 else "W"
        out.append("".join(cells))
    return out


def mound(a, winter):
    """Dirt mound the pole stands in. The ring is 2:1 iso, so the scarecrow stands on the corner."""
    ramp = ["grey_l", "white"] if winter else DIRT
    a.ellipse(0, 1, 6, 3, lambda u, v, x, y: "brown_d" if u > 0 else "brown")
    a.ellipse(0, 0, 6, 3, lambda u, v, x, y: shade(ramp, 0.9 - 0.45 * u / 6.0, x, y))


def scarecrow(a, winter):
    assert all(len(r) == 21 for r in SCARE), "scarecrow rows must be 21 wide"
    mound(a, winter)
    rows = snow_map(SCARE) if winter else SCARE
    a.blit(rows, SCARE_KEY, 10, 30)                      # pivot = mound centre (map col 10, row 30)


# ---------------------------------------------------------------- lightning rod (~12x41)

POLE_TOP = -33


def lightning_rod(a):
    foot(a, 4, 2, lambda u, v, x, y: shade(["grey", "grey_l", "white"], 0.55 - 0.35 * u / 4.0, x, y),
         "grey_d", "slate")
    for s in (-1, 1):                                    # guy wires, drawn first so the pole covers them
        line(a, (s * 1, -22), (s * 6, 1), "grey_l")
        a.put(s * 6, 1, "slate")
    for v in (-1, -2):                                   # iron neck
        for u in range(-2, 3):
            a.put(u, v, tone_cyl(IRON, u, 2.5, OX + u, OY + v))
    for v in range(-3, POLE_TOP + 1, -1):                # pole, 3px wide
        for u in (-1, 0, 1):
            a.put(u, v, tone_cyl(IRON, u, 1.5, OX + u, OY + v))
    for v in (-14, -28):                                 # collars
        for u in range(-2, 3):
            a.put(u, v, "grey_d" if u > 0 else "grey")
    for u in range(-2, 3):                               # copper spike cap and tip
        a.put(u, POLE_TOP, tone_cyl(COPPER, u, 2.5, OX + u, OY + POLE_TOP))
    for u in (-1, 0, 1):
        a.put(u, POLE_TOP - 1, tone_cyl(COPPER, u, 1.5, OX + u, OY + POLE_TOP - 1))
        a.put(u, POLE_TOP - 2, tone_cyl(COPPER, u, 1.0, OX + u, OY + POLE_TOP - 2))
    a.put(0, POLE_TOP - 3, "tan")
    a.put(0, POLE_TOP - 4, "cream")


def main():
    sp = Sprites(sys.argv[1], "deco")
    for name, fn in (("copper_sprinkler", copper_sprinkler), ("iron_sprinkler", iron_sprinkler),
                     ("gold_sprinkler", gold_sprinkler), ("lightning_rod", lightning_rod)):
        a = Art()
        fn(a)
        a.save(sp, name)
    for name, winter in (("scarecrow", False), ("scarecrow_snow", True)):
        a = Art()
        scarecrow(a, winter)
        a.save(sp, name)
    sp.done()


main()
