"""Villager face portraits (pixel-direct): mara, tobias, ivy, bram, juniper, pip, sela, fennel.

portraits/<id>  30x30 body, bust shot facing forward on a rounded-corner tile with no
frame. finish() adds the 1px ink outline and the hard drop shadow. Pivot = centre of the
body (+ pad). Endesga-32 only, checker dither between ramp steps, lit from the top-left,
the same shading model as the crop and icon modules.

Layout (index space, mirror x -> 29 - x, face centre line 14.5):
  head 4..19 rows, eyes 10..12, brows 8..9, nose 13..14, mouth 16..17, torso from row 22.
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, Sprites, new, finish  # noqa: E402
import math  # noqa: E402

W = H = 30
CX = 14.5
_L = (-0.55, -0.7, 0.6)
LIGHT = tuple(v / math.sqrt(sum(u * u for u in _L)) for v in _L)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def col(c):
    return E[c] if isinstance(c, str) else c


def tone(ramp, v, x, y):
    """Ramp colour for a fractional level v, checker-dithered between neighbouring steps."""
    n = len(ramp) - 1
    v = max(0.0, min(float(n), v))
    b = int(v)
    f = v - b
    if b >= n or f < 0.3:
        return col(ramp[b])
    if f > 0.7:
        return col(ramp[b + 1])
    return col(ramp[b + 1]) if (x + y) % 2 == 0 else col(ramp[b])


class Canvas:
    def __init__(self):
        self.im = new(W, H)
        self.px = self.im.load()

    def put(self, x, y, c):
        if c is not None and 0 <= x < W and 0 <= y < H:
            self.px[x, y] = col(c)


def mirror(pts):
    return {(W - 1 - x, y) for x, y in pts}


def superell(cx, cy, rx, ry, p=2.0):
    """Superellipse (p=2 is an ellipse, larger p is boxier) in index space."""
    return {(x, y) for y in range(H) for x in range(W) if (abs(x - cx) / rx) ** p + (abs(y - cy) / ry) ** p <= 1.0}


def rounded(r=7):
    """Rounded-corner tile mask: the portrait frame is this shape, no border."""
    out = set()
    for y in range(H):
        for x in range(W):
            dx = max(0.0, r - (x + 0.5), (x + 0.5) - (W - r))
            dy = max(0.0, r - (y + 0.5), (y + 0.5) - (H - r))
            if dx * dx + dy * dy <= r * r:
                out.add((x, y))
    return out


MASK = rounded(7)
HEAD = superell(CX, 11.5, 7.2, 8.2, 2.2) & MASK
NECK = {(x, y) for x in range(12, 18) for y in range(16, 24)} & MASK
TORSO = {p for p in superell(CX, 37, 13.5, 14.5, 2.6) if p[1] >= 21} & MASK


def dist_in(s):
    """BFS distance (1 = edge) of every pixel of s to the outside of s."""
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


def sh(c, s, ramp, depth=3.0, gain=1.0, off=0.0):
    """Dome-shade a pixel set, lit from the top-left (same model as crops_a / icons_a)."""
    dist = dist_in(s)
    hgt = {p: depth * math.sqrt(max(0.0, 1 - (1 - min(1.0, (d - 0.5) / depth)) ** 2)) for p, d in dist.items()}
    n = len(ramp) - 1
    for (x, y) in s:
        gx = (hgt.get((x + 1, y), 0) - hgt.get((x - 1, y), 0)) / 2
        gy = (hgt.get((x, y + 1), 0) - hgt.get((x, y - 1), 0)) / 2
        nl = math.sqrt(gx * gx + gy * gy + 1)
        lv = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / nl
        v = ((lv + 0.25) / 1.25) * n * gain + off
        c.put(x, y, tone(ramp, v, x, y))


def bg(c, ramp):
    """Backdrop: lighter at the top, darker at the bottom, dithered."""
    n = len(ramp) - 1
    for x, y in MASK:
        v = n * (1.0 - 0.8 * y / (H - 1) - 0.2 * x / (W - 1))
        c.put(x, y, tone(ramp, v, x, y))


def hairline(s, line, side_y=13, side_w=6.3):
    """Keep hair above the hairline (line(x) -> y) plus the locks that hang past the face."""
    return {(x, y) for (x, y) in s if y <= line(x) or (abs(x - CX) >= side_w and y <= side_y)}


def body(c, skin, cloth, back=(), head=HEAD):
    """Back hair, torso, neck and head, in that order, with the chin shadow on the neck."""
    for s, ramp in back:
        sh(c, s & MASK, ramp, depth=2.5)
    sh(c, TORSO, cloth, depth=3.0)
    sh(c, NECK, SKIN[skin], depth=1.5)
    sh(c, head, SKIN[skin], depth=3.5)
    for x, y in NECK:
        if y in (20, 21) and (x, y) not in head:
            c.put(x, y, SKIN[skin][0])


# ---------------------------------------------------------------- palettes

SKIN = {
    "skin": ["tan_d", "tan", "skin", "cream"],
    "tan": ["brown_l", "tan_d", "tan", "skin"],
    "tan_d": ["brown", "brown_l", "tan_d", "tan"],
    "brown_l": ["brown_d", "brown", "brown_l", "tan_d"],
}
HAIR = {
    "auburn": ["brown_d", "brown", "rust", "clay"],
    "brown": ["brown_d", "brown", "brown_l", "tan"],
    "amber": ["rust", "clay", "amber", "yellow"],
    "grey": ["slate", "grey_d", "grey", "grey_l"],
    "ink": ["ink", "slate_d", "slate", "grey_d"],
    "white": ["grey", "grey_l", "white"],
}
RED = ["purple", "red_d", "red", "pink"]
BLUE = ["slate_d", "blue_d", "blue", "cyan"]
PINK = ["purple", "magenta", "pink"]
TEAL = ["teal_dd", "blue_d", "blue"]
GREY = ["slate", "grey_d", "grey", "grey_l"]
BROWN = ["brown_d", "brown", "brown_l"]
CAP_BROWN = ["brown_d", "brown", "brown_l", "tan"]
CAP_ORANGE = ["rust", "red_d", "orange", "amber"]
STRIPE = ["red_d", "red", "pink"]

# ---------------------------------------------------------------- features

EYE = {
    "dot": [(10, 11), (11, 11), (10, 12), (11, 12)],
    "wide": [(10, 10), (11, 10), (10, 11), (11, 11), (10, 12), (11, 12)],
    "happy": [(9, 12), (10, 11), (11, 11), (12, 12)],
    "sly": [(10, 11), (11, 11), (11, 12)],
    "squint": [(10, 11), (11, 11)],
}
EYE_HL = {"dot": (10, 11), "wide": (10, 10)}
BROW = {
    "flat": [(10, 9), (11, 9)],
    "arch": [(9, 9), (10, 8), (11, 8)],
    "angry": [(9, 8), (10, 8), (11, 9)],
    "thin": [(9, 9), (10, 8), (11, 7)],
    "bushy": [(8, 9), (9, 8), (10, 8), (11, 8), (12, 9)],
}
MOUTH = {
    "smile": [(12, 16), (13, 17), (14, 17), (15, 17), (16, 17), (17, 16)],
    "flat": [(13, 17), (14, 17), (15, 17), (16, 17)],
    "smirk": [(13, 17), (14, 17), (15, 17), (16, 16), (17, 16)],
    "frown": [(12, 17), (13, 16), (14, 16), (15, 16), (16, 16), (17, 17)],
}


def eyes(c, kind, col_="ink", hl="white"):
    for x, y in EYE[kind]:
        c.put(x, y, col_)
        c.put(W - 1 - x, y, col_)
    if kind in EYE_HL and hl:
        x0, y0 = EYE_HL[kind]
        c.put(x0, y0, hl)
        c.put(W - 1 - (x0 + 1), y0, hl)


def brows(c, kind, col_="brown_d"):
    for x, y in BROW[kind]:
        c.put(x, y, col_)
        c.put(W - 1 - x, y, col_)


def mouth(c, kind, col_="brown"):
    for x, y in MOUTH[kind]:
        c.put(x, y, col_)


def grin(c):
    """Open grin: ink lips, white teeth."""
    for x in range(12, 18):
        c.put(x, 16, "ink")
    for x in range(13, 17):
        c.put(x, 17, "white")
        c.put(x, 18, "ink")


def nose(c, col_):
    c.put(15, 13, col_)
    c.put(15, 14, col_)


def blush(c, col_="pink"):
    for x, y in ((8, 14), (9, 14), (8, 15)):
        c.put(x, y, col_)
        c.put(W - 1 - x, y, col_)


def spots(c, pts, col_):
    for x, y in pts:
        c.put(x, y, col_)
        c.put(W - 1 - x, y, col_)


def ring(cx, cy, r0, r1):
    return {(x, y) for y in range(H) for x in range(W) if r0 <= math.hypot(x - cx, y - cy) <= r1}


# ---------------------------------------------------------------- characters

def mara(c):
    """Warm baker: auburn hair in a bun, red dress, white flour-dusted apron, rosy cheeks."""
    bg(c, ["amber", "yellow", "white"])
    body(c, "skin", RED)
    apron = {(x, y) for (x, y) in TORSO if 9 <= x <= 20 and y >= 25}
    sh(c, apron, ["grey_l", "cream", "white"], depth=2.0)
    spots(c, [(11, 23), (11, 24)], "cream")
    c.put(12, 27, "white")
    c.put(16, 29, "white")
    c.put(18, 26, "white")
    hair = hairline(superell(CX, 8.4, 8.4, 7.4, 2.2) & MASK, lambda x: 6.5 + 0.4 * abs(x - CX))
    bun = superell(CX, 3.0, 2.6, 2.6, 2.0) & MASK
    sh(c, hair | bun, HAIR["auburn"], depth=2.2)
    c.put(13, 1, "cream")
    c.put(14, 2, "cream")
    nose(c, SKIN["skin"][1])
    blush(c, "pink")
    eyes(c, "dot")
    brows(c, "arch", "brown")
    mouth(c, "smile", "brown")


def tobias(c):
    """Carpenter: brown cap, beard, blue work shirt with straps, pencil behind the ear."""
    bg(c, ["slate", "grey_d", "grey"])
    body(c, "tan", BLUE)
    spots(c, [(10, 23), (10, 24), (9, 25)], "brown_l")
    beard = {(x, y) for (x, y) in HEAD if y >= 15 and abs(x - CX) <= 6.2}
    sh(c, beard, HAIR["brown"], depth=2.0)
    for x in range(11, 19):
        c.put(x, 14, "brown")
    nose(c, SKIN["tan"][1])
    crown = {p for p in superell(CX, 5.6, 7.6, 4.8, 2.0) if p[1] <= 7} & MASK
    brim = {(x, 8) for x in range(5, 24)}
    sh(c, crown, CAP_BROWN, depth=2.5)
    sh(c, brim, ["brown_d", "brown", "brown_l"], depth=1.0)
    for x, y, cc in ((21, 10, "yellow"), (22, 9, "yellow"), (23, 8, "yellow"), (24, 7, "yellow"),
                     (22, 10, "amber"), (23, 9, "amber"), (24, 8, "amber"), (25, 6, "pink")):
        c.put(x, y, cc)
    eyes(c, "dot")
    brows(c, "angry", "brown_d")
    mouth(c, "flat", "brown_d")


def ivy(c):
    """Florist: amber hair with a pink flower, pink dress with a green collar, cheerful grin."""
    bg(c, ["green_dd", "green_d", "green"])
    back = [(superell(CX, 13.0, 9.6, 12.6, 2.2) & MASK, HAIR["amber"])]
    body(c, "skin", PINK, back=back)
    for x, y in ((10, 26), (18, 25), (14, 28)):
        c.put(x, y, "yellow")
    hair = hairline(superell(CX, 8.4, 8.6, 7.6, 2.2) & MASK, lambda x: 6.0 + 0.7 * abs(x - CX), side_y=12)
    sh(c, hair, HAIR["amber"], depth=2.2)
    for x, y in ((20, 5), (19, 6), (21, 6), (20, 7)):
        c.put(x, y, "pink")
    c.put(20, 6, "yellow")
    blush(c, "pink")
    nose(c, SKIN["skin"][1])
    eyes(c, "wide")
    brows(c, "arch", "brown")
    grin(c)


def bram(c):
    """Blacksmith: bald, soot on forehead and cheeks, big moustache, leather apron, broad shoulders."""
    bg(c, ["red_d", "rust", "orange"])
    ears = {(6, 10), (7, 10), (6, 11), (7, 11), (6, 12), (7, 12)}
    ears |= mirror(ears)
    body(c, "tan_d", GREY, head=HEAD | ears)
    apron = {(x, y) for (x, y) in TORSO if 9 <= x <= 20 and y >= 24}
    sh(c, apron, BROWN, depth=2.5)
    spots(c, [(10, 23), (9, 24), (11, 22)], "brown_d")
    for x, y in ((12, 6), (13, 7), (15, 6), (16, 7)):
        c.put(x, y, "slate_d")
    spots(c, [(9, 13), (10, 14), (9, 14)], "slate_d")
    for x in range(11, 19):
        c.put(x, 14, "brown_d")
        c.put(x, 15, "brown_d" if x not in (11, 18) else "brown")
    c.put(12, 14, "brown")
    c.put(17, 14, "brown")
    for x, y in ((11, 5), (12, 5), (13, 5)):
        c.put(x, y, "cream")
    eyes(c, "dot")
    brows(c, "angry", "brown_d")
    mouth(c, "frown", "brown_d")


def juniper(c):
    """Innkeeper: grey hair in a bun, round-cornered cat-eye glasses, red shawl, kind smile."""
    bg(c, ["purple", "magenta", "pink"])
    body(c, "tan", TEAL)
    shawl = {(x, y) for (x, y) in TORSO if y >= 22 and (abs(x - CX) >= 4 or y >= 28)}
    sh(c, shawl, ["red_d", "red", "pink"], depth=2.5)
    bun = superell(19.6, 4.6, 2.6, 2.6, 2.0) & MASK
    hair = hairline(superell(CX, 8.6, 8.4, 7.4, 2.2) & MASK, lambda x: 5.8 + 0.25 * abs(x - CX), side_y=13)
    sh(c, bun | hair, HAIR["grey"], depth=2.4)
    frame = {(x, 10) for x in range(8, 13)} | {(x, 13) for x in range(8, 13)} | {(7, 11), (7, 12), (13, 11), (13, 12)}
    lens = {(x, y) for x in range(8, 13) for y in (11, 12)}
    for p in frame | mirror(frame):
        c.put(p[0], p[1], "brown_d")
    for p in lens | mirror(lens):
        c.put(p[0], p[1], "cyan")
    c.put(14, 11, "brown_d")
    c.put(15, 11, "brown_d")
    nose(c, SKIN["tan"][1])
    spots(c, [(9, 15), (10, 16)], SKIN["tan"][1])
    blush(c, "pink")
    eyes(c, "dot", hl=None)
    brows(c, "arch", "slate")
    mouth(c, "smile", "brown")


def pip(c):
    """Village kid: orange cap, messy brown hair, freckles, gap-toothed grin, striped shirt."""
    bg(c, ["blue_d", "blue", "cyan"])
    body(c, "skin", STRIPE)
    for y in (25, 28):
        for x, yy in ((x, y) for x in range(W)):
            if (x, yy) in TORSO and (x + yy) % 2 == 0:
                c.put(x, yy, "white")
    hair = superell(CX, 6.2, 8.4, 6.0, 2.0) & MASK & {p for p in MASK if p[1] >= 3}
    sh(c, hair, HAIR["brown"], depth=2.0)
    crown = {p for p in superell(CX, 6.0, 7.6, 4.8, 2.0) if p[1] <= 6} & MASK
    sh(c, crown, CAP_ORANGE, depth=2.5)
    for x in range(5, 25):
        c.put(x, 7, "rust")
    nose(c, SKIN["skin"][1])
    spots(c, [(9, 13), (11, 14), (8, 15)], "brown_l")
    spots(c, [(13, 13), (15, 14)], "brown_l")
    blush(c, "pink")
    eyes(c, "wide")
    brows(c, "thin", "brown_d")
    for x in range(12, 18):
        c.put(x, 16, "ink")
    for x in range(13, 17):
        c.put(x, 17, "white")
    c.put(14, 17, "ink")
    for x in range(13, 17):
        c.put(x, 18, "ink")


def sela(c):
    """Vintner: sleek dark hair, sly half-lidded eyes, purple scarf, red lips."""
    bg(c, ["slate_d", "slate", "grey_d"])
    back = [(superell(CX, 13.5, 9.8, 9.2, 2.2) & MASK & {p for p in MASK if p[1] <= 21}, HAIR["ink"])]
    body(c, "skin", ["brown_d", "brown", "red_d"], back=back)
    wrap = {(x, y) for x in range(9, 21) for y in range(20, 24)} | {(x, 24) for x in range(8, 22)}
    wrap &= MASK
    sh(c, wrap, PINK, depth=2.2)
    sh(c, {(17, 24), (18, 25), (18, 26), (19, 27), (19, 28), (20, 26), (20, 27)} & MASK, PINK, depth=1.5)
    hair = hairline(superell(CX, 8.6, 8.6, 7.6, 2.2) & MASK, lambda x: 6.0 + 0.5 * abs(x - 11.5), side_y=13)
    sh(c, hair, HAIR["ink"], depth=2.2)
    spots(c, [(19, 6), (20, 6)], "pink")
    nose(c, SKIN["skin"][1])
    spots(c, [(9, 15)], "brown_d")
    eyes(c, "sly")
    brows(c, "arch", "brown_d")
    mouth(c, "smirk", "red")


def fennel(c):
    """Doc Fennel: weathered old herbalist, white hair at the sides, round spectacles, herb sprig."""
    bg(c, ["teal_dd", "teal_dd", "blue_d"])
    body(c, "brown_l", BROWN)
    lapel = {(13, 22), (14, 22), (15, 22), (16, 22), (14, 23), (15, 23)}
    for p in lapel:
        c.put(p[0], p[1], "cream")
    side = {p for p in superell(CX, 9.0, 8.8, 9.6, 2.0) if abs(p[0] - CX) >= 6.3 and p[1] <= 13} & MASK
    sh(c, side, HAIR["white"], depth=2.0)
    for x, y in ((10, 3), (11, 4), (18, 4), (19, 3)):
        c.put(x, y, "grey_l")
    beard = {(x, y) for (x, y) in HEAD if y >= 17 and abs(x - CX) <= 4.5}
    sh(c, beard, HAIR["white"], depth=1.5)
    for x, y in ((12, 5), (13, 5), (14, 5)):
        c.put(x, y, "cream")
    rim = ring(10.5, 11.0, 2.2, 3.1) | ring(18.5, 11.0, 2.2, 3.1)
    lens = ring(10.5, 11.0, 0.0, 2.1) | ring(18.5, 11.0, 0.0, 2.1)
    for x, y in lens:
        if 0 <= x < W and 0 <= y < H and (x, y) in mirror(lens) | lens:
            c.put(x, y, "cream")
    for x, y in rim:
        c.put(x, y, "amber")
    c.put(14, 11, "amber")
    c.put(15, 11, "amber")
    nose(c, SKIN["brown_l"][2])
    for x in range(11, 19):
        c.put(x, 15, "grey_l")
    mouth(c, "smile", "brown_d")
    eyes(c, "dot", hl=None)
    brows(c, "bushy", "grey_l")
    for x, y, cc in ((21, 25, "brown"), (22, 24, "green_d"), (22, 23, "green"), (23, 22, "green"),
                     (23, 21, "green"), (24, 20, "green_d"), (24, 19, "green"), (25, 19, "green"),
                     (25, 20, "green_d"), (23, 20, "green")):
        c.put(x, y, cc)


PORTRAITS = [("mara", mara), ("tobias", tobias), ("ivy", ivy), ("bram", bram),
             ("juniper", juniper), ("pip", pip), ("sela", sela), ("fennel", fennel)]


def main():
    sp = Sprites(sys.argv[1], "portraits")
    for name, fn in PORTRAITS:
        c = Canvas()
        fn(c)
        out, pad = finish(c.im)
        sp.save(name, out, 15 + pad, 15 + pad)
    sp.done()


main()
