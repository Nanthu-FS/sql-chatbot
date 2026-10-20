"""Polish pass for finished sprites (art round 3): clean shading, hue-shifted colour, coloured
outlines and crisp cast shadows. Runs on a finished image (ink outline + hard drop shadow from
finish()) and returns a new image; the pivot is unchanged (the canvas only grows right and down).

  kind "world"  scenery, crops, buildings, people, vehicles, rocks: everything below plus a cast
                shadow toward the lower right (sun from the top left) with dappled sun spots
                under tree canopies, and a darker contact shadow at the foot.
  kind "item"   inventory items: the same colour work, a small hard shadow.
  kind "ui"     HUD and panel icons, villager portraits: the item treatment (art round 4).
  kind "chrome" 9-slice UI frames, buttons, bars and tabs: de-dither and hue shift only. Ink
                pixels, sizes and corners stay exactly as drawn, so the slice borders hold.

Steps:
  1. De-dither: checkerboard and ordered-dither patterns between two close colours become clean
     bands of in-between colours (the palette is no longer limited to Endesga-32).
  2. Hue shift: shadows lean toward blue-violet, highlights toward warm yellow (in OKLab).
  3. Selective outline: each ink outline pixel takes a dark shade of the colour it borders,
     a little lighter on the lit top-left side.
"""
import math

from PIL import Image

INK = (24, 20, 37)
SHADOW = (24, 20, 52)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


# ---------------------------------------------------------------- colour (OKLab)

def _lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _gam(c):
    c = max(0.0, min(1.0, c))
    return round(255 * (c * 12.92 if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055))


def to_lab(rgb):
    r, g, b = (_lin(v) for v in rgb[:3])
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l, m, s = (math.copysign(abs(v) ** (1 / 3), v) for v in (l, m, s))
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def from_lab(lab):
    L, a, b = lab
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return (_gam(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            _gam(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            _gam(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s))


def dist(a, b):
    la, lb = to_lab(a), to_lab(b)
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(la, lb)))


def mix(a, b, t):
    la, lb = to_lab(a), to_lab(b)
    return from_lab(tuple(x + (y - x) * t for x, y in zip(la, lb)))


def _rotate_toward(h, target, t):
    d = (target - h + math.pi) % (2 * math.pi) - math.pi
    return h + d * t


SHADE_HUE = math.radians(285)     # blue-violet
LIGHT_HUE = math.radians(95)      # warm yellow


def hue_shift(rgb, strength=1.0):
    L, a, b = to_lab(rgb)
    C = math.hypot(a, b)
    h = math.atan2(b, a)
    if L < 0.55:
        t = min(1.0, (0.55 - L) / 0.4) * 0.28 * strength
        h = _rotate_toward(h, SHADE_HUE, t)
        C = C + (0.035 - C) * t if C < 0.035 else C * (1 + 0.08 * t)
    elif L > 0.7:
        t = min(1.0, (L - 0.7) / 0.3) * 0.22 * strength
        h = _rotate_toward(h, LIGHT_HUE, t)
        C = C + (0.03 - C) * t if C < 0.03 else C
    else:
        C *= 1.06
    return from_lab((L, C * math.cos(h), C * math.sin(h)))


def outline_of(rgb, lit):
    """A dark shade of a fill colour for its selective outline."""
    L, a, b = to_lab(rgb)
    C = math.hypot(a, b)
    h = _rotate_toward(math.atan2(b, a), SHADE_HUE, 0.35)
    L2 = min(L * (0.55 if lit else 0.42), 0.36 if lit else 0.27)
    L2 = max(L2, 0.13)
    C2 = min(C * 0.85, 0.09)
    return from_lab((L2, C2 * math.cos(h), C2 * math.sin(h)))


# ---------------------------------------------------------------- passes

def _strip(im):
    """Splits a finished sprite into fill pixels and outline pixels (the hard shadow is dropped)."""
    w, h = im.size
    px = im.load()
    fill, line = {}, set()
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 0 or (c[:3] == INK and c[3] < 255):
                continue
            fill[(x, y)] = c[:3]
    for p, c in list(fill.items()):
        if c == INK and any((p[0] + dx, p[1] + dy) not in fill for dx, dy in N4):
            line.add(p)
    for p in line:
        del fill[p]
    return fill, line


def dedither(fill, close=0.24):
    """Flattens dithered areas (two close colours interleaved) into in-between colours."""
    cache = {}

    def near(a, b):
        k = (a, b) if a < b else (b, a)
        if k not in cache:
            cache[k] = dist(a, b) < close
        return cache[k]

    flagged = set()
    for (x, y), c in fill.items():
        other = {}
        for dx, dy in N4:
            n = fill.get((x + dx, y + dy))
            if n is not None and n != c:
                other[n] = other.get(n, 0) + 1
        for n, k in other.items():
            if k >= 2 and near(c, n):
                flagged.add((x, y))
                break
    region = set()
    for x, y in flagged:
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if (x + dx, y + dy) in fill:
                    region.add((x + dx, y + dy))
    out = dict(fill)
    mixes = {}
    for (x, y) in region:
        c = fill[(x, y)]
        counts = {}
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                n = fill.get((x + dx, y + dy))
                if n is not None:
                    counts[n] = counts.get(n, 0) + 1
        top = sorted(counts.items(), key=lambda kv: -kv[1])
        if len(top) < 2:
            continue
        (a, ka), (b, kb) = top[0], top[1]
        if c not in (a, b) or not near(a, b):
            continue
        t = round(kb / (ka + kb) * 2) / 2
        key = (a, b, t)
        if key not in mixes:
            mixes[key] = mix(a, b, t)
        out[(x, y)] = mixes[key]
    return out


