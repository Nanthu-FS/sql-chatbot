"""Hand-pixeled farm props (art round 5), drawn pixel by pixel on the iso grid (see _iso.py).

Dither Farm's own look, the painted toy: bold saturated paint, chunky rounded silhouettes and a
lit edge on every top, like hand-painted wooden farm toys. Signature marks that repeat across
the farm: the barn's red-and-cream trim on every building and vehicle, the wheat-sheaf emblem
(as on the app icon) and hand-painted names. Reference packs were studied for readability only;
nothing is copied from them.

  vehicles/truck_se_0/1, truck_ne_0/1   the red pickup, driving down-right / away up-right
  vehicles/truck_load                   produce crates in its bed (drawn on the parked truck)
  buildings/workbench(_snow)            the farmyard workbench
  buildings/well(_snow)                 the stone well
  buildings/market(_snow)               the market stall

Pivot = the ground centre of the footprint.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _iso import Iso, soften  # noqa: E402
from _pix import Sprites, finish  # noqa: E402

# Paint (top / left face / right face).
RED = ("red", "red_d", "purple")
CREAM = ("cream", "tan", "tan_d")
WOOD = ("tan", "brown_l", "brown")
WOOD_D = ("brown_l", "brown", "brown_d")
IRON = ("grey_l", "grey", "grey_d")
DARK = ("slate", "slate_d", "ink")


def wheel(c, x, y, frame, rim="cream"):
    """A toy wheel: dark tyre, painted rim, red hub; the bright notch turns between frames."""
    c.ellipse(x, y, 2.5, 2.5, "ink")
    c.ellipse(x, y, 1.2, 1.2, rim)
    c.dot(x, y, "red")
    sx, sy = ((1, -1), (1, 1))[frame]
    c.dot(x + sx, y + sy, "white")


# ---------------------------------------------------------------- truck

def truck_se(frame):
    """Driving down-right: the left side and the front (grille, lamps) show."""
    L, W = 12, 5
    c = Iso(40, 34, 12, 14)
    c.box(0.5, 11.5, 0.5, 4.5, 1, 2.5, *DARK, lit=False)                      # chassis
    # Bed: red sides with cream stakes and rim, the wheat emblem, a wooden floor inside.
    c.box(0, 6.6, 0, W, 2, 6, "cream", "red_d", "purple")
    c.box(0.4, 6.6, 0.4, W - 0.4, 2.6, 5.6, "brown", "brown_l", "brown_d", lit=False)
    for i in range(3):
        c.uline(0.6, 6.4, 1.3 + i * 1.2, 2.6, "brown_d")                      # floor planks
    for u in (0.3, 6.2):
        c.zline(u, W, 2, 6, "cream")                                          # stakes
    x, y = c.p(2.4, W, 5.8)
    c.wheat(x, y)
    # Cab: red, cream roof, windows with a glint, door seam and handle.
    c.box(6.6, 9.6, 0, W, 2, 10, "cream", "red", "red_d")
    c.box(6.9, 9.3, W - 0.01, W, 6.2, 9.0, "blue_d", "blue_d", "blue_d", lit=False)
    c.box(9.59, 9.6, 0.5, W - 0.5, 6.4, 9.4, "blue_d", "blue_d", "blue_d", lit=False)
    x, y = c.p(7.2, W, 8.5)
    c.dot(x, y, "cyan"); c.dot(x + 1, y, "white"); c.dot(x + 2, y + 1, "cyan")
    x, y = c.p(9.6, 1.0, 8.9)
    c.dot(x, y, "white"); c.dot(x - 1, y + 1, "cyan")
    x, y = c.p(9.6, W, 6.2)                                                   # pillar between windows
    for k in range(4):
        c.dot(x - 1, y - k, "red"); c.dot(x - 2, y - k, "red_d")
    c.zline(6.9, W, 2.4, 9.2, "red_d")
    x, y = c.p(7.6, W, 5.6)
    c.dot(x, y, "cream"); c.dot(x + 1, y, "cream")
    # Bonnet: red, cream stripe, grille, lamps, cream bumper.
    c.box(9.6, L, 0, W, 2, 6.6, "red", "red", "red_d")
    c.uline(9.6, L, W, 4.6, "cream")
    c.uline(6.6, 9.6, W, 4.6, "cream")
    c.box(L - 0.01, L, 1.4, W - 1.4, 3.2, 5.6, "ink", "ink", "ink", lit=False)
    c.vline(L, 2, 1.6, 4.2, "grey_l")
    c.vline(L, 2, 1.6, 5.2, "grey")
    for v in (0.5, W - 0.9):
        x, y = c.p(L, v, 5.0)
        c.dot(x - 1, y, "yellow"); c.dot(x - 2, y, "white")
    c.box(L, L + 0.25, 0.2, W - 0.2, 1.8, 2.6, "cream", "cream", "tan")
    c.box(9.2, 9.5, W, W + 0.5, 7.4, 8.6, *IRON)                                # mirror
    x, y = c.p(0.1, W, 5.5)
    c.dot(x, y, "pink_r"); c.dot(x + 1, y, "pink_r")                          # tail lamp
    for u in (2.4, 9.7):
        x, y = c.p(u, W, 2)
        wheel(c, x, y + 1, frame)
    soften(c.im)
    return c, c.pivot(L / 2, W / 2)


def truck_ne(frame):
    """Driving away up-right: the right side and the tailgate show (the front is hidden)."""
    L, W = 12, 5
    c = Iso(40, 34, 22, 8)
    c.box(0.5, 4.5, 0.5, 11.5, 1, 2.5, *DARK, lit=False)
    # Length runs along v here: bonnet at the far end (small v), cab, then the bed nearest.
    c.box(0, W, 0, 2.6, 2, 6, "red", "red", "red_d")                         # bonnet
    c.box(0, W, 2.6, 5.6, 2, 8.8, "cream", "red", "red_d")                   # cab
    c.box(W - 0.01, W, 3.0, 5.2, 5.6, 8.0, "blue_d", "blue_d", "blue_d", lit=False)
    x, y = c.p(W, 3.4, 7.6)
    c.dot(x - 1, y, "cyan"); c.dot(x - 2, y + 1, "white")
    x, y = c.p(0.8, 5.59, 8.2)                                              # rear window
    for i in range(6):
        c.dot(x + i, y + i // 2, "blue_d"); c.dot(x + i, y + i // 2 + 1, "blue_d")
    c.dot(x + 1, y + 1, "cyan")
    c.box(0, W, 5.6, L, 2, 6, "cream", "red", "red_d")                       # bed
    c.box(0.4, W - 0.4, 5.6, L - 0.4, 2.6, 5.6, "brown", "brown_l", "brown_d", lit=False)
    c.vline(W, round(L - 5.6), 5.6, 4.2, "cream")                            # side stripe
    for i in range(3):
        c.vline(0.8 + i * 1.2, round(L - 6.2), 5.8, 2.6, "brown_d")
    x, y = c.p(W / 2 - 0.4, L, 5.8)                                          # tailgate emblem
    c.wheat(x - 2, y)
    for u in (0.4, W - 0.8):                                                 # tail lamps
        x, y = c.p(u, L, 4.8)
        c.dot(x, y, "pink_r"); c.dot(x + 1, y, "pink_r")
    c.box(0.2, W - 0.2, L, L + 0.25, 1.8, 2.6, "cream", "cream", "tan")      # bumper
    for v in (3.2, 9.6):
        x, y = c.p(W, v, 2)
        wheel(c, x, y + 1, frame)
    soften(c.im)
    return c, c.pivot(W / 2, L / 2)


# ---------------------------------------------------------------- workbench

def workbench(winter):
    """Painted-toy bench: red frame and drawer, natural wood top, a cream tool board with tools."""
    c = Iso(34, 34, 9, 14)
    U, V = 7, 3
    top = "white" if winter else "tan"
    for u, v in ((0.2, 2.4), (6.4, 2.4), (6.4, 0.2)):                        # red legs
        c.box(u, u + 0.5, v, v + 0.5, 0, 6, "red", "red_d", "purple")
    c.box(0.4, 6.6, 0.4, 2.6, 1.6, 2.4, *WOOD_D)                                # bottom shelf
    c.box(0.8, 4.0, 0.8, 2.2, 2.4, 3.8, "tan", "brown_l", "brown")          # planks on it
    c.uline(0.8, 4.0, 2.2, 3.1, "brown")
    c.box(0, U, 0, V, 6, 7.6, top, "red", "red_d")                            # top with a red apron
    c.uline(0, U, 1, 7.6, "brown_l" if not winter else "grey_l")              # plank seams
    c.uline(0, U, 2, 7.6, "brown_l" if not winter else "grey_l")
    c.uline(0, U, V, 6.6, "cream")                                            # cream trim on the apron
    c.box(2, 5, V - 0.01, V, 3.8, 5.8, "red_d", "red_d", "red_d", lit=False)  # drawer
    x, y = c.p(3.5, V, 4.8)
    c.dot(x, y, "cream"); c.dot(x + 1, y, "cream")
    # Tool board at the back, cream with a red frame, tools hanging on it.
    c.box(0, U, -0.5, 0, 7.6, 11.8, "red", "cream", "tan_d")
    c.uline(0, U, 0, 11.3, "red")
    c.zline(0.1, 0, 7.6, 11.7, "red")
    c.zline(U - 0.1, 0, 7.6, 11.7, "red")
    x, y = c.p(1.0, 0, 10.6)                                                  # hammer
    for i in range(3):
        c.dot(x + i, y, "grey_l"); c.dot(x + i, y + 1, "grey")
    for i in range(3):
        c.dot(x + 1, y + 2 + i, "brown")
    x, y = c.p(2.6, 0, 10.2)                                                  # saw
    c.dot(x, y, "red"); c.dot(x + 1, y, "red"); c.dot(x, y + 1, "red_d")
    for i in range(6):
        c.dot(x + 2 + i, y + i // 2, "grey_l"); c.dot(x + 2 + i, y + i // 2 + 1, "grey" if i % 2 else "grey_d")
    x, y = c.p(5.4, 0, 10.8)                                                  # wrench
    c.dot(x - 1, y, "grey"); c.dot(x + 1, y, "grey"); c.dot(x, y + 1, "grey")
    for i in range(3):
        c.dot(x, y + 2 + i, "grey_d")
    c.box(5.4, 6.7, 1.0, 2.2, 7.6, 9.2, *IRON)                               # vice
    c.box(1.0, 1.6, 1.2, 1.8, 7.6, 9.6, "yellow", "amber", "orange")        # oil can
    soften(c.im)
    return c, c.pivot(U / 2, V / 2)


# ---------------------------------------------------------------- well

def well(winter):
    """Stone drum, cream posts, a red shingle roof with cream trim, red bucket on its rope."""
    c = Iso(40, 46, 20, 30)
    cx, cy = c.p(0, 0, 0)
    rx, ry, hgt = 10, 5, 6
    for z in range(hgt + 1):
        c.ellipse(cx, cy - z, rx, ry, "grey" if z < hgt else "grey_l")
    for z in (2, 4):                                                          # mortar courses
        for x in range(cx - rx + 1, cx + rx):
            dx = (x - cx) / rx
            c.dot(x, round(cy - z + ry * (1 - dx * dx) ** 0.5), "grey_d")
    for x in range(cx - rx + 3, cx + rx - 1, 4):                              # staggered joints
        for z0 in (0, 2, 4):
            xx = x + (2 if (z0 // 2) % 2 else 0)
            if abs(xx - cx) >= rx - 1:
                continue
            dx = (xx - cx) / rx
            y = round(cy - z0 + ry * (1 - dx * dx) ** 0.5)
            c.dot(xx, y - 1, "grey_d"); c.dot(xx, y - 2, "grey_d")
    for x in range(cx - rx + 1, cx - 2):                                      # moss at the foot
        dx = (x - cx) / rx
        if (x * 7) % 5 < 3:
            c.dot(x, round(cy + ry * (1 - dx * dx) ** 0.5) - 1, "green")
    c.ellipse(cx, cy - hgt, rx - 2, ry - 1, "white" if winter else "blue_d")
    if not winter:
        c.ellipse(cx + 1, cy - hgt + 1, rx - 4, ry - 2, "blue")
        c.dot(cx - 3, cy - hgt - 1, "cyan"); c.dot(cx - 2, cy - hgt - 1, "cyan")
    for px_ in (cx - 8, cx + 8):                                              # cream posts
        for z in range(hgt, hgt + 10):
            c.dot(px_, cy - z, "cream"); c.dot(px_ + 1, cy - z, "tan")
    for x in range(cx - 8, cx + 10):                                          # crossbeam
        c.dot(x, cy - hgt - 9, "cream"); c.dot(x, cy - hgt - 8, "tan_d")
    for z in range(hgt + 4, hgt + 8):
        c.dot(cx, cy - z, "tan")                                              # rope
    c.ellipse(cx, cy - hgt - 3, 2, 1, "pink")                                 # red bucket, cream hoops
    for z in range(hgt + 1, hgt + 4):
        c.dot(cx - 2, cy - z, "red"); c.dot(cx + 2, cy - z, "red_d")
        for x in range(cx - 1, cx + 2):
            c.dot(x, cy - z, "red")
    for x in range(cx - 2, cx + 3):
        c.dot(x, cy - hgt - 2, "cream")
    for i in range(3):                                                        # crank
        c.dot(cx + 10 + i, cy - hgt - 8, "grey")
    c.dot(cx + 12, cy - hgt - 7, "grey"); c.dot(cx + 12, cy - hgt - 6, "red")
    # Roof: red shingle rows, cream eave board and ridge cap (the barn's colours).
    base, lip, seam = ("white", "white", "grey_l") if winter else ("red", "pink", "red_d")
    rows = 3
    for row in range(rows):
        y = cy - hgt - 12 - row * 2
        half = 12 - row * 4
        for x in range(cx - half, cx + half + 2):
            c.dot(x, y, lip)
            c.dot(x, y + 1, seam if (x + row * 2) % 4 == 0 else base)
    for x in range(cx - 12, cx + 14):
        c.dot(x, cy - hgt - 10, "cream"); c.dot(x, cy - hgt - 9, "tan_d")      # eave board
    for x in range(cx - 3, cx + 5):
        c.dot(x, cy - hgt - 12 - rows * 2, "cream")                           # ridge cap
    soften(c.im)
    return c, (cx, cy + ry)


# ---------------------------------------------------------------- market stall

def market(winter):
    """Red counter with cream trim, produce crates, a striped awning and a painted FRESH sign."""
    c = Iso(52, 60, 22, 40)
    U, V = 8, 4
    for u, v in ((0, V - 0.4), (U - 0.4, V - 0.4), (U - 0.4, 0)):            # cream posts
        c.box(u, u + 0.4, v, v + 0.4, 0, 16, *CREAM)
    c.box(0, U, 0, V, 0, 9, "cream", "red", "red_d")                         # counter
    for k in range(1, U):
        c.zline(k, V, 0.6, 8.4, "red_d")                                     # front boards
    c.uline(0, U, V, 1, "cream")                                             # cream skirting
    crops = [("red", "pink"), ("orange", "amber"), ("green_d", "green")]
    for i, (dark, light) in enumerate(crops):                                # produce crates
        u0 = 0.5 + i * 2.5
        c.box(u0, u0 + 2.1, 0.8, 3.4, 9, 10.2, "brown", "tan", "brown_l")
        for j in range(5):
            x, y = c.p(u0 + 0.5 + (j % 3) * 0.6, 1.4 + (j // 3) * 1.1, 10.6)
            c.dot(x, y, light); c.dot(x + 1, y, dark); c.dot(x, y + 1, dark)
    stripes = ("white", "grey_l") if winter else ("red", "cream")
    for k in range(U * 2):                                                   # striped awning
        u = k * 0.5
        c.poly([(u, -0.4, 18.5), (u + 0.5, -0.4, 18.5), (u + 0.5, V + 0.6, 15), (u, V + 0.6, 15)], stripes[(k // 2) % 2])
    for k in range(U * 2):                                                   # scalloped valance
        u = k * 0.5
        x, y = c.p(u, V + 0.6, 15)
        col = ("grey_l" if winter else "red_d") if (k // 2) % 2 == 0 else ("grey" if winter else "tan")
        c.dot(x, y + 1, col); c.dot(x + 1, y + 1, col)
        if k % 2 == 0:
            c.dot(x, y + 2, col)
    if not winter:
        c.box(U - 0.01, U, -0.4, V + 0.6, 15, 18.5, "purple", "purple", "red_d", lit=False)
    # The painted sign on top of the awning: FRESH with the wheat emblem.
    sx, sy = c.p(U / 2, -0.4, 18.5)
    sx -= 13; sy -= 8
    for x in range(sx, sx + 28):
        for y in range(sy, sy + 7):
            edge = x in (sx, sx + 27) or y in (sy, sy + 6)
            c.dot(x, y, "red_d" if edge else "cream")
    c.text(sx + 2, sy + 1, "FRESH", "red")
    c.wheat(sx + 22, sy + 1)
    for x in (sx + 4, sx + 23):
        c.dot(x, sy + 7, "brown"); c.dot(x, sy + 8, "brown")
    soften(c.im)
    return c, c.pivot(U / 2, V / 2)


def truck_load():
    """Crates of produce in the bed of the parked (down-right) truck, on the truck's own pivot."""
    L, W = 12, 5
    c = Iso(40, 34, 12, 14)
    for u0, v0, z0 in ((0.6, 0.6, 2.6), (3.4, 0.6, 2.6), (0.6, 0.6, 5.8)):
        c.box(u0, u0 + 2.6, v0, v0 + 3.6, z0, z0 + 3.2, "tan", "brown_l", "brown")
        c.uline(u0, u0 + 2.6, v0 + 3.6, z0 + 1.6, "brown")
    for u, v, light, dark in ((1.2, 1.4, "pink", "red"), (2.2, 2.6, "amber", "orange"), (4.2, 1.6, "green", "green_d"), (5.0, 2.8, "pink", "red")):
        x, y = c.p(u, v, 9.4 if u < 3.4 else 6.2)
        c.dot(x, y, light); c.dot(x + 1, y, dark); c.dot(x, y + 1, dark); c.dot(x + 1, y + 1, dark)
    return c, c.pivot(L / 2, W / 2)


def emit(sp, name, made):
    cnv, (px, py) = made
    x0, y0, x1, y1 = cnv.im.getbbox()
    body, pad = finish(cnv.im.crop((x0, y0, x1, y1)))
    sp.save(name, body, px - x0 + pad, py - y0 + pad)


def main():
    art = sys.argv[1]
    veh = Sprites(art, "vehicles")
    bld = Sprites(art, "buildings")
    for f in (0, 1):
        emit(veh, f"truck_se_{f}", truck_se(f))
        emit(veh, f"truck_ne_{f}", truck_ne(f))
    emit(veh, "truck_load", truck_load())
    for name, fn in (("workbench", workbench), ("well", well), ("market", market)):
        emit(bld, name, fn(False))
        emit(bld, name + "_snow", fn(True))
    pivots = {}
    for sp in (veh, bld):
        pivots.update(sp.pivots)
    print(json.dumps(pivots))


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
    cw, ch = 62, 78
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


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
    else:
        main()
