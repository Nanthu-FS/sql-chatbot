"""Rocks (pixel-direct): quarry and mine objects, written to art/rocks/.

Names follow the node ids in data/mine.json. MineScreen.rockSprite() maps amethyst_node to
gem_node_amethyst (and the other gems), boulder to boulder, glow_mushroom to
cave_mushroom_cluster. It also draws rubble, crack_1/crack_2, ladder_hole, mine_mouth,
mine_cart, torch_wall and support_beam_prop.

Pivot = base centre (centre column of the bottom row, plus the finish() pad), so a node sits
on its tile. Two exceptions: ladder_hole pivots on the centre of its iso diamond (it is drawn at
the tile centre), and crack_1/crack_2 are overlays that share the rock's base-centre pivot.

Endesga-32 only, Bayer dither between ramp steps, light from the top-left, finished with
finish() (1px ink outline + hard drop shadow).
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import BAYER, E, Sprites, diamond_rows, finish, new  # noqa: E402
import math  # noqa: E402
import random  # noqa: E402

_L = (-0.55, -0.7, 0.6)
_LN = math.sqrt(sum(v * v for v in _L))
LIGHT = tuple(v / _LN for v in _L)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

# Ramps run dark -> light.
STONE = ["slate_d", "slate", "grey_d", "grey", "grey_l", "white"]
STONE_DK = ["ink", "slate_d", "slate", "grey_d", "grey"]
STONE_BACK = ["slate_d", "slate", "grey_d", "grey"]
CLAY = ["brown_d", "brown", "brown_l", "clay", "tan"]
COAL = ["ink", "ink", "slate_d", "slate", "grey"]
IRON = ["slate_d", "slate", "grey_d", "grey", "grey_l"]
CAP = ["blue_d", "blue", "cyan", "white"]
WOOD = ["brown_d", "brown", "brown_l", "tan_d", "tan", "cream"]
HOLE = ["ink", "ink", "ink", "slate_d", "slate"]
WHEEL = ["ink", "slate_d", "slate", "grey"]
TOPWOOD = ["brown_l", "tan_d", "tan", "cream"]

GEM_ROWS = [
    ".LLLMMDD.",
    "LGLLMMDDD",
    "LLLLMMDDD",
    ".LLLMMDD.",
    "..LLMDD..",
    "...LMD...",
    "....M....",
]


# ---------------------------------------------------------------- canvas and tone

def col(c):
    return E[c] if isinstance(c, str) else c


def tone(ramp, v, x, y):
    """Ramp colour at level v (0..len-1), Bayer-dithered between the two nearest steps."""
    n = len(ramp) - 1
    v = min(max(v, 0.0), float(n))
    b = int(v)
    if b >= n:
        return col(ramp[n])
    f = v - b
    return col(ramp[b + 1]) if f > (BAYER[y % 4][x % 4] + 0.5) / 16 else col(ramp[b])


class Pic:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = new(w, h)
        self.px = self.im.load()

    def put(self, x, y, c):
        if c is not None and 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = col(c)

    def on(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h and self.px[x, y][3] > 0


def emit(sp, name, pic, outline=True, shadow=True, ax=None, ay=None):
    """Finish (pad, drop shadow, ink outline) and save. Default pivot: bottom centre."""
    out, pad = finish(pic.im, outline=outline, shadow=shadow)
    sp.save(name, out, ((pic.w - 1) // 2 if ax is None else ax) + pad,
            (pic.h - 1 if ay is None else ay) + pad)


def paint(pic, cols, sep=None):
    """Paint {(x, y): colour}. Cells touching pixels that were already on the canvas (another
    shape) get the sep colour, which draws the seam between overlapping rocks."""
    seam = []
    if sep is not None:
        seam = [p for p in cols if any(pic.on(p[0] + dx, p[1] + dy) and (p[0] + dx, p[1] + dy) not in cols
                                       for dx, dy in N4)]
    for (x, y), c in cols.items():
        pic.put(x, y, c)
    for x, y in seam:
        pic.put(x, y, sep)


# ---------------------------------------------------------------- shapes

def ellipse(cx, cy, rx, ry, k=1.0):
    """Pixels whose centres fall inside an ellipse. cx, cy are pixel indices."""
    out = set()
    for y in range(math.floor(cy - ry) - 1, math.ceil(cy + ry) + 2):
        for x in range(math.floor(cx - rx) - 1, math.ceil(cx + rx) + 2):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= k:
                out.add((x, y))
    return out


def move(cells, dx, dy):
    return {(x + dx, y + dy) for x, y in cells}


def stone_sil(w, h, seed, lumps=2, amp=0.06, p_top=2.0, p_bot=4.5):
    """Lumpy superellipse filling a w x h box: round on top, flatter on the ground side."""
    r = random.Random(seed)
    waves = [(2 + i, r.uniform(0, 2 * math.pi), amp * r.uniform(0.5, 1.0)) for i in range(lumps)]
    s = set()
    for y in range(h):
        for x in range(w):
            u = (x + 0.5 - w / 2) / (w / 2)
            v = (y + 0.5 - h / 2) / (h / 2)
            th = math.atan2(v, u)
            lim = 1 + sum(a * math.sin(k * th + ph) for k, ph, a in waves)
            p = p_top if v < 0 else p_bot
            if abs(u) ** p + abs(v) ** p <= lim ** p:
                s.add((x, y))
    return s


def edge_dist(sil):
    """Distance in pixels from the silhouette edge (edge pixels are 1)."""
    dist, q = {}, []
    for p in sil:
        if any((p[0] + dx, p[1] + dy) not in sil for dx, dy in N4):
            dist[p] = 1
            q.append(p)
    i = 0
    while i < len(q):
        x, y = q[i]
        i += 1
        for dx, dy in N4:
            n = (x + dx, y + dy)
            if n in sil and n not in dist:
                dist[n] = dist[(x, y)] + 1
                q.append(n)
    return dist


def shade_cols(sil, ramp, depth=2.5, gain=0.8, off=-0.4, tilt=1.0):
    """Dome shading over a silhouette, lit from the top-left, with a light top-left rim and a
    dark bottom-right rim. tilt adds a top-left to bottom-right falloff across the body.
    Returns ({pixel: colour}, edge distance)."""
    dist = edge_dist(sil)
    hgt = {p: depth * math.sqrt(max(0.0, 1 - (1 - min(1.0, (d - 0.5) / depth)) ** 2)) for p, d in dist.items()}
    n = len(ramp) - 1
    ys = [p[1] for p in sil]
    xs = [p[0] for p in sil]
    y0, y1, x0, x1 = min(ys), max(ys), min(xs), max(xs)
    cols = {}
    for (x, y) in sil:
        gx = (hgt.get((x + 1, y), 0) - hgt.get((x - 1, y), 0)) / 2
        gy = (hgt.get((x, y + 1), 0) - hgt.get((x, y - 1), 0)) / 2
        nl = math.sqrt(gx * gx + gy * gy + 1)
        lv = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / nl
        ty = (y - y0) / max(1, y1 - y0)
        tx = (x - x0) / max(1, x1 - x0)
        v = ((lv + 0.75) / 1.5) * n * gain + off + tilt * (1.2 * (0.5 - ty) + 0.5 * (0.5 - tx))
        cols[(x, y)] = tone(ramp, v, x, y)
    for p, d in dist.items():
        if d != 1:
            continue
        x, y = p
        if (x, y - 1) not in sil or (x - 1, y) not in sil:
            cols[p] = col(ramp[max(0, n - 1)])
        elif (x + 1, y) not in sil or (x, y + 1) not in sil:
            cols[p] = col(ramp[0])
    return cols, dist


def layer(pic, sil, ramp, sep=None, **kw):
    cols, dist = shade_cols(sil, ramp, **kw)
    paint(pic, cols, sep)
    return dist


def rock_node(w, h, seed, ramp=STONE, **kw):
    pic = Pic(w, h)
    sil = stone_sil(w, h, seed, **kw)
    dist = layer(pic, sil, ramp)
    return pic, sil, dist


def line_pts(a, b):
    """Bresenham line from a to b, both ends included."""
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


def crack(pic, pts, colour, force=False):
    """Jagged 1px crack through pts, with a light lip on the pixel to its right."""
    cells = set()
    for a, b in zip(pts, pts[1:]):
        cells.update(line_pts(a, b))
    for x, y in cells:
        pic.put(x, y, colour)
    for x, y in cells:
        if (x + 1, y) not in cells and (force or pic.on(x + 1, y)):
            pic.put(x + 1, y, "white")


def specks(pic, dist, rng, n, colours, pred=None, mind=2):
    cells = sorted(p for p, d in dist.items() if d >= mind and (pred is None or pred(*p)))
    for x, y in rng.sample(cells, min(n, len(cells))):
        pic.put(x, y, rng.choice(colours))


def vein(pic, sil, a, b, rng, n, colours, glint=None, jitter=1.5):
    """Ore flecks (2px) scattered along a line through the rock, kept off the edge."""
    inner = {p for p in sil if all((p[0] + dx, p[1] + dy) in sil for dx, dy in N4)}
    for i in range(n):
        t = (i + rng.random()) / n
        x = round(a[0] + (b[0] - a[0]) * t + rng.uniform(-jitter, jitter))
        y = round(a[1] + (b[1] - a[1]) * t + rng.uniform(-jitter, jitter))
        if (x, y) not in inner or (x + 1, y) not in inner:
            continue
        c = rng.choice(colours)
        pic.put(x, y, c)
        pic.put(x + 1, y, c)
        if glint is not None and (x, y - 1) in inner:
            pic.put(x, y - 1, glint)


def gem(pic, cx, top, cols):
    """Faceted gem (9x9) embedded in the top of a dark rock. cols: dark, mid, light, glint."""
    dark, mid, light, glint = cols
    key = {"L": light, "M": mid, "D": dark, "G": glint}
    cells = {}
    for j, row in enumerate(GEM_ROWS):
        for i, ch in enumerate(row):
            if ch in key:
                cells[(cx - 4 + i, top + j)] = key[ch]
    paint(pic, cells, sep="ink")


def mushroom(pic, cx, cap_c, rx, ry, floor_y):
    """Glow mushroom: a domed cyan cap on a two-pixel cream stem down to floor_y."""
    for y in range(cap_c, floor_y + 1):
        pic.put(cx - 1, y, "cream")
        pic.put(cx, y, "tan")
    cap = {p for p in ellipse(cx, cap_c, rx, ry) if p[1] <= cap_c}
    layer(pic, cap, CAP)
    pic.put(cx - 1, cap_c - int(ry) + 1, "white")
    pic.put(cx + 1, cap_c - 1, "white")


# ---------------------------------------------------------------- stone nodes

def s_rock(sp):
    pic, sil, dist = rock_node(25, 15, 11, lumps=2, amp=0.06)
    rng = random.Random(3)
    crack(pic, [(9, 5), (11, 7), (10, 9)], "slate_d")
    specks(pic, dist, rng, 3, ["white", "grey_l"], lambda x, y: x < 12 and y < 8)
    specks(pic, dist, rng, 2, ["grey_d"], lambda x, y: x > 12 and y > 6)
    emit(sp, "rock", pic)


def s_rock_big(sp):
    pic = Pic(29, 19)
    back = move(stone_sil(13, 11, 21, lumps=1, amp=0.05), 16, 8)
    front = move(stone_sil(21, 17, 22, lumps=2, amp=0.07), 0, 2)
    layer(pic, back, STONE_BACK)
    dist = layer(pic, front, STONE, sep="slate_d")
    rng = random.Random(4)
    crack(pic, [(7, 8), (9, 10), (8, 12)], "slate_d")
    specks(pic, dist, rng, 4, ["white", "grey_l"], lambda x, y: y < 9)
    emit(sp, "rock_big", pic)


def s_boulder(sp):
    pic, sil, dist = rock_node(35, 26, 31, lumps=3, amp=0.08, p_top=2.0, p_bot=4.0)
    rng = random.Random(5)
    crack(pic, [(12, 9), (15, 12), (14, 16), (18, 19)], "slate_d")
    vein(pic, sil, (8, 17), (22, 11), rng, 7, ["rust", "red_d", "clay"], glint="grey_l")
    specks(pic, dist, rng, 5, ["white", "grey_l"], lambda x, y: y < 12 and x < 18)
    emit(sp, "boulder", pic)


def s_rubble(sp):
    pic = Pic(21, 8)
    for dx, dy, w, h, seed in ((1, 2, 8, 6, 111), (7, 0, 9, 8, 112), (14, 3, 7, 5, 113)):
        sil = move(stone_sil(w, h, seed, lumps=1, amp=0.06, p_bot=3.5), dx, dy)
        layer(pic, sil, STONE, sep="slate_d")
    emit(sp, "rubble", pic)


def s_coal_rock(sp):
    pic, sil, dist = rock_node(25, 16, 41, lumps=2, amp=0.06)
    for cx, cy, rx, ry in ((7, 7, 2.2, 1.6), (13, 5, 2.4, 1.7), (18, 8, 2.0, 1.5), (10, 11, 2.0, 1.5), (16, 12, 1.8, 1.4)):
        lump = ellipse(cx, cy, rx, ry) & sil
        layer(pic, lump, COAL)
    emit(sp, "coal_rock", pic)


def _ore_rock(sp, name, seed, fn):
    pic, sil, dist = rock_node(25, 15, seed, lumps=2, amp=0.06)
    rng = random.Random(seed)
    fn(pic, sil, dist, rng)
    emit(sp, name, pic)


def _copper(pic, sil, dist, rng):
    vein(pic, sil, (5, 12), (20, 3), rng, 9, ["orange", "rust"], glint="amber")
    vein(pic, sil, (9, 12), (16, 7), rng, 3, ["green", "green_d"])


def _iron(pic, sil, dist, rng):
    vein(pic, sil, (4, 11), (21, 5), rng, 9, ["red_d", "rust", "red"], glint="grey_l")


def _gold(pic, sil, dist, rng):
    vein(pic, sil, (4, 11), (21, 4), rng, 8, ["amber", "yellow"], glint="white")


def s_copper_vein(sp):
    _ore_rock(sp, "copper_vein", 51, _copper)


def s_iron_vein(sp):
    _ore_rock(sp, "iron_vein", 52, _iron)


def s_gold_vein(sp):
    _ore_rock(sp, "gold_vein", 53, _gold)


def s_clay_bank(sp):
    pic, sil, dist = rock_node(27, 15, 71, ramp=CLAY, lumps=1, amp=0.05, p_top=2.0, p_bot=3.0)
    rng = random.Random(7)
    for y in (5, 9):
        for x in range(27):
            if (x, y) in sil and dist[(x, y)] >= 2 and rng.random() < 0.75:
                pic.put(x, y, "brown")
    specks(pic, dist, rng, 4, ["tan", "grey_l"], lambda x, y: y > 5)
    emit(sp, "clay_bank", pic)


def s_quartz_cluster(sp):
    pic = Pic(25, 22)
    for cx, tip, base, halfw in ((6, 7, 18, 3.0), (19, 6, 18, 3.0), (12, 0, 18, 4.5), (9, 11, 19, 2.5), (16, 10, 19, 2.5)):
        cols = {}
        for y in range(tip, base + 1):
            t = (y - tip) / max(1, base - tip)
            hw = halfw * min(1.0, t / 0.35)
            for x in range(cx - int(halfw) - 1, cx + int(halfw) + 2):
                if abs(x - cx) <= hw + 0.01:
                    if y == tip:
                        c = "white"
                    elif x < cx:
                        c = "white"
                    elif x == cx:
                        c = "grey_l"
                    else:
                        c = "grey"
                    cols[(x, y)] = c
        paint(pic, cols, sep="slate")
    base = move(stone_sil(25, 6, 61, lumps=1, amp=0.05, p_top=2.5, p_bot=4.0), 0, 16)
    layer(pic, base, STONE_DK, sep="slate_d")
    emit(sp, "quartz_cluster", pic)


def s_geode_rock(sp):
    pic, sil, dist = rock_node(23, 21, 81, lumps=2, amp=0.05, p_top=2.0, p_bot=2.4)
    # A jagged split across the round stone, with crystals standing up inside it.
    spine = [(4, 6), (6, 8), (8, 7), (10, 10), (12, 10), (14, 13), (17, 14)]
    cells = set()
    for a, b in zip(spine, spine[1:]):
        cells.update(line_pts(a, b))
    for x, y in cells:          # dark wall on the upper side, purple core below it
        if (x, y - 1) in sil and (x, y - 1) not in cells:
            pic.put(x, y - 1, "ink")
    for x, y in cells:
        if (x, y) in sil:
            pic.put(x, y, "magenta" if (x + y) % 3 else "purple")
    for x, y in cells:          # lit lower lip
        if (x, y + 1) in sil and (x, y + 1) not in cells:
            pic.put(x, y + 1, "pink")
    for x, y in (spine[2], spine[4]):    # crystal tips poking out of the split
        for dx, dy, c in ((0, -1, "cyan"), (0, -2, "white"), (1, -1, "cyan")):
            if (x + dx, y + dy) in sil and (x + dx, y + dy) not in cells:
                pic.put(x + dx, y + dy, c)
    for x, y in (spine[1], spine[5]):
        if (x, y + 1) in sil and (x, y + 1) not in cells:
            pic.put(x, y + 1, "cyan")
    emit(sp, "geode_rock", pic)


def s_gem_nodes(sp):
    gems = {
        "amethyst": ("purple", "magenta", "pink", "white"),
        "emerald": ("green_dd", "green_d", "green", "white"),
        "ruby": ("purple", "red_d", "red", "pink"),
        "diamond": ("blue_d", "blue", "cyan", "white"),
    }
    for name, cols in gems.items():
        pic, sil, dist = rock_node(25, 18, 61, ramp=STONE_DK, lumps=2, amp=0.06)
        rng = random.Random(9)
        specks(pic, dist, rng, 4, ["grey", "slate"], lambda x, y: y > 6)
        specks(pic, dist, rng, 2, ["grey"], lambda x, y: y <= 6)
        gem(pic, 12, 3, cols)
        pic.put(18, 6, "white")
        pic.put(6, 2, "white")
        emit(sp, f"gem_node_{name}", pic)


def s_cracks(sp):
    c1 = [[(13, 6), (12, 8), (10, 9), (9, 11)], [(12, 8), (14, 10)]]
    c2 = c1 + [[(8, 4), (9, 7), (7, 9), (6, 12)], [(16, 5), (15, 8), (17, 11), (16, 13)], [(9, 11), (8, 14)]]
    for name, lines in (("crack_1", c1), ("crack_2", c2)):
        pic = Pic(25, 17)
        for pts in lines:
            crack(pic, pts, "ink", force=True)
        emit(sp, name, pic, outline=False, shadow=False, ax=12, ay=16)


# ---------------------------------------------------------------- mine and quarry props

def s_ladder_hole(sp):
    pic = Pic(34, 20)
    outer = {(x + 1, y + 4) for y, x0, x1 in diamond_rows(16) for x in range(x0, x1)}
    inner = {(x + 7, y + 7) for y, x0, x1 in diamond_rows(10) for x in range(x0, x1)}
    rim = outer - inner
    layer(pic, rim, STONE_DK, tilt=0.4)
    for x, y in inner:
        v = 2.5 - ((x - 17) + (y - 11)) / 6
        pic.put(x, y, tone(HOLE, v, x, y))
    for x0 in (15, 18):
        for y in range(0, 15):
            pic.put(x0, y, "tan")
            pic.put(x0 + 1, y, "brown")
    for y in (1, 4, 7, 10, 13):
        for x in range(15, 20):
            pic.put(x, y, "tan_d")
    emit(sp, "ladder_hole", pic, ax=17, ay=11)


def s_mine_mouth(sp):
    W, H = 55, 48
    CX, CY, R = 27, 27, 8
    pic = Pic(W, H)
    rock = move(stone_sil(W, 42, 81, lumps=3, amp=0.05, p_top=2.6, p_bot=6.0), 0, 6)
    layer(pic, rock, STONE)
    rng = random.Random(12)
    vein(pic, rock, (4, 14), (11, 30), rng, 6, ["amber", "orange"], glint="yellow")
    vein(pic, rock, (47, 12), (51, 27), rng, 5, ["rust", "orange"], glint="amber")
    for dx, dy, w, h, seed in ((2, 43, 7, 4, 121), (46, 44, 6, 4, 122)):
        sil = move(stone_sil(w, h, seed, lumps=1, amp=0.06, p_top=2.0, p_bot=3.5), dx, dy)
        layer(pic, sil, STONE, sep="slate_d")

    def half(y):
        if y < CY - R:
            return None
        return R if y >= CY else math.sqrt(R * R - (CY - y) ** 2)

    top = CY - R
    for y in range(top, H):
        hw = half(y)
        f = (y - top) / (H - top)
        for x in range(W):
            if abs(x - CX) <= hw + 1e-6:
                pic.put(x, y, "ink" if f < 0.5 else "slate_d" if f < 0.8 else "slate")
    # warm lantern glow inside the mouth
    LX, LY = 31, 23
    for y in range(top, H):
        for x in range(W):
            if pic.px[x, y][3] and (x - LX) ** 2 + (y - LY) ** 2 <= 40 and pic.px[x, y] in (E["ink"] + (255,), E["slate_d"] + (255,)):
                pic.put(x, y, "brown")
    # rails and ties running out of the mouth
    for x in range(10, 45):
        pic.put(x, 45, "grey_l")
        pic.put(x, 46, "grey_d")
        if x % 4 == 0:
            pic.put(x, 47, "brown")
    # timber frame: two posts and a cap beam over the arch
    for y in range(top, H):
        hw = half(y)
        if hw is None:
            continue
        for x in range(W):
            d = abs(x - CX)
            if hw < d <= hw + 2:
                c = "tan_d" if y <= top + 3 else ("brown_l" if x < CX else "brown")
                if (y % 6) == 3:
                    c = "brown_d"
                pic.put(x, y, c)
    for y, c in ((15, "tan"), (16, "tan_d"), (17, "brown_l"), (18, "brown")):
        for x in range(13, 42):
            pic.put(x, y, c)
    for x in (13, 14, 40, 41):
        for y in range(15, 19):
            pic.put(x, y, "brown_d")
    for x, y in ((20, 16), (33, 17), (18, 21), (36, 21), (18, 40), (36, 40)):
        pic.put(x, y, "grey_l" if y > 20 and x in (18, 36) else "brown_d")
    # hanging lantern
    for y in (19, 20):
        pic.put(LX, y, "grey")
    for (x, y), c in {
        (30, 21): "ink", (31, 21): "ink", (32, 21): "ink",
        (29, 22): "ink", (30, 22): "yellow", (31, 22): "yellow", (32, 22): "yellow", (33, 22): "ink",
        (29, 23): "ink", (30, 23): "yellow", (31, 23): "white", (32, 23): "yellow", (33, 23): "ink",
        (29, 24): "ink", (30, 24): "amber", (31, 24): "amber", (32, 24): "amber", (33, 24): "ink",
        (30, 25): "ink", (31, 25): "ink", (32, 25): "ink",
    }.items():
        pic.put(x, y, c)
    emit(sp, "mine_mouth", pic, ax=27, ay=47)


def s_mine_cart(sp):
    W, H = 27, 19
    pic = Pic(W, H)
    # Ore heaped above an iron tub, two wheels under it.
    heap = move(stone_sil(23, 8, 91, lumps=2, amp=0.06, p_top=2.2, p_bot=2.6), 2, 0)
    body = {(x, y) for x in range(3, 24) for y in range(8, 15)}
    layer(pic, body, IRON, tilt=0.6)
    for x in range(3, 24):
        pic.put(x, 8, "grey_l")      # tub rim
        pic.put(x, 9, "slate_d")     # inside lip under the ore
    for x in (6, 13, 20):
        pic.put(x, 12, "grey_l")     # rivets
    for x in range(4, 24, 5):
        pic.put(x, 14, "slate_d")    # plate seams on the bottom edge
    dist = layer(pic, heap, STONE, sep="slate_d")
    rng = random.Random(13)
    vein(pic, heap, (7, 4), (19, 2), rng, 4, ["amber", "orange"], glint="yellow")
    specks(pic, dist, rng, 4, ["ink", "slate_d"], None, mind=1)
    for cx in (7, 19):
        layer(pic, ellipse(cx, 15, 2.9, 2.9), WHEEL)
        pic.put(cx, 15, "grey_l")
        pic.put(cx + 1, 15, "grey")
    emit(sp, "mine_cart", pic, ax=13, ay=H - 1)


def s_crate(sp):
    W, H = 23, 20
    pic = Pic(W, H)
    top = {(x, y) for y in range(H) for x in range(W)
           if abs(x + 0.5 - 11.5) / 11.5 + abs(y + 0.5 - 5.5) / 5.5 <= 1}
    low = {}
    for x, y in top:
        low[x] = max(low.get(x, -1), y)
    sides = set()
    for x, yl in low.items():
        for y in range(yl + 1, min(H, yl + 10)):
            sides.add((x, y))
    left = {p for p in sides if p[0] <= 11}
    right = sides - left
    tcols, _ = shade_cols(top, TOPWOOD)
    for y in (2, 7):
        for x in range(W):
            if (x, y) in tcols:
                tcols[(x, y)] = "brown_l"
    paint(pic, tcols)
    for p in left:
        pic.put(p[0], p[1], "brown_l" if p[0] % 4 else "brown")
    for p in right:
        pic.put(p[0], p[1], "brown" if p[0] % 4 else "brown_d")
    for p in sides:
        if p[0] in (0, W - 1):
            pic.put(p[0], p[1], "grey_d")
        elif p[1] == low[p[0]] + 4:
            pic.put(p[0], p[1], "brown_d")
    cx, cy = 5, low[5] + 5
    for dx, dy in ((-1, -1), (0, 0), (1, 1), (1, -1), (-1, 1)):
        pic.put(cx + dx, cy + dy, "brown_d")
    for x, y in ((4, 5), (18, 5)):
        pic.put(x, y, "grey_l")
    emit(sp, "crate", pic)


def s_torch_wall(sp):
    pic = Pic(7, 27)
    for y, hw in enumerate([0.5, 1.3, 1.8, 2.2, 2.4, 2.2, 1.6]):
        for x in range(7):
            dx = abs(x - 3)
            if dx <= hw:
                c = "red" if dx > hw * 0.6 else "orange" if dx > hw * 0.2 else "amber"
                if dx < 0.5 and y >= 2:
                    c = "yellow"
                pic.put(x, y, c)
    for x in range(1, 6):
        pic.put(x, 7, "grey_l")
    for x in range(2, 5):
        pic.put(x, 8, "slate")
    for y in range(9, 24):
        pic.put(2, y, "brown_l")
        pic.put(3, y, "brown")
        pic.put(4, y, "brown_d")
    for y in (12, 18):
        for x in range(2, 5):
            pic.put(x, y, "tan_d")
    foot = move(stone_sil(7, 3, 101, lumps=0, p_top=2.0, p_bot=3.0), 0, 24)
    layer(pic, foot, STONE)
    emit(sp, "torch_wall", pic)


def s_support_beam(sp):
    pic = Pic(25, 27)
    for x0 in (3, 19):
        for y in range(7, 26):
            for i, c in enumerate(("brown_l", "brown", "brown_d")):
                pic.put(x0 + i, y, c)
    for y, c in ((2, "tan"), (3, "tan_d"), (4, "brown_l"), (5, "brown_l"), (6, "brown_d")):
        for x in range(25):
            pic.put(x, y, c)
    for x, y in ((0, 2), (24, 2), (0, 6), (24, 6)):
        pic.put(x, y, "brown_d")
    for a, b in (((6, 12), (11, 7)), ((18, 12), (13, 7))):
        for x, y in line_pts(a, b):
            pic.put(x, y, "brown")
            pic.put(x + 1, y, "brown_l")
    for x in list(range(1, 8)) + list(range(17, 24)):
        pic.put(x, 25, "brown_l")
        pic.put(x, 26, "brown_d")
    for x, y in ((2, 3), (22, 3), (4, 9), (20, 9)):
        pic.put(x, y, "grey_l")
    emit(sp, "support_beam_prop", pic)


def s_cave_mushrooms(sp):
    pic = Pic(23, 17)
    base = move(stone_sil(21, 6, 71, lumps=1, amp=0.05, p_top=2.5, p_bot=4.0), 1, 11)
    layer(pic, base, STONE_DK)
    mushroom(pic, 5, 8, 3, 2.5, 11)
    mushroom(pic, 18, 7, 3, 2.5, 11)
    mushroom(pic, 11, 5, 4.5, 3.5, 11)
    pic.put(1, 4, "cyan")
    pic.put(21, 3, "cyan")
    pic.put(9, 0, "white")
    emit(sp, "cave_mushroom_cluster", pic)


def main():
    sp = Sprites(sys.argv[1], "rocks")
    for fn in (s_rock, s_rock_big, s_boulder, s_rubble, s_coal_rock, s_copper_vein, s_iron_vein,
               s_gold_vein, s_clay_bank, s_quartz_cluster, s_geode_rock, s_gem_nodes, s_cracks,
               s_ladder_hole, s_mine_mouth, s_mine_cart, s_crate, s_torch_wall, s_support_beam,
               s_cave_mushrooms):
        fn(sp)
    sp.done()


main()