def _bottom_profile(mask):
    bottom = {}
    for x, y in mask:
        if y > bottom.get(x, -1):
            bottom[x] = y
    return bottom


def cast_shadow(mask, w, h, tree=False):
    """Ground shadow of a silhouette, sun from the top left: each pixel lands down-right of its
    column's foot in proportion to its height. Returns ({pixel: alpha}, new w, new h)."""
    KX, KY = 0.42, 0.16
    bottom = _bottom_profile(mask)
    shade = set()
    for x, y in mask:
        foot = bottom[x]
        hgt = foot - y
        sx = round(x + hgt * KX)
        sy = round(foot + hgt * KY * 0.5)
        shade.add((sx, sy))
        shade.add((sx + 1, sy))
    # Close the gaps the projection leaves: fill each row between its ends, then round off.
    rows = {}
    for x, y in shade:
        lo, hi = rows.get(y, (x, x))
        rows[y] = (min(lo, x), max(hi, x))
    solid = set()
    for y, (lo, hi) in rows.items():
        for x in range(lo, hi + 1):
            solid.add((x, y))
    alpha = {}
    for p in solid:
        if p in mask:
            continue
        alpha[p] = 92
    # Contact shadow right under the foot.
    for x, foot in bottom.items():
        for k in (1, 2):
            for dx in (0, 1):
                p = (x + dx, foot + k)
                if p not in mask:
                    alpha[p] = 150 if k == 1 else 120
    # Sun spots through the canopy: small round holes in a tree's shadow.
    if tree and alpha:
        ys = [p[1] for p in alpha]
        xs = [p[0] for p in alpha]
        x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
        for i in range(max(2, (x1 - x0) // 10)):
            cx = x0 + 3 + (i * 37 + 11) % max(1, x1 - x0 - 6)
            cy = y0 + 1 + (i * 23 + 5) % max(1, y1 - y0 - 2)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (2, 0) if i % 2 else (-1, 0)):
                q = (cx + dx, cy + dy)
                if q in alpha and alpha[q] < 100:
                    del alpha[q]
    nw = max([w] + [p[0] + 1 for p in alpha])
    nh = max([h] + [p[1] + 1 for p in alpha])
    return alpha, nw, nh


def chrome(im):
    """9-slice UI chrome: de-dither and hue-shift the fills, nothing else. Ink pixels keep their
    colour and place, translucent pixels keep their alpha, and the size does not change."""
    im = im.convert("RGBA")
    w, h = im.size
    src = im.load()
    solid, glass = {}, {}
    for y in range(h):
        for x in range(w):
            c = src[x, y]
            if c[3] == 0 or c[:3] == INK:
                continue
            if c[3] == 255:
                solid[(x, y)] = c[:3]
            else:
                glass[(x, y)] = c
    solid = dedither(solid)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = out.load()
    for p, c in solid.items():
        px[p] = hue_shift(c) + (255,)
    for p, c in glass.items():
        px[p] = hue_shift(c[:3]) + (c[3],)
    for y in range(h):
        for x in range(w):
            c = src[x, y]
            if c[3] > 0 and c[:3] == INK:
                px[x, y] = c
    return out


def polish(im, kind, name=""):
    if kind == "chrome":
        return chrome(im)
    im = im.convert("RGBA")
    w, h = im.size
    fill, line = _strip(im)
    if not fill:
        return im
    fill = dedither(fill)
    fill = {p: hue_shift(c) for p, c in fill.items()}
    lines = {}
    for (x, y) in line:
        ns = [fill[(x + dx, y + dy)] for dx, dy in N4 if (x + dx, y + dy) in fill]
        if not ns:
            lines[(x, y)] = hue_shift(INK)
            continue
        base = min(ns, key=lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2])
        lit = (x, y + 1) in fill or (x + 1, y) in fill
        lines[(x, y)] = outline_of(base, lit)
    mask = set(fill) | set(line)
    if kind == "world":
        tree = name.startswith("trees/") and not any(s in name for s in ("stump", "sapling", "branch", "beehive"))
        alpha, nw, nh = cast_shadow(mask, w, h, tree)
        out = Image.new("RGBA", (nw, nh), (0, 0, 0, 0))
        px = out.load()
        for p, a in alpha.items():
            px[p] = SHADOW + (a,)
    else:
        out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        px = out.load()
        for x, y in mask:
            for dx, dy in ((1, 2), (0, 2), (1, 1)):
                q = (x + dx, y + dy)
                if q not in mask and 0 <= q[0] < w and 0 <= q[1] < h:
                    px[q] = SHADOW + (90,)
    for p, c in fill.items():
        px[p] = c + (255,)
    for p, c in lines.items():
        px[p] = c + (255,)
    return out


# Groups and the treatment they get; anything else (UI, icons, tiles, fx) is left alone.
KIND = {
    "buildings": "world", "crops": "world", "deco": "world", "people": "world", "rocks": "world",
    "trees": "world", "vehicles": "world", "decor": "world", "pets": "world", "animals": "world", "fishing": "world", "critters": "world", "items": "item", "items_big": "item",
    "icons": "ui", "icons_big": "ui", "portraits": "ui", "ui": "chrome",
}
# The story busts (*_bust) are cut out and already final; they are not polished.
SKIP = ("_lit", "rocks/crack_", "rocks/ladder_hole", "deco/fence", "trees/branch_pile", "_bust", "decor/path_")


def kind_of(name):
    if any(s in name for s in SKIP):
        return None
    return KIND.get(name.split("/")[0])
