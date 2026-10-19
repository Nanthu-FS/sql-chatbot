"""Hand-pixeled farm props (art round 5), drawn pixel by pixel on the iso grid (see _iso.py).

Style, from the owner's reference packs (Cute Fantasy, Farm RPG, Modern Interiors), translated to
our iso view: warm colours, flat 3-tone faces lit from the top left, a lit edge on every top,
chunky 2-3 px features (plank seams, trims, frames with a white glint, knobs and bolts), no
dithering and no noise.

  vehicles/truck_se_0/1, truck_ne_0/1   the red pickup, driving down-right / away up-right
  buildings/workbench(_snow)            the farmyard workbench
  buildings/well(_snow)                 the stone well
  buildings/market(_snow)               the market stall

Pivot = the ground centre of the footprint.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _iso import Iso  # noqa: E402
from _pix import Sprites, finish  # noqa: E402

# Paint ramps (top / left face / right face) and accents.
RED = ("red", "red_d", "purple")
WOOD = ("tan", "brown_l", "brown")
WOOD_D = ("brown_l", "brown", "brown_d")
IRON = ("grey_l", "grey", "grey_d")
DARK = ("slate", "slate_d", "ink")
STONE = ("grey_l", "grey", "grey_d")


# ---------------------------------------------------------------- truck

def truck_se(frame):
    """Driving down-right: the left side and the front (grille, lamps) show."""
    L, W = 12, 5
    c = Iso(40, 34, 12, 14)
    c.box(0.5, 11.5, 0.5, 4.5, 1, 2.5, *DARK)                       # chassis
    # Bed: wooden sides with stakes, a lit rim, darker floor seen from above.
    c.box(0, 6.6, 0, W, 2, 7, "brown", *WOOD[1:])
    c.box(0.4, 6.6, 0.4, W - 0.4, 2, 6.6, "brown_d", "brown", "brown_d")
    c.uline(0, 6.6, W, 7, "tan")
    c.uline(0, 6.6, W, 4.5, "brown")
    for u in (0.2, 3.3):
        c.zline(u, W, 2, 7, "brown")
    for i in range(3):                                              # floor planks
        c.uline(0.6, 6.4, 1.4 + i * 1.2, 2.6, "brown")
    # Cab with doors, side window and windscreen.
    c.box(6.6, 9.6, 0, W, 2, 11, "red", "red_d", "purple")
    c.uline(6.6, 9.6, W, 11, "pink")                                # lit roof edge
    c.box(6.9, 9.3, W - 0.01, W, 6.5, 9.8, "blue_d", "blue_d", "blue_d")
    c.box(9.59, 9.6, 0.5, W - 0.5, 7.0, 10.2, "blue_d", "blue_d", "blue_d")
    x, y = c.p(7.2, W, 9.3)
    c.dot(x, y, "cyan"); c.dot(x + 1, y, "white"); c.dot(x + 2, y + 1, "cyan")
    x, y = c.p(9.6, 1.0, 9.7)
    c.dot(x, y, "white"); c.dot(x - 1, y + 1, "cyan")
    c.zline(6.9, W, 2.4, 10, "purple")                              # door seam
    x, y = c.p(9.6, W, 6.5)                                         # corner pillar between the windows
    for k in range(4):
        c.dot(x - 1, y - k, "red"); c.dot(x - 2, y - k, "red_d")
    hx, hy = c.p(7.6, W, 5.6)
    c.dot(hx, hy, "grey_l"); c.dot(hx + 1, hy, "grey_l")
    c.uline(6.6, 9.6, W, 5.0, "cream")                              # pinstripe
    # Bonnet, grille, lamps, chrome bumper.
    c.box(9.6, L, 0, W, 2, 6.6, "red", "red_d", "purple")
    c.uline(9.6, L, W, 6.6, "pink")
    c.uline(9.6, L, W, 5.0, "cream")
    c.box(L - 0.01, L, 1.4, W - 1.4, 3.2, 5.6, "ink", "ink", "ink")         # grille
    c.vline(L, 2, 1.6, 4.2, "grey_l")
    c.vline(L, 2, 1.6, 5.2, "grey")
    for v in (0.5, W - 0.9):                                                # headlamps
        x, y = c.p(L, v, 5.0)
        c.dot(x - 1, y, "yellow"); c.dot(x - 2, y, "white")
    c.box(L, L + 0.25, 0.2, W - 0.2, 1.8, 2.6, "grey_l", "grey", "grey_l")   # bumper
    c.box(9.2, 9.5, W, W + 0.5, 8, 9.2, *IRON)                      # mirror
    tx, ty = c.p(0.1, W, 5.5)
    c.dot(tx, ty, "pink_r"); c.dot(tx + 1, ty, "pink_r")            # tail lamp
    # Wheels: tyre, rim, hub; the spoke turns between frames.
    for u in (2.4, 9.7):
        x, y = c.p(u, W, 2)
        c.ellipse(x, y, 3.5, 3.5, "purple" if u > 6 else "brown_d")         # wheel arch
        c.ellipse(x, y + 1, 2.5, 2.5, "ink")
        c.ellipse(x, y + 1, 1, 1, "grey")
        y -= 1
        c.dot(x, y + 1, "grey_l")
        sx, sy = ((1, 0), (0, 1))[frame]
        c.dot(x + sx, y + 1 + sy, "white")
    return c, c.pivot(L / 2, W / 2)


def truck_ne(frame):
    """Driving away up-right: the right side and the tailgate show (front hidden)."""
    L, W = 12, 5
    c = Iso(40, 34, 22, 8)
    c.box(0.5, 4.5, 0.5, 11.5, 1, 2.5, *DARK)
    # Here the length runs along v: the cab is at the far end (small v), the bed near (large v).
    c.box(0, W, 2.6, 2.6 + 3.0, 2, 11, "red", "red_d", "purple")          # cab
    c.vline(W, 3, 2.6, 11, "pink")
    c.box(W - 0.01, W, 3.0, 5.2, 6.5, 9.8, "blue_d", "blue_d", "blue_d")   # side window
    x, y = c.p(W, 3.4, 9.4)
    c.dot(x - 1, y, "cyan"); c.dot(x - 2, y + 1, "white")
    c.box(0, W, 0, 2.6, 2, 6.6, "red", "red_d", "purple")                 # bonnet
    x, y = c.p(0.8, 5.59, 10.2)                                           # rear cab window
    for i in range(6):
        c.dot(x + i, y + i // 2, "blue_d"); c.dot(x + i, y + i // 2 + 1, "blue_d")
    c.dot(x + 1, y + 1, "cyan")
    c.box(0, W, 5.6, L, 2, 7, "brown", "tan_d", "brown")                  # bed
    c.box(0.4, W - 0.4, 5.6, L - 0.4, 2, 6.6, "brown_d", "brown", "brown_d")
    c.uline(0, W, L, 4.6, "brown_l")                                      # tailgate planks
    c.vline(W, round(L - 5.6), 5.6, 7, "tan")
    c.vline(W, round(L - 5.6), 5.6, 4.5, "brown_d")
    c.uline(0, W, L, 7, "tan")                                            # tailgate rim
    for u in (0.6, W - 1.0):                                              # tail lamps
        x, y = c.p(u, L, 5.6)
        c.dot(x, y, "pink_r"); c.dot(x + 1, y, "pink_r")
    x, y = c.p(W / 2 - 0.8, L, 4.0)
    for i in range(4):
        c.dot(x + i, y + i // 2, "cream")                                # plate
    c.box(0.2, W - 0.2, L, L + 0.25, 1.8, 2.6, "grey_l", "grey_l", "grey")   # rear bumper
    for v in (3.2, 9.6):
        x, y = c.p(W, v, 2)
        c.ellipse(x, y, 3.5, 3.5, "purple" if v < 6 else "brown_d")
        c.ellipse(x, y + 1, 2.5, 2.5, "ink")
        c.ellipse(x, y + 1, 1, 1, "grey")
        sx, sy = ((1, 0), (0, 1))[frame]
        c.dot(x + sx, y + 1 + sy, "white")
    return c, c.pivot(W / 2, L / 2)


# ---------------------------------------------------------------- workbench

def workbench(winter):
    c = Iso(34, 34, 9, 14)
    U, V = 7, 3
    top = "white" if winter else "tan"
    for u, v in ((0.2, 2.4), (6.4, 2.4), (6.4, 0.2)):                    # legs
        c.box(u, u + 0.5, v, v + 0.5, 0, 7, *WOOD_D)
    c.box(0.4, 6.6, 0.4, 2.6, 2, 2.8, *WOOD_D)                            # bottom shelf
    c.box(0.8, 4.0, 0.8, 2.2, 2.8, 4.4, "tan", "brown_l", "brown")      # planks on it
    c.uline(0.8, 4.0, 2.2, 3.6, "brown")
    c.box(0, U, 0, V, 7, 8.6, top, "brown_l", "brown")                    # bench top
    c.uline(0, U, 1, 8.6, "brown_l" if not winter else "grey_l")          # plank seams
    c.uline(0, U, 2, 8.6, "brown_l" if not winter else "grey_l")
    c.uline(0, U, V, 7, "brown_d")
    c.box(2, 5, V - 0.01, V, 4.6, 6.8, "brown", "brown", "brown")         # drawer
    x, y = c.p(3.5, V, 5.6)
    c.dot(x, y, "grey_l"); c.dot(x + 1, y, "grey_l")
    c.uline(2, 5, V, 6.8, "brown_d")
    # Tool wall at the back with peg holes and tools hanging on it.
    c.box(0, U, -0.5, 0, 8.6, 15.5, "brown", "brown_l", "brown_d")
    c.uline(0, U, 0, 15.4, "tan")                                       # lit top of the board
    x, y = c.p(1.0, 0, 14.4)                                            # hammer: iron head, wooden handle
    for i in range(3):
        c.dot(x + i, y, "grey_l"); c.dot(x + i, y + 1, "grey")
    for i in range(4):
        c.dot(x + 1, y + 2 + i, "brown_d")
    x, y = c.p(2.6, 0, 14.0)                                            # saw: red handle, toothed blade
    c.dot(x, y, "red"); c.dot(x + 1, y, "red"); c.dot(x, y + 1, "red_d")
    for i in range(6):
        c.dot(x + 2 + i, y + i // 2, "grey_l"); c.dot(x + 2 + i, y + i // 2 + 1, "grey" if i % 2 else "grey_d")
    x, y = c.p(5.4, 0, 14.6)                                            # wrench
    c.dot(x - 1, y, "grey_l"); c.dot(x + 1, y, "grey_l"); c.dot(x, y + 1, "grey_l")
    for i in range(4):
        c.dot(x, y + 2 + i, "grey")
    c.box(5.4, 6.7, 1.0, 2.2, 8.6, 10.2, *IRON)                           # vice
    c.box(1.0, 1.6, 1.2, 1.8, 8.6, 10.6, "pink", "red", "red_d")         # oil can
    return c, c.pivot(U / 2, V / 2)


# ---------------------------------------------------------------- well

def well(winter):
    c = Iso(40, 44, 20, 28)
    cx, cy = c.p(0, 0, 0)
    rx, ry, hgt = 10, 5, 9
    for z in range(hgt + 1):                                              # stone drum
        c.ellipse(cx, cy - z, rx, ry, "grey" if z < hgt else "grey_l")
    for z in (3, 6):                                                      # mortar courses
        for x in range(cx - rx + 1, cx + rx):
            dx = (x - cx) / rx
            y = round(cy - z + ry * (1 - dx * dx) ** 0.5)
            c.dot(x, y, "grey_d")
    for k, x in enumerate(range(cx - rx + 3, cx + rx - 1, 4)):           # staggered joints
        for z0 in (0, 3, 6):
            xx = x + (2 if (z0 // 3) % 2 else 0)
            if abs(xx - cx) >= rx - 1:
                continue
            dx = (xx - cx) / rx
            y = round(cy - z0 + ry * (1 - dx * dx) ** 0.5)
            c.dot(xx, y - 1, "grey_d"); c.dot(xx, y - 2, "grey_d")
    for x in range(cx - rx + 1, cx - 2):                                  # moss at the foot
        dx = (x - cx) / rx
        y = round(cy + ry * (1 - dx * dx) ** 0.5)
        if (x * 7) % 5 < 3:
            c.dot(x, y - 1, "green_d")
    c.ellipse(cx, cy - hgt, rx - 2, ry - 1, "white" if winter else "blue_d")   # water
    if not winter:
        c.ellipse(cx + 1, cy - hgt + 1, rx - 4, ry - 2, "blue")
        c.dot(cx - 3, cy - hgt - 1, "cyan"); c.dot(cx - 2, cy - hgt - 1, "cyan")
    # Posts, crossbeam with rope and bucket, crank.
    for px_ in (cx - 8, cx + 8):
        for z in range(hgt, hgt + 14):
            c.dot(px_, cy - z, "brown_l"); c.dot(px_ + 1, cy - z, "brown")
    for x in range(cx - 8, cx + 10):
        c.dot(x, cy - hgt - 13, "brown_l"); c.dot(x, cy - hgt - 12, "brown")
    for z in range(hgt + 4, hgt + 12):
        c.dot(cx, cy - z, "tan")
    c.ellipse(cx, cy - hgt - 3, 2, 1, "brown_l")
    for z in range(hgt + 1, hgt + 4):
        c.dot(cx - 2, cy - z, "brown"); c.dot(cx + 2, cy - z, "brown_d")
        for x in range(cx - 1, cx + 2):
            c.dot(x, cy - z, "brown_l")
    c.dot(cx - 2, cy - hgt - 2, "grey_d"); c.dot(cx + 2, cy - hgt - 2, "grey_d")
    for i in range(3):                                                    # crank
        c.dot(cx + 10 + i, cy - hgt - 12, "grey")
    c.dot(cx + 12, cy - hgt - 11, "grey"); c.dot(cx + 12, cy - hgt - 10, "brown_l")
    # Gable roof: the front slope in shingle rows (lit lip, seams), dark eave, ridge cap on top.
    snow = winter
    base, lip, seam = ("white", "white", "grey_l") if snow else ("brown_l", "tan", "brown")
    rows = 4
    for row in range(rows):
        y = cy - hgt - 16 - row * 2
        half = 12 - row * 3
        for x in range(cx - half, cx + half + 2):
            c.dot(x, y, lip)
            c.dot(x, y + 1, seam if (x + row * 2) % 4 == 0 else base)
    for x in range(cx - 12, cx + 14):
        c.dot(x, cy - hgt - 14, "brown_d")                             # eave shadow line
    for x in range(cx - 3, cx + 5):
        c.dot(x, cy - hgt - 16 - rows * 2, "grey_l" if snow else "brown_d")   # ridge cap
    return c, (cx, cy + ry)


# ---------------------------------------------------------------- market stall

def market(winter):
    c = Iso(52, 54, 22, 34)
    U, V = 8, 4
    for u, v in ((0, V - 0.4), (U - 0.4, V - 0.4), (U - 0.4, 0)):         # corner posts
        c.box(u, u + 0.4, v, v + 0.4, 0, 19, *WOOD_D)
    c.box(0, U, 0, V, 0, 9, "brown_l", "brown_l", "brown")               # counter
    for k in range(1, U):
        c.zline(k, V, 0, 9, "brown")                                     # front boards
    c.uline(0, U, V, 9, "tan")
    c.uline(0, U, V, 1, "brown_d")
    # Produce in crates on the counter: tomatoes, carrots, cabbages.
    crops = [("red", "pink"), ("orange", "amber"), ("green_d", "green")]
    for i, (dark, light) in enumerate(crops):
        u0 = 0.5 + i * 2.5
        c.box(u0, u0 + 2.1, 0.8, 3.4, 9, 10.2, "brown", "tan", "brown_l")
        for j in range(5):
            x, y = c.p(u0 + 0.5 + (j % 3) * 0.6, 1.4 + (j // 3) * 1.1, 10.6)
            c.dot(x, y, light); c.dot(x + 1, y, dark); c.dot(x, y + 1, dark)
    # Hanging price board.
    x, y = c.p(U, V / 2, 15)
    for i in range(5):
        for j in range(4):
            c.dot(x - 2 - i, y + j + i // 2, "slate" if 0 < j < 3 else "brown")
    c.dot(x - 4, y + 2, "white"); c.dot(x - 5, y + 2, "white")
    # Striped awning sloping down to the front.
    stripes = ("white", "grey_l") if winter else ("red", "cream")
    for k in range(U * 2):
        u = k * 0.5
        col = stripes[(k // 2) % 2]
        c.poly([(u, -0.4, 22), (u + 0.5, -0.4, 22), (u + 0.5, V + 0.6, 18), (u, V + 0.6, 18)], col)
    for k in range(U * 2):                                               # scalloped valance
        u = k * 0.5
        x, y = c.p(u, V + 0.6, 18)
        col = ("grey_l" if winter else "red_d") if (k // 2) % 2 == 0 else ("grey" if winter else "tan")
        c.dot(x, y + 1, col); c.dot(x + 1, y + 1, col)
        if k % 2 == 0:
            c.dot(x, y + 2, col)
    c.box(U - 0.01, U, -0.4, V + 0.6, 18, 22, "purple", "purple", "red_d") if not winter else None
    return c, c.pivot(U / 2, V / 2)


PROPS = [("vehicles/truck_se_0", lambda: truck_se(0)), ("vehicles/truck_ne_0", lambda: truck_ne(0)),
         ("buildings/workbench", lambda: workbench(False)), ("buildings/well", lambda: well(False)),
         ("buildings/market", lambda: market(False))]


def preview(out):
    """Today's sprites (top row) over the hand-pixeled ones (bottom row), polished like the game, 5x."""
    from PIL import Image, ImageDraw
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    import polish
    import json
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app", "src", "main", "assets", "art")
    man = json.load(open(os.path.join(art, "manifest.json")))
    cw, ch = 62, 74
    sheet = Image.new("RGBA", (cw * len(PROPS), ch * 2), (62, 137, 72, 255))
    d = ImageDraw.Draw(sheet)
    hand = Image.open(os.path.join(art, "people/hana_se_idle.png")).convert("RGBA")
    hp = man["people/hana_se_idle"]
    for i, (name, fn) in enumerate(PROPS):
        for row in range(2):
            ax, ay = i * cw + 36, row * ch + ch - 14
            d.line([(ax - 16, ay), (ax, ay - 8), (ax + 16, ay), (ax, ay + 8), (ax - 16, ay)], fill=(48, 110, 60, 255))
            sheet.alpha_composite(hand, (i * cw + 6 - hp["ax"] + 4, ay - hp["ay"]))
            if row == 0:
                im = Image.open(os.path.join(art, name + ".png")).convert("RGBA")
                p = man[name]
                sheet.alpha_composite(im, (ax - p["ax"], ay - p["ay"]))
            else:
                cnv, (px_, py_) = fn()
                x0, y0, x1, y1 = cnv.im.getbbox()
                body, pad = finish(cnv.im.crop((x0, y0, x1, y1)))
                body = polish.polish(body, "world", name)
                sheet.alpha_composite(body, (ax - (px_ - x0 + pad), ay - (py_ - y0 + pad)))
    sheet.resize((sheet.width * 5, sheet.height * 5), Image.NEAREST).save(out)


if __name__ == "__main__" and len(sys.argv) > 2 and sys.argv[1] == "--preview":
    preview(sys.argv[2])
