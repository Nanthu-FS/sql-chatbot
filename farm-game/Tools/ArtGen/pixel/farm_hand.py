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
  buildings/barn(_snow)                 the barn, with DITHER FARM painted on its roof
  buildings/silo(_snow)                 the silo
  buildings/orders(_snow)               the orders notice board

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
    ink = "grey" if winter else "cream"                                  # the name shows through the snow
    c.text_u(-U + 0.6, 2.6, R - PK * 2.6 / V + 1, "DITHER", ink, shadow=None if winter else shade)
    c.text_u(-U + 2.6, 5.0, R - PK * 5.0 / V + 1, "FARM", ink, shadow=None if winter else shade)
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
    for name, fn in (("barn", barn), ("silo", silo), ("orders", orders), ("workbench", workbench), ("well", well), ("market", market)):
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
    elif len(sys.argv) > 2 and sys.argv[1] == "--preview2":
        preview2(sys.argv[2])
    else:
        main()
