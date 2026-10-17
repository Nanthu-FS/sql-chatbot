"""Isometric ground tiles (32x16 top face). Pivot = top corner (16, 0).

Flat ground (grass, path, floors) has no outline so it tiles seamlessly; its
dither is aligned to world parity, which tile offsets (16, 8) preserve.
Raised plots get sides and a 1px ink outline so each plot reads on its own.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, CLEAR, Sprites, alpha, bayer, diamond_rows, in_diamond, new, rng  # noqa: E402


def flat(base, dark, level, seed, details=()):
    """Seamless flat tile: base colour, a light dither of [dark], plus inner details."""
    im = new(32, 16)
    px = im.load()
    for y, x0, x1 in diamond_rows():
        for x in range(x0, x1):
            px[x, y] = dark if bayer(x, y, level) else base
    r = rng(seed)
    for kind, colors, count in details:
        for _ in range(count):
            for _try in range(20):
                x, y = r.randrange(4, 28), r.randrange(3, 13)
                pts = SHAPES[kind]
                if all(in_diamond(x + dx, y + dy) and not _near_edge(x + dx, y + dy) for dx, dy, _ in pts):
                    for dx, dy, ci in pts:
                        px[x + dx, y + dy] = colors[ci]
                    break
    return im


def _near_edge(x, y):
    return not all(in_diamond(x + dx, y + dy) for dx, dy in ((-2, 0), (2, 0), (0, -1), (0, 1)))


# Little pixel motifs: (dx, dy, colour index)
SHAPES = {
    "tuft": [(0, 0, 0), (2, 0, 0), (1, -1, 0), (1, 0, 1)],
    "blade": [(0, 0, 0), (0, -1, 0), (1, -2, 1)],
    "flower": [(0, -1, 0), (-1, 0, 0), (1, 0, 0), (0, 1, 0), (0, 0, 1)],
    "pebble": [(0, 0, 0), (1, 0, 0), (0, -1, 1)],
    "leaf": [(0, 0, 0), (1, 0, 1)],
    "speck": [(0, 0, 0)],
    "crack": [(0, 0, 0), (1, 1, 0), (2, 1, 0), (3, 2, 0)],
}


def plot(top, furrow, ridge, left, right, sheen=None, seed=1):
    """Raised soil bed: 32x16 top with furrows along the column axis, 3px sides, ink outline."""
    side = 3
    im = new(32, 16 + side)
    px = im.load()
    # Sides first (visible below the two lower edges).
    for y, x0, x1 in diamond_rows():
        if y < 8:
            continue
        for x in range(x0, x1):
            for s in range(1, side + 1):
                px[x, y + s] = left if x < 16 else right
    r = rng(seed)
    for y, x0, x1 in diamond_rows():
        for x in range(x0, x1):
            v = (y * 2 - x) % 8
            c = furrow if v in (0, 1) else ridge if v == 3 else top
            if c is top and bayer(x, y, 3):
                c = ridge
            px[x, y] = c
    if sheen:
        for _ in range(7):
            x, y = r.randrange(6, 26), r.randrange(4, 12)
            if in_diamond(x, y) and in_diamond(x + 1, y):
                px[x, y] = sheen
                px[x + 1, y] = sheen
    outline(im)
    return im


def outline(im):
    """1px ink outline around every opaque region (drawn inside the canvas bounds)."""
    w, h = im.size
    px = im.load()
    edge = []
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if nx < 0 or ny < 0 or nx >= w or ny >= h or px[nx, ny][3] == 0:
                    edge.append((x, y))
                    break
    for x, y in edge:
        px[x, y] = E["ink"]


def cursor(color, fill_alpha):
    im = new(32, 16)
    px = im.load()
    for y, x0, x1 in diamond_rows():
        for x in range(x0, x1):
            border = not in_diamond(x - 2, y) or not in_diamond(x + 2, y) or not in_diamond(x, y - 1) or not in_diamond(x, y + 1)
            if border:
                px[x, y] = color
            elif bayer(x, y, 6):
                px[x, y] = alpha(color, fill_alpha)
    return im


def water(frame):
    im = flat(E["blue"], E["blue_d"], 4, 50)
    px = im.load()
    for y, x0, x1 in diamond_rows():
        for x in range(x0, x1):
            if (x * 1 + y * 3 + frame * 4) % 16 == 0 and in_diamond(x + 1, y):
                px[x, y] = E["cyan"]
                px[x + 1, y] = E["cyan"]
    return im


def main():
    s = Sprites(sys.argv[1], "tiles")
    tip = (16, 0)
    s.save("grass_a", flat(E["green"], E["green_d"], 2, 1, [("tuft", (E["green_d"], E["green_dd"]), 3)]), *tip)
    s.save("grass_b", flat(E["green"], E["green_d"], 2, 2, [("blade", (E["green_d"], E["yellow"]), 2), ("tuft", (E["green_d"], E["green_dd"]), 1)]), *tip)
    s.save("grass_c", flat(E["green"], E["green_d"], 2, 3, [("flower", (E["white"], E["yellow"]), 1), ("tuft", (E["green_d"], E["green_dd"]), 2)]), *tip)
    s.save("grass_d", flat(E["green"], E["green_d"], 2, 4, [("flower", (E["pink"], E["yellow"]), 1), ("pebble", (E["grey"], E["grey_l"]), 1)]), *tip)
    # Seasonal ground: summer is sun-bleached, fall is russet with leaves, winter is snow.
    s.save("summer_a", flat(E["green"], E["yellow"], 1, 21, [("tuft", (E["green_d"], E["green_dd"]), 3)]), *tip)
    s.save("summer_b", flat(E["green"], E["yellow"], 1, 22, [("flower", (E["amber"], E["orange"]), 1), ("blade", (E["green_d"], E["yellow"]), 2)]), *tip)
    s.save("summer_c", flat(E["green"], E["yellow"], 1, 23, [("flower", (E["white"], E["yellow"]), 2)]), *tip)
    s.save("summer_d", flat(E["green"], E["yellow"], 1, 24, [("pebble", (E["grey"], E["grey_l"]), 1), ("tuft", (E["green_d"], E["green_dd"]), 2)]), *tip)
    s.save("fall_a", flat(E["green_d"], E["tan_d"], 3, 31, [("leaf", (E["rust"], E["clay"]), 3)]), *tip)
    s.save("fall_b", flat(E["green_d"], E["tan_d"], 3, 32, [("leaf", (E["amber"], E["clay"]), 3), ("tuft", (E["green_dd"], E["teal_dd"]), 1)]), *tip)
    s.save("fall_c", flat(E["green_d"], E["tan_d"], 3, 33, [("leaf", (E["red_d"], E["rust"]), 2), ("leaf", (E["amber"], E["orange"]), 2)]), *tip)
    s.save("fall_d", flat(E["green_d"], E["tan_d"], 3, 34, [("pebble", (E["grey_d"], E["grey"]), 1), ("leaf", (E["rust"], E["clay"]), 2)]), *tip)
    s.save("winter_a", flat(E["white"], E["grey_l"], 3, 41, [("speck", (E["cyan"],), 2)]), *tip)
    s.save("winter_b", flat(E["white"], E["grey_l"], 3, 42, [("tuft", (E["green_d"], E["green_dd"]), 1), ("speck", (E["grey"],), 2)]), *tip)
    s.save("winter_c", flat(E["white"], E["grey_l"], 3, 43, [("pebble", (E["grey_d"], E["grey"]), 1)]), *tip)
    s.save("winter_d", flat(E["white"], E["grey_l"], 3, 44, [("blade", (E["green_d"], E["grey"]), 2)]), *tip)
    s.save("forest_fall", flat(E["green_dd"], E["rust"], 4, 45, [("leaf", (E["amber"], E["clay"]), 4), ("leaf", (E["red_d"], E["rust"]), 2)]), *tip)
    s.save("forest_winter", flat(E["grey_l"], E["grey"], 4, 46, [("tuft", (E["green_dd"], E["teal_dd"]), 2), ("speck", (E["white"],), 3)]), *tip)
    s.save("soil_snow", plot(E["white"], E["grey_l"], E["grey_l"], E["grey"], E["grey_d"], sheen=E["grey_l"], seed=15), *tip)
    s.save("grass_dark", flat(E["green_d"], E["green_dd"], 3, 5, [("leaf", (E["rust"], E["clay"]), 3), ("tuft", (E["green_dd"], E["teal_dd"]), 2)]), *tip)
    s.save("forest_a", flat(E["green_d"], E["green_dd"], 5, 6, [("leaf", (E["rust"], E["clay"]), 4), ("leaf", (E["amber"], E["clay"]), 1)]), *tip)
    s.save("forest_b", flat(E["green_d"], E["green_dd"], 5, 7, [("tuft", (E["green_dd"], E["teal_dd"]), 3), ("pebble", (E["grey_d"], E["grey"]), 1)]), *tip)
    s.save("path", flat(E["tan"], E["tan_d"], 4, 8, [("pebble", (E["tan_d"], E["cream"]), 3), ("speck", (E["brown_l"],), 4)]), *tip)
    s.save("quarry", flat(E["grey"], E["grey_d"], 5, 9, [("pebble", (E["grey_d"], E["grey_l"]), 4), ("speck", (E["slate"],), 5)]), *tip)
    s.save("mine_a", flat(E["slate"], E["slate_d"], 6, 10, [("crack", (E["ink"],), 1), ("speck", (E["grey_d"],), 5)]), *tip)
    s.save("mine_b", flat(E["slate"], E["slate_d"], 6, 11, [("pebble", (E["slate_d"], E["grey_d"]), 3), ("speck", (E["purple"],), 1)]), *tip)
    s.save("water_a", water(0), *tip)
    s.save("water_b", water(1), *tip)
    s.save("soil_dry", plot(E["brown_l"], E["brown"], E["tan_d"], E["brown"], E["brown_d"], seed=12), *tip)
    s.save("soil_wet", plot(E["brown"], E["brown_d"], E["brown_l"], E["brown_d"], E["ink"], sheen=E["grey_d"], seed=13), *tip)
    s.save("soil_fert", plot(E["brown_l"], E["brown"], E["tan_d"], E["brown"], E["brown_d"], sheen=E["green"], seed=14), *tip)
    s.save("cursor", cursor(E["white"], 110), *tip)
    s.save("cursor_ok", cursor(E["green"], 110), *tip)
    s.save("cursor_bad", cursor(E["red"], 110), *tip)
    shadow = new(32, 16)
    for y, x0, x1 in diamond_rows():
        for x in range(x0, x1):
            shadow.putpixel((x, y), alpha(E["ink"], 90))
    s.save("shadow", shadow, *tip)
    s.done()


main()
