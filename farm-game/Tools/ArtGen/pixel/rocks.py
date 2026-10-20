"""Rocks (pixel-direct): quarry and mine objects, written to art/rocks/.

Names follow the node ids in data/mine.json, each in three shape variants (<id>, <id>_2, <id>_3)
that MineScreen picks per cell. MineScreen.rockSprite() maps amethyst_node to gem_node_amethyst
(and the other gems), boulder to boulder, glow_mushroom to cave_mushroom_cluster. It also draws
rubble, crack_1/crack_2, ladder_hole, mine_mouth (+ _summer, _fall, _snow), mine_cart,
torch_wall and support_beam_prop.

Pivot = base centre (centre column of the bottom row, plus the finish() pad), so a node sits
on its tile. Exceptions: ladder_hole pivots on the centre of its iso diamond (it is drawn at the
tile centre), crack_1/crack_2 are overlays that share the rock's base-centre pivot, and the
mine mouth pivots on the centre of its 2x2 footprint.

Light from the top-left, clean ramp steps (no dither), finished with finish(); the polish pass
in Sprites.save gives the colour work, outlines and cast shadows (art round 3).
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, Sprites, diamond_rows, finish, new  # noqa: E402
import math  # noqa: E402
import random  # noqa: E402

_L = (-0.55, -0.7, 0.6)
_LN = math.sqrt(sum(v * v for v in _L))
LIGHT = tuple(v / _LN for v in _L)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

# Ramps run dark -> light.
STONE = ["slate_d", "slate", "grey_d", "grey", "grey_l", "white"]
STONE_DK = ["ink", "slate_d", "slate", "grey_d", "grey"]
CLAY = ["brown_d", "brown", "brown_l", "clay", "tan"]
COAL = ["ink", "ink", "slate_d", "slate", "grey"]
IRON = ["slate_d", "slate", "grey_d", "grey", "grey_l"]
CAP = ["blue_d", "blue", "cyan", "white"]
WOOD = ["brown_d", "brown", "brown_l", "tan_d", "tan", "cream"]
HOLE = ["ink", "ink", "ink", "slate_d", "slate"]
WHEEL = ["ink", "slate_d", "slate", "grey"]
TOPWOOD = ["brown_l", "tan_d", "tan", "cream"]



# ---------------------------------------------------------------- canvas and tone

def col(c):
    return E[c] if isinstance(c, str) else c


def tone(ramp, v, x, y):
    """Ramp colour at level v (0..len-1): the nearest step, no dither (art round 3: clean)."""
    n = len(ramp) - 1
    return col(ramp[int(round(min(max(v, 0.0), float(n))))])


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

def s_rubble(sp):
    pic = Pic(21, 8)
    for dx, dy, w, h, seed in ((1, 2, 8, 6, 111), (7, 0, 9, 8, 112), (14, 3, 7, 5, 113)):
        sil = move(stone_sil(w, h, seed, lumps=1, amp=0.06, p_bot=3.5), dx, dy)
        layer(pic, sil, STONE, sep="slate_d")
    emit(sp, "rubble", pic)


def s_cracks(sp):
    c1 = [[(10, 4), (9, 6), (8, 7), (7, 9)], [(9, 6), (11, 8)]]
    c2 = c1 + [[(6, 3), (7, 5), (5, 7), (5, 9)], [(13, 3), (12, 6), (13, 8), (12, 10)], [(7, 9), (6, 11)]]
    for name, lines in (("crack_1", c1), ("crack_2", c2)):
        pic = Pic(19, 13)
        for pts in lines:
            crack(pic, pts, "ink", force=True)
        emit(sp, name, pic, outline=False, shadow=False, ax=9, ay=12)


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


# ---------------------------------------------------------------- mine nodes (art round 3, style A)
# Ore-coloured rocks about a farmhand wide and knee high, three shape variants each (<id>, <id>_2,
# <id>_3; the third is the first mirrored): peach copper with teal, rusty iron, dark stone with
# gold nuggets, glossy coal, stacked grey slabs, clay, quartz and gem crystals, geodes, boulders.

STONE_MID = ["slate_d", "slate", "grey_d", "grey", "grey_l"]
PEACH = ["brown", "brown_l", "tan_d", "tan", "cream"]
TEAL = ["teal_dd", "blue_d", "blue", "cyan", "white"]
RUSTY = ["brown_d", "rust", "clay", "tan", "cream"]
GOLD = ["rust", "orange", "amber", "yellow", "white"]
GLOSSY = ["ink", "ink", "slate_d", "slate", "grey_l"]
QUARTZ = ["slate", "grey", "grey_l", "white", "white"]
SAND = ["brown_d", "brown", "brown_l", "tan_d", "tan", "cream"]
GEMS = {
    "amethyst": ["ink", "purple", "magenta", "pink", "white"],
    "emerald": ["ink", "green_dd", "green_d", "green", "white"],
    "ruby": ["ink", "red_d", "red", "pink", "white"],
    "diamond": ["ink", "blue_d", "blue", "cyan", "white"],
}


def flip(pic):
    from PIL import Image
    pic.im = pic.im.transpose(Image.FLIP_LEFT_RIGHT)
    pic.px = pic.im.load()
    return pic


def body(pic, x, y, w, h, seed, ramp, flat=False, sep=None):
    sil = move(stone_sil(w, h, seed, lumps=2, amp=0.07, p_top=3.4 if flat else 2.1, p_bot=4.5), x, y)
    layer(pic, sil, ramp, sep=sep)
    return sil


def nugget(pic, cx, cy, r, ramp, sep="ink"):
    """A faceted chunk: a squat hexagon, top-left facet lit, a bright glint."""
    n = len(ramp) - 1
    cells = {}
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = x + 0.5 - cx, (y + 0.5 - cy) * 1.3
            if abs(dx) + abs(dy) * 0.55 > r or abs(dy) > r * 0.95:
                continue
            if dy < -0.2 * r:
                v = n - 1 if dx < 0.4 * r else n - 2
            elif dx < -0.25 * r:
                v = n - 2
            else:
                v = 1
            cells[(x, y)] = col(ramp[max(0, v)])
    paint(pic, cells, sep=sep)
    pic.put(int(cx - r * 0.35), int(cy - r * 0.5), ramp[n])


def shard(pic, cx, base, h, w, ramp, lean=0.0):
    """A pointed crystal: lit left face, dark right face, bright tip."""
    cells = {}
    tip = base - h
    for y in range(tip, base + 1):
        t = (y - tip) / max(1, h)
        half = max(0.5, w * min(1.0, t / 0.4))
        x0 = cx + lean * (1 - t) * h * 0.35
        for x in range(int(x0 - half) - 1, int(x0 + half) + 2):
            d = x + 0.5 - x0
            if abs(d) > half:
                continue
            c = ramp[-1] if y == tip else (ramp[3] if d < -0.3 else ramp[2] if d < 0.5 else ramp[1])
            cells[(x, y)] = col(c)
    paint(pic, cells, sep=ramp[0])


def rand(v, seed):
    return random.Random(seed * 31 + v * 977)


def n_stone(v):
    r = rand(v, 1)
    pic = Pic(19, 12)
    body(pic, 0, 4, 19, 8, 11 + v, STONE, flat=True)
    body(pic, 3 + r.randint(0, 3), 0, 11 + r.randint(-1, 1), 6, 21 + v, STONE, flat=True, sep="slate_d")
    crack(pic, [(8, 6), (9, 8)], "slate_d")
    return pic


def n_coal(v):
    r = rand(v, 2)
    pic = Pic(18, 11)
    box = {(x, y) for x in range(18) for y in range(11)}
    for cx, cy, rx in ((5, 7, 4.2), (12, 7, 4.4), (9, 4, 3.8)):
        layer(pic, ellipse(cx + r.uniform(-0.6, 0.6), cy, rx, rx * 0.8) & box, GLOSSY, sep="ink")
        pic.put(int(cx - 1), int(cy - 2), "white")
    return pic


def n_copper(v):
    r = rand(v, 3)
    pic = Pic(19, 12)
    sil = body(pic, 0, 0, 19, 12, 31 + v, PEACH)
    for _ in range(4):
        x, y = r.randint(4, 15), r.randint(3, 8)
        if (x, y) in sil:
            nugget(pic, x, y, 2.2, TEAL, sep="teal_dd")
    return pic


def n_iron(v):
    r = rand(v, 4)
    pic = Pic(20, 13)
    body(pic, 0, 3, 13, 10, 41 + v, RUSTY, flat=True)
    body(pic, 8, 4 + r.randint(0, 1), 12, 9, 51 + v, RUSTY, flat=True, sep="brown_d")
    body(pic, 4 + r.randint(0, 3), 0, 9, 7, 61 + v, RUSTY, flat=True, sep="brown_d")
    for x, y in ((6, 7), (14, 8), (9, 3)):
        pic.put(x + r.randint(-1, 1), y, "grey_l")
    return pic


def n_gold(v):
    r = rand(v, 5)
    pic = Pic(19, 12)
    sil = body(pic, 0, 0, 19, 12, 71 + v, STONE_MID)
    for i in range(3):
        x, y = 4 + i * 5 + r.randint(-1, 1), r.randint(3, 7)
        if (x, y) in sil:
            nugget(pic, x, y, 2.6, GOLD, sep="brown_d")
    return pic


def n_clay(v):
    r = rand(v, 6)
    pic = Pic(19, 10)
    sil = body(pic, 0, 0, 19, 10, 81 + v, CLAY)
    for y in (4, 7):
        for x in range(19):
            if (x, y) in sil and (x - 1, y) in sil and (x + 1, y) in sil and r.random() < 0.8:
                pic.put(x, y, "brown")
    return pic


def n_quartz(v):
    r = rand(v, 7)
    pic = Pic(17, 16)
    body(pic, 0, 11, 17, 5, 91 + v, STONE_DK)
    for cx, h, w, lean in ((5, 8, 2.0, -0.4), (9, 11, 2.6, 0.0), (13, 7, 2.0, 0.5)):
        shard(pic, cx + r.uniform(-0.5, 0.5), 12, h + r.randint(-1, 1), w, QUARTZ, lean)
    return pic


def n_geode(v):
    r = rand(v, 8)
    pic = Pic(15, 14)
    sil = body(pic, 0, 0, 15, 14, 101 + v, STONE_MID)
    cav = {p for p in ellipse(8.5 + r.uniform(-0.5, 0.5), 7, 4.2, 3.2) if p in sil}
    for x, y in cav:
        pic.put(x, y, "ink" if y < 6 else "purple")
    for x, y in sorted(cav)[::3]:
        if y >= 6:
            pic.put(x, y, "magenta")
    for dx in (-2, 0, 2):
        pic.put(8 + dx, 8, "cyan")
        pic.put(8 + dx, 7, "white")
    return pic


def n_gem(name):
    def build(v):
        r = rand(v, 9)
        pic = Pic(19, 16)
        body(pic, 0, 9, 19, 7, 111 + v, STONE_DK)
        for cx, h, w, lean in ((6, 7, 2.2, -0.6), (10, 10, 2.8, 0.0), (14, 6, 2.0, 0.6)):
            shard(pic, cx + r.uniform(-0.5, 0.5), 11, h + r.randint(-1, 1), w, GEMS[name], lean)
        return pic
    return build


CRYSTALITE = ["ink", "teal_dd", "blue_d", "cyan", "white"]


def n_crystalite(v):
    """Deep-mine crystalite (round 6): a dark slab split by a seam of glowing teal crystal."""
    r = rand(v, 12)
    pic = Pic(21, 17)
    sil = body(pic, 0, 6, 21, 11, 131 + v, STONE_DK)
    for cx, h, w, lean in ((5, 8, 2.0, -0.7), (9, 12, 2.6, -0.2), (13, 10, 2.4, 0.3), (17, 6, 1.8, 0.8)):
        shard(pic, cx + r.uniform(-0.5, 0.5), 12, h + r.randint(-1, 1), w, CRYSTALITE, lean)
    for _ in range(4):
        x, y = r.randint(2, 18), r.randint(12, 15)
        if (x, y) in sil:
            pic.put(x, y, "cyan")
    return pic


def n_boulder(v):
    r = rand(v, 10)
    pic = Pic(26, 18)
    sil = body(pic, 0, 0, 26, 18, 121 + v, STONE)
    for y in (7, 11):
        for x in range(26):
            if (x, y + (x // 7) % 2) in sil and r.random() < 0.7:
                pic.put(x, y + (x // 7) % 2, "rust" if x % 3 else "clay")
    crack(pic, [(9, 4), (11, 7), (10, 10)], "slate_d")
    return pic


NODES = {
    "rock": n_stone, "coal_rock": n_coal, "copper_vein": n_copper, "iron_vein": n_iron, "gold_vein": n_gold,
    "clay_bank": n_clay, "quartz_cluster": n_quartz, "geode_rock": n_geode, "boulder": n_boulder,
    "crystalite_vein": n_crystalite,
    **{f"gem_node_{g}": n_gem(g) for g in GEMS},
}


def s_nodes(sp):
    for name, fn in NODES.items():
        for v in range(3):
            pic = fn(v)
            if v == 2:
                flip(pic)
            emit(sp, name if v == 0 else f"{name}_{v + 1}", pic)


# ---------------------------------------------------------------- the Mine Mouth
# A timber-framed tunnel cut into a grass-topped sandstone block that joins the quarry cliffs. Its
# cap uses the painted ground's grass for each season (GroundPainter), so it matches the cliff tops.

GRASS_CAPS = {
    "": [(0x23, 0x46, 0x3F), (0x2F, 0x6B, 0x43), (0x4A, 0x8C, 0x3F), (0x6A, 0xA8, 0x45), (0xA3, 0xC9, 0x5A)],
    "_summer": [(0x2A, 0x4A, 0x3A), (0x3D, 0x6E, 0x3A), (0x5D, 0x94, 0x3C), (0x84, 0xB4, 0x43), (0xC2, 0xD2, 0x64)],
    "_fall": [(0x3B, 0x35, 0x32), (0x5E, 0x4A, 0x35), (0x7F, 0x6A, 0x3A), (0xA5, 0x86, 0x44), (0xD0, 0xA8, 0x5A)],
    "_snow": [(0x6D, 0x7E, 0xA0), (0x9E, 0xB0, 0xCC), (0xC9, 0xD5, 0xE6), (0xE8, 0xEE, 0xF6), (0xFF, 0xFF, 0xFF)],
}


def _block(cap, W=66, H=38, top=4):
    """An iso rock block on a 2x2 tile footprint: grass cap, sandstone chunk faces (left lit)."""
    D = 32
    pic = Pic(W, top + D + H + 2)
    cx = W // 2
    rnd = random.Random(5)
    seeds = {}
    cap = [c + (255,) for c in cap]

    def chunk(fx, fz, left):
        gx, gz = int(fx // 9), int(fz // 7)
        d1 = d2 = 1e9
        best = None
        for oz in (-1, 0, 1):
            for ox in (-1, 0, 1):
                k = (gx + ox, gz + oz)
                if k not in seeds:
                    seeds[k] = (k[0] * 9 + 1.5 + rnd.random() * 6, k[1] * 7 + 1 + rnd.random() * 5, rnd.random())
                sx, sz, tk = seeds[k]
                d = (fx - sx) ** 2 + ((fz - sz) * 1.2) ** 2
                if d < d1:
                    d2, d1, best = d1, d, (sx, sz, tk)
                elif d < d2:
                    d2 = d
        sx, sz, tk = best
        v = (3.0 if left else 1.7) + (tk - 0.5) + max(-0.9, min(0.9, (fz - sz) * 0.2 - (fx - sx) * 0.08))
        if math.sqrt(d2) - math.sqrt(d1) < 0.9:
            v = 0.8 if left else 0.3
        return SAND[int(round(max(0, min(len(SAND) - 1, v))))]

    for y in range(pic.h):
        for x in range(W):
            dx = x + 0.5 - cx
            adx = abs(dx)
            if adx > D:
                continue
            top_edge = top + adx / 2
            bot_edge = top + D - adx / 2
            if top_edge <= y + 0.5 <= bot_edge:
                lv = 2 if (y + 0.5) < top + D / 2 - dx * 0.2 else 1
                if (y + 0.5) > bot_edge - 1.2:
                    lv = 4
                pic.put(x, y, cap[lv])
            elif bot_edge < y + 0.5 <= bot_edge + H:
                z = bot_edge + H - (y + 0.5)
                left = dx < 0
                if (y + 0.5) < bot_edge + 2 + (x * 7 % 3 == 0):
                    pic.put(x, y, cap[0])
                else:
                    pic.put(x, y, chunk(x if left else 2 * cx - x, z, left))
    return pic, {"cx": cx, "top": top, "D": D, "H": H}


def _face_point(geo, s, z):
    """Pixel on the left face at fraction s along it (0 left corner .. 1 front corner), z px up."""
    return (geo["cx"] - geo["D"] + s * geo["D"],
            geo["top"] + geo["D"] / 2 + s * geo["D"] / 2 + geo["H"] - z)


def mine_mouth(cap):
    pic, geo = _block(cap)
    # The tunnel: a dark opening in the left face, a little taller than a farmhand.
    s0, s1, h = 0.28, 0.76, 25
    for y in range(pic.h):
        for x in range(pic.w):
            s = (x + 0.5 - (geo["cx"] - geo["D"])) / geo["D"]
            if s0 <= s <= s1:
                z = geo["top"] + geo["D"] / 2 + s * geo["D"] / 2 + geo["H"] - (y + 0.5)
                if 0 <= z <= h:
                    f = z / h
                    pic.put(x, y, "ink" if f > 0.45 else "slate_d" if f > 0.15 else "slate")
    # Timber frame: two posts and a lintel along the face, and a lantern hanging inside.
    for s in (0.25, 0.28, 0.76, 0.79):
        for z in range(0, 27):
            x, y = _face_point(geo, s, z)
            pic.put(int(x), int(y), "brown_l" if s < 0.5 else "brown")
    for s100 in range(22, 83):
        for z, c in ((26, "tan"), (27, "brown_l"), (28, "brown")):
            x, y = _face_point(geo, s100 / 100, z)
            pic.put(int(x), int(y), c)
    lx, ly = _face_point(geo, 0.52, 22)
    for (dx, dy), c in {(0, 0): "grey", (0, 1): "amber", (-1, 2): "yellow", (0, 2): "white", (1, 2): "yellow", (0, 3): "amber"}.items():
        pic.put(int(lx) + dx, int(ly) + dy, c)
    return pic, geo["cx"], geo["top"] + geo["D"] // 2 + geo["H"]


def s_mine_mouths(sp):
    for suffix, cap in GRASS_CAPS.items():
        pic, ax, ay = mine_mouth(cap)
        emit(sp, "mine_mouth" + suffix, pic, ax=ax, ay=ay)


def main():
    sp = Sprites(sys.argv[1], "rocks")
    for fn in (s_nodes, s_rubble, s_cracks, s_ladder_hole, s_mine_mouths, s_mine_cart, s_crate, s_torch_wall,
               s_support_beam, s_cave_mushrooms):
        fn(sp)
    sp.done()


main()
