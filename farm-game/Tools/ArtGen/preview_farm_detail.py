"""Style sheet for the farm detail pass (owner feedback: the farm props look plain).

Renders the farmyard props four ways, side by side with a farmhand for scale:
  NOW       today's sprites
  A DETAIL  the same size, more surface detail (trims, seams, hardware, small props)
  B BIGGER  A, 25% larger so the detail reads on a phone
  C LIVED-IN B with clutter around it (load, sawhorse, sacks, trough)

  python3 preview_farm_detail.py <out.png>

Nothing here touches the game's art; the chosen direction is then built into the pixel modules.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "pixel"))
import polish  # noqa: E402
import scaled  # noqa: E402
from _pix import E, finish  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

ART = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "art")
V = scaled.module("village")
B = scaled.module("buildings")
CHROME = ["grey_d", "grey", "grey_l", "white"]
STONE = ["slate", "grey_d", "grey", "grey_l", "white"]
WOOD = ["brown_d", "brown", "brown_l", "tan"]


def h2(x, y, k=0):
    """A stable 0..1 hash of a pixel (knots, moss and other sparse marks)."""
    n = (int(x) * 374761393 + int(y) * 668265263 + k * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return (n & 0xFFFF) / 65535.0


# ---------------------------------------------------------------- truck (village.py frame: x forward, y across)

def truck_d(load=False, heading="se", frame=0):
    box, lit, RED, GLASS = V["box"], V["lit"], V["RED"], V["GLASS"]
    ph = V["PHASE"][frame]

    def side_trim(tag, z, a):
        if tag in ("Yp", "Yn"):
            if 6.2 <= z < 6.8:
                return "cream"                       # pinstripe along the body
            if z < 5.0:
                return "red_d"                       # darker rocker panel
        return None

    def body(tag, n, a, b, z, x, y):
        return side_trim(tag, z, a) or lit(RED, n, x, y)

    def cab(tag, n, a, b, z, x, y):
        if tag in ("Yp", "Yn"):
            if 0.12 < a < 0.46 and 7.6 < z < 10.6:
                return "white" if (a < 0.2 and z > 9.8) else lit(GLASS, n, x, y)     # glint
            if abs(a - 0.08) < 0.025 and z < 10.6:
                return "red_d"                       # door seam
            if 0.14 < a < 0.24 and 6.9 < z < 7.5:
                return "grey_l"                      # door handle
        if tag == "Xp" and abs(b) < 0.24 and 8.4 < z < 10.8:
            return "white" if (b < -0.12 and z > 10.0) else lit(GLASS, n, x, y)
        if tag == "Xn" and abs(b) < 0.22 and 7.6 < z < 10.6:
            return lit(GLASS, n, x, y)
        return side_trim(tag, z, a) or lit(RED, n, x, y)

    def bonnet(tag, n, a, b, z, x, y):
        if tag == "Xp" and abs(b) < 0.2 and 5.0 < z < 7.4:
            return "grey_l" if int(math.floor((b + 0.2) * 14)) % 2 else "grey_d"     # chrome grille
        if tag == "Xp" and abs(b) < 0.05 and 7.4 <= z < 7.9:
            return "yellow"                          # badge
        if tag == "Zp" and abs(b) < 0.02:
            return "red_d"                           # hood seam
        return side_trim(tag, z, a) or lit(RED, n, x, y)

    def bed(tag, n, a, b, z, x, y):
        if tag in ("Xp", "Xn", "Yp", "Yn"):
            if z > 8.4:
                return "tan"                         # lit top rail
            if (a + 0.9) % 0.3 < 0.05:
                return "brown_d"                     # stakes
            if z % 2.2 < 0.5:
                return "brown_d"
        return lit(WOOD, n, x, y)

    def chrome(tag, n, a, b, z, x, y):
        return lit(CHROME, n, x, y, crisp=True)

    def lamp(tag, n, a, b, z, x, y):
        return "white" if abs(z - 6.7) < 0.35 else "yellow"

    parts = [
        (box(-0.9, 0.9, -0.28, 0.28, 2.5, 4.5), V["dark_mat"]),
        (box(-0.9, 0.05, -0.3, 0.3, 4.0, 4.6), bed),
        (box(-0.9, 0.05, -0.34, -0.3, 4.6, 9.0), bed),
        (box(-0.9, 0.05, 0.3, 0.34, 4.6, 9.0), bed),
        (box(-0.96, -0.9, -0.34, 0.34, 4.0, 9.0), bed),
        (box(0.05, 0.55, -0.3, 0.3, 4.5, 11.5), cab),
        (box(0.55, 0.92, -0.3, 0.3, 4.5, 8.0), bonnet),
        (box(0.05, 0.55, -0.32, 0.32, 11.5, 12.5), body),
        (box(0.9, 0.98, -0.34, 0.34, 3.4, 4.8), chrome),             # chrome front bumper
        (box(-0.98, -0.9, -0.34, 0.34, 2.8, 3.8), chrome),
        (box(0.9, 0.94, -0.22, -0.1, 6.0, 7.4), lamp),
        (box(0.9, 0.94, 0.1, 0.22, 6.0, 7.4), lamp),
        (box(-0.98, -0.92, -0.26, -0.1, 6.0, 7.4), V["tail_mat"]),
        (box(-0.98, -0.92, 0.1, 0.26, 6.0, 7.4), V["tail_mat"]),
        (box(0.36, 0.76, -0.37, -0.3, 4.3, 6.2), body),              # front fenders
        (box(0.36, 0.76, 0.3, 0.37, 4.3, 6.2), body),
        (box(-0.74, -0.26, -0.38, -0.34, 4.2, 5.8), body),           # rear fenders
        (box(-0.74, -0.26, 0.34, 0.38, 4.2, 5.8), body),
        (box(0.46, 0.52, -0.42, -0.32, 8.8, 9.9), chrome),           # mirrors
        (box(0.46, 0.52, 0.32, 0.42, 8.8, 9.9), chrome),
        (box(0.08, 0.11, 0.24, 0.27, 12.5, 16.5), V["dark_mat"]),    # aerial
    ]
    parts += V["wheels"](-0.5, 5.5, 0.2, 5.5, 0.3, ph)
    parts += V["wheels"](0.55, 5.5, 0.2, 5.5, 0.3, ph)
    solids = V["build"](parts, heading)
    if load:
        solids += V["truck_load"]()
    return solids


# ---------------------------------------------------------------- workbench (buildings.py frame: u, v, z)

def workbench_d(clutter=False):
    box, Solid, flat, prism, circle, lit = B["box"], B["Solid"], B["flat"], B["prism"], B["circle"], B["lit"]
    solids = list(B["workbench"](False))
    base_top = solids[0].mat

    def top(tag, n, u, v, z, x, y):
        if tag == "T" and h2(u * 40, v * 40) < 0.06:
            return "brown"                            # knots in the planks
        if tag in ("R", "L") and z > 10.0:
            return "tan"                              # worn front lip
        return base_top(tag, n, u, v, z, x, y)

    def drawer(tag, n, u, v, z, x, y):
        if tag == "L":
            if abs(u) < 0.07 and 6.5 < z < 7.3:
                return "grey_l"                       # brass pull
            if abs(u) > 0.3 or z < 5.9 or z > 7.9:
                return "brown_d"
            return "brown_l"
        return "brown"

    def planks(tag, n, u, v, z, x, y):
        if tag == "T":
            return "tan"
        return "brown_d" if z % 0.9 < 0.3 else "brown_l"

    solids[0] = Solid(solids[0].cons, top)
    solids += [
        Solid(box(-0.34, 0.34, 0.22, 0.31, 5.6, 8.3), drawer),                       # drawer
        Solid(box(-0.6, 0.6, -0.2, 0.2, 2.0, 2.8), flat(WOOD)),                       # bottom shelf
        Solid(box(-0.52, 0.12, -0.14, 0.14, 2.8, 4.6), planks),                       # planks on it
        Solid(box(0.16, 0.22, -0.29, -0.25, 13.0, 17.2), flat(CHROME)),               # wrench
        Solid(prism(circle(0.07, 8, -0.48, 0.1), 10.5, 12.6), flat(["red_d", "red", "red", "pink"])),   # oil can
        Solid(box(0.24, 0.42, 0.06, 0.2, 10.5, 11.4), flat(["slate", "grey_d", "grey", "grey_l"])),   # clamp
    ]
    for pu in (-0.62, 0.62):                                                          # iron corner brackets
        for pv in (-0.2, 0.2):
            solids.append(Solid(box(pu - 0.09, pu + 0.09, pv - 0.09, pv + 0.09, 7.4, 8.5), flat(CHROME)))
    if clutter:
        for su in (0.95, 1.35):                                                       # sawhorse
            for sv in (-0.18, 0.18):
                solids.append(Solid(box(su - 0.04, su + 0.04, sv - 0.04, sv + 0.04, 0.0, 6.0), flat(WOOD)))
        solids.append(Solid(box(0.88, 1.42, -0.06, 0.06, 6.0, 7.0), flat(WOOD)))
        solids.append(Solid(box(0.84, 1.5, -0.14, 0.14, 7.0, 7.9), planks))          # plank being cut
        solids.append(Solid(prism(circle(0.12, 8, -0.95, 0.35), 0.0, 4.5), flat(["slate", "grey_d", "grey", "grey_l"])))  # nail bucket
        solids.append(Solid(prism(circle(0.2, 9, 0.1, 0.55), 0.0, 0.8), flat(["brown_l", "tan", "tan", "cream"])))         # shavings
    return solids


def well_d(clutter=False):
    box, Solid, flat, prism, circle = B["box"], B["Solid"], B["flat"], B["prism"], B["circle"]
    solids = list(B["well"](False))
    ring = solids[0].mat

    def mossy(tag, n, u, v, z, x, y):
        c = ring(tag, n, u, v, z, x, y)
        if tag != "T" and z < 4.0 and c != "grey_d" and h2(x, y, 3) < 0.35 - z * 0.07:
            return "green_d" if h2(x, y, 4) < 0.5 else "green"
        if tag == "T" and u * u + v * v >= 0.13 and h2(x, y, 5) < 0.08:
            return "green"                            # moss in the rim's cracks
        return c

    solids[0] = B["Solid"](solids[0].cons, mossy)
    dark = flat(["ink", "slate", "grey_d", "grey"])
    solids += [
        Solid(box(0.34, 0.46, -0.32, -0.27, 16.4, 17.2), dark),                       # crank axle
        Solid(box(0.44, 0.48, -0.32, -0.27, 14.0, 17.2), dark),                       # crank arm
        Solid(box(0.44, 0.48, -0.36, -0.23, 13.8, 14.4), flat(WOOD)),                 # handle
        Solid(prism(circle(0.1, 8, 0.75, 0.2), 0.0, 3.5), flat(WOOD)),                # spare bucket at the foot
    ]
    if clutter:
        water = ["blue_d", "blue", "cyan", "white"]
        solids.append(Solid(box(-1.1, -0.5, 0.45, 0.75, 0.0, 3.0), flat(WOOD)))      # trough
        solids.append(Solid(box(-1.06, -0.54, 0.49, 0.71, 3.0, 3.2), flat(water)))
        solids.append(Solid(prism(circle(0.1, 8, 0.7, -0.6), 0.0, 2.5), flat(["rust", "clay", "clay", "tan"])))   # flower pot
        solids.append(Solid(B["cone"](circle(0.12, 8, 0.7, -0.6), 2.5, 5.0, 0.7, -0.6), flat(["green_d", "green", "green", "yellow"])))
    return solids


def market_d(clutter=False):
    box, Solid, flat, prism, circle, cone = B["box"], B["Solid"], B["flat"], B["prism"], B["circle"], B["cone"]
    solids = list(B["market"](False))

    def chalkboard(tag, n, u, v, z, x, y):
        if tag == "L":
            if z < 1.0 or z > 9.2 or abs(u - 0.62) > 0.13:
                return "brown"                        # frame
            if 6.5 < z < 7.3 and abs(u - 0.62) < 0.08:
                return "white"                        # chalk lines: name...
            if 4.0 < z < 4.8 and abs(u - 0.6) < 0.05:
                return "yellow"                       # ...and price
            return "slate"
        return "brown_d"

    basket = flat(["brown_d", "brown", "brown_l", "tan"])
    solids += [
        Solid(box(0.46, 0.78, 0.52, 0.58, 0.0, 10.0), chalkboard),                    # price board
        Solid(prism(circle(0.14, 9, -0.2, 0.58), 0.0, 3.5), basket),                  # baskets of produce
        Solid(cone(circle(0.13, 9, -0.2, 0.58), 3.5, 6.0, -0.2, 0.58), flat(B["PILE"]["tomato"])),
        Solid(prism(circle(0.14, 9, 0.16, 0.6), 0.0, 3.5), basket),
        Solid(cone(circle(0.13, 9, 0.16, 0.6), 3.5, 6.0, 0.16, 0.6), flat(B["PILE"]["carrot"])),
        Solid(box(-0.05, 0.05, -0.02, 0.02, 15.0, 19.3), flat(["ink", "slate", "grey_d", "grey"])),   # lantern hook
        Solid(prism(circle(0.06, 6), 13.2, 15.0), flat(["amber", "yellow", "yellow", "white"])),
    ]
    if clutter:
        sack = flat(["tan_d", "tan", "cream", "white"]) if "tan_d" in E else flat(["brown_l", "tan", "cream", "white"])
        solids.append(Solid(prism(circle(0.15, 8, -0.75, 0.25), 0.0, 5.0), sack))   # grain sacks
        solids.append(Solid(prism(circle(0.14, 8, -0.78, -0.08), 0.0, 4.4), sack))
        solids.append(Solid(box(-0.82, -0.5, -0.5, -0.2, 0.0, 3.0), V["crate_mat"]) if False else
                      Solid(box(-0.82, -0.5, -0.5, -0.2, 0.0, 3.0), flat(WOOD)))
        solids.append(Solid(cone(circle(0.16, 9, 0.62, -0.5), 0.0, 3.6, 0.62, -0.5), flat(["rust", "orange", "orange", "amber"])))   # pumpkin
    return solids


# ---------------------------------------------------------------- rendering and the sheet

def sprite(ns, solids, s, name):
    """Render like scaled.emit, then the polish pass: an RGBA image and its pivot."""
    big = dict(ns)
    big.update(W=200, H=170, CANVAS=200, CX=100, CY=125)
    im = scaled.render(big, solids, s)
    x0, y0, x1, y1 = im.getbbox()
    out, pad = finish(im.crop((x0, y0, x1, y1)))
    out = polish.polish(out, "world", name)
    return out, (100 - x0 + pad, 125 - y0 + pad)


def current(name):
    import json
    m = json.load(open(os.path.join(ART, "manifest.json")))
    p = m[name]
    return Image.open(os.path.join(ART, name + ".png")).convert("RGBA"), (p["ax"], p["ay"])


PROPS = [
    # name in the game, module ns, now scale, detailed builder
    ("vehicles/truck_se_0", V, 0.66, lambda c: truck_d(load=c)),
    ("buildings/workbench", B, 0.55, lambda c: workbench_d(c)),
    ("buildings/well", B, 0.7, lambda c: well_d(c)),
    ("buildings/market", B, 0.72, lambda c: market_d(c)),
]
ROWS = [("NOW", None), ("A  DETAIL", (1.0, False)), ("B  BIGGER", (1.25, False)), ("C  LIVED-IN", (1.25, True))]
CW, CH = 72, 64                       # cell size in game pixels
GRASS, GRASS_D = E["green_d"], E["green_dd"]


def cell(sprite_im, pivot):
    c = Image.new("RGBA", (CW, CH), GRASS)
    d = ImageDraw.Draw(c)
    ax, ay = 44, CH - 14
    for k in range(-1, 2):            # three ground tiles under the prop
        cx, cy = ax + k * 16, ay + k * 8
        d.polygon([(cx - 16, cy), (cx, cy - 8), (cx + 16, cy), (cx, cy + 8)], outline=GRASS_D)
    hand, hp = current("people/hana_se_idle")
    c.alpha_composite(hand, (9 - hp[0], ay + 2 - hp[1]))
    c.alpha_composite(sprite_im, (ax - pivot[0], ay - pivot[1]))
    return c


def main():
    out = sys.argv[1]
    label_w = 40
    sheet = Image.new("RGBA", (label_w + CW * len(PROPS), 14 + CH * len(ROWS)), E["ink"])
    d = ImageDraw.Draw(sheet)
    for j, (name, _, _, _) in enumerate(PROPS):
        d.text((label_w + j * CW + 4, 2), name.split("/")[1].replace("_se_0", "").upper(), fill=E["cream"])
    for i, (label, opt) in enumerate(ROWS):
        d.text((2, 14 + i * CH + CH // 2 - 4), label.split()[0], fill=E["yellow"])
        for j, (name, ns, s, builder) in enumerate(PROPS):
            if opt is None:
                im, pv = current(name)
            else:
                k, clutter = opt
                im, pv = sprite(ns, builder(clutter), s * k, name)
            sheet.alpha_composite(cell(im, pv), (label_w + j * CW, 14 + i * CH))
            sys.stderr.write(f"{label} {name}\n")
    sheet = sheet.resize((sheet.width * 5, sheet.height * 5), Image.NEAREST)
    sheet.convert("RGB").save(out)
    print(out, sheet.size)


main()
