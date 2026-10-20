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
  buildings/barn(_snow)                 the barn, its slate roof plain
  buildings/silo(_snow)                 the silo
  buildings/orders(_snow)               the orders notice board
  vehicles/harvester_se_0/1, _ne_0/1    the yellow combine (its reel turns between frames)
  deco/crate_empty, crate_full          the field crates
  deco/field_sign                       a field's sign (the engine letters it)
  deco/lantern                          the lamp post (night.py picks out its glass for lantern_lit)
  deco/fence_r, fence_l                 one tile of fence along +col / +row, pivot at the start corner
  buildings/compost_bin, bakery_oven, preserves_jar, beehive, fruit_press, seed_maker
  buildings/oil_press, market_cart, harvest_shed, planter_shed, windmill
  buildings/cottage_c(_lit)             the bunkhouse, and its lit windows for the night overlay
  deco/hay_bale, trough, bridge, sign_forest, sign_mine
  decor/<id>                            the decorations the player buys and places (Decor.kt)
  crops/giant_pumpkin, _melon, _cabbage giant crops filling a 3x3 patch (Giant.kt)
                                        farm machines, each with a cream icon plate

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


# ---------------------------------------------------------------- barn (2x2)

def barn(winter):
    """Red board walls on a stone footing with cream corner trim and eave band, X-brace doors and
    a hay loft in the gable end, loft windows and a wheat barn-quilt on the long wall, a slate roof
    with the farm's name painted on it, cream rake boards and ridge, and a cream cupola carrying
    the wheat emblem."""
    U, V = 8, 6                     # half-size: the long side runs along u
    WH, PK = 21, 10                 # wall height, roof peak above the eaves
    R = WH + PK
    ze = WH - PK / V                # eave height one unit out
    c = Iso(80, 88, 40, 62)
    roof, shade, seam = (("white", "grey_l", "grey_l") if winter else ("slate", "slate_d", "slate_d"))
    # Back slope (only its underside sliver shows past the gable), walls, footing.
    c.poly([(-U - 1, 0, R), (U + 1, 0, R), (U + 1, -V - 1, ze), (-U - 1, -V - 1, ze)], "slate_d")
    c.box(-U, U, -V, V, 0, 2, "grey_l", "grey", "grey_d", lit=False)
    c.box(-U, U, -V, V, 2, WH, "red", "red", "red_d", lit=False)
    for u in range(-U + 3, U, 3):
        c.zline(u, V, 0.4, 1.6, "grey_d")
    c.uline(-U, U, V, 1, "grey_d")
    for u in range(-U + 2, U, 2):
        c.zline(u, V, 2, WH, "red_d")                                   # board seams
    for v in range(-V + 2, V, 2):
        c.zline(U, v, 2, WH + PK * (1 - abs(v) / V) - 1, "purple")
    c.poly([(U, -V, WH), (U, V, WH), (U, 0, R)], "red_d")               # gable end
    for v in range(-V + 2, V, 2):
        c.zline(U, v, WH, WH + PK * (1 - abs(v) / V) - 1, "purple")
    # Cream trim: corner boards, the eave band and the gable's belt board.
    c.zline(U, V, 0, WH, "cream", dx=-1); c.zline(U, V, 0, WH, "tan")
    c.zline(-U, V, 0, WH, "cream"); c.zline(-U, V, 0, WH, "cream", dx=1)
    c.zline(U, -V, 0, WH, "tan", dx=-1); c.zline(U, -V, 0, WH, "tan", dx=-2)
    c.uline(-U, U, V, WH, "cream")
    c.vline(U, 2 * V, -V, WH, "tan")
    # Big doors in the gable end: cream frame, red leaves, cream X braces.
    c.poly([(U, -3.5, 2), (U, 3.5, 2), (U, 3.5, 14), (U, -3.5, 14)], "cream")
    for v0, v1 in ((-3, -0.25), (0.25, 3)):
        c.poly([(U, v0, 2), (U, v1, 2), (U, v1, 13.4), (U, v0, 13.4)], "red")
        c.line((U, v0, 2), (U, v1, 13.4), "cream")
        c.line((U, v1, 2), (U, v0, 13.4), "cream")
    c.dot(*c.p(U, 0.6, 8), "grey_d"); c.dot(*c.p(U, -0.6, 8), "grey_d")  # handles
    # Hay loft: cream frame, dark inside, hay spilling over the sill.
    c.poly([(U, -2, WH + 0.8), (U, 2, WH + 0.8), (U, 2, WH + 5.4), (U, -2, WH + 5.4)], "cream")
    c.poly([(U, -1.5, WH + 1.3), (U, 1.5, WH + 1.3), (U, 1.5, WH + 4.8), (U, -1.5, WH + 4.8)], "brown_d")
    for v, k in ((-1.2, 2), (-0.6, 3), (0, 2), (0.6, 3), (1.2, 2)):
        x, y = c.p(U, v, WH + 1.3)
        for j in range(k):
            c.dot(x, y - j, "yellow" if j else "amber")
    for u0 in (-U + 2, U - 4.5):                                        # loft windows
        c.poly([(u0, V, 9), (u0 + 2.5, V, 9), (u0 + 2.5, V, 15), (u0, V, 15)], "cream")
        c.poly([(u0 + 0.5, V, 9.6), (u0 + 2, V, 9.6), (u0 + 2, V, 14.4), (u0 + 0.5, V, 14.4)], "slate_d")
        c.zline(u0 + 1.25, V, 9.6, 14.4, "cream")
    x, y = c.p(0, V, 12)                                            # barn-quilt diamond
    for dy in range(-5, 6):
        half = 5 - abs(dy)
        for dx in range(-half, half + 1):
            edge = abs(dx) == half
            c.dot(x + dx, y + dy + (dx + 6) // 2 - 3, "red_d" if edge else "cream")
    c.wheat(x - 2, y - 3)
    # Front slope: shingle rows, cream fascia at the eave and cream rake boards, cream ridge.
    c.poly([(-U - 1, 0, R), (U + 1, 0, R), (U + 1, V + 1, ze), (-U - 1, V + 1, ze)], roof)
    for k in range(1, V + 1):
        if k in (1, V):                                                 # (clear under the name)
            c.uline(-U - 1, U + 1, k * 1.0 + 0.2, R - PK * (k + 0.2) / V, seam if k % 2 else shade)
    c.uline(-U - 1, U + 1, V + 1, ze, "cream"); c.uline(-U - 1, U + 1, V + 1, ze - 1, "tan")
    c.line((U + 1, 0, R + 1), (U + 1, V + 1, ze), "cream")
    c.line((U + 1, 0, R + 1), (U + 1, -V - 1, ze), "cream")
    c.line((U + 1, 0, R), (U + 1, -V - 1, ze - 1), "tan")
    c.uline(-U - 1, U + 1, 0, R + 1, "cream")
    # Round 7: the roof is plain now (the game is Rebuild The Village; owner asked to remove the lettering).
    # Cupola: cream box with the wheat on its face, red pyramid cap, weathervane.
    c.box(-1.6, 1.6, -1.6, 1.6, R - 3, R + 4, "cream", "cream", "tan")
    c.wheat(*c.p(-1.2, 1.6, R + 3))
    cap = ("white", "grey_l") if winter else ("red", "red_d")
    c.poly([(-2.2, 2.2, R + 4), (2.2, 2.2, R + 4), (0, 0, R + 9)], cap[0])
    c.poly([(2.2, -2.2, R + 4), (2.2, 2.2, R + 4), (0, 0, R + 9)], cap[1])
    c.uline(-2.2, 2.2, 2.2, R + 4, "cream"); c.vline(2.2, 4.4, -2.2, R + 4, "tan")
    c.zline(0, 0, R + 9, R + 13, "grey_d")
    x, y = c.p(0, 0, R + 12)
    for i in range(-3, 4):
        c.dot(x + i, y, "grey")
    c.dot(x + 3, y - 1, "red"); c.dot(x + 3, y + 1, "red"); c.dot(x + 4, y, "red")
    soften(c.im)
    return c, c.pivot(0, 0)


# ---------------------------------------------------------------- silo (1x1)

def silo(winter):
    """A round tower on a stone ring: cream body with red hoops, a red dome with a cream eave and
    finial, a ladder, a cream-framed hatch and the wheat emblem on a red roundel."""
    body, hoop = ("white", "cream", "tan", "tan_d"), ("pink", "red", "red_d", "purple")
    dome = ("white", "grey_l") if winter else ("red", "red_d")
    c = Iso(40, 80, 20, 70)
    cx, cy = c.p(0, 0, 0)
    rx, ry, H, D = 11, 5.5, 36, 9

    def band(dx, ramp):
        return ramp[0] if dx < -0.55 else ramp[1] if dx < 0.15 else ramp[2] if dx < 0.7 else ramp[3]

    def ring(z, ramp, r=rx, filled=False):
        rr = r * ry / rx
        for x in range(cx - r, cx + r + 1):
            dx = (x - cx) / r
            s = max(0.0, 1 - dx * dx) ** 0.5
            col = band(dx, ramp)
            y1 = round(cy - z + rr * s)
            if filled:
                for y in range(round(cy - z - rr * s), y1 + 1):
                    c.dot(x, y, col)
            else:
                c.dot(x, y1, col)

    stone = ("grey_l", "grey", "grey_d", "slate")
    for z in range(H + 1):
        ramp = stone if z < 6 else hoop if (z % 10 in (4, 5)) else body
        ring(z, ramp)
    for x in range(cx - rx + 2, cx + rx - 1, 3):                        # stone joints
        dx = (x - cx) / rx
        y = round(cy + ry * (1 - dx * dx) ** 0.5)
        c.dot(x, y - 3, "grey_d"); c.dot(x + 1, y - 1, "grey_d")
    # Dome: stacked shrinking discs, a cream eave ring and a finial.
    for k in range(D + 1):
        r = max(1, round(rx * (1 - (k / (D + 0.5)) ** 2) ** 0.5))
        ring(H + k, (dome[0], dome[0], dome[1], dome[1]) if k < D - 2 else (dome[0],) * 4, r=r, filled=True)
    ring(H, ("white", "cream", "tan", "tan"))
    c.zline(0, 0, H + D + 1, H + D + 4, "cream")
    c.dot(cx, cy - H - D - 4, "white")
    # Ladder on the right.
    lx = cx + 6
    for z in range(4, H + 1):
        y = round(cy - z + ry * (1 - (6 / rx) ** 2) ** 0.5)
        c.dot(lx, y, "grey_d"); c.dot(lx + 3, y, "grey_d")
        if z % 3 == 0:
            c.dot(lx + 1, y, "grey"); c.dot(lx + 2, y, "grey")
    # Hatch at the foot, the wheat on a roundel up top.
    hx = cx - 4
    for z in range(1, 9):
        y = round(cy - z + ry * (1 - (4 / rx) ** 2) ** 0.5)
        for i in range(5):
            edge = i in (0, 4) or z == 8
            c.dot(hx + i, y + (i // 3), "cream" if edge else "slate_d")
    x, y = cx - 3, cy - 26 + 2
    rcol = hoop[1]
    c.ellipse(x, y, 4, 4, rcol)
    c.wheat(x - 2, y - 3)
    soften(c.im)
    return c, (cx, cy)


# ---------------------------------------------------------------- orders board (1x1)

def orders(winter):
    """A notice board: red posts with cream caps, a red-framed cork board with pinned order notes
    (one marked urgent with a red !), a little red shingle roof with a cream ridge."""
    c = Iso(36, 40, 18, 30)
    B = 3.6
    for u in (-B - 0.5, B):                                             # red posts
        c.box(u, u + 0.5, -0.3, 0.2, 0, 16, "red", "red", "red_d")
    c.box(-B, B, -0.25, 0.15, 5, 15, "red", "red", "red_d", lit=False)  # frame
    c.poly([(-B + 0.5, 0.15, 5.6), (B - 0.5, 0.15, 5.6), (B - 0.5, 0.15, 14.4), (-B + 0.5, 0.15, 14.4)], "tan")
    for u in (-1.5, 1.0):                                               # cork flecks
        x, y = c.p(u, 0.15, 7)
        c.dot(x, y, "tan_d"); c.dot(x + 3, y + 3, "tan_d")
    notes = [(-2.8, 13.8, "white", False), (-0.4, 13.6, "yellow", False), (1.4, 13.8, "white", True),
             (-2.2, 9.4, "pink", False), (0.6, 9.2, "cream", False)]
    for u0, ztop, col, urgent in notes:
        x, y = c.p(u0, 0.15, ztop)
        for i in range(4):
            for j in range(4 if not urgent else 5):
                c.dot(x + i, y + j + i // 2, col)
        if urgent:
            c.dot(x + 1, y, "red"); c.dot(x + 1, y + 1, "red"); c.dot(x + 1, y + 2, "red"); c.dot(x + 2, y + 4, "red")
        else:
            c.dot(x + 1, y, "red")                                      # pin
            c.dot(x, y + 2, "grey"); c.dot(x + 1, y + 2, "grey"); c.dot(x + 2, y + 3, "grey"); c.dot(x + 3, y + 3, "grey")
    # Roof: red shingles, cream ridge and eave, the gable end in dark red.
    sh = ("white", "grey_l") if winter else ("red", "red_d")
    c.poly([(B + 1, 0, 19), (B + 1, -1.6, 16), (B + 1, 1.6, 16)], "purple")
    c.poly([(-B - 1, 0, 19), (B + 1, 0, 19), (B + 1, 1.6, 16), (-B - 1, 1.6, 16)], sh[0])
    c.uline(-B - 1, B + 1, 0.8, 17.5, sh[1])
    c.uline(-B - 1, B + 1, 1.6, 16, "cream")
    c.uline(-B - 1, B + 1, 0, 19.6, "cream")
    c.line((B + 1, 0, 19.6), (B + 1, -1.6, 16), "cream")
    soften(c.im)
    return c, c.pivot(0, 0)


def preview2(out):
    """Batch 2 (barn, silo, orders board): today's sprite then the hand-pixeled options, 5x."""
    from PIL import Image, ImageDraw
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    import polish
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app", "src", "main", "assets", "art")
    man = json.load(open(os.path.join(art, "manifest.json")))
    hand = Image.open(os.path.join(art, "people/hana_se_idle.png")).convert("RGBA")
    hp = man["people/hana_se_idle"]
    rows = [[("buildings/barn", None, 2, "NOW"), ("buildings/barn", lambda: barn(False), 2, "new"),
             ("buildings/barn", lambda: barn(True), 2, "winter")],
            [("buildings/silo", None, 1, "NOW"), ("buildings/silo", lambda: silo(False), 1, "new"),
             ("buildings/silo", lambda: silo(True), 1, "winter"), ("buildings/orders", None, 1, "NOW"),
             ("buildings/orders", lambda: orders(False), 1, "new")]]
    cws, ch = (150, 120), 104
    sheet = Image.new("RGBA", (600, ch * 2), (62, 137, 72, 255))
    d = ImageDraw.Draw(sheet)
    for ri, row in enumerate(rows):
        cw = cws[ri]
        for i, (name, fn, tiles, label) in enumerate(row):
            ax, ay = i * cw + cw // 2 + 6, ri * ch + ch - 20
            d.text((i * cw + 4, ri * ch + 2), label, fill=(255, 255, 255, 255))
            t = 16 * tiles
            d.line([(ax - 2 * t, ay), (ax, ay - t), (ax + 2 * t, ay), (ax, ay + t), (ax - 2 * t, ay)], fill=(48, 110, 60, 255))
            sheet.alpha_composite(hand, (ax - 2 * t + 4 - hp["ax"], ay + t // 2 - hp["ay"]))
            if fn is None:
                im = Image.open(os.path.join(art, name + ".png")).convert("RGBA")
                p = man[name]
                sheet.alpha_composite(im, (ax - p["ax"], ay - p["ay"]))
            else:
                cnv, (px_, py_) = fn()
                x0, y0, x1, y1 = cnv.im.getbbox()
                body, pad = finish(cnv.im.crop((x0, y0, x1, y1)))
                sys.stderr.write("%s bh %d\n" % (name, body.height))
                body = polish.polish(body, "world", name)
                sheet.alpha_composite(body, (ax - (px_ - x0 + pad), ay - (py_ - y0 + pad)))
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(out)


# ---------------------------------------------------------------- harvester

HARVESTER = ("yellow", "amber", "orange")


def reel(c, along_v, at, span, z, frame):
    """The harvester's red reel: cream bats round an axle, a quarter turn apart, turning between
    frames. along_v: the axle runs along v (at = u) or along u (at = v)."""
    import math
    for k in range(4):
        a = math.pi / 2 * k + (math.pi / 4 if frame else 0)
        du, dz = 1.6 * math.cos(a), 1.6 * math.sin(a)
        if along_v:
            c.vline(at + du, span[1] - span[0], span[0], z + dz, "cream" if dz > -0.5 else "tan")
        else:
            c.uline(span[0], span[1], at + du, z + dz, "cream" if dz > -0.5 else "tan")
    for s in span:
        if along_v:
            c.line((at - 1.6, s, z), (at + 1.6, s, z), "red"); c.line((at, s, z - 1.6), (at, s, z + 1.6), "red_d")
        else:
            c.line((s, at - 1.6, z), (s, at + 1.6, z), "red"); c.line((s, at, z - 1.6), (s, at, z + 1.6), "red_d")


def drum(c, u0, span, frame):
    """The reel seen from the front-right: a slim red drum whose cream bats move round a step
    between frames, with a red end ring and grey hub."""
    v0, v1 = span
    c.box(u0 + 0.4, u0 + 2.0, v0, v1, 4.4, 7.0, "red", "red_d", "purple", lit=False)
    for z in ((5.0, 6.4) if frame == 0 else (5.7,)):
        c.vline(u0 + 2.0, v1 - v0, v0, z, "cream")
    if frame:
        c.vline(u0 + 1.2, v1 - v0, v0, 7.0, "cream")
    x, y = c.p(u0 + 1.2, v1, 5.7)
    for dx, dy in ((-1, -2), (0, -2), (1, -1), (1, 0), (1, 1), (0, 2), (-1, 2), (-2, 1), (-2, 0), (-2, -1)):
        c.dot(x + dx, y + dy, "red")
    c.dot(x, y, "grey_l")


def harvester_se(frame):
    """Driving down-right: a tall toy combine, glass cab up front, the red header and turning reel
    ahead of it, the unloading pipe folded back along the side."""
    top, left, right = HARVESTER
    L, W = 6, 4.4
    c = Iso(44, 36, 14, 19)
    c.line((0.6, W, 10.4), (-1.6, W + 0.4, 10.4), "grey"); c.line((0.6, W, 9.8), (-1.6, W + 0.4, 9.8), "grey_d")  # pipe
    c.box(0.3, L - 0.3, 0.4, W - 0.4, 1, 2.5, *DARK, lit=False)
    c.box(0, L, 0, W, 2, 9, top, left, right)                                # body
    c.box(0.3, 3.0, 0.5, W - 0.5, 9, 9.8, top, left, right)                 # grain tank lid
    c.zline(1.8, 1.0, 9.8, 11.6, "grey_d"); c.dot(*c.p(1.8, 1.0, 11.6), "ink")    # exhaust
    c.uline(0, L, W, 4.0, "cream")                                           # cream stripe
    c.wheat(*c.p(0.6, W, 8.2))
    c.box(3.4, L, 0.7, W - 0.7, 9, 12.2, "cream", "cyan", "blue")            # glass cab, cream roof
    c.zline(3.5, W - 0.7, 9, 12.2, "cream"); c.zline(L, W - 0.7, 9, 12.2, "cream", dx=-1)
    x, y = c.p(4.0, W - 0.7, 11.6)
    c.dot(x, y, "white"); c.dot(x + 1, y + 1, "white")
    for u in (1.3, 4.4):                                                     # wheels
        x, y = c.p(u, W, 2)
        wheel(c, x, y + 1, frame)
    # Header: red with cream teeth, wider than the body; the reel on arms above it.
    c.box(L, L + 2, -0.8, W + 0.8, 0.8, 3.0, "red", "red_d", "purple")
    c.vline(L + 2, W + 1.6, -0.8, 1.2, "cream")                              # cutter bar
    for v in (-0.8, W + 0.8):
        c.line((L - 0.2, v, 7.6), (L + 1.2, v, 5.7), "red_d")
    drum(c, L + 0.2, (-0.8, W + 0.8), frame)
    soften(c.im)
    return c, c.pivot(L / 2, W / 2)


def harvester_ne(frame):
    """Driving away up-right: the right side and the back show; the header and reel peek out
    past the cab at the far end."""
    top, left, right = HARVESTER
    L, W = 6, 4.4
    c = Iso(44, 36, 22, 14)
    # Length runs along v: header and reel at the far end (small v), the tank at the back.
    c.box(-0.8, W + 0.8, -2, 0, 0.8, 3.0, "red", "red_d", "purple")
    reel(c, False, -1.2, (-0.8, W + 0.8), 5.4, frame)
    c.box(0.4, W - 0.4, 0.3, L - 0.3, 1, 2.5, *DARK, lit=False)
    c.box(0, W, 0, L, 2, 9.4, top, left, right)
    c.box(0.7, W - 0.7, 0, 2.6, 9.4, 12.6, "cream", "blue", "cyan")          # glass cab
    c.zline(W - 0.7, 2.6, 9.4, 12.6, "cream")
    c.box(0.5, W - 0.5, 3.0, L - 0.3, 9.4, 10.2, top, left, right)           # tank lid
    c.zline(1.0, 4.6, 10.2, 12.4, "grey_d"); c.dot(*c.p(1.0, 4.6, 12.4), "ink")
    c.vline(W, L, 0, 4.0, "cream")
    c.line((W, L - 0.6, 10.0), (W + 0.4, L + 1.6, 10.0), "grey")             # pipe
    x, y = c.p(W / 2 - 0.4, L, 8.6)
    c.wheat(x - 2, y)
    for v in (1.6, 4.7):
        x, y = c.p(W, v, 2)
        wheel(c, x, y + 1, frame)
    soften(c.im)
    return c, c.pivot(W / 2, L / 2)


# ---------------------------------------------------------------- crates, fence, sign, lamp

def crate(full):
    """A slatted produce crate with cream corner battens and a red stamp; full ones are heaped."""
    c = Iso(22, 22, 11, 12)
    S = 3.6
    c.box(-S / 2, S / 2, -S / 2, S / 2, 0, 5, "tan", "tan", "brown_l")
    c.poly([(-S / 2 + 0.5, -S / 2 + 0.5, 5), (S / 2 - 0.5, -S / 2 + 0.5, 5), (S / 2 - 0.5, S / 2 - 0.5, 5), (-S / 2 + 0.5, S / 2 - 0.5, 5)], "brown_d")
    c.uline(-S / 2, S / 2, S / 2, 2.5, "brown_l"); c.vline(S / 2, S, -S / 2, 2.5, "brown_d")   # slat gaps
    c.zline(S / 2, S / 2, 0, 5, "cream", dx=-1); c.zline(S / 2, S / 2, 0, 5, "tan")
    c.zline(-S / 2, S / 2, 0, 5, "cream")
    x, y = c.p(-0.6, S / 2, 4.2)
    c.dot(x, y, "red"); c.dot(x + 1, y, "red"); c.dot(x, y + 1, "red"); c.dot(x + 1, y + 1, "red_d")
    if full:
        for u, v, z, light, dark in ((-0.7, -0.7, 5.6, "orange", "rust"), (0.6, -0.7, 6.2, "pink", "red"),
                                     (-0.6, 0.6, 5.8, "green", "green_d"), (0.6, 0.6, 5.6, "pink", "red"),
                                     (0, 0, 6.8, "yellow", "amber")):
            x, y = c.p(u, v, z)
            for dx, dy, col in ((0, -1, light), (-1, 0, light), (0, 0, light), (1, 0, dark), (-1, 1, dark), (0, 1, dark), (1, 1, dark)):
                c.dot(x + dx, y + dy, col)
    soften(c.im)
    return c, c.pivot(0, 0)


def fence(along_u):
    """One tile of fence from a tile corner: two cream rails between chunky red posts with cream
    caps. Posts sit flush with both ends, so neighbouring segments share one chunky post."""
    rail, post = ("white", "cream", "tan"), ("red", "red", "red_d")
    c = Iso(20, 20, 2 if along_u else 18, 9)
    ends = ((0, 0.7), (7.3, 8))

    def p_box(a, b):
        if along_u:
            c.box(a, b, -0.35, 0.35, 0, 7.5, *post)
        else:
            c.box(-0.35, 0.35, a, b, 0, 7.5, *post)
        x, y = c.p((a + b) / 2, 0, 7.5) if along_u else c.p(0, (a + b) / 2, 7.5)
        c.dot(x, y, "cream"); c.dot(x - 1, y, "cream")
    p_box(*ends[0])
    for z0 in (2.4, 5.2):
        if along_u:
            c.box(0.7, 7.3, -0.2, 0.2, z0, z0 + 1.3, *rail)
        else:
            c.box(-0.2, 0.2, 0.7, 7.3, z0, z0 + 1.3, *rail)
    p_box(*ends[1])
    return c, c.p(0, 0, 0)


def field_sign():
    """A red board in a cream frame on two red legs; the engine letters the board in white."""
    c = Iso(22, 26, 11, 20)
    B = 3.4
    for u in (-B + 0.6, B - 1.1):
        c.box(u, u + 0.5, -0.2, 0.2, 0, 6, "red_d", "red_d", "purple")
    c.box(-B, B, -0.2, 0.2, 5, 15.6, "cream", "cream", "tan")
    c.poly([(-B + 0.6, 0.2, 5.8), (B - 0.6, 0.2, 5.8), (B - 0.6, 0.2, 14.8), (-B + 0.6, 0.2, 14.8)], "red")
    c.uline(-B + 0.6, B - 0.6, 0.2, 6.6, "red_d")
    soften(c.im)
    return c, c.pivot(0, 0)


LAMP = [
    "...r...",
    "..sss..",
    ".sSSSs.",
    ".iyyyi.",
    ".iyWyi.",
    ".iAyAi.",
    ".iiiii.",
    "...rR..",
    "...rR..",
    "...rR..",
    "...rR..",
    "...rR..",
    "..sSs..",
    "..sss..",
]
LAMP_KEY = {"r": "red", "R": "red_d", "s": "slate", "S": "slate_d", "i": "ink", "y": "yellow", "W": "white", "A": "amber"}


def lantern():
    """The farm lamp post: a red post, a slate cap and foot, warm glass (picked out at night)."""
    c = Iso(7, len(LAMP), 3, len(LAMP) - 1)
    for y, row in enumerate(LAMP):
        for x, ch in enumerate(row):
            if ch in LAMP_KEY:
                c.dot(x, y, LAMP_KEY[ch])
    return c, (3, len(LAMP) - 1)


def preview3(out):
    """Batch 3 (harvester, crates, fence, field sign, lamp): today's sprite then the options, 5x."""
    from PIL import Image, ImageDraw
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    import polish
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app", "src", "main", "assets", "art")
    man = json.load(open(os.path.join(art, "manifest.json")))
    hand = Image.open(os.path.join(art, "people/hana_se_idle.png")).convert("RGBA")
    hp = man["people/hana_se_idle"]
    rows = [[("vehicles/harvester_se_0", None, "NOW"), ("vehicles/harvester_se_0", lambda: harvester_se(0), "new"),
             ("vehicles/harvester_ne_0", lambda: harvester_ne(0), "away")],
            [("deco/crate_full", None, "NOW"), ("deco/crate_empty", lambda: crate(False), "new"),
             ("deco/crate_full", lambda: crate(True), "full"), ("deco/field_sign", None, "NOW"),
             ("deco/field_sign", field_sign, "new"), ("deco/lantern", None, "NOW"), ("deco/lantern", lantern, "new")]]
    sheet = Image.new("RGBA", (420, 190), (62, 137, 72, 255))
    d = ImageDraw.Draw(sheet)

    def place(name, fn, ax, ay):
        if fn is None:
            im = Image.open(os.path.join(art, name + ".png")).convert("RGBA")
            p = man[name]
            sheet.alpha_composite(im, (ax - p["ax"], ay - p["ay"]))
            return
        cnv, (px_, py_) = fn()
        x0, y0, x1, y1 = cnv.im.getbbox()
        body, pad = finish(cnv.im.crop((x0, y0, x1, y1)))
        sys.stderr.write("%s bh %d\n" % (name, body.height))
        if polish.kind_of(name):
            body = polish.polish(body, polish.kind_of(name), name)
        sheet.alpha_composite(body, (ax - (px_ - x0 + pad), ay - (py_ - y0 + pad)))
    for ri, (row, cw) in enumerate(zip(rows, (84, 60))):
        for i, (name, fn, label) in enumerate(row):
            ax, ay = i * cw + cw // 2 + 4, ri * 66 + 52
            d.text((i * cw + 2, ri * 66 + 1), label, fill=(255, 255, 255, 255))
            sheet.alpha_composite(hand, (ax - 24 - hp["ax"], ay - hp["ay"]))
            place(name, fn, ax, ay)
            if name == "deco/field_sign":
                for dx, dy in ((0, 0), (1, 0), (2, 0), (0, 1), (2, 1), (0, 2), (1, 2), (2, 2), (0, 3), (2, 3), (0, 4), (2, 4)):
                    sheet.putpixel((ax - 1 + dx, ay - 14 + dy), (255, 255, 255, 255))   # the engine's letter
    # Fences: a field corner of each paint (today, cream rails, red rails).
    for k, paint in enumerate((None, "new")):
        bx, by = 56 + k * 136, 158
        d.text((bx - 36, by - 16), "fence NOW" if paint is None else "fence " + paint, fill=(255, 255, 255, 255))
        for i in range(3):
            for along_u in (True, False):
                name = "deco/fence_r" if along_u else "deco/fence_l"
                ox, oy = (bx + 16 * i, by - 8 + 8 * i) if along_u else (bx - 16 * i - 16, by - 8 - 8 * i + 8 * 1 - 8)
                if along_u:
                    ox, oy = bx + 16 * i, by - 8 + 8 * i
                else:
                    ox, oy = bx - 16 * i, by - 8 + 8 * i
                place(name, None if paint is None else (lambda a=along_u: fence(a)), ox, oy)
    sheet.resize((sheet.width * 5, sheet.height * 5), Image.NEAREST).save(out)


# ---------------------------------------------------------------- farm machines (1x1)

# Small painted icons for the machines' cream plates (upright, centred on the plate).
ICONS = {
    "sprout": (["g.g", "ggg", ".b."], {"g": "green", "b": "brown"}),
    "loaf": ([".ooo.", "oyoyo", "ooooo"], {"o": "orange", "y": "amber"}),
    "jar": ([".c.", "rrr", "rpr", "rrr"], {"c": "cream", "r": "red", "p": "pink"}),
    "bee": (["w.w", "yky", ".y."], {"w": "white", "y": "yellow", "k": "ink"}),
    "apple": ([".b.", "rrr", "rpr", ".r."], {"b": "green_d", "r": "red", "p": "pink"}),
    "seed": (["..g", ".g.", "bb.", "bb."], {"g": "green", "b": "brown_l"}),
}


def plate(c, u0, u1, v, z0, z1, icon):
    """A cream plate with a dark red edge painted on a +v face, an icon in the middle."""
    c.poly([(u0, v, z0), (u1, v, z0), (u1, v, z1), (u0, v, z1)], "red_d")
    c.poly([(u0 + 0.5, v, z0 + 0.6), (u1 - 0.5, v, z0 + 0.6), (u1 - 0.5, v, z1 - 0.6), (u0 + 0.5, v, z1 - 0.6)], "cream")
    rows, key = ICONS[icon]
    x, y = c.p((u0 + u1) / 2, v, (z0 + z1) / 2)
    x -= len(rows[0]) // 2
    y -= len(rows) // 2
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in key:
                c.dot(x + i, y + j, key[ch])


def compost_bin():
    """A slatted wooden bin on red corner posts, dark compost heaped inside with green sprouts."""
    c = Iso(30, 28, 15, 16)
    S, H = 2.4, 6
    c.box(-S, S, -S, S, 0, H, "tan", "tan", "brown_l")
    c.poly([(-S + 0.6, -S + 0.6, H), (S - 0.6, -S + 0.6, H), (S - 0.6, S - 0.6, H), (-S + 0.6, S - 0.6, H)], "brown_d")
    for z in (2, 4):                                                         # slat gaps
        c.uline(-S, S, S, z, "brown"); c.vline(S, 2 * S, -S, z, "brown_d")
    for u, v, dx in ((S, S, -1), (-S, S, 0), (S, -S, -1)):                  # red corner posts
        c.zline(u, v, 0, H + 0.6, "red", dx=dx); c.zline(u, v, 0, H + 0.6, "red_d" if dx else "red", dx=dx + 1)
    for u, v in ((-0.7, -0.5), (0.5, 0.2), (-0.2, 0.8)):                    # the heap and sprouts
        x, y = c.p(u, v, H + 0.6)
        for dx in (-1, 0, 1):
            c.dot(x + dx, y, "brown"); c.dot(x + dx, y + 1, "brown_d")
        c.dot(x, y - 1, "green"); c.dot(x - 1, y - 2, "green"); c.dot(x + 1, y - 2, "green_d")
    plate(c, -1.5, 1.5, S, 0.8, 5.2, "sprout")
    soften(c.im)
    return c, c.pivot(0, 0)


def bakery_oven():
    """A brick base with cream trim, a cream plaster dome with a red band, a glowing arched mouth
    and a chimney; a loaf on the plate."""
    c = Iso(32, 40, 16, 30)
    S, B = 2.8, 5.5
    c.box(-S, S, -S, S, 0, B, "cream", "rust", "red_d")                      # brick base
    for z in (1.8, 3.8):
        c.uline(-S, S, S, z, "red_d"); c.vline(S, 2 * S, -S, z, "purple")
    c.box(0.8, 1.7, -2.0, -1.1, B, 17, "grey_l", "grey", "grey_d")          # chimney at the back
    c.box(0.6, 1.9, -2.2, -0.9, 16, 17.4, "cream", "cream", "tan")
    cx, cy = c.p(0, 0, B)
    R, D = 7, 8
    for k in range(D + 1):                                                  # the dome, built up in rings
        r = max(1, round(R * (1 - (k / (D + 0.5)) ** 2) ** 0.5))
        rr = r / 2
        for x in range(cx - r, cx + r + 1):
            dx = (x - cx) / r
            s_ = max(0.0, 1 - dx * dx) ** 0.5
            col = ("red" if dx < 0.3 else "red_d") if k == 2 else ("white" if dx < -0.45 else "cream" if dx < 0.35 else "tan")
            for y in range(round(cy - k - rr * s_), round(cy - k + rr * s_) + 1):
                c.dot(x, y, col)
    mx, my = cx - 3, cy + 1                                                 # the glowing mouth
    for j, (half, inner) in enumerate(((2, "orange"), (2, "amber"), (2, "yellow"), (1, "amber"))):
        for i in range(-half, half + 1):
            c.dot(mx + i, my - j, "ink" if abs(i) == half else inner)
    c.dot(mx, my - 4, "ink")
    plate(c, -2.4, 0.2, S, 0.6, 5.0, "loaf")
    soften(c.im)
    return c, c.pivot(0, 0)


def preserves_jar():
    """A red shelf with cream edges: two rows of bright jars with cream lids, a jar on the crest."""
    c = Iso(32, 40, 16, 30)
    U, V = 2.8, 1.2
    c.box(-U, U, -V, V, 0, 13, "cream", "red", "red_d")
    c.poly([(-U + 0.6, V, 1.0), (U - 0.6, V, 1.0), (U - 0.6, V, 12.2), (-U + 0.6, V, 12.2)], "purple")   # inside
    for z in (1.0, 6.6):
        c.uline(-U + 0.6, U - 0.6, V, z, "cream")                            # shelves
    cols = (("pink", "red"), ("orange", "amber"), ("magenta", "purple"), ("green", "green_d"))
    for row, z in enumerate((1.6, 7.2)):
        for k in range(2):
            light, dark = cols[(k + row * 2) % 4]
            x, y = c.p(-U + 1.3 + k * 2.2, V, z + 4.6)
            for j in range(5):
                for i in range(3):
                    if j == 0:
                        c.dot(x + i, y + j, "cream")
                    else:
                        c.dot(x + i, y + j, "white" if (i == 0 and j == 2) else (light if i < 2 else dark))
    plate(c, -1.4, 1.4, V, 13, 17.4, "jar")
    soften(c.im)
    return c, c.pivot(0, 0)


def beehive():
    """Two white hive boxes with a yellow seam on red legs, a red lid, an entrance and bees."""
    c = Iso(26, 26, 13, 17)
    S = 2.0
    for u, v in ((-S + 0.3, S - 0.5), (S - 0.5, S - 0.5), (S - 0.5, -S + 0.3)):
        c.box(u, u + 0.4, v, v + 0.4, 0, 2.2, "red", "red", "red_d")
    c.box(-S, S, -S, S, 2.2, 5.4, "white", "white", "grey_l")
    c.box(-S, S, -S, S, 5.4, 8.6, "white", "white", "grey_l")
    c.uline(-S, S, S, 5.4, "yellow"); c.vline(S, 2 * S, -S, 5.4, "amber")
    c.box(-S - 0.3, S + 0.3, -S - 0.3, S + 0.3, 8.6, 9.6, "cream", "red", "red_d")  # lid
    c.box(-0.6, 0.6, -0.6, 0.6, 9.6, 10.2, "red", "red", "red_d")          # handle
    x, y = c.p(-0.8, S, 3.0)                                                 # entrance
    for i in range(3):
        c.dot(x + i, y + i // 2, "ink")
    plate(c, -1.3, 1.3, S, 5.6, 8.4, "bee")
    for u, v, z in ((S + 1.6, 0, 7), (-0.5, S + 1.6, 9), (S + 0.6, -S - 0.6, 11)):   # bees
        x, y = c.p(u, v, z)
        c.dot(x, y, "yellow"); c.dot(x + 1, y, "ink"); c.dot(x, y - 1, "white")
    soften(c.im)
    return c, c.pivot(0, 0)


def fruit_press():
    """A red-hooped barrel under a cream screw frame with a red turning wheel; juice runs from a
    spout into a cream cup. An apple on the plate."""
    c = Iso(32, 38, 16, 28)
    cx, cy = c.p(0, 0, 0)
    rx, ry, H = 5, 2.5, 8
    for z in range(H + 1):                                                  # the barrel
        rr = rx - (1 if z in (0, H) else 0)
        for x in range(cx - rr, cx + rr + 1):
            dx = (x - cx) / rr
            s_ = max(0.0, 1 - dx * dx) ** 0.5
            hoop = z in (2, 6)
            col = ("red" if dx < 0.3 else "red_d") if hoop else ("tan" if dx < -0.4 else "brown_l" if dx < 0.4 else "brown")
            c.dot(x, round(cy - z + ry * s_), col)
    c.ellipse(cx, cy - H, rx - 1, ry - 1, "purple")
    c.ellipse(cx, cy - H - 1, rx - 2, ry - 1, "magenta")                    # fruit mash
    for px_ in (cx - 6, cx + 5):                                            # frame
        for y in range(cy - H - 7, cy - 1):
            c.dot(px_, y, "cream"); c.dot(px_ + 1, y, "tan")
    for x in range(cx - 6, cx + 7):
        c.dot(x, cy - H - 7, "cream"); c.dot(x, cy - H - 6, "tan_d")
    for y in range(cy - H - 6, cy - H):                                     # screw
        c.dot(cx, y, "grey" if y % 2 else "grey_d")
    for x in range(cx - 3, cx + 4):                                         # red wheel on top
        c.dot(x, cy - H - 9, "red")
    c.dot(cx - 3, cy - H - 8, "red_d"); c.dot(cx + 3, cy - H - 8, "red_d"); c.dot(cx, cy - H - 8, "grey_l")
    c.dot(cx, cy - H - 10, "red")
    # Spout and cup at the front.
    sx, sy = cx + 2, cy + ry - 4
    c.dot(sx, sy, "grey"); c.dot(sx + 1, sy, "grey")
    c.dot(sx + 1, sy + 1, "magenta"); c.dot(sx + 1, sy + 2, "magenta")
    for i in range(4):
        c.dot(sx - 1 + i, sy + 3, "cream"); c.dot(sx - 1 + i, sy + 4, "cream" if i < 3 else "tan")
    c.dot(sx, sy + 3, "magenta"); c.dot(sx + 1, sy + 3, "magenta")
    # Plate on the left of the barrel.
    for j in range(6):
        for i in range(5):
            c.dot(cx - 5 + i, cy - 6 + j, "red_d" if i in (0, 4) or j in (0, 5) else "cream")
    rows, key = ICONS["apple"]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in key:
                c.dot(cx - 4 + i, cy - 5 + j, key[ch])
    soften(c.im)
    return c, (cx, round(cy + ry))


def seed_maker():
    """A red cabinet with cream trim, a grey hopper of seeds on top, a crank and a drawer of seed
    packets; a seed on the plate."""
    c = Iso(32, 38, 16, 26)
    S = 2.3
    c.box(-S, S, -S, S, 0, 8, "cream", "red", "red_d")
    c.uline(-S, S, S, 7.2, "cream"); c.vline(S, 2 * S, -S, 7.2, "tan")
    c.box(-1.4, 1.4, S - 0.01, S, 1.0, 3.4, "red_d", "red_d", "red_d", lit=False)  # drawer
    x, y = c.p(0, S, 2.4)
    c.dot(x - 1, y, "cream"); c.dot(x, y, "cream")
    for i, col in enumerate(("yellow", "green", "pink")):                  # packets peeking out
        x, y = c.p(-1.0 + i * 0.9, S, 3.8)
        c.dot(x, y, col); c.dot(x + 1, y, col)
    plate(c, -1.6, 1.2, S, 3.8, 7.0, "seed")
    # Hopper: a grey funnel with seeds.
    c.box(-1.1, 1.1, -1.1, 1.1, 8, 9, "grey", "grey", "grey_d")
    c.box(-1.9, 1.9, -1.9, 1.9, 9, 12, "grey_l", "grey", "grey_d")
    c.poly([(-1.5, -1.5, 12), (1.5, -1.5, 12), (1.5, 1.5, 12), (-1.5, 1.5, 12)], "brown")
    for u, v in ((-0.8, -0.4), (0.4, -1.0), (0.6, 0.5), (-0.4, 0.8)):
        x, y = c.p(u, v, 12)
        c.dot(x, y, "amber")
    # Crank on the right side.
    x, y = c.p(S, 0.4, 5.6)
    c.dot(x + 1, y, "grey_d"); c.dot(x + 2, y, "grey_d"); c.dot(x + 3, y - 1, "grey_d"); c.dot(x + 3, y - 2, "red")
    soften(c.im)
    return c, c.pivot(0, 0)


MACHINES = [("compost_bin", compost_bin), ("bakery_oven", bakery_oven), ("preserves_jar", preserves_jar),
            ("beehive", beehive), ("fruit_press", fruit_press), ("seed_maker", seed_maker)]


def preview4(out):
    """Batch 4 (six farm machines): today's sprite over the hand-pixeled one, 5x."""
    from PIL import Image, ImageDraw
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    import polish
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app", "src", "main", "assets", "art")
    man = json.load(open(os.path.join(art, "manifest.json")))
    hand = Image.open(os.path.join(art, "people/hana_se_idle.png")).convert("RGBA")
    hp = man["people/hana_se_idle"]
    cw, ch = 64, 62
    sheet = Image.new("RGBA", (cw * len(MACHINES), ch * 2), (62, 137, 72, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, fn) in enumerate(MACHINES):
        for row in range(2):
            ax, ay = i * cw + 38, row * ch + ch - 10
            d.line([(ax - 16, ay), (ax, ay - 8), (ax + 16, ay), (ax, ay + 8), (ax - 16, ay)], fill=(48, 110, 60, 255))
            sheet.alpha_composite(hand, (ax - 26 - hp["ax"], ay - hp["ay"]))
            key = "buildings/" + name
            if row == 0:
                im = Image.open(os.path.join(art, key + ".png")).convert("RGBA")
                p = man[key]
                sheet.alpha_composite(im, (ax - p["ax"], ay - p["ay"]))
                d.text((i * cw + 2, 1), name.split("_")[0], fill=(255, 255, 255, 255))
            else:
                cnv, (px_, py_) = fn()
                x0, y0, x1, y1 = cnv.im.getbbox()
                body, pad = finish(cnv.im.crop((x0, y0, x1, y1)))
                sys.stderr.write("%s bh %d (now %d)\n" % (name, body.height, man[key]["bh"]))
                body = polish.polish(body, "world", key)
                sheet.alpha_composite(body, (ax - (px_ - x0 + pad), ay - (py_ - y0 + pad)))
    sheet.resize((sheet.width * 5, sheet.height * 5), Image.NEAREST).save(out)


ICONS.update({
    "drop": ([".a.", "aya", "aaa"], {"a": "amber", "y": "yellow"}),
    "coin": ([".y.", "yay", ".y."], {"y": "yellow", "a": "amber"}),
})


def rings(c, cx, cy, z0, z1, r0, r1, ramp, band=None):
    """A round body built up in rings from z0 to z1, the radius going r0 -> r1; flat colour bands
    lit from the left. band(z) may return a ramp to paint that ring instead."""
    for z in range(z0, z1 + 1):
        t = (z - z0) / max(1, z1 - z0)
        r = max(1, round(r0 + (r1 - r0) * t))
        rr = r / 2
        rp = band(z) if band else None
        rp = rp or ramp
        for x in range(cx - r, cx + r + 1):
            dx = (x - cx) / r
            s_ = max(0.0, 1 - dx * dx) ** 0.5
            col = rp[0] if dx < -0.5 else rp[1] if dx < 0.2 else rp[2] if dx < 0.7 else rp[3]
            for y in range(round(cy - z - rr * s_), round(cy - z + rr * s_) + 1):
                c.dot(x, y, rp[1] if z == z1 else col)


def oil_press():
    """A wooden bed with a grey millstone standing on edge (red hub), a crank arm, and a big amber
    oil jar; an oil drop on the plate."""
    c = Iso(34, 36, 17, 26)
    U, V = 3.0, 2.0
    c.box(-U, U, -V, V, 0, 4, "tan", "brown_l", "brown")
    c.uline(-U, U, V, 2, "brown")
    c.zline(U, V, 0, 4, "red", dx=-1); c.zline(-U, V, 0, 4, "red")
    x, y = c.p(-0.8, 0, 4)                                                  # millstone on edge
    for j in range(-13, 1):
        for i in range(-7, 8):
            e = i * i / 42 + (j + 6.5) ** 2 / 44
            if e <= 1:
                c.dot(x + i, y + j, "grey_d" if e > 0.8 and i > 0 else "grey_l" if i < -2 else "grey" if i < 3 else "grey_d")
    for i in range(-5, 6, 2):
        c.dot(x + i, y - 6 - (1 if i < 0 else 0), "grey_d")
    c.dot(x, y - 7, "red"); c.dot(x + 1, y - 7, "red_d"); c.dot(x, y - 8, "red"); c.dot(x + 1, y - 8, "red")
    for k in range(6):                                                      # crank arm
        c.dot(x + 1 + k, y - 8 - k // 2, "brown")
    c.dot(x + 7, y - 11, "red"); c.dot(x + 7, y - 12, "red")
    jx, jy = c.p(2.2, 1.4, 4)                                               # oil jar
    for j in range(5):
        w = 1 if j in (0, 4) else 2
        for i in range(-w, w + 1):
            c.dot(jx + i, jy - j, "cream" if j == 4 else ("yellow" if i < 0 else "amber" if i < 2 else "orange"))
    c.dot(jx - 1, jy - 2, "white")
    plate(c, -2.4, -0.2, V, 0.6, 3.8, "drop")
    soften(c.im)
    return c, c.pivot(0, 0)


def market_cart():
    """A red cart with cream trim and one big cream wheel, crates of produce, and a red-and-cream
    striped canopy on cream poles; a coin on the plate."""
    c = Iso(34, 40, 17, 28)
    U, V = 3.2, 1.8
    c.box(-U, U, -V, V, 3, 7.5, "brown_l", "red", "red_d")
    c.uline(-U, U, V, 7.5, "cream"); c.vline(U, 2 * V, -V, 7.5, "tan")
    for u, v, light, dark in ((-1.8, -0.6, "pink", "red"), (-0.2, -0.4, "orange", "rust"), (1.4, -0.6, "green", "green_d"),
                              (-1.0, 0.8, "yellow", "amber"), (0.8, 0.8, "pink", "red")):
        x, y = c.p(u, v, 7.5)
        for dx, dy, col in ((0, -1, light), (1, -1, light), (0, 0, dark), (1, 0, dark)):
            c.dot(x + dx, y + dy, col)
    plate(c, -1.4, 1.4, V, 3.6, 6.8, "coin")
    x, y = c.p(0.4, V, 3)                                                   # big wheel
    c.ellipse(x, y, 3.5, 3.5, "ink")
    c.ellipse(x, y, 2.3, 2.3, "cream")
    c.dot(x, y, "red"); c.dot(x - 1, y - 1, "tan"); c.dot(x + 1, y + 1, "tan")
    c.line((U, 0, 4), (U + 2.4, 0, 3), "brown")                             # handle
    for u, v in ((-U + 0.2, V - 0.2), (U - 0.2, V - 0.2), (U - 0.2, -V + 0.2)):
        c.zline(u, v, 7.5, 15, "cream")
    stripes = ("red", "cream")
    for k in range(int(U * 2 * 2)):
        u = -U - 0.4 + k * 0.5
        c.poly([(u, -V - 0.4, 16.5), (u + 0.5, -V - 0.4, 16.5), (u + 0.5, V + 0.6, 14.2), (u, V + 0.6, 14.2)], stripes[(k // 2) % 2])
    for k in range(int(U * 2 * 2)):
        u = -U - 0.4 + k * 0.5
        x, y = c.p(u, V + 0.6, 14.2)
        col = "red_d" if (k // 2) % 2 == 0 else "tan"
        c.dot(x, y + 1, col); c.dot(x + 1, y + 1, col)
    soften(c.im)
    return c, c.pivot(0, 0)


def shed(c, U, V, WH, PK, roof=("red", "red_d"), walls=("cream", "cream", "tan")):
    """A small gable shed: cream board walls on a stone footing, red roof with cream rake boards
    (ridge along u, gable end facing +u)."""
    R = WH + PK
    ze = WH - PK / V
    c.poly([(-U - 0.6, 0, R), (U + 0.6, 0, R), (U + 0.6, -V - 0.6, ze), (-U - 0.6, -V - 0.6, ze)], "red_d")
    c.box(-U, U, -V, V, 0, 1.2, "grey_l", "grey", "grey_d", lit=False)
    c.box(-U, U, -V, V, 1.2, WH, *walls, lit=False)
    for u in range(round(-U) + 1, round(U)):
        c.zline(u, V, 1.2, WH, "tan")
    c.poly([(U, -V, WH), (U, V, WH), (U, 0, R)], walls[2])
    c.zline(U, V, 0, WH, "red", dx=-1); c.zline(-U, V, 0, WH, "red"); c.zline(U, -V, 0, WH, "red_d", dx=-1)
    c.poly([(-U - 0.6, 0, R), (U + 0.6, 0, R), (U + 0.6, V + 0.6, ze), (-U - 0.6, V + 0.6, ze)], roof[0])
    for k in (1.2, 2.4):
        c.uline(-U - 0.6, U + 0.6, k, R - PK * k / V, roof[1])
    c.uline(-U - 0.6, U + 0.6, V + 0.6, ze, "cream")
    c.line((U + 0.6, 0, R + 1), (U + 0.6, V + 0.6, ze), "cream")
    c.line((U + 0.6, 0, R + 1), (U + 0.6, -V - 0.6, ze), "cream")
    c.uline(-U - 0.6, U + 0.6, 0, R + 1, "cream")
    return R


def harvest_shed():
    """An open-fronted cream shed with a red roof: wheat sheaves stacked inside, a scythe leaning
    by the opening and the wheat emblem on the gable."""
    c = Iso(40, 44, 20, 30)
    U, V, WH, PK = 3.4, 2.8, 11, 6
    shed(c, U, V, WH, PK)
    c.poly([(-U + 0.8, V, 1.2), (U - 0.8, V, 1.2), (U - 0.8, V, WH - 1), (-U + 0.8, V, WH - 1)], "brown_d")   # opening
    for k, u in enumerate((-2.0, -0.8, 0.4, 1.6)):                         # sheaves
        x, y = c.p(u, V, 1.2)
        for j in range(7):
            w = 1 if 2 <= j <= 4 else 2
            for i in range(-w, w + 1):
                c.dot(x + i, y - j, "brown_l" if j == 3 else ("yellow" if i < 0 else "amber"))
    c.wheat(*c.p(U, -0.4, WH + 4.4))
    x, y = c.p(U + 0.4, V - 0.2, 0)                                         # scythe
    for k in range(13):
        c.dot(x + k // 4, y - k, "brown"); c.dot(x + k // 4 + 1, y - k, "brown_l")
    for i in range(5):
        c.dot(x + 3 - i, y - 12 + i // 2, "grey_l")
    soften(c.im)
    return c, c.pivot(0, 0)


def planter_shed():
    """A cream potting shed with a red roof, a red door with a sprout plate, a window box of
    flowers and a tray of seedlings by the wall."""
    c = Iso(40, 44, 20, 30)
    U, V, WH, PK = 3.0, 2.6, 11, 6
    shed(c, U, V, WH, PK, roof=("green", "green_d"))
    c.poly([(0.4, V, 1.2), (2.2, V, 1.2), (2.2, V, 8.4), (0.4, V, 8.4)], "red")             # door
    c.poly([(0.6, V, 1.2), (2.0, V, 1.2), (2.0, V, 8.0), (0.6, V, 8.0)], "red_d")
    c.dot(*c.p(1.8, V, 4.6), "cream")
    x, y = c.p(1.3, V, 7.2)
    rows, key = ICONS["sprout"]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in key:
                c.dot(x - 1 + i, y + j, key[ch])
    c.poly([(-2.4, V, 5), (-0.6, V, 5), (-0.6, V, 8.4), (-2.4, V, 8.4)], "cream")          # window
    c.poly([(-2.1, V, 5.4), (-0.9, V, 5.4), (-0.9, V, 8.0), (-2.1, V, 8.0)], "blue_d")
    c.box(-2.6, -0.4, V, V + 0.6, 4, 5, "red", "red", "red_d")                              # window box
    for i, col in enumerate(("pink", "yellow", "white", "pink")):
        x, y = c.p(-2.4 + i * 0.6, V + 0.3, 5.6)
        c.dot(x, y, col); c.dot(x, y + 1, "green")
    c.box(U + 0.4, U + 2.0, -1.6, 0.4, 0, 1.2, "brown_l", "brown", "brown_d")              # seedling tray
    for u, v in ((U + 0.8, -1.2), (U + 1.6, -1.0), (U + 0.9, -0.2), (U + 1.6, 0.1)):
        x, y = c.p(u, v, 1.2)
        c.dot(x, y - 1, "green"); c.dot(x, y, "green_d")
    soften(c.im)
    return c, c.pivot(0, 0)


def windmill():
    """A tapered cream tower with red bands, a red door and window, a red cap, and four cream
    sails in red frames turned toward the viewer."""
    c = Iso(56, 76, 28, 66)
    cx, cy = c.p(0, 0, 0)
    body = ("white", "cream", "tan", "tan_d")
    red = ("pink", "red", "red_d", "purple")
    rings(c, cx, cy, 0, 34, 9, 6, body, band=lambda z: red if z in (1, 2, 33, 34) else None)
    for j in range(8):                                                      # door
        for i in range(-2, 2):
            c.dot(cx - 3 + i, cy + 3 - j, "red_d" if j == 7 or i in (-2, 1) else "red")
    c.dot(cx - 3, cy - 1, "cream")
    for j in range(4):                                                      # window
        for i in range(2):
            c.dot(cx - 2 + i, cy - 20 - j, "slate_d" if 0 < j < 3 else "cream")
    for z in range(35, 44):                                                 # the cap
        t = (z - 35) / 9
        r = max(1, round(7 * (1 - t)))
        for x in range(cx - r, cx + r + 1):
            dx = (x - cx) / r if r else 0
            s_ = max(0.0, 1 - dx * dx) ** 0.5
            for y in range(round(cy - z - r / 2 * s_), round(cy - z + r / 2 * s_) + 1):
                c.dot(x, y, "pink" if dx < -0.4 else "red" if dx < 0.3 else "red_d")
    for i in range(-7, 8):
        c.dot(cx + i, cy - 35 + round(3.5 * (1 - (i / 7.0) ** 2) ** 0.5), "cream")
    c.dot(cx, cy - 45, "cream"); c.dot(cx, cy - 46, "cream")
    # Sails: four arms in an X from a hub on the front of the cap.
    hx, hy = cx - 3, cy - 36
    for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        a, b = (hx + sx * 4, hy + sy * 4), (hx + sx * 15, hy + sy * 15)     # cloth beside the spar
        ox, oy = -sy * 4, sx * 4
        c.d.polygon([a, b, (b[0] + ox, b[1] + oy), (a[0] + ox, a[1] + oy)], fill=c.c("cream"), outline=c.c("red"))
        c.d.line([(hx + sx * 2, hy + sy * 2), b], fill=c.c("red_d"), width=1)
        c.d.line([(hx + sx * 2 + (1 if sx < 0 else 0), hy + sy * 2), (b[0] + (1 if sx < 0 else 0), b[1])], fill=c.c("red_d"), width=1)
    c.dot(hx, hy, "grey_d"); c.dot(hx + 1, hy, "grey"); c.dot(hx, hy - 1, "grey")
    soften(c.im)
    return c, (cx, cy + 4)


MACHINES_2 = [("oil_press", oil_press), ("market_cart", market_cart), ("harvest_shed", harvest_shed),
              ("planter_shed", planter_shed), ("windmill", windmill)]


def preview5(out):
    """Batch 5 (the last farm machines): today's sprite over the hand-pixeled one, 4x."""
    from PIL import Image, ImageDraw
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    import polish
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app", "src", "main", "assets", "art")
    man = json.load(open(os.path.join(art, "manifest.json")))
    hand = Image.open(os.path.join(art, "people/hana_se_idle.png")).convert("RGBA")
    hp = man["people/hana_se_idle"]
    cw, ch = 76, 90
    sheet = Image.new("RGBA", (cw * len(MACHINES_2), ch * 2), (62, 137, 72, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, fn) in enumerate(MACHINES_2):
        for row in range(2):
            ax, ay = i * cw + 44, row * ch + ch - 10
            d.line([(ax - 16, ay), (ax, ay - 8), (ax + 16, ay), (ax, ay + 8), (ax - 16, ay)], fill=(48, 110, 60, 255))
            sheet.alpha_composite(hand, (ax - 28 - hp["ax"], ay - hp["ay"]))
            key = "buildings/" + name
            if row == 0:
                im = Image.open(os.path.join(art, key + ".png")).convert("RGBA")
                p = man[key]
                sheet.alpha_composite(im, (ax - p["ax"], ay - p["ay"]))
                d.text((i * cw + 2, 1), name.replace("_", " "), fill=(255, 255, 255, 255))
            else:
                cnv, (px_, py_) = fn()
                x0, y0, x1, y1 = cnv.im.getbbox()
                body, pad = finish(cnv.im.crop((x0, y0, x1, y1)))
                sys.stderr.write("%s bh %d (now %d)\n" % (name, body.height, man[key]["bh"]))
                body = polish.polish(body, "world", key)
                sheet.alpha_composite(body, (ax - (px_ - x0 + pad), ay - (py_ - y0 + pad)))
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(out)


# ---------------------------------------------------------------- yard props and the bunkhouse

ICONS.update({
    "bed": (["c....", "crrrr", "ccccc", "c...c"], {"c": "cream", "r": "red"}),
    "tree": ([".g.", "ggg", "GgG", ".b."], {"g": "green", "G": "green_d", "b": "brown"}),
    "pick": (["sss.", "...s", "..b.", ".b.."], {"s": "grey_d", "b": "brown"}),
})


def bunkhouse(lit=False):
    """The farmhands' bunkhouse: a cream cottage with a red roof, red door and shutters, cream
    window frames, a chimney and a bed on the plate. lit=True draws only the glowing windows (the
    night overlay)."""
    c = Iso(56, 60, 28, 42)
    U, V, WH, PK = 4.4, 3.4, 13, 8
    windows = [(V, -3.4, -1.8, 5.2, 9.4), (V, 2.0, 3.6, 5.2, 9.4)]          # on the +v face: (v, u0, u1, z0, z1)
    gable = [(-1.0, 1.0, 5.2, 9.4), (-0.7, 0.7, WH + 1.6, WH + 4.4)]          # on the +u face: (v0, v1, z0, z1)
    if lit:
        for v, u0, u1, z0, z1 in windows:
            c.poly([(u0 + 0.3, v, z0 + 0.5), (u1 - 0.3, v, z0 + 0.5), (u1 - 0.3, v, z1 - 0.5), (u0 + 0.3, v, z1 - 0.5)], "amber")
            c.zline((u0 + u1) / 2, v, z0 + 0.5, z1 - 0.5, "yellow")
        for v0, v1, z0, z1 in gable:
            c.poly([(U, v0 + 0.3, z0 + 0.5), (U, v1 - 0.3, z0 + 0.5), (U, v1 - 0.3, z1 - 0.5), (U, v0 + 0.3, z1 - 0.5)], "amber")
        return c, c.pivot(0, 0)
    c.box(1.6, 2.8, -2.6, -1.4, WH, WH + PK + 4, "grey_l", "grey", "grey_d")  # chimney (behind the ridge)
    c.box(1.4, 3.0, -2.8, -1.2, WH + PK + 3, WH + PK + 4.2, "cream", "cream", "tan")
    shed(c, U, V, WH, PK)
    for v, u0, u1, z0, z1 in windows:
        c.poly([(u0, v, z0), (u1, v, z0), (u1, v, z1), (u0, v, z1)], "cream")
        c.poly([(u0 + 0.3, v, z0 + 0.5), (u1 - 0.3, v, z0 + 0.5), (u1 - 0.3, v, z1 - 0.5), (u0 + 0.3, v, z1 - 0.5)], "blue_d")
        c.zline((u0 + u1) / 2, v, z0 + 0.5, z1 - 0.5, "cream")
        c.zline(u0 - 0.2, v, z0, z1, "red"); c.zline(u1 + 0.6, v, z0, z1, "red")      # shutters
        x, y = c.p(u0 + 0.4, v, z1 - 0.8)
        c.dot(x, y, "cyan")
    for v0, v1, z0, z1 in gable:
        c.poly([(U, v0, z0), (U, v1, z0), (U, v1, z1), (U, v0, z1)], "cream")
        c.poly([(U, v0 + 0.3, z0 + 0.5), (U, v1 - 0.3, z0 + 0.5), (U, v1 - 0.3, z1 - 0.5), (U, v0 + 0.3, z1 - 0.5)], "slate_d")
    c.poly([(-0.9, V, 1.2), (0.9, V, 1.2), (0.9, V, 9.6), (-0.9, V, 9.6)], "cream")   # door
    c.poly([(-0.6, V, 1.2), (0.6, V, 1.2), (0.6, V, 9.2), (-0.6, V, 9.2)], "red")
    c.dot(*c.p(0.4, V, 5), "cream")
    c.box(-1.4, 1.4, V, V + 0.8, 0, 1.2, "grey_l", "grey", "grey_d")      # step
    plate(c, -1.2, 1.2, V, 10, 12.6, "bed")
    c.box(-U + 0.4, -U + 2.0, V + 0.2, V + 0.8, 1.6, 2.2, "brown_l", "brown", "brown_d")   # bench
    soften(c.im)
    return c, c.pivot(0, 0)


def hay_bale():
    """A square straw bale tied with two red twine bands."""
    c = Iso(26, 24, 13, 16)
    U, V, H = 2.4, 1.7, 4.6
    c.box(-U, U, -V, V, 0, H, "yellow", "amber", "orange")
    for u, v, z in ((-1.6, V, 2), (0.6, V, 3.2), (-0.4, V, 1.2), (1.6, V, 2.4)):     # straw ends
        x, y = c.p(u, v, z)
        c.dot(x, y, "yellow")
    for u in (-1.0, 1.0):                                                   # twine
        c.zline(u, V, 0, H, "red")
        c.vline(u, 2 * V, -V, H, "red")
    c.vline(U, 2 * V, -V, 2.4, "rust")
    soften(c.im)
    return c, c.pivot(0, 0)


def trough():
    """A wooden water trough on red legs, water with a glint inside a cream rim."""
    c = Iso(28, 22, 14, 14)
    U, V = 2.8, 1.3
    for u, v in ((-U + 0.2, V - 0.5), (U - 0.5, V - 0.5), (U - 0.5, -V + 0.2)):
        c.box(u, u + 0.3, v, v + 0.3, 0, 1.4, "red", "red", "red_d")
    c.box(-U, U, -V, V, 1.4, 4.6, "cream", "brown_l", "brown")
    c.uline(-U, U, V, 3, "brown")
    c.poly([(-U + 0.5, -V + 0.5, 4.6), (U - 0.5, -V + 0.5, 4.6), (U - 0.5, V - 0.5, 4.6), (-U + 0.5, V - 0.5, 4.6)], "blue")
    c.uline(-U + 0.5, U - 0.5, -V + 0.5, 4.6, "blue_d")
    x, y = c.p(-0.6, 0, 4.6)
    c.dot(x, y, "white"); c.dot(x + 1, y, "cyan")
    soften(c.im)
    return c, c.pivot(0, 0)


def bridge():
    """A plank bridge running along v (up-right on screen): natural planks, red posts with cream
    caps and cream rails, like the fences. Pivot on the water at its centre."""
    c = Iso(50, 38, 25, 24)
    U, L = 2.9, 8
    rail, post = ("white", "cream", "tan"), ("red", "red", "red_d")

    def railing(u):
        c.box(u - 0.2, u + 0.2, -L, L, 9.4, 10.4, *rail)
        for v in (-L + 0.2, -L / 3, L / 3, L - 1.0):
            c.box(u - 0.4, u + 0.4, v, v + 0.8, 6.4, 11.2, *post)
            x, y = c.p(u, v + 0.4, 11.2)
            c.dot(x, y, "cream"); c.dot(x - 1, y, "cream")
    for u, v in ((-U + 0.4, -L + 1), (U - 0.8, -L + 1), (-U + 0.4, L - 1.4), (U - 0.8, L - 1.4)):
        c.box(u, u + 0.4, v, v + 0.4, 0, 3.6, "brown_l", "brown", "brown_d")   # piles
    railing(-U + 0.3)
    c.box(-U - 0.2, U + 0.2, -L, L, 3.6, 5, "brown_l", "brown", "brown_d", lit=False)
    c.box(-U, U, -L, L, 5, 6.4, "tan", "brown_l", "brown")
    for k in range(1, L):
        c.uline(-U, U, -L + 2 * k, 6.4, "brown")                               # plank seams
    railing(U - 0.3)
    soften(c.im)
    return c, c.pivot(0, 0)


def signpost(icon):
    """A red post with a cream arrow board in a red edge, the destination's icon painted on it."""
    c = Iso(26, 30, 13, 24)
    c.box(-0.3, 0.3, -0.3, 0.3, 0, 15, "red", "red", "red_d")
    c.dot(*c.p(0, 0, 15), "cream")
    u0, u1, z0, z1 = -3.0, 2.6, 9, 14.4
    pts = [(u0, 0.35, z0), (u1, 0.35, z0), (u1 + 1.2, 0.35, (z0 + z1) / 2), (u1, 0.35, z1), (u0, 0.35, z1)]
    c.poly(pts, "red_d")
    inner = [(u0 + 0.5, 0.35, z0 + 0.6), (u1, 0.35, z0 + 0.6), (u1 + 0.6, 0.35, (z0 + z1) / 2), (u1, 0.35, z1 - 0.6), (u0 + 0.5, 0.35, z1 - 0.6)]
    c.poly(inner, "cream")
    rows, key = ICONS[icon]
    x, y = c.p(-0.4, 0.35, (z0 + z1) / 2)
    x -= len(rows[0]) // 2
    y -= len(rows) // 2
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in key:
                c.dot(x + i, y + j, key[ch])
    soften(c.im)
    return c, c.pivot(0, 0)


YARD = [("buildings/cottage_c", bunkhouse), ("deco/hay_bale", hay_bale), ("deco/trough", trough),
        ("deco/bridge", bridge), ("deco/sign_forest", lambda: signpost("tree")), ("deco/sign_mine", lambda: signpost("pick"))]


def preview6(out):
    """Yard props: today's sprite over the hand-pixeled one, 4x."""
    from PIL import Image, ImageDraw
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    import polish
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app", "src", "main", "assets", "art")
    man = json.load(open(os.path.join(art, "manifest.json")))
    hand = Image.open(os.path.join(art, "people/hana_se_idle.png")).convert("RGBA")
    hp = man["people/hana_se_idle"]
    cw, ch = 80, 76
    sheet = Image.new("RGBA", (cw * len(YARD), ch * 2), (62, 137, 72, 255))
    for i, (key, fn) in enumerate(YARD):
        for row in range(2):
            ax, ay = i * cw + 46, row * ch + ch - 12
            sheet.alpha_composite(hand, (ax - 30 - hp["ax"], ay - hp["ay"]))
            if row == 0:
                im = Image.open(os.path.join(art, key + ".png")).convert("RGBA")
                p = man[key]
                sheet.alpha_composite(im, (ax - p["ax"], ay - p["ay"]))
            else:
                cnv, (px_, py_) = fn()
                x0, y0, x1, y1 = cnv.im.getbbox()
                body, pad = finish(cnv.im.crop((x0, y0, x1, y1)))
                sys.stderr.write("%s bh %d (now %d)\n" % (key, body.height, man[key].get("bh", 0)))
                body = polish.polish(body, "world", key)
                sheet.alpha_composite(body, (ax - (px_ - x0 + pad), ay - (py_ - y0 + pad)))
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(out)


def emit_overlay(sp, name, made, under):
    """Saves an overlay (no outline, shadow or polish) on the canvas and pivot of the sprite
    `under` already saved by sp, so the engine can draw one exactly over the other."""
    from PIL import Image
    cnv, (px, py) = made
    base = Image.open(os.path.join(sp.dir, under + ".png"))
    p = sp.pivots[f"{sp.group}/{under}"]
    out = Image.new("RGBA", base.size, (0, 0, 0, 0))
    out.alpha_composite(cnv.im, (p["ax"] - px, p["ay"] - py))
    sp.save(name, out, p["ax"], p["ay"])


# ---------------------------------------------------------------- decorations (round 5)

def _map(c, x0, y0, rows, key):
    """Pixel map onto the canvas with its top-left at (x0, y0)."""
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in key:
                c.dot(x0 + i, y0 + j, key[ch])


def decor_flowers(light, mid, dark):
    """A raised bed: cream-capped wooden sides, dark soil and a mound of flowers with leaves."""
    c = Iso(30, 24, 15, 13)
    S = 2.6
    c.box(-S, S, -S, S, 0, 2.4, "cream", "brown_l", "brown")
    c.poly([(-S + 0.4, -S + 0.4, 2.4), (S - 0.4, -S + 0.4, 2.4), (S - 0.4, S - 0.4, 2.4), (-S + 0.4, S - 0.4, 2.4)], "brown_d")
    for k, (u, v) in enumerate(((-1.4, -1.0), (0.2, -1.4), (1.4, -0.4), (-1.0, 0.4), (0.4, 0.6), (1.4, 1.2), (-0.2, 1.6), (-1.6, 1.4))):
        x, y = c.p(u, v, 3.4)
        c.dot(x - 1, y + 1, "green_d"); c.dot(x + 1, y + 1, "green")
        c.dot(x, y, mid); c.dot(x - 1, y, light if k % 2 else mid); c.dot(x + 1, y, dark); c.dot(x, y - 1, light)
    soften(c.im)
    return c, c.pivot(0, 0)


def decor_bench():
    """A red garden bench with cream slats and arm rests."""
    c = Iso(30, 26, 15, 16)
    U, V = 3.0, 1.0
    for u in (-U, U - 0.5):
        c.box(u, u + 0.5, -V, V, 0, 3.4, "red", "red", "red_d")
    c.box(-U, U, -V, V, 3.4, 4.2, "cream", "tan", "tan_d")
    c.uline(-U, U, 0, 4.2, "tan")
    c.box(-U, U, -V - 0.3, -V + 0.1, 4.2, 8, "cream", "tan", "tan_d")
    c.uline(-U, U, -V + 0.1, 6.4, "tan_d")
    for u in (-U, U - 0.5):
        c.box(u, u + 0.5, -V, V, 4.2, 5.6, "red", "red", "red_d")
    soften(c.im)
    return c, c.pivot(0, 0)


GNOME = [
    "....r....",
    "...rr....",
    "...rrr...",
    "..rrRrr..",
    "..rrrrr..",
    ".rrrrrrr.",
    "..sksks..",
    "..sssss..",
    ".wwwswww.",
    ".bwwwwwb.",
    "bbbwwwbbb",
    ".bnnnnnb.",
    ".bbbbbbb.",
    "..bb.bb..",
    "..kk.kk..",
]


def decor_gnome():
    """A garden gnome: tall red hat, rosy face, white beard, blue coat with a brown belt."""
    c = Iso(22, 24, 11, 20)
    c.box(-1.6, 1.6, -1.6, 1.6, 0, 1.2, "grey_l", "grey", "grey_d")
    key = {"r": "red", "R": "pink", "s": "skin", "k": "ink", "w": "white", "b": "blue", "n": "brown"}
    x, y = c.p(0, 0, 1.2)
    _map(c, x - 4, y - len(GNOME), GNOME, key)
    soften(c.im)
    return c, c.pivot(0, 0)


def decor_birdbath():
    """A stone birdbath: pedestal, a wide bowl of water and a little bird on the rim."""
    c = Iso(26, 26, 13, 20)
    c.box(-1.6, 1.6, -1.6, 1.6, 0, 1.2, "grey_l", "grey", "grey_d")
    c.box(-0.6, 0.6, -0.6, 0.6, 1.2, 7, "grey_l", "grey", "grey_d")
    cx, cy = c.p(0, 0, 7)
    c.ellipse(cx, cy + 1, 8, 4, "grey_d")
    c.ellipse(cx, cy, 8, 4, "grey_l")
    c.ellipse(cx, cy, 6, 3, "blue")
    c.dot(cx - 2, cy - 1, "cyan"); c.dot(cx - 1, cy - 1, "white")
    for dx, dy, col in ((5, -3, "red_d"), (6, -3, "red"), (6, -4, "red"), (7, -4, "amber"), (5, -2, "red_d")):
        c.dot(cx + dx, cy + dy, col)
    soften(c.im)
    return c, c.pivot(0, 0)


def decor_lamp():
    """A garden lamp: a red post on a slate foot, a cream lantern with warm glass and a slate cap."""
    c = Iso(14, 30, 7, 27)
    c.box(-1.0, 1.0, -1.0, 1.0, 0, 1.0, "slate", "slate_d", "ink")
    c.box(-0.35, 0.35, -0.35, 0.35, 1.0, 13, "red", "red", "red_d")
    c.box(-1.1, 1.1, -1.1, 1.1, 13, 18, "cream", "cream", "tan")
    x, y = c.p(0, 1.1, 15.5)
    for dy in (-2, -1, 0, 1):
        c.dot(x - 2, y + dy, "amber"); c.dot(x - 1, y + dy, "yellow")
    c.dot(x - 1, y - 2, "white")
    c.poly([(-1.5, 1.5, 18), (1.5, 1.5, 18), (0, 0, 21)], "slate")
    c.poly([(1.5, -1.5, 18), (1.5, 1.5, 18), (0, 0, 21)], "slate_d")
    soften(c.im)
    return c, c.pivot(0, 0)


def decor_topiary():
    """A round clipped bush in a red pot with a cream rim."""
    c = Iso(26, 30, 13, 24)
    c.box(-1.8, 1.8, -1.8, 1.8, 0, 3.6, "brown_d", "red", "red_d")
    c.uline(-1.8, 1.8, 1.8, 3.2, "cream"); c.vline(1.8, 3.6, -1.8, 3.2, "tan")
    cx, cy = c.p(0, 0, 3.6)
    for k in range(12):                                                     # the ball, built in rings
        r = round(6.5 * max(0.0, 1 - ((k - 6) / 6.5) ** 2) ** 0.5)
        if r <= 0:
            continue
        for x in range(cx - r, cx + r + 1):
            dx = (x - cx) / r
            col = "green" if dx < -0.3 else "green_d" if dx < 0.5 else "green_dd"
            c.dot(x, cy - 2 - k, col)
    for dx, dy in ((-3, -9), (-1, -12), (2, -8), (-4, -5)):
        c.dot(cx + dx, cy + dy, "yellow")
    soften(c.im)
    return c, c.pivot(0, 0)


def decor_pond():
    """A little lily pond: a stone rim, water with a glint, two lily pads and a pink flower."""
    c = Iso(34, 22, 17, 11)
    cx, cy = c.p(0, 0, 0)
    c.ellipse(cx, cy + 1, 14, 7, "grey_d")
    c.ellipse(cx, cy, 14, 7, "grey_l")
    c.ellipse(cx, cy, 11, 5, "blue_d")
    c.ellipse(cx + 1, cy + 1, 9, 4, "blue")
    c.dot(cx - 5, cy - 2, "cyan"); c.dot(cx - 4, cy - 2, "white")
    for px_, py_ in ((cx + 3, cy - 1), (cx - 4, cy + 2)):
        for dx in (-1, 0, 1):
            c.dot(px_ + dx, py_, "green"); c.dot(px_ + dx, py_ + 1, "green_d")
    c.dot(cx + 3, cy - 2, "pink"); c.dot(cx + 4, cy - 2, "white")
    for k in range(0, 360, 40):                                             # rim stones
        import math
        a = math.radians(k)
        c.dot(round(cx + 13 * math.cos(a)), round(cy + 6 * math.sin(a)), "grey")
    return c, (cx, cy + 7)


def decor_arch():
    """A cream rose arch over the path: two posts, a curved top, green vines and pink roses."""
    c = Iso(30, 40, 15, 34)
    for v in (-2.2, 2.2):
        c.box(-0.4, 0.4, v - 0.4, v + 0.4, 0, 20, "cream", "cream", "tan")
    x0, y0 = c.p(0, 2.2, 20)
    x1, y1 = c.p(0, -2.2, 20)
    for i in range(0, 11):
        t = i / 10
        x = round(x0 + (x1 - x0) * t)
        y = round(y0 + (y1 - y0) * t - 5 * (1 - (2 * t - 1) ** 2))
        c.dot(x, y, "cream"); c.dot(x, y + 1, "tan")
        if i % 2 == 0:
            c.dot(x, y - 1, "green"); c.dot(x + 1, y, "green_d")
        if i % 3 == 1:
            c.dot(x, y - 1, "pink"); c.dot(x + 1, y - 1, "red")
    for v in (-2.2, 2.2):
        for z in range(2, 20, 3):
            x, y = c.p(0.4, v, z)
            c.dot(x, y, "green"); c.dot(x - 1, y - 1, "green_d")
            if z % 6 == 2:
                c.dot(x + 1, y - 1, "pink")
    soften(c.im)
    return c, c.pivot(0, 0)


def decor_sheaf():
    """The Golden Sheaf statue: the farm's wheat emblem cast in gold, twice size, on a cream plinth
    with red trim."""
    from _iso import WHEAT
    c = Iso(30, 40, 15, 33)
    c.box(-2.6, 2.6, -2.6, 2.6, 0, 1.2, "grey_l", "grey", "grey_d")
    c.box(-2.0, 2.0, -2.0, 2.0, 1.2, 8, "cream", "cream", "tan")
    c.uline(-2.0, 2.0, 2.0, 7.0, "red"); c.vline(2.0, 4.0, -2.0, 7.0, "red_d")
    x, y = c.p(0, 0, 8)
    cols = {"a": "amber", "y": "yellow", "Y": "white", "b": "orange"}
    for j, row in enumerate(WHEAT):
        for i, ch in enumerate(row):
            if ch in cols:
                for dx in (0, 1):
                    for dy in (0, 1):
                        c.dot(x - 5 + 2 * i + dx, y - 13 + 2 * j + dy, cols[ch])
    soften(c.im)
    return c, c.pivot(0, 0)


DECOR_ART = [("flowers_red", lambda: decor_flowers("pink", "red", "red_d")),
             ("flowers_sun", lambda: decor_flowers("yellow", "amber", "orange")),
             ("bench", decor_bench), ("gnome", decor_gnome), ("birdbath", decor_birdbath), ("lamp", decor_lamp),
             ("topiary", decor_topiary), ("pond", decor_pond), ("arch", decor_arch), ("sheaf", decor_sheaf)]


def decor_picket():
    """A run of cream pickets with pointed tops on two tan rails, between red end posts."""
    c = Iso(32, 26, 16, 16)
    for z in (2, 5):                                                        # rails
        c.uline(-3.2, 3.2, 0, z, "tan")
        c.uline(-3.2, 3.2, 0.3, z - 0.6, "tan_d")
    for k in range(5):                                                      # pickets, a gap between each
        x, y = c.p(-2.6 + k * 1.5, 0, 0)
        for j in range(8):
            c.dot(x, y - j, "white"); c.dot(x + 1, y - j, "cream")
        c.dot(x, y - 8, "cream")
    for u in (-3.4, 3.2):                                                   # red end posts with cream caps
        x, y = c.p(u, 0, 0)
        for j in range(9):
            c.dot(x, y - j, "red"); c.dot(x + 1, y - j, "red_d")
        c.dot(x, y - 9, "cream"); c.dot(x + 1, y - 9, "cream")
    return c, c.pivot(0, 0)


def decor_path(stone):
    """Flat path stones (grey flags) or bricks (red, herringbone) across a tile; drawn flat."""
    c = Iso(36, 20, 18, 10)
    if stone:
        for (u, v, s) in ((-2.6, -2.4, 2.0), (0.2, -2.8, 2.2), (2.4, -1.2, 1.8), (-2.8, 0.3, 2.2), (0.0, 0.0, 2.0),
                          (2.6, 1.6, 2.0), (-1.4, 2.4, 1.9), (1.0, 2.6, 1.6)):
            c.poly([(u - s / 2, v - s / 2, 0), (u + s / 2, v - s / 2, 0), (u + s / 2, v + s / 2, 0), (u - s / 2, v + s / 2, 0)], "grey")
            c.uline(u - s / 2, u + s / 2, v + s / 2, 0, "grey_d")
            x, y = c.p(u - s / 4, v - s / 4, 0)
            c.dot(x, y, "grey_l")
    else:
        for k in range(-4, 4):
            for j in range(-3, 4):
                u0, v0 = k + (0.5 if j % 2 else 0), j
                col = "red" if (k + j) % 3 else "rust"
                c.poly([(u0, v0 - 0.45, 0), (u0 + 0.9, v0 - 0.45, 0), (u0 + 0.9, v0 + 0.45, 0), (u0, v0 + 0.45, 0)], col)
                c.uline(u0, u0 + 1, v0 + 0.45, 0, "tan_d")                   # mortar
        # Trim to the tile's diamond.
        px = c.im.load()
        for y in range(c.h):
            for x in range(c.w):
                if abs(x - 18) / 16 + abs(y - 10) / 8 > 1:
                    px[x, y] = (0, 0, 0, 0)
    return c, c.pivot(0, 0)


def decor_haystack():
    """A round golden hay stack with a few loose straws."""
    import math
    c = Iso(28, 26, 14, 21)
    cx, cy = 14, 21
    for z in range(0, 12):
        r = round(9 * (1 - (z / 12.5) ** 2) ** 0.5)
        for x in range(cx - r, cx + r + 1):
            dx = (x - cx) / max(1, r)
            c.dot(x, cy - z, "yellow" if dx < -0.3 else "amber" if dx < 0.5 else "orange")
    for a in range(0, 360, 50):
        x = round(cx + 7 * math.cos(math.radians(a)))
        c.dot(x, cy - 5 - (a % 3), "brown_l")
    c.dot(cx - 2, cy - 12, "yellow"); c.dot(cx + 1, cy - 13, "amber")
    return c, (cx, cy)


def decor_sunflowers():
    """Three tall sunflowers with yellow petals and brown faces."""
    c = Iso(28, 36, 14, 32)
    for k, (dx, h) in enumerate(((-6, 22), (1, 27), (7, 19))):
        x, y = 14 + dx, 32
        for j in range(h):
            c.dot(x, y - j, "green_d" if j % 4 else "green")
        c.dot(x - 1, y - h // 2, "green"); c.dot(x - 2, y - h // 2 + 1, "green"); c.dot(x + 1, y - h // 3, "green")
        top = y - h
        for ddx, ddy in ((-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, -1), (-1, 1), (1, 1), (-2, -1), (2, 1)):
            c.dot(x + ddx, top + ddy, "yellow")
        c.dot(x, top, "brown"); c.dot(x - 1, top, "brown_d"); c.dot(x, top - 1, "brown_l")
    return c, (14, 32)


def decor_blossom():
    """A little blossom tree: brown trunk, a round pink canopy with white petals."""
    import math
    c = Iso(30, 40, 15, 36)
    cx, cy = 15, 36
    for j in range(14):
        c.dot(cx, cy - j, "brown"); c.dot(cx + 1, cy - j, "brown_d")
    c.dot(cx - 1, cy - 9, "brown"); c.dot(cx - 2, cy - 10, "brown")
    for k, (ox, oy, r) in enumerate(((0, -24, 9), (-6, -19, 6), (6, -19, 6), (0, -17, 6))):
        c.ellipse(cx + ox, cy + oy, r, r * 0.8, "pink" if k else "pink")
    for a in range(0, 360, 37):
        x = round(cx + 7 * math.cos(math.radians(a)))
        y = round(cy - 22 + 6 * math.sin(math.radians(a)))
        c.dot(x, y, "white")
    c.ellipse(cx + 4, cy - 18, 4, 3, "magenta")
    c.dot(cx - 3, cy - 27, "white"); c.dot(cx - 4, cy - 26, "white")
    return c, (cx, cy)


def decor_pumpkins():
    """A pile of three pumpkins; the front one is a smiling jack-o'-lantern."""
    c = Iso(30, 24, 15, 20)

    def pumpkin(x, y, r, face=False):
        for j in range(-r, r + 1):
            half = round(r * 1.25 * max(0.0, 1 - (j / (r + 0.5)) ** 2) ** 0.5)
            for i in range(-half, half + 1):
                c.dot(x + i, y + j, "amber" if i < -half // 3 else "orange" if i < half // 2 else "rust")
        c.dot(x, y - r - 1, "green_d"); c.dot(x + 1, y - r - 2, "green_d")
        for i in (-half // 2, half // 2):
            for j in range(-r + 1, r):
                c.dot(x + i, y + j, "rust")
        if face:
            c.dot(x - 2, y - 1, "yellow"); c.dot(x + 2, y - 1, "yellow")
            for i in range(-2, 3):
                c.dot(x + i, y + 2, "yellow")
    pumpkin(9, 15, 4)
    pumpkin(20, 14, 4)
    pumpkin(15, 18, 5, face=True)
    return c, (15, 21)


def decor_snowman():
    """A three-ball snowman: coal eyes and buttons, a carrot nose, a red scarf, stick arms."""
    c = Iso(26, 34, 13, 31)
    for y0, r in ((26, 6), (17, 5), (9, 4)):
        c.ellipse(13, y0, r, r, "white", outline="grey_l")
        c.ellipse(15, y0 + 1, r - 2, r - 2, "white")
    c.dot(11, 8, "ink"); c.dot(14, 8, "ink")
    c.dot(13, 10, "orange"); c.dot(14, 10, "orange"); c.dot(15, 11, "orange")
    for y in (15, 18, 21):
        c.dot(13, y, "ink")
    for i in range(-4, 5):
        c.dot(13 + i, 13, "red")
    c.dot(16, 14, "red"); c.dot(16, 15, "red_d")
    for k in range(4):
        c.dot(7 - k, 16 - k, "brown"); c.dot(19 + k, 16 - k, "brown")
    c.ellipse(13, 5, 4, 1, "ink"); c.ellipse(13, 3, 2, 2, "ink")
    return c, (13, 31)


def decor_vane():
    """A rooster weathervane on a red post: the cockerel in iron, N and S arms below it."""
    c = Iso(24, 40, 12, 37)
    c.box(-0.8, 0.8, -0.8, 0.8, 0, 1.0, "grey_l", "grey", "grey_d")
    c.box(-0.3, 0.3, -0.3, 0.3, 1.0, 20, "red", "red", "red_d")
    x, y = c.p(0, 0, 20)
    for i in range(-6, 7):
        c.dot(x + i, y, "grey_d")
    c.dot(x - 7, y, "grey_d"); c.dot(x + 7, y - 1, "grey_d"); c.dot(x + 7, y + 1, "grey_d")
    rooster = [
        "....kk..",
        "...kkkr.",
        "..kkkk..",
        "kkkkkk..",
        ".kkkkk..",
        "..k..k..",
    ]
    _map(c, x - 4, y - 9, rooster, {"k": "slate_d", "r": "red"})
    c.dot(x, y - 2, "grey_d"); c.dot(x, y - 1, "grey_d")
    return c, c.pivot(0, 0)


def decor_fountain():
    """A round stone fountain: a wide basin of water, a column and a spray falling back."""
    c = Iso(44, 40, 22, 32)
    cx, cy = 22, 32
    c.ellipse(cx, cy - 2, 18, 8, "grey_d")
    c.ellipse(cx, cy - 4, 18, 8, "grey_l")
    c.ellipse(cx, cy - 4, 15, 6, "blue")
    c.ellipse(cx + 2, cy - 3, 11, 4, "blue_d")
    for j in range(14):
        c.dot(cx - 1, cy - 6 - j, "grey_l"); c.dot(cx, cy - 6 - j, "grey"); c.dot(cx + 1, cy - 6 - j, "grey_d")
    c.ellipse(cx, cy - 20, 5, 2, "grey_l")
    for i, (dx, dy) in enumerate(((-4, -24), (-6, -21), (-7, -17), (4, -24), (6, -21), (7, -17), (0, -26), (-2, -25), (2, -25))):
        c.dot(cx + dx, cy + dy, "cyan" if i % 2 else "white")
    for dx in (-8, -6, 6, 8):
        c.dot(cx + dx, cy - 9, "white")
    c.dot(cx - 9, cy - 6, "cyan"); c.dot(cx + 9, cy - 6, "cyan")
    return c, (cx, cy)


DECOR_ART += [("picket", decor_picket), ("path_stone", lambda: decor_path(True)), ("path_brick", lambda: decor_path(False)),
              ("haystack", decor_haystack), ("sunflowers", decor_sunflowers), ("blossom", decor_blossom),
              ("pumpkins", decor_pumpkins), ("snowman", decor_snowman), ("vane", decor_vane), ("fountain", decor_fountain)]
FLAT_DECOR = {"path_stone", "path_brick"}


def preview_decor(out):
    from PIL import Image
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    import polish
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app", "src", "main", "assets", "art")
    hand = Image.open(os.path.join(art, "people/hana_se_idle.png")).convert("RGBA")
    cw = 40
    sheet = Image.new("RGBA", (cw * (len(DECOR_ART) + 1), 64), (62, 137, 72, 255))
    sheet.alpha_composite(hand, (8, 52 - hand.height))
    for i, (name, fn) in enumerate(DECOR_ART):
        cnv, (px_, py_) = fn()
        x0, y0, x1, y1 = cnv.im.getbbox()
        body, pad = finish(cnv.im.crop((x0, y0, x1, y1)))
        body = polish.polish(body, "world", "decor/" + name)
        ax, ay = (i + 1) * cw + 20, 56
        sheet.alpha_composite(body, (ax - (px_ - x0 + pad), ay - (py_ - y0 + pad)))
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(out)


# ---------------------------------------------------------------- giant crops (round 6)

def giant(kind):
    """A giant crop filling a 3x3 patch: a big shaded body (ribbed pumpkin, striped melon or a
    leafy cabbage), with vines and leaves at its foot. Pivot = the patch's centre on the ground."""
    import math
    c = Iso(80, 64, 40, 52)
    cx, cy = 40, 52
    R, H = 30, 34
    body = {"pumpkin": ("amber", "orange", "rust", "brown"), "melon": ("green", "green_d", "green_dd", "teal_dd"),
            "cabbage": ("green", "green", "green_d", "green_dd")}[kind]
    # Leaves and vines on the ground first.
    for a in range(0, 360, 45):
        x = round(cx + (R + 4) * math.cos(math.radians(a)))
        y = round(cy - 4 + (R / 2.6) * math.sin(math.radians(a)))
        c.ellipse(x, y, 5, 2, "green_d")
        c.dot(x - 1, y - 1, "green")
    # The body: a squashed ball, shaded in flat bands from the light (top left).
    top = cy - H
    for y in range(top, cy + 1):
        t = (y - (top + cy) / 2) / (H / 2)
        half = R * max(0.0, 1 - t * t) ** 0.5
        for x in range(round(cx - half), round(cx + half) + 1):
            dx = (x - cx) / R
            lit = dx * 0.8 + t * 0.6
            col = body[0] if lit < -0.45 else body[1] if lit < 0.15 else body[2] if lit < 0.6 else body[3]
            c.dot(x, y, col)
    if kind == "pumpkin":
        for k in (-2, -1, 1, 2):                                            # ribs
            for y in range(top + 3, cy - 1):
                t = (y - (top + cy) / 2) / (H / 2)
                half = R * max(0.0, 1 - t * t) ** 0.5
                c.dot(round(cx + k / 3 * half), y, "rust" if k < 0 else "brown")
        for j in range(6):                                                  # stem
            c.dot(cx - 1 + j // 3, top - j + 2, "brown_l"); c.dot(cx + j // 3, top - j + 2, "brown")
        c.dot(cx + 2, top - 3, "green"); c.dot(cx + 3, top - 4, "green_d")
    elif kind == "melon":
        for k in (-3, -2, -1, 0, 1, 2, 3):                                  # stripes
            for y in range(top + 2, cy - 1):
                t = (y - (top + cy) / 2) / (H / 2)
                half = R * max(0.0, 1 - t * t) ** 0.5
                x = round(cx + k / 3.6 * half)
                for w in range(3):
                    c.dot(x + w, y, "teal_dd" if w == 1 else "green_dd")
        c.dot(cx, top + 1, "brown"); c.dot(cx + 1, top, "brown_l")
    else:
        # Leaves wrapped round the head: curved leaf edges and pale veins from the crown.
        for k in (-3, -2, -1, 0, 1, 2, 3):
            for y in range(top + 2, cy - 1):
                t = (y - (top + cy) / 2) / (H / 2)
                half = R * max(0.0, 1 - t * t) ** 0.5
                x = round(cx + k / 3.4 * half + 3 * math.sin(t * 2.2 + k))
                c.dot(x, y, "green_dd" if k % 2 else "yellow")
        for a in range(200, 341, 14):                                       # the outer leaf's frilly rim
            x = round(cx + (R - 1) * math.cos(math.radians(a)))
            y = round(cy - H / 2 + (H / 2) * math.sin(math.radians(a)))
            c.dot(x, y - 1, "green_dd"); c.dot(x, y, "green_d")
    # A highlight on the top left and the shadow line where it meets the soil.
    c.ellipse(cx - 12, top + 9, 4, 2, "white" if kind == "pumpkin" else "yellow")
    for x in range(cx - R + 4, cx + R - 3):
        c.dot(x, cy, "brown_d")
    return c, (cx, cy - 2)


GIANTS = [("giant_pumpkin", lambda: giant("pumpkin")), ("giant_melon", lambda: giant("melon")),
          ("giant_cabbage", lambda: giant("cabbage"))]


def preview_giant(out):
    from PIL import Image
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    import polish
    art = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app", "src", "main", "assets", "art")
    hand = Image.open(os.path.join(art, "people/hana_se_idle.png")).convert("RGBA")
    sheet = Image.new("RGBA", (300, 80), (62, 137, 72, 255))
    sheet.alpha_composite(hand, (6, 70 - hand.height))
    for i, (name, fn) in enumerate(GIANTS):
        cnv, (px_, py_) = fn()
        x0, y0, x1, y1 = cnv.im.getbbox()
        body, pad = finish(cnv.im.crop((x0, y0, x1, y1)))
        body = polish.polish(body, "world", "crops/" + name)
        ax, ay = 70 + i * 88, 70
        sheet.alpha_composite(body, (ax - (px_ - x0 + pad), ay - (py_ - y0 + pad)))
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(out)


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
    for f in (0, 1):
        emit(veh, f"harvester_se_{f}", harvester_se(f))
        emit(veh, f"harvester_ne_{f}", harvester_ne(f))
    deco = Sprites(art, "deco")
    emit(deco, "crate_empty", crate(False))
    emit(deco, "crate_full", crate(True))
    emit(deco, "field_sign", field_sign())
    emit(deco, "lantern", lantern())
    emit(deco, "fence_r", fence(True))
    emit(deco, "fence_l", fence(False))
    for name, fn in MACHINES + MACHINES_2:
        emit(bld, name, fn())
    for key, fn in YARD:
        group, name = key.split("/")
        emit(bld if group == "buildings" else deco, name, fn())
    emit_overlay(bld, "cottage_c_lit", bunkhouse(True), "cottage_c")
    dec = Sprites(art, "decor")
    for name, fn in DECOR_ART:
        if name in FLAT_DECOR:
            cnv, (px, py) = fn()                                            # paths: no outline or shadow
            x0, y0, x1, y1 = cnv.im.getbbox()
            dec.save(name, cnv.im.crop((x0, y0, x1, y1)), px - x0, py - y0)
        else:
            emit(dec, name, fn())
    crops = Sprites(art, "crops")
    for name, fn in GIANTS:
        emit(crops, name, fn())
    for name, fn in (("barn", barn), ("silo", silo), ("orders", orders), ("workbench", workbench), ("well", well), ("market", market)):
        emit(bld, name, fn(False))
        emit(bld, name + "_snow", fn(True))
    pivots = {}
    for sp in (veh, bld, deco, dec, crops):
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
    elif len(sys.argv) > 2 and sys.argv[1] == "--preview2":
        preview2(sys.argv[2])
    elif len(sys.argv) > 2 and sys.argv[1] == "--preview3":
        preview3(sys.argv[2])
    elif len(sys.argv) > 2 and sys.argv[1] == "--preview4":
        preview4(sys.argv[2])
    elif len(sys.argv) > 2 and sys.argv[1] == "--preview5":
        preview5(sys.argv[2])
    elif len(sys.argv) > 2 and sys.argv[1] == "--preview6":
        preview6(sys.argv[2])
    elif len(sys.argv) > 2 and sys.argv[1] == "--preview-decor":
        preview_decor(sys.argv[2])
    elif len(sys.argv) > 2 and sys.argv[1] == "--preview-giant":
        preview_giant(sys.argv[2])
    else:
        main()
