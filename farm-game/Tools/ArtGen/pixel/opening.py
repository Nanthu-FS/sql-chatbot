"""Round 7 (Rebuild The Village): the new opening's backdrops and the launch title (pixel-direct).

scenes/bedroom          Grandpa Elias's bedroom at dusk, 270x480 like the bank (bottom-aligned): plank walls,
                        the window onto the sunset fields and the old windmill, his flat cap on its peg, the
                        wedding photo, the lamp and his notebook on the bedside table, and the bed's headboard
                        right where his portrait sits
scenes/porch            the farmhouse porch after dark: Grandpa's empty rocking chair with the quilt over it,
                        the lantern, and Willowmere's ruins on the hills under the first stars
scenes/bank_old         the loan office forty years ago (older poster, no clock); the flashbacks tint it sepia
scenes/hill             the hill above Willowmere at sunset (dates, the proposal, the anniversary)
scenes/square           the village square dressed for the wedding
scenes/stamp_refused    the bank's rubber stamp, slammed onto the screen; stamp_approved for the finale
title/sky_0..4          the sky from night to morning, crossfaded while the sun rises
title/sun, cloud_a/b    the rising sun with its glow; drifting clouds
title/mountains, hills_far, hills  the ridges behind the village, far to near: shaded toward the sun,
                        hazy at the foot, snow on the peaks, trees along the near crest
title/rays              the sun's rays (pivot = centre), turned slowly in the code
title/fore              tall grass and flowers in the bottom corners, close to the eye (pivot = bottom left)
title/village           ruined Willowmere on the ridge: broken huts, the windmill without its sails, the
                        collapsed barn, a dead tree
title/ground            the hill in front: tall grass, flowers and the broken WILLOWMERE sign
title/<w>_<i>_new/_old  the title's letters (REBUILD, VILLAGE): fresh nailed planks, and the same letter
                        ruined (grey, cracked, chipped, askew)
title/ribbon            the red ribbon that carries THE

Clean shading lit from the top-left; dithering only for sky and glow gradients. Scenes and title layers
pivot at the top-left; letters at their bottom centre; the sun at its centre.
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
from _pix import E, Sprites, new, bayer  # noqa: E402
import scenes  # noqa: E402

W, H = 270, 480
HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "assets", "fonts", "Silkscreen-Regular.ttf")
FONT_BOLD = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "assets", "fonts", "Silkscreen-Bold.ttf")
DISPLAY = os.path.join(HERE, "..", "fonts", "LilitaOne-Regular.ttf")


def C(c):
    return E[c] if isinstance(c, str) else c


def rect(px, x0, y0, x1, y1, c, w=W, h=H):
    for y in range(max(0, y0), min(h, y1)):
        for x in range(max(0, x0), min(w, x1)):
            px[x, y] = C(c)


def vgrad(px, x0, y0, x1, y1, cols, w=W, h=H):
    """A vertical gradient through [cols], Bayer-dithered between neighbouring steps."""
    n = len(cols) - 1
    for y in range(max(0, y0), min(h, y1)):
        t = (y - y0) / max(1, (y1 - y0 - 1)) * n
        i = min(n - 1, int(t)) if n > 0 else 0
        f = t - i
        for x in range(max(0, x0), min(w, x1)):
            px[x, y] = C(cols[min(n, i + 1)] if n > 0 and bayer(x, y, int(f * 16)) else cols[i])


def poly(im, pts, c):
    ImageDraw.Draw(im).polygon(pts, fill=C(c))


def ellipse(im, box, c, outline=None):
    ImageDraw.Draw(im).ellipse(box, fill=C(c), outline=C(outline) if outline else None)


def line(im, pts, c, width=1):
    ImageDraw.Draw(im).line(pts, fill=C(c), width=width)


def text(im, x, y, s, c, size=8, font=FONT, center=False):
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    f = ImageFont.truetype(font, size)
    if center:
        x -= d.textlength(s, font=f) / 2
    d.text((x, y), s, font=f, fill=C(c))


def shade_rect(px, x0, y0, x1, y1, base, lit, dark, w=W, h=H):
    """A block lit from the top-left: a light top and left edge, a dark bottom and right edge."""
    rect(px, x0, y0, x1, y1, base, w, h)
    rect(px, x0, y0, x1, y0 + 1, lit, w, h)
    rect(px, x0, y0, x0 + 1, y1, lit, w, h)
    rect(px, x0, y1 - 1, x1, y1, dark, w, h)
    rect(px, x1 - 1, y0, x1, y1, dark, w, h)


# ---------------------------------------------------------------- the bedroom (dusk)

LAMP = (124, 300)   # the oil lamp's flame, for the code's glow (backdrop coordinates)
WINDOW = (22, 78, 122, 218)


def planks(px, y0, y1, rng):
    """Vertical wall planks, warm in the dusk light, with knots and dark seams."""
    x = 0
    while x < W:
        pw = rng.choice((16, 18, 20))
        base = rng.choice(("brown", "brown", "brown_l"))
        rect(px, x, y0, x + pw, y1, base)
        rect(px, x, y0, x + 1, y1, "brown_d")
        rect(px, x + 1, y0, x + 2, y1, "clay" if base == "brown_l" else "brown_l")
        for _ in range(2):
            ky = rng.randrange(y0 + 10, y1 - 10)
            kx = x + rng.randrange(4, pw - 5)
            rect(px, kx, ky, kx + 3, ky + 1, "brown_d")
            rect(px, kx + 1, ky + 1, kx + 2, ky + 2, "brown_d")
        x += pw


def sunset_view(im, px, x0, y0, x1, y1):
    """The fields at sunset through the window: banded sky, the sun on the hills, the windmill."""
    vgrad(px, x0, y0, x1, y1 - 30, ["purple", "magenta", "pink", "orange", "amber", "yellow"])
    hy = y1 - 34
    ellipse(im, (x0 + 52, hy - 12, x0 + 76, hy + 12), "yellow")
    ellipse(im, (x0 + 57, hy - 7, x0 + 71, hy + 7), "white")
    for x in range(x0, x1):
        far = hy + int(3 * math.sin(x * 0.11) + 2 * math.sin(x * 0.27))
        rect(px, x, far, x + 1, y1, "purple")
        near = hy + 8 + int(4 * math.sin(x * 0.07 + 1.3))
        rect(px, x, near, x + 1, y1, "slate_d")
    # Field rows catching the last light.
    for k in range(4):
        yy = y1 - 22 + k * 5
        for x in range(x0, x1, 2):
            if (x // 2 + k) % 3 == 0:
                px[x, yy] = C("teal_dd")
    # The old windmill, sails still on (it broke later).
    mx, my = x0 + 22, hy + 4
    poly(im, [(mx - 4, my), (mx + 4, my), (mx + 2, my - 22), (mx - 2, my - 22)], "ink")
    for a in (0.3, 1.87, 3.44, 5.01):
        ex, ey = mx + 13 * math.cos(a), my - 22 + 13 * math.sin(a)
        line(im, [(mx, my - 22), (ex, ey)], "ink", 2)
    # A lone tree.
    tx, ty = x0 + 84, hy + 6
    rect(px, tx, ty - 10, tx + 2, ty, "ink")
    ellipse(im, (tx - 6, ty - 20, tx + 8, ty - 8), "ink")
    # Birds heading home.
    for bx, by in ((x0 + 30, y0 + 22), (x0 + 38, y0 + 18), (x0 + 45, y0 + 26)):
        px[bx, by] = C("ink"); px[bx + 1, by + 1] = C("ink"); px[bx + 2, by] = C("ink")


def bedroom():
    rng = random.Random(7)
    im = new(W, H)
    px = im.load()
    planks(px, 0, 330, rng)
    # Ceiling beam and rafters.
    rect(px, 0, 0, W, 16, "brown_d")
    rect(px, 0, 16, W, 18, "ink")
    for x in range(10, W, 64):
        poly(im, [(x, 18), (x + 8, 18), (x + 2, 30), (x - 6, 30)], "brown_d")
    # Window: curtain rod, frame, the sunset, mullions, sill with a geranium.
    x0, y0, x1, y1 = WINDOW
    rect(px, x0 - 6, y0 - 6, x1 + 6, y1 + 6, "cream")
    rect(px, x0 - 6, y1 + 2, x1 + 6, y1 + 6, "tan_d")
    rect(px, x0 - 3, y0 - 3, x1 + 3, y0 - 1, "tan")
    sunset_view(im, px, x0, y0, x1, y1)
    rect(px, (x0 + x1) // 2 - 2, y0, (x0 + x1) // 2 + 2, y1, "cream")
    rect(px, x0, (y0 + y1) // 2 - 2, x1, (y0 + y1) // 2 + 2, "cream")
    rect(px, x0 - 10, y1 + 6, x1 + 10, y1 + 10, "tan")
    rect(px, x0 - 10, y1 + 10, x1 + 10, y1 + 12, "brown_d")
    # The geranium on the sill.
    rect(px, x1 - 26, y1 - 2, x1 - 14, y1 + 6, "rust")
    rect(px, x1 - 26, y1 - 2, x1 - 14, y1, "clay")
    for fx, fy in ((x1 - 24, y1 - 8), (x1 - 18, y1 - 11), (x1 - 15, y1 - 6), (x1 - 21, y1 - 5)):
        rect(px, fx, fy, fx + 3, fy + 3, "red")
        px[fx + 1, fy + 1] = C("pink")
    for gx in range(x1 - 25, x1 - 14, 3):
        rect(px, gx, y1 - 4, gx + 2, y1 - 2, "green_d")
    # Curtains on a rod.
    rect(px, x0 - 18, y0 - 12, x1 + 18, y0 - 9, "brown_d")
    rect(px, x0 - 20, y0 - 13, x0 - 16, y0 - 8, "amber")
    rect(px, x1 + 16, y0 - 13, x1 + 20, y0 - 8, "amber")
    for cx0, cx1 in ((x0 - 16, x0 + 6), (x1 - 6, x1 + 16)):
        for y in range(y0 - 9, y1 + 8):
            # Gathered at the tie-back, wider below it.
            pinch = 5 if abs(y - (y0 + 92)) < 4 else 0
            for x in range(cx0 + (pinch if cx0 < x0 else 0), cx1 - (pinch if cx0 > x0 else 0)):
                fold = (x - cx0) % 6
                px[x, y] = C("blue_d" if fold in (0, 1) else "blue" if fold < 4 else "cyan" if fold == 4 else "blue")
        rect(px, cx0 + 2, y0 + 90, cx1 - 2, y0 + 94, "amber")
    # The wedding photo: Elias and Rose, a long time ago.
    fx0, fy0 = 150, 86
    line(im, [(fx0 + 18, fy0 - 10), (fx0 + 2, fy0), (fx0 + 34, fy0), (fx0 + 18, fy0 - 10)], "brown_d")
    px[fx0 + 18, fy0 - 11] = C("grey_l")
    rect(px, fx0, fy0, fx0 + 36, fy0 + 44, "amber")
    rect(px, fx0 + 1, fy0 + 1, fx0 + 36, fy0 + 2, "yellow")
    rect(px, fx0 + 3, fy0 + 3, fx0 + 33, fy0 + 41, "brown_d")
    rect(px, fx0 + 4, fy0 + 4, fx0 + 32, fy0 + 40, "tan")
    rect(px, fx0 + 4, fy0 + 30, fx0 + 32, fy0 + 40, "tan_d")
    # Groom (dark suit, cap in hand) and bride (white dress, veil, flowers).
    gx, gy = fx0 + 12, fy0 + 38
    rect(px, gx - 3, gy - 14, gx + 3, gy, "slate_d")
    rect(px, gx - 2, gy - 20, gx + 2, gy - 14, "skin")
    rect(px, gx - 2, gy - 21, gx + 2, gy - 19, "brown_d")
    bx, by = fx0 + 22, fy0 + 38
    poly(im, [(bx - 5, by), (bx + 5, by), (bx + 2, by - 13), (bx - 2, by - 13)], "white")
    rect(px, bx - 2, by - 19, bx + 2, by - 13, "skin")
    rect(px, bx - 3, by - 20, bx + 3, by - 18, "amber")
    rect(px, bx - 4, by - 19, bx - 2, by - 8, "grey_l")
    rect(px, bx - 1, by - 10, bx + 2, by - 8, "pink")
    # His cap on its peg, his coat beside it.
    rect(px, 236, 96, 240, 100, "brown_d")
    rect(px, 237, 95, 239, 96, "brown_l")
    ellipse(im, (224, 100, 250, 112), "slate")
    rect(px, 225, 106, 250, 108, "slate_d")
    poly(im, [(244, 108), (254, 110), (252, 113), (242, 112)], "ink")
    rect(px, 230, 102, 240, 104, "grey_d")
    rect(px, 256, 96, 260, 100, "brown_d")
    poly(im, [(252, 100), (264, 100), (268, 160), (248, 160)], "teal_dd")
    poly(im, [(254, 100), (258, 100), (256, 158), (250, 158)], "green_dd")
    # Shelf: books, a seed jar, a wooden toy truck (his first one).
    rect(px, 160, 152, 222, 156, "brown_l")
    rect(px, 160, 156, 222, 158, "brown_d")
    for i, (bc, bh) in enumerate((("red_d", 18), ("blue_d", 16), ("green_d", 19), ("amber", 14), ("purple", 17))):
        bx0 = 162 + i * 5
        rect(px, bx0, 152 - bh, bx0 + 4, 152, bc)
        rect(px, bx0, 152 - bh + 3, bx0 + 4, 152 - bh + 4, "cream")
    rect(px, 192, 138, 202, 152, "cyan")
    rect(px, 193, 139, 201, 151, "blue")
    for sx, sy in ((195, 147), (198, 144), (196, 141), (199, 149)):
        px[sx, sy] = C("tan")
    rect(px, 191, 136, 203, 138, "brown_l")
    rect(px, 206, 144, 220, 150, "red")
    rect(px, 206, 140, 212, 144, "red_d")
    for wx in (208, 217):
        rect(px, wx, 149, wx + 3, 152, "ink")
    # A cross-stitch over the bed: HOME, with little flowers.
    sx0, sy0 = 180, 196
    rect(px, sx0 - 2, sy0 - 2, sx0 + 50, sy0 + 26, "brown_l")
    rect(px, sx0, sy0, sx0 + 48, sy0 + 24, "cream")
    for x in range(sx0 + 2, sx0 + 47, 3):
        px[x, sy0 + 2] = C("red"); px[x, sy0 + 21] = C("green_d")
    text(im, sx0 + 24, sy0 + 7, "HOME", "red_d", center=True)
    # Baseboard and floor.
    rect(px, 0, 326, W, 332, "brown_d")
    rect(px, 0, 326, W, 327, "brown_l")
    for i, y in enumerate(range(332, H, 9)):
        rect(px, 0, y, W, y + 9, "brown_l" if i % 2 else "tan_d")
        rect(px, 0, y + 8, W, y + 9, "brown")
        for x in range((i * 37) % 60, W, 60):
            rect(px, x, y, x + 1, y + 9, "brown")
    # The window's light on the floor, warm.
    for y in range(334, 388):
        k = (y - 334) * 0.9
        for x in range(int(40 + k), int(130 + k)):
            if 0 <= x < W and bayer(x, y, 7):
                px[x, y] = C("amber")
    # Braided rug.
    for i, c in enumerate(("red_d", "cream", "rust", "cream", "red")):
        ellipse(im, (30 + i * 5, 352 + i * 2, 160 - i * 5, 388 - i * 2), c)
    # The bed: carved headboard with posts and knobs (his portrait sits in front of it).
    rect(px, 150, 268, 266, 400, "brown_l")
    for x in range(150, 266):
        top = 268 - int(10 * math.sin((x - 150) / 116 * math.pi))
        rect(px, x, top, x + 1, 268, "brown_l")
        px[x, top] = C("clay")
    rect(px, 160, 284, 256, 400, "brown")
    rect(px, 162, 286, 254, 400, "brown_d")
    rect(px, 164, 288, 252, 400, "brown")
    for x0p in (146, 258):
        rect(px, x0p, 250, x0p + 10, 400, "brown_l")
        rect(px, x0p, 250, x0p + 2, 400, "clay")
        rect(px, x0p + 8, 250, x0p + 10, 400, "brown_d")
        ellipse(im, (x0p - 1, 238, x0p + 11, 250), "brown_l", outline="brown_d")
        px[x0p + 3, 241] = C("tan")
    # Bedside table: drawer, the oil lamp, his notebook, a glass of water.
    shade_rect(px, 108, 318, 148, 392, "brown", "brown_l", "brown_d")
    rect(px, 104, 314, 152, 320, "brown_l")
    rect(px, 104, 314, 152, 315, "tan_d")
    shade_rect(px, 112, 326, 144, 344, "brown_l", "tan_d", "brown_d")
    rect(px, 126, 333, 130, 336, "amber")
    lx, ly = LAMP
    ellipse(im, (lx - 8, 306, lx + 8, 314), "amber", outline="brown_d")
    rect(px, lx - 3, 300, lx + 3, 307, "rust")
    ellipse(im, (lx - 6, 286, lx + 6, 304), "grey_l")
    ellipse(im, (lx - 4, 288, lx + 4, 302), "cream")
    rect(px, lx - 4, 284, lx + 4, 286, "amber")
    ellipse(im, (lx - 2, ly - 6, lx + 2, ly + 2), "yellow")
    px[lx, ly - 2] = C("white")
    poly(im, [(132, 312), (148, 312), (150, 316), (134, 316)], "brown_d")
    rect(px, 134, 310, 148, 312, "rust")
    rect(px, 140, 310, 141, 318, "amber")
    rect(px, 110, 304, 116, 314, "cyan")
    rect(px, 111, 308, 115, 313, "blue")
    return im


# ---------------------------------------------------------------- the porch (night)

LANTERN = (40, 64)


def ruins(im, px, base, col, lit):
    """Willowmere on the ridge after decades of neglect: huts with holed roofs, the chapel's broken
    steeple, the windmill stump; [lit] windows still burning."""
    for hx, hw, hh, broken in ((24, 26, 16, True), (56, 20, 13, False), (172, 24, 15, True), (204, 28, 18, False), (236, 18, 12, True)):
        rect(px, hx, base - hh, hx + hw, base, col)
        poly(im, [(hx - 2, base - hh), (hx + hw + 2, base - hh), (hx + hw // 2, base - hh - 10)], col)
        if broken:
            poly(im, [(hx + hw // 2 + 1, base - hh - 8), (hx + hw - 2, base - hh), (hx + hw // 2 + 2, base - hh)], C("slate"))
    # Chapel with the steeple snapped off.
    rect(px, 102, base - 28, 130, base, col)
    poly(im, [(98, base - 28), (134, base - 28), (116, base - 42)], col)
    rect(px, 111, base - 56, 121, base - 38, col)
    poly(im, [(111, base - 56), (118, base - 64), (121, base - 58), (121, base - 56)], col)
    # Windmill stump, no sails.
    poly(im, [(146, base), (160, base), (156, base - 34), (150, base - 34)], col)
    for wx, wy in lit:
        rect(px, wx, wy, wx + 3, wy + 3, "amber")
        px[wx + 1, wy + 1] = C("yellow")


def porch():
    rng = random.Random(11)
    im = new(W, H)
    px = im.load()
    vgrad(px, 0, 0, W, 300, ["ink", "slate_d", "slate_d", "purple", "magenta", "pink"])
    for _ in range(90):
        sx, sy = rng.randrange(W), rng.randrange(0, 190)
        if (sx - 206) ** 2 + (sy - 58) ** 2 < 400:
            continue
        px[sx, sy] = C(rng.choice(("white", "grey_l", "grey_l", "cyan")))
    # A thin moon.
    ellipse(im, (194, 46, 218, 70), "cream")
    ellipse(im, (199, 42, 223, 66), "slate_d")
    # Hills and the ruins.
    for x in range(W):
        y = 238 + int(6 * math.sin(x * 0.04) + 3 * math.sin(x * 0.13))
        rect(px, x, y, x + 1, 300, "purple")
    ruins(im, px, 262, "slate_d", [(30, 252), (210, 248)])
    for x in range(W):
        y = 258 + int(4 * math.sin(x * 0.06 + 2))
        rect(px, x, y, x + 1, 340, "teal_dd")
    # Field rows down to the farm, and the path.
    for k, y in enumerate(range(268, 330, 6)):
        for x in range(0, W, 3 + k // 3):
            px[x, y] = C("green_dd")
    poly(im, [(126, 266), (144, 266), (176, 340), (96, 340)], "brown_d")
    # Porch: roof beam, posts, railing with balusters, floorboards.
    rect(px, 0, 0, W, 12, "brown_d")
    rect(px, 0, 12, W, 14, "ink")
    for x0p in (0, 258):
        shade_rect(px, x0p, 0, x0p + 12, H, "brown", "brown_l", "brown_d")
    for y in range(340, H, 8):
        rect(px, 0, y, W, y + 8, "brown" if (y // 8) % 2 else "brown_l")
        rect(px, 0, y + 7, W, y + 8, "brown_d")
    rect(px, 0, 336, W, 342, "brown_d")
    rect(px, 12, 296, 258, 302, "brown_l")
    rect(px, 12, 296, 258, 297, "tan_d")
    rect(px, 12, 302, 258, 304, "brown_d")
    for x in range(18, 256, 12):
        shade_rect(px, x, 304, x + 5, 338, "brown", "brown_l", "brown_d")
    # The lantern on its chain (the code draws its glow).
    lx, ly = LANTERN
    for y in range(14, ly - 10, 2):
        px[lx, y] = C("grey")
    rect(px, lx - 6, ly - 12, lx + 6, ly - 9, "ink")
    shade_rect(px, lx - 5, ly - 9, lx + 5, ly + 6, "amber", "yellow", "rust")
    rect(px, lx - 3, ly - 6, lx + 3, ly + 3, "yellow")
    rect(px, lx - 1, ly - 4, lx + 1, ly + 1, "white")
    rect(px, lx - 6, ly + 6, lx + 6, ly + 8, "ink")
    # Grandpa's rocking chair, empty, the quilt over its arm.
    cx = 128
    rect(px, cx, 290, cx + 4, 352, "brown_l")
    rect(px, cx + 30, 290, cx + 34, 352, "brown_l")
    for y in range(294, 322, 6):
        rect(px, cx + 4, y, cx + 30, y + 3, "brown")
    rect(px, cx - 2, 322, cx + 36, 328, "brown_l")
    rect(px, cx - 2, 326, cx + 36, 328, "brown_d")
    for x in range(cx - 8, cx + 44):
        y = 354 - int(5 * math.cos((x - cx - 17) / 26 * math.pi / 2)) if abs(x - cx - 17) < 26 else 354
        rect(px, x, y, x + 1, y + 2, "brown_d")
    rect(px, cx + 2, 328, cx + 5, 352, "brown")
    rect(px, cx + 29, 328, cx + 32, 352, "brown")
    for i, y in enumerate(range(300, 340, 4)):
        for x in range(cx + 18, cx + 38):
            if x - cx - 18 < 12 + (i % 3):
                px[x, y] = C(("red", "amber", "green_d", "pink")[((x // 4) + i) % 4])
                px[x, y + 1] = C(("red_d", "rust", "green_dd", "magenta")[((x // 4) + i) % 4])
    # Boots by the door.
    rect(px, 226, 330, 236, 340, "brown_d")
    rect(px, 238, 332, 248, 340, "brown_d")
    rect(px, 226, 330, 236, 332, "brown")
    return im


# ---------------------------------------------------------------- the bank, forty years ago

def bank_old():
    im = new(W, H)
    px = im.load()
    scenes.wall(px)
    scenes.window(px)
    scenes.plaque(im, px)
    x0, y0, x1, y1 = 158, 74, 252, 196
    rect(px, x0 + 2, y0 + 2, x1 + 2, y1 + 2, "teal_dd")
    rect(px, x0, y0, x1, y1, "brown_l")
    rect(px, x0 + 2, y0 + 2, x1 - 2, y1 - 2, "cream")
    rect(px, x0 + 2, y0 + 2, x1 - 2, y0 + 26, "red_d")
    text(im, (x0 + x1) // 2, y0 + 5, "THRIFT TODAY", "cream", center=True)
    text(im, (x0 + x1) // 2, y0 + 15, "WEALTH TOMORROW", "cream", center=True)
    # A stack of coins and a fat piggy bank.
    for i in range(6):
        rect(px, x0 + 14, y1 - 34 - i * 6, x0 + 38, y1 - 30 - i * 6, "amber")
        rect(px, x0 + 14, y1 - 30 - i * 6, x0 + 38, y1 - 28 - i * 6, "rust")
    ellipse(im, (x0 + 46, y1 - 64, x0 + 84, y1 - 34), "pink", outline="magenta")
    rect(px, x0 + 52, y1 - 38, x0 + 56, y1 - 30, "magenta")
    rect(px, x0 + 74, y1 - 38, x0 + 78, y1 - 30, "magenta")
    rect(px, x0 + 60, y1 - 64, x0 + 70, y1 - 62, "ink")
    px[x0 + 76, y1 - 54] = C("ink")
    text(im, (x0 + x1) // 2, y1 - 20, "GOLDCREST", "brown", center=True)
    text(im, (x0 + x1) // 2, y1 - 11, "SINCE 1921", "brown", center=True)
    # A calendar where the clock is now.
    rect(px, 212, 34, 240, 64, "cream")
    rect(px, 212, 34, 240, 42, "red_d")
    for k in range(12):
        rect(px, 215 + (k % 4) * 6, 45 + (k // 4) * 6, 219 + (k % 4) * 6, 49 + (k // 4) * 6, "grey_d")
    return im


# ---------------------------------------------------------------- the stamps

def stamp(word, ink, light, seed):
    rng = random.Random(seed)
    f = ImageFont.truetype(DISPLAY, 30)
    probe = ImageDraw.Draw(Image.new("L", (1, 1)))
    tw = int(probe.textlength(word, font=f))
    w, h = tw + 28, 50
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    d.rounded_rectangle((1, 1, w - 2, h - 2), radius=6, outline=C(ink), width=3)
    d.rounded_rectangle((6, 6, w - 7, h - 7), radius=3, outline=C(ink), width=1)
    d.text((14, 6), word, font=f, fill=C(ink))
    px = im.load()
    # Uneven ink: specks of the paper show through, the inked edges a touch lighter.
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                continue
            r = rng.random()
            if r < 0.09:
                px[x, y] = (0, 0, 0, 0)
            elif r < 0.2:
                px[x, y] = C(light)
    out = im.rotate(-9, resample=Image.NEAREST, expand=True)
    return out


# ---------------------------------------------------------------- the title

SKIES = [
    ["ink", "ink", "slate_d", "slate_d", "slate"],                     # night
    ["ink", "slate_d", "slate", "purple", "magenta"],                  # first light
    ["slate_d", "purple", "magenta", "pink", "orange"],                # dawn
    ["purple", "magenta", "pink", "orange", "amber", "yellow"],         # sunrise
    ["blue_d", "blue", "blue", "cyan", "cream"],                      # morning
]


def sky(i):
    im = new(W, 300)
    px = im.load()
    vgrad(px, 0, 0, W, 300, SKIES[i], h=300)
    if i <= 1:
        rng = random.Random(3)
        for _ in range(70 - i * 30):
            sx, sy = rng.randrange(W), rng.randrange(0, 200)
            px[sx, sy] = C(rng.choice(("white", "grey_l", "grey", "cyan")))
    return im


def sun():
    s = 96
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    px = im.load()
    c = s / 2
    for y in range(s):
        for x in range(s):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c)
            if d < 12:
                px[x, y] = C("white") if d < 7 else C("yellow")
            elif d < 16:
                px[x, y] = C("yellow") if bayer(x, y, int((16 - d) / 4 * 16)) else C("amber")
            elif d < 46:
                # Halo: sparse amber dots thinning out.
                if bayer(x, y, int(max(0.0, (46 - d) / 30) ** 1.6 * 9)):
                    px[x, y] = C("amber")[:3] + (160,) if d < 30 else C("yellow")[:3] + (110,)
    return im


def cloud(seed, w=58, h=18):
    rng = random.Random(seed)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    puffs = [(rng.randrange(8, w - 8), rng.randrange(6, h - 4), rng.randrange(5, 9)) for _ in range(7)]
    for cx, cy, r in puffs:
        ellipse(im, (cx - r, cy - r, cx + r, cy + r), "cream")
    px = im.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] and (y + 1 >= h or px[x, y + 1][3] == 0 or y > h - 6):
                px[x, y] = C("grey_l")
    for cx, cy, r in puffs:
        px[max(0, cx - r // 2), max(0, cy - r + 1)] = C("white")
    return im


SUN_X = 192   # where the sun rises (the light comes from up there, slightly right)


def ridge(x, kind):
    if kind == "mountains":
        # Peaks: tents of different heights, with a little roughness on the slopes.
        peaks = ((18, 30, 0.55), (70, 16, 0.75), (128, 26, 0.6), (190, 10, 0.8), (246, 22, 0.65), (300, 30, 0.6))
        y = min(60 - max(0.0, (60 - top) - abs(x - px_) * k) for (px_, top, k) in peaks)
        return int(y) + (1 if (x * 7) % 5 == 0 else 0)
    if kind == "far":
        return 26 + int(10 * math.sin(x * 0.025 + 0.6) + 6 * math.sin(x * 0.07))
    return 20 + int(8 * math.sin(x * 0.03 + 2.1) + 4 * math.sin(x * 0.11))


def hills(kind):
    """A ridge layer, shaded so it reads as land and not a cut-out: the slopes facing the sun lit with
    a bright rim, the slopes turned away in shadow, and the colour fading into haze at the foot
    (where the mist sits between the layers). Mountains get snow on their peaks; the near hills a
    line of round trees along the crest."""
    h = {"mountains": 90, "far": 80, "near": 90}[kind]
    ramp = {"mountains": ("slate", "grey_d", "grey", "grey_l"),
            "far": ("slate_d", "purple", "magenta", "pink"),
            "near": ("green_dd", "teal_dd", "green_d", "green")}[kind]
    haze = {"mountains": "grey_l", "far": "pink", "near": "green_d"}[kind]
    im = Image.new("RGBA", (W, h), (0, 0, 0, 0))
    px = im.load()
    tops = [ridge(x, kind) for x in range(W)]
    for x in range(W):
        top = tops[x]
        slope = tops[min(W - 1, x + 1)] - tops[max(0, x - 1)]
        toward = (SUN_X - x) * slope < 0 or abs(slope) < 1       # this slope faces the sun
        for y in range(top, h):
            k = y - top
            if k < 2:
                c = ramp[3] if toward else ramp[2]
            elif k < 7:
                c = ramp[2] if toward and bayer(x, y, 10) else ramp[1]
            else:
                c = ramp[1] if bayer(x, y, max(0, 9 - (k - 7) // 2)) else ramp[0]
            # The haze at the foot of the layer.
            f = (y - (h - 26)) / 26
            if f > 0 and bayer(x, y, int(f * 13)):
                c = haze
            px[x, y] = C(c)
        if kind == "mountains" and top < 30:
            for y in range(top, min(h, top + (30 - top) // 2 + 2)):
                px[x, y] = C("white" if toward else "grey_l")
    if kind == "near":
        rng = random.Random(8)
        x = 2
        while x < W:
            r = rng.randrange(4, 8)
            if rng.random() < 0.55:
                cy = tops[min(W - 1, x)] + 1
                for yy in range(cy - r * 2, cy + 2):
                    for xx in range(x - r, x + r + 1):
                        if 0 <= xx < W and 0 <= yy < h and ((xx - x) / r) ** 2 + ((yy - (cy - r)) / (r * 1.1)) ** 2 <= 1:
                            lit = (xx - x) * (1 if SUN_X > x else -1) + (cy - r - yy) * 0.8
                            c = "green" if lit > r * 0.5 else "green_d" if lit > -r * 0.3 else "green_dd"
                            px[xx, yy] = C(c)
                for yy in range(cy - 1, cy + 3):
                    if 0 <= yy < h:
                        px[x, yy] = C("brown_d")
            x += r * 2 + rng.randrange(0, 6)
    return im


def rays():
    """Sun rays: soft wedges round a centre, drawn rotating slowly behind the hills."""
    s = 300
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    px = im.load()
    c = s / 2
    for y in range(s):
        for x in range(s):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c)
            if d < 20 or d > c:
                continue
            a = math.atan2(y - c, x - c)
            w = math.cos(a * 9) * 0.5 + 0.5
            if w < 0.72:
                continue
            fade = (1 - d / c) * (w - 0.72) / 0.28
            if bayer(x, y, int(fade * 16)):
                px[x, y] = C("cream")[:3] + (int(70 * fade) + 30,)
    return im


def fore():
    """Tall grass and flower heads in the bottom corners, close to the eye, framing the view."""
    w, h = W, 120
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    rng = random.Random(21)
    for side in (0, 1):
        for _ in range(110):
            bx = rng.randrange(0, 80)
            bx = bx if side == 0 else W - 1 - bx
            height = int(rng.randrange(40, 118) * (1 - abs(bx - (0 if side == 0 else W)) / 100))
            if height < 12:
                continue
            lean = rng.uniform(-0.35, 0.35) + (0.25 if side == 0 else -0.25)
            for k in range(height):
                x = int(bx + lean * k * k / height)
                y = h - 1 - k
                width = 3 if k < height * 0.4 else 2 if k < height * 0.75 else 1
                for dx in range(width):
                    if 0 <= x + dx < w:
                        px[x + dx, y] = C("ink" if dx == 0 and k < height * 0.3 else "green_dd" if k < height * 0.7 else "teal_dd")
        for _ in range(6):
            fx = rng.randrange(8, 60)
            fx = fx if side == 0 else W - 1 - fx
            fy = rng.randrange(h - 80, h - 40)
            for yy in range(fy, h):
                px[fx, yy] = C("green_dd")
            col = rng.choice((("red_d", "red"), ("purple", "magenta"), ("rust", "amber")))
            for dx in range(-3, 4):
                for dy in range(-3, 4):
                    if dx * dx + dy * dy <= 9 and 0 <= fx + dx < w:
                        px[fx + dx, fy + dy] = C(col[1] if dx + dy < 0 else col[0])
            px[fx, fy] = C("yellow")
        # A solid dark base where the blades crowd together.
        for x in range(0, 70):
            xx = x if side == 0 else W - 1 - x
            for y in range(h - int(26 * (1 - x / 70)), h):
                px[xx, y] = C("ink" if y > h - 6 else "green_dd")
    return im


def village():
    """Ruined Willowmere in daylight colours (the code darkens it until the sun is up)."""
    w, h = W, 110
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    base = 96

    def hut(x, hw, hh, roof, hole):
        shade_rect(px, x, base - hh, x + hw, base, "brown_l", "tan_d", "brown")
        for k in range(x + 3, x + hw - 2, 4):
            rect(px, k, base - hh + 2, k + 1, base, "brown")
        poly(im, [(x - 3, base - hh), (x + hw + 3, base - hh), (x + hw // 2, base - hh - 12)], roof)
        line(im, [(x - 3, base - hh), (x + hw // 2, base - hh - 12), (x + hw + 3, base - hh)], "ink")
        if hole:
            poly(im, [(x + hw // 2 + 2, base - hh - 8), (x + hw - 1, base - hh - 1), (x + hw // 2 + 4, base - hh - 1)], "ink")
            rect(px, x + hw // 2 + 4, base - hh - 5, x + hw // 2 + 5, base - hh, "brown")
        rect(px, x + hw // 2 - 3, base - 9, x + hw // 2 + 3, base, "brown_d")
        rect(px, x + 3, base - hh + 4, x + 7, base - hh + 8, "ink")
        line(im, [(x + 3, base - hh + 4), (x + 7, base - hh + 8)], "brown")

    hut(8, 30, 20, "grey_d", True)
    hut(46, 22, 15, "slate", False)
    hut(196, 26, 18, "grey_d", True)
    hut(232, 22, 14, "slate", True)
    # The collapsed barn: red walls, the roof fallen in.
    shade_rect(px, 78, base - 26, 120, base, "red_d", "red", "purple")
    poly(im, [(74, base - 26), (100, base - 40), (104, base - 30), (90, base - 18), (74, base - 22)], "slate_d")
    poly(im, [(104, base - 30), (124, base - 26), (124, base - 22), (106, base - 24)], "grey_d")
    rect(px, 92, base - 14, 104, base, "ink")
    line(im, [(92, base - 14), (104, base)], "red_d")
    line(im, [(104, base - 14), (92, base)], "red_d")
    # The windmill without its sails, one stuck arm.
    poly(im, [(140, base), (158, base), (154, base - 50), (144, base - 50)], "cream")
    poly(im, [(140, base), (146, base), (146, base - 50), (144, base - 50)], "tan")
    poly(im, [(142, base - 50), (156, base - 50), (149, base - 60)], "red_d")
    rect(px, 147, base - 52, 151, base - 48, "brown_d")
    line(im, [(149, base - 50), (166, base - 72)], "brown_d", 2)
    rect(px, 160, base - 72, 170, base - 64, "tan_d")
    rect(px, 146, base - 16, 152, base, "brown_d")
    # Dead tree and a leaning fence.
    rect(px, 176, base - 30, 179, base, "brown_d")
    line(im, [(178, base - 22), (186, base - 32)], "brown_d")
    line(im, [(177, base - 16), (170, base - 26)], "brown_d")
    line(im, [(178, base - 30), (182, base - 38)], "brown_d")
    for k, fx in enumerate(range(124, 140, 5)):
        line(im, [(fx, base), (fx + 2 + k, base - 9)], "brown")
    line(im, [(124, base - 6), (140, base - 4)], "brown_l")
    # Ground strip under the village.
    for x in range(w):
        for y in range(base, h):
            px[x, y] = C("green_d" if y == base else "green_dd")
    return im


def ground():
    """The hill in front: tall grass, wildflowers and the broken WILLOWMERE sign."""
    w, h = W, 150
    rng = random.Random(5)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for x in range(w):
        top = 30 + int(12 * math.sin(x * 0.018 + 0.4) + 3 * math.sin(x * 0.09))
        for y in range(top, h):
            k = y - top
            px[x, y] = C("green" if k < 2 else "green_d" if k < 10 or bayer(x, y, 6) else "green_dd")
    # Perspective: the grass tufts and flowers grow bigger toward the viewer.
    for _ in range(260):
        gx = rng.randrange(w)
        top = 30 + int(12 * math.sin(gx * 0.018 + 0.4) + 3 * math.sin(gx * 0.09))
        gy = rng.randrange(top + 2, h)
        near = (gy - top) / (h - top)
        size = 2 + int(near * 9)
        for blade in (-1, 0, 1) if near > 0.4 else (0,):
            for k in range(size - abs(blade) * 2):
                x = gx + blade * (1 + k // 3)
                if gy - k >= 0 and 0 <= x < w:
                    px[x, gy - k] = C("green" if k > size // 3 else "green_d")
            if near > 0.6:
                px[gx, gy] = C("green_dd")
    for _ in range(60):
        fx = rng.randrange(4, w - 4)
        top = 30 + int(12 * math.sin(fx * 0.018 + 0.4) + 3 * math.sin(fx * 0.09))
        fy = rng.randrange(top + 6, h - 4)
        near = (fy - top) / (h - top)
        c = rng.choice((("pink", "magenta"), ("yellow", "amber"), ("white", "grey_l"), ("cyan", "blue"), ("red", "red_d")))
        r = 1 if near < 0.45 else 2
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if abs(dx) + abs(dy) <= r and 0 <= fx + dx < w and fy + dy < h:
                    px[fx + dx, fy + dy] = C(c[0] if dx + dy < 0 else c[1])
        px[fx, fy] = C("amber")
    # The sign: one post, the board hanging by a single nail.
    sx = 30
    shade_rect(px, sx, 2, sx + 5, 70, "brown_l", "tan_d", "brown_d")
    board = Image.new("RGBA", (72, 18), (0, 0, 0, 0))
    bp = board.load()
    shade_rect(bp, 0, 0, 72, 18, "tan_d", "tan", "brown", w=72, h=18)
    for x in range(0, 72, 9):
        rect(bp, x, 0, x + 1, 18, "brown_l", w=72, h=18)
    text(board, 36, 5, "WILLOWMERE", "brown_d", center=True)
    bp[3, 3] = C("grey_l")
    board = board.rotate(-18, resample=Image.NEAREST, expand=True)
    im.alpha_composite(board, (sx - 2, 10))
    rect(px, sx + 1, 12, sx + 4, 15, "grey_l")
    return im


DEPTH = 5


def letter(ch, ruined, seed):
    """One title letter: nailed fresh planks, or the same shape grey, chipped, cracked and askew."""
    f = ImageFont.truetype(DISPLAY, 38)
    probe = ImageDraw.Draw(Image.new("L", (1, 1)))
    l, t, r, b = probe.textbbox((0, 0), ch, font=f)
    gw, gh = r - l, b - t
    mask = Image.new("L", (gw, gh), 0)
    d = ImageDraw.Draw(mask)
    d.fontmode = "1"
    d.text((-l, -t), ch, font=f, fill=255)
    m = mask.load()
    on = {(x, y) for y in range(gh) for x in range(gw) if m[x, y]}
    rng = random.Random(seed)
    if ruined:
        # Chips bitten out of the edges and a crack through.
        for _ in range(3):
            ex, ey = rng.choice(sorted(on))
            rad = rng.randrange(2, 4)
            on -= {(x, y) for (x, y) in on if (x - ex) ** 2 + (y - ey) ** 2 <= rad * rad}
        cx = rng.randrange(gw // 3, gw * 2 // 3 + 1)
        for y in range(gh):
            on.discard((cx + (y // 4) % 2 - y // 9, y))
    pad = 4
    w, h = gw + pad * 2, gh + pad * 2
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    ramp = ("slate_d", "slate", "grey_d", "grey") if ruined else ("brown", "brown_l", "tan_d", "tan")
    plank = 7
    for (x, y) in on:
        band = y // plank
        yy = y - band * plank
        up = (x, y - 1) not in on
        left = (x - 1, y) not in on
        down = (x, y + 1) not in on
        right = (x + 1, y) not in on
        if up or yy == 0:
            c = ramp[3]
        elif down or yy == plank - 1:
            c = ramp[0]
        elif left:
            c = ramp[2]
        elif right:
            c = ramp[1]
        else:
            c = ramp[2] if (x + band * 3) % 11 else ramp[1]
        px[x + pad, y + pad] = C(c)
    if not ruined:
        # Grain dashes and a nail at each end of every plank.
        for band in range(gh // plank + 1):
            row = [x for (x, y) in on if y == band * plank + 3]
            if not row:
                continue
            for x in (min(row) + 1, max(row) - 1):
                if (x, band * plank + 3) in on:
                    px[x + pad, band * plank + 3 + pad] = C("grey_l")
                    if (x, band * plank + 4) in on:
                        px[x + pad, band * plank + 4 + pad] = C("grey_d")
            for _ in range(2):
                gx = rng.choice(row)
                for k in range(3):
                    if (gx + k, band * plank + 2) in on:
                        px[gx + k + pad, band * plank + 2 + pad] = C("brown_l")
    else:
        for _ in range(len(on) // 40):
            mx, my = rng.choice(sorted(on))
            px[mx + pad, my + pad] = C("green_dd")
            if (mx + 1, my) in on:
                px[mx + 1 + pad, my + pad] = C("green_d")
    # The depth: the letter's side, extruded down and to the right (dark wood or dark slate, a
    # little lighter along its top edge), so the boards stand out of the screen.
    body = {(x + pad, y + pad) for (x, y) in on}
    side = set()
    for d in range(1, DEPTH + 1):
        for (x, y) in body:
            q = (x + d // 2, y + d)
            if q not in body:
                side.add(q)
    w, h = w + DEPTH, h + DEPTH
    big = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    big.alpha_composite(im, (0, 0))
    im = big
    px = im.load()
    sides = ("ink", "slate_d") if ruined else ("brown_d", "brown")
    for (x, y) in side:
        top_edge = (x, y - 1) in body
        px[x, y] = C(sides[1] if top_edge or (x + y) % 5 == 0 else sides[0])
    body = body | side
    ring1 = {(x + dx, y + dy) for (x, y) in body for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))} - body
    for (x, y) in ring1:
        if 0 <= x < w and 0 <= y < h:
            px[x, y] = C("brown_d" if not ruined else "ink")
    inner = body | ring1
    ring2 = {(x + dx, y + dy) for (x, y) in inner for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))} - inner
    for (x, y) in ring2:
        if 0 <= x < w and 0 <= y < h:
            px[x, y] = C("ink")
    shadow = Image.new("RGBA", (w + 5, h + 6), (0, 0, 0, 0))
    sp = shadow.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3]:
                sp[x + 3, y + 5] = C("ink")[:3] + (110,)
    shadow.alpha_composite(im, (0, 0))
    if ruined:
        shadow = shadow.rotate(rng.choice((-7, -5, 4, 6, 8)), resample=Image.NEAREST, expand=True)
    return shadow


def ribbon():
    w, h = 70, 20
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    # Tails (folded, darker) then the band.
    poly(im, [(0, 5), (14, 5), (14, 17), (0, 17), (5, 11)], "red_d")
    poly(im, [(w - 1, 5), (w - 15, 5), (w - 15, 17), (w - 1, 17), (w - 6, 11)], "red_d")
    poly(im, [(10, 15), (14, 15), (14, 19)], "purple")
    poly(im, [(w - 11, 15), (w - 15, 15), (w - 15, 19)], "purple")
    rect(px, 10, 1, w - 10, 16, "red", w=w, h=h)
    rect(px, 10, 1, w - 10, 2, "pink", w=w, h=h)
    rect(px, 10, 14, w - 10, 16, "red_d", w=w, h=h)
    text(im, w // 2, 3, "THE", "cream", size=8, font=FONT_BOLD, center=True)
    out = Image.new("RGBA", (w + 2, h + 2), (0, 0, 0, 0))
    o = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3]:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + dx + 1 < w + 2 and 0 <= y + dy + 1 < h + 2:
                        o[x + dx + 1, y + dy + 1] = C("ink")
    out.alpha_composite(im, (1, 1))
    return out


# ---------------------------------------------------------------- Grandpa's letters

def _outline(im, c="ink"):
    """A 1px outline round everything opaque (in place, on a padded copy)."""
    w, h = im.size
    out = Image.new("RGBA", (w + 2, h + 2), (0, 0, 0, 0))
    src = im.load()
    o = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + dx + 1 < w + 2 and 0 <= y + dy + 1 < h + 2:
                        o[x + dx + 1, y + dy + 1] = C(c)
    out.alpha_composite(im, (1, 1))
    return out


def seal(im, cx, cy, r):
    ellipse(im, (cx - r, cy - r, cx + r, cy + r), "red_d")
    ellipse(im, (cx - r + 1, cy - r + 1, cx + r - 2, cy + r - 2), "red")
    px = im.load()
    px[cx - r // 2, cy - r // 2] = C("pink")
    # An E pressed into the wax.
    for yy in range(cy - 2, cy + 3):
        px[cx - 1, yy] = C("red_d")
    for xx in range(cx - 1, cx + 2):
        px[xx, cy - 2] = C("red_d"); px[xx, cy] = C("red_d"); px[xx, cy + 2] = C("red_d")


def envelope(w, h, open_=False):
    flap = h * 2 // 3
    top = flap if open_ else 0
    im = Image.new("RGBA", (w, h + top), (0, 0, 0, 0))
    px = im.load()
    # Body, with the side folds a shade darker.
    rect(px, 0, top, w, top + h, "cream", w=w, h=h + top)
    poly(im, [(0, top + h - 1), (w // 2, top + h // 2), (w - 1, top + h - 1)], "tan")
    poly(im, [(0, top), (w // 2 - 2, top + h // 2 + 2), (0, top + h - 1)], "tan")
    poly(im, [(w - 1, top), (w // 2 + 2, top + h // 2 + 2), (w - 1, top + h - 1)], "tan_d")
    if open_:
        # The flap folded up, its inside darker; a letter peeking out; the seal snapped in two.
        rect(px, 3, top - 4, w - 3, top + 6, "white", w=w, h=h + top)
        for yy in range(top - 2, top + 5, 2):
            rect(px, 6, yy, w - 6, yy + 1, "grey_l", w=w, h=h + top)
        poly(im, [(0, top), (w // 2, top - flap), (w - 1, top)], "tan_d")
        poly(im, [(3, top), (w // 2, top - flap + 4), (w - 4, top)], "tan")
        seal(im, w // 2 - 4, top - 6, 4)
        seal(im, w // 2 + 4, top + 2, 4)
    else:
        poly(im, [(0, 0), (w // 2, flap), (w - 1, 0)], "tan_d")
        poly(im, [(2, 0), (w // 2, flap - 3), (w - 3, 0)], "cream")
        line(im, [(0, 0), (w // 2, flap), (w - 1, 0)], "brown_l")
        seal(im, w // 2, flap - 1, max(4, w // 10))
    return _outline(im)


def letter_paper():
    w, h = 236, 300
    rng = random.Random(21)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        for x in range(w):
            # Deckled edges: a little bitten in here and there.
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge < 2 and rng.random() < 0.35:
                continue
            c = "cream"
            if edge < 5 and bayer(x, y, 6):
                c = "tan"
            px[x, y] = C(c)
    # Age spots and a tea ring.
    for _ in range(14):
        sx, sy = rng.randrange(10, w - 10), rng.randrange(10, h - 10)
        for k in range(rng.randrange(3, 9)):
            x, y = sx + rng.randrange(-3, 4), sy + rng.randrange(-2, 3)
            if px[x, y][3]:
                px[x, y] = C("tan")
    for a in range(0, 360, 4):
        x = int(196 + 16 * math.cos(math.radians(a))); y = int(262 + 12 * math.sin(math.radians(a)))
        if bayer(x, y, 9):
            px[x, y] = C("tan_d")
    # Faint ruled lines.
    for y in range(40, h - 26, 13):
        for x in range(14, w - 14):
            if (x // 2) % 2 == 0:
                px[x, y] = C("tan")
    # A pressed forget-me-not in the corner.
    for dx, dy in ((0, -2), (-2, 0), (2, 0), (0, 2), (-1, -1), (1, 1), (1, -1), (-1, 1)):
        px[w - 22 + dx, 16 + dy] = C("blue")
    px[w - 22, 16] = C("yellow")
    line(im, [(w - 22, 18), (w - 30, 30)], "green_d")
    px[w - 27, 25] = C("green")
    return _outline(im, "brown_l")


# ---------------------------------------------------------------- romance backdrops (round 7, Part 3)

def tint(px, x, y, col, a):
    """Blends colour [col] over pixel (x, y) by [a] (cast shadows, glows, haze)."""
    if not (0 <= x < W and 0 <= y < H):
        return
    r, g, b, al = px[x, y]
    c = C(col)
    px[x, y] = (int(r + (c[0] - r) * a), int(g + (c[1] - g) * a), int(b + (c[2] - b) * a), al or 255)


def shadow_poly(im, pts, col="ink", a=0.38):
    """A cast shadow: the polygon's pixels darkened (not painted over)."""
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    m = mask.load()
    px = im.load()
    x0, x1 = max(0, int(min(p[0] for p in pts))), min(W, int(max(p[0] for p in pts)) + 1)
    y0, y1 = max(0, int(min(p[1] for p in pts))), min(H, int(max(p[1] for p in pts)) + 1)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if m[x, y]:
                tint(px, x, y, col, a)


def blob(px, cx, cy, rx, ry, ramp, sun=(1.0, -0.4), rim=None):
    """A rounded form shaded toward the [sun] direction: dark core, mid, light, an optional rim."""
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            nx, ny = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = nx * nx + ny * ny
            if d > 1 or not (0 <= x < W and 0 <= y < H):
                continue
            # A sphere: its normal against a light from [sun], tilted toward the viewer.
            nz = math.sqrt(max(0.0, 1 - d))
            ln = math.sqrt(sun[0] ** 2 + sun[1] ** 2 + 0.5)
            lit = (nx * sun[0] + ny * sun[1] + nz * 0.7) / ln
            v = (lit + 0.2) / 1.1 * (len(ramp) - 1)
            k = max(0, min(len(ramp) - 1, int(v)))
            frac = v - int(v)
            if rim and d > 0.8 and (nx * sun[0] + ny * sun[1]) > 0.3:
                px[x, y] = C(rim)
            elif k + 1 < len(ramp) and bayer(x, y, int(frac * 16)):
                px[x, y] = C(ramp[k + 1])
            else:
                px[x, y] = C(ramp[k])


def hill():
    """The hill above Willowmere at sunset, lit from the low sun behind and to the right: the old
    oak's canopy rimmed in gold, long shadows reaching toward the viewer, the far ridge melting into
    haze, the village's windows lighting up, a checked blanket and a basket on the near slope."""
    rng = random.Random(31)
    im = new(W, H)
    px = im.load()
    sx, sy = 176, 232
    vgrad(px, 0, 0, W, 250, ["slate_d", "purple", "magenta", "pink", "orange", "amber", "yellow"])
    # The glow round the sun, wide and soft, and long thin rays fanning up.
    for y in range(0, 300):
        for x in range(W):
            d = math.hypot(x - sx, (y - sy) * 1.6)
            if d < 120:
                tint(px, x, y, "yellow", 0.55 * (1 - d / 120) ** 2)
    for k in range(9):
        a = math.pi + 0.25 + k * 0.33
        for t in range(30, 240):
            x, y = int(sx + math.cos(a) * t), int(sy + math.sin(a) * t * 0.9)
            for w_ in range(-1 - t // 60, 2 + t // 60):
                if 0 <= x + w_ < W and 0 <= y < 240 and bayer(x + w_, y, 3):
                    tint(px, x + w_, y, "cream", 0.18 * (1 - t / 240))
    ellipse(im, (sx - 15, sy - 15, sx + 15, sy + 15), "yellow")
    ellipse(im, (sx - 10, sy - 10, sx + 10, sy + 10), "white")
    # Clouds: dark undersides, edges lit pink and gold toward the sun.
    for cy, cx0, cw in ((92, 18, 96), (126, 150, 112), (166, 36, 74), (60, 168, 70)):
        for x in range(cx0, cx0 + cw):
            t = (x - cx0) / cw
            hgt = int(4 * math.sin(t * math.pi)) + 1
            for y in range(cy - hgt, cy + 2):
                px[x, y] = C("purple" if y > cy - 1 else "magenta")
            px[x, cy - hgt] = C("orange" if x > sx - 60 else "pink")
    # Far ridge in haze, the village's rooftops on it, windows lit, the windmill turning again.
    for x in range(W):
        y = 238 + int(5 * math.sin(x * 0.035 + 1) + 2 * math.sin(x * 0.14))
        for yy in range(y, 300):
            px[x, yy] = C("magenta" if yy - y < 2 else "purple")
            tint(px, x, yy, "pink", max(0.0, 0.35 - (yy - y) * 0.02))
    for i, hx in enumerate(range(6, 262, 21)):
        if 146 < hx < 204:
            continue
        hh = 7 + (i * 5) % 6
        b = 244 + (i % 3)
        wdt = 10 + (i % 2) * 3
        rect(px, hx, b - hh, hx + wdt, b, "purple")
        rect(px, hx + wdt - 3, b - hh, hx + wdt, b, "slate_d")
        poly(im, [(hx - 2, b - hh), (hx + wdt + 2, b - hh), (hx + wdt // 2, b - hh - wdt // 2 - 2)], "slate_d")
        line(im, [(hx + wdt // 2, b - hh - wdt // 2 - 2), (hx + wdt + 2, b - hh)], "magenta")
        rect(px, hx + 2, b - hh + 3, hx + 4, b - hh + 5, "yellow")
        if wdt > 11:
            rect(px, hx + 7, b - hh + 3, hx + 9, b - hh + 5, "amber")
    mx, my = 222, 244
    poly(im, [(mx - 4, my), (mx + 4, my), (mx + 2, my - 22), (mx - 2, my - 22)], "slate_d")
    line(im, [(mx + 2, my - 22), (mx + 4, my)], "magenta")
    for a in (0.6, 2.17, 3.74, 5.31):
        line(im, [(mx, my - 22), (mx + 13 * math.cos(a), my - 22 + 13 * math.sin(a))], "slate_d", 2)
    # A band of mist in the valley.
    for y in range(250, 272):
        for x in range(W):
            tint(px, x, y, "pink", 0.28 * (1 - abs(y - 261) / 11))
    # The fields below: rows that converge toward the far ridge, gold where the light rakes them.
    for x in range(W):
        y = 264 + int(4 * math.sin(x * 0.05 + 2))
        rect(px, x, y, x + 1, 334, "teal_dd")
    for k in range(12):
        fx = -60 + k * 34
        for t in range(0, 70):
            x = int(sx + (fx - sx) * (t / 70) ** 0.8)
            y = 266 + t
            if 0 <= x < W - 1:
                px[x, y] = C("amber" if t < 20 else "rust" if t < 45 else "brown_d")
                px[x + 1, y] = C("green_dd")
    # The near hilltop: lit along its crest, darker away from the sun and toward the viewer.
    crest = [318 - int(18 * math.sin((x + 20) / W * math.pi)) for x in range(W)]
    for x in range(W):
        top = crest[x]
        warm = (x / W) ** 1.4
        for y in range(top, H):
            k = y - top
            if k < 2:
                c = "yellow" if warm > 0.5 else "amber"
            elif k < 4:
                c = "amber" if warm > 0.6 else "green"
            elif k < 26:
                c = "green_d" if bayer(x, y, int(min(1.0, warm * 1.8 + 0.25) * 16)) else "green_dd"
            else:
                c = "green_dd" if not bayer(x, y, max(0, 6 - (k - 26) // 14)) else "green_d"
            px[x, y] = C(c)
        for y in range(top, H):
            tint(px, x, y, "teal_dd", min(0.5, max(0.0, (y - 380) / 200)) * (1 - warm * 0.5))
    # Grass: tufts growing bigger toward the viewer, their tips catching the light.
    for _ in range(420):
        gx, gy = rng.randrange(W), rng.randrange(316, H)
        if gy < crest[gx] + 3:
            continue
        near = (gy - 316) / (H - 316)
        k = 2 + int(near * 9)
        for blade in (-1, 0, 1):
            lean = blade * 0.4
            for j in range(k - abs(blade)):
                x = int(gx + blade + lean * j)
                if 0 <= x < W:
                    px[x, gy - j] = C("amber" if j == k - abs(blade) - 1 and gx > 120 else "green" if j > k // 2 else "green_d")
    for _ in range(40):
        fx, fy = rng.randrange(4, W - 4), rng.randrange(330, H - 4)
        near = (fy - 330) / (H - 330)
        r = 1 + int(near * 2)
        col = rng.choice((("pink", "magenta"), ("yellow", "amber"), ("white", "grey_l"), ("red", "red_d")))
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if abs(dx) + abs(dy) <= r and 0 <= fx + dx < W:
                    px[fx + dx, fy + dy] = C(col[0] if dx + dy >= 0 else col[1])
        px[fx, fy] = C("amber")
    # The oak's long shadow, stretching down-left toward the viewer.
    shadow_poly(im, [(14, 326), (34, 326), (0, 480), (-60, 480)], a=0.45)
    # The oak: trunk lit on its sun side, roots, branches, a canopy of rounded clumps rimmed in gold.
    rect(px, 16, 150, 32, 332, "brown_d")
    rect(px, 26, 150, 32, 332, "brown")
    rect(px, 30, 150, 32, 332, "clay")
    for y in range(160, 330, 9):
        rect(px, 19, y, 23, y + 1, "ink")
    poly(im, [(10, 334), (16, 312), (20, 334)], "brown_d")
    poly(im, [(28, 334), (32, 312), (40, 334)], "brown")
    line(im, [(28, 214), (62, 172)], "brown", 4)
    line(im, [(30, 212), (64, 174)], "clay", 1)
    line(im, [(22, 192), (2, 152)], "brown_d", 3)
    canopy = [(6, 150, 30, 24), (30, 118, 42, 32), (66, 150, 30, 22), (54, 96, 30, 22), (98, 126, 22, 18),
              (0, 104, 26, 24), (22, 80, 28, 20), (84, 98, 18, 14)]
    for (cx, cy, rx, ry) in sorted(canopy, key=lambda c: c[1]):
        blob(px, cx, cy, rx, ry, ["ink", "green_dd", "green_d", "green"], sun=(0.9, 0.0), rim="amber")
    for _ in range(700):
        lx, ly = rng.randrange(0, 126), rng.randrange(56, 182)
        c = px[lx, ly][:3]
        if c == C("green_d")[:3]:
            px[lx, ly] = C("green" if rng.random() < 0.4 else "green_dd")
        elif c == C("green")[:3] and rng.random() < 0.3:
            px[lx, ly] = C("yellow")
    # The blanket in perspective, its far edge lit, a fold and its shadow; the basket and its shadow.
    bx0, by0, bw, bh = 66, 368, 156, 50
    shadow_poly(im, [(bx0 + 4, by0 + bh), (bx0 + bw - 4, by0 + bh), (bx0 + bw - 40, by0 + bh + 14), (bx0 - 20, by0 + bh + 14)], a=0.4)
    for y in range(by0, by0 + bh):
        t = (y - by0) / bh
        inset = int((1 - t) * 16)
        for x in range(bx0 + inset, bx0 + bw - inset):
            u = (x - bx0 - inset) / max(1, bw - 2 * inset)
            cell_u = int(u * 10)
            cell_v = int(t ** 0.85 * 6)
            col = ("red", "red_d") if (cell_u + cell_v) % 2 else ("cream", "tan")
            fold = 0.45 < u < 0.5
            dark = u > 0.5 and t > 0.1 and t < 0.6 and u < 0.62
            px[x, y] = C(col[1] if (fold or dark or y > by0 + bh - 3) else col[0])
            if abs((u * 10) % 1) < 0.08 or abs((t ** 0.85 * 6) % 1) < 0.1:
                px[x, y] = C(col[1])
        if y < by0 + 2:
            for x in range(bx0 + inset, bx0 + bw - inset):
                tint(px, x, y, "yellow", 0.4)
    bx, by = 200, 344
    shadow_poly(im, [(bx, by + 26), (bx + 32, by + 26), (bx + 8, by + 44), (bx - 26, by + 44)], a=0.42)
    for y in range(by, by + 26):
        for x in range(bx, bx + 32):
            weave = ((x - bx) // 3 + (y - by) // 3) % 2
            side = x > bx + 24
            px[x, y] = C(("brown", "brown_d")[weave] if side else ("tan_d", "brown_l")[weave])
    rect(px, bx - 1, by - 2, bx + 33, by + 1, "brown_d")
    rect(px, bx - 1, by - 2, bx + 26, by - 1, "tan")
    line(im, [(bx + 2, by - 2), (bx + 16, by - 18), (bx + 30, by - 2)], "brown_d", 2)
    line(im, [(bx + 17, by - 17), (bx + 29, by - 3)], "clay", 1)
    rect(px, bx + 6, by - 6, bx + 18, by - 1, "red")
    rect(px, bx + 6, by - 6, bx + 18, by - 5, "pink")
    rect(px, bx + 20, by - 10, bx + 23, by - 1, "green_d")
    # Foreground: tall dark grass framing the bottom corners, out of the light.
    for side in (0, 1):
        for _ in range(60):
            gx = rng.randrange(0, 60)
            gx = gx if side == 0 else W - 1 - gx
            ht = int(rng.randrange(20, 70) * (1 - (gx if side == 0 else W - 1 - gx) / 70))
            lean = rng.uniform(-0.3, 0.3)
            for j in range(max(0, ht)):
                x = int(gx + lean * j * j / max(1, ht))
                if 0 <= x < W:
                    px[x, H - 1 - j] = C("ink" if j < ht * 0.5 else "green_dd")
                    if j < ht * 0.3 and 0 <= x + 1 < W:
                        px[x + 1, H - 1 - j] = C("ink")
    return im


def facade(im, px, x0, x1, top, base, wall, roof, timber=False, side=0, side_dir=1):
    """A house front, lit from the top-left: shaded wall, a receding side wall of [side] px, deep-set
    windows with sills and flower boxes, a roof with tile rows lit on one slope."""
    if side:
        sx0, sx1 = (x1, x1 + side) if side_dir > 0 else (x0 - side, x0)
        for x in range(sx0, sx1):
            k = (x - sx0) if side_dir > 0 else (sx1 - 1 - x)
            yt = top - int(k * 0.35)
            rect(px, x, yt, x + 1, base, wall[0])
        poly(im, [(sx0, top), (sx1, top - int(side * 0.35)), ((sx0 + sx1) // 2, top - 22)], "slate_d")
    rect(px, x0, top, x1, base, wall[1])
    rect(px, x0, top, x1, top + 1, wall[2])
    rect(px, x0, top, x0 + 1, base, wall[2])
    rect(px, x1 - 2, top, x1, base, wall[0])
    if timber:
        for xx in range(x0, x1, 12):
            rect(px, xx, top, xx + 2, base, "brown_d")
            rect(px, xx + 2, top, xx + 3, base, "brown")
        rect(px, x0, top + 40, x1, top + 42, "brown_d")
        for xx in range(x0, x1 - 12, 24):
            line(im, [(xx + 2, top + 40), (xx + 12, top + 2)], "brown_d", 2)
    else:
        for yy in range(top + 2, base, 4):
            for xx in range(x0 + (yy // 4 % 2) * 4, x1, 8):
                rect(px, xx, yy, xx + 1, yy + 1, wall[0])
    # Roof: tile rows; the left slope in light, the right in shade; the eave's shadow on the wall.
    mid = (x0 + x1) // 2
    poly(im, [(x0 - 4, top), (mid, top - 26), (mid, top)], roof[1])
    poly(im, [(mid, top - 26), (x1 + 4, top), (mid, top)], roof[0])
    for k in range(1, 6):
        yy = top - 26 + k * 5
        half = int((x1 - x0 + 8) / 2 * k / 5.2)
        rect(px, mid - half, yy, mid + half, yy + 1, roof[0] if k % 2 else "ink")
    rect(px, x0 - 4, top, x1 + 4, top + 2, "ink")
    for x in range(x0, x1):
        for y in range(top + 2, top + 6):
            tint(px, x, y, "ink", 0.3 - (y - top - 2) * 0.07)
    for wx in range(x0 + 8, x1 - 12, 20):
        for wy in (top + 14, top + 56):
            if wy + 20 > base:
                continue
            rect(px, wx - 1, wy - 1, wx + 13, wy + 17, "white")
            rect(px, wx, wy, wx + 12, wy + 16, "blue_d")
            rect(px, wx + 2, wy + 2, wx + 12, wy + 16, "blue")
            rect(px, wx + 2, wy + 2, wx + 6, wy + 5, "cyan")
            rect(px, wx + 5, wy, wx + 7, wy + 16, "white")
            rect(px, wx, wy + 7, wx + 12, wy + 8, "white")
            rect(px, wx - 2, wy + 16, wx + 14, wy + 18, "grey_l")
            rect(px, wx - 2, wy + 18, wx + 14, wy + 20, "brown_d")
            for fx in range(wx - 1, wx + 13, 2):
                px[fx, wy + 15] = C(("pink", "red", "yellow")[(fx // 2) % 3])
                px[fx + 1, wy + 15] = C("green_d")


def square():
    """The village square on the wedding day, in late-morning sun from the top-left: fronts with
    receding side walls and deep windows, the church tower set back in a little haze, cobbles in
    perspective toward the fountain, cast shadows from the houses, the arch, the lamps and the bench,
    bunting and petals."""
    rng = random.Random(41)
    im = new(W, H)
    px = im.load()
    vgrad(px, 0, 0, W, 210, ["blue_d", "blue", "cyan", "cyan", "cream"])
    for cy, cx0, cw in ((40, 18, 64), (72, 168, 74)):
        ellipse(im, (cx0, cy, cx0 + cw, cy + 16), "grey_l")
        ellipse(im, (cx0 - 2, cy - 2, cx0 + cw - 4, cy + 12), "white")
        ellipse(im, (cx0 + 10, cy - 9, cx0 + cw - 16, cy + 8), "white")
    # The tower, set back: lighter, a little blue with distance.
    shade_rect(px, 104, 56, 166, 300, "grey", "grey_l", "grey_d")
    rect(px, 156, 56, 166, 300, "slate")
    poly(im, [(98, 56), (135, 12), (135, 56)], "slate")
    poly(im, [(135, 12), (172, 56), (135, 56)], "slate_d")
    ellipse(im, (123, 80, 147, 104), "grey_d")
    ellipse(im, (124, 81, 146, 103), "cream")
    line(im, [(135, 92), (135, 84)], "ink"); line(im, [(135, 92), (141, 92)], "ink")
    rect(px, 128, 120, 142, 150, "slate_d")
    poly(im, [(128, 120), (135, 112), (142, 120)], "slate_d")
    for y in range(56, 300):
        for x in range(104, 166):
            tint(px, x, y, "cyan", 0.12)
    shade_rect(px, 122, 252, 148, 300, "brown", "brown_l", "brown_d")
    ellipse(im, (122, 240, 148, 264), "brown")
    rect(px, 134, 252, 136, 300, "brown_d")
    # The fronts, left and right, their side walls receding toward the tower.
    facade(im, px, 0, 58, 118, 300, ("red_d", "red", "pink"), ("red_d", "red"), side=14, side_dir=1)
    facade(im, px, 58 + 14, 104, 150, 300, ("brown", "clay", "tan_d"), ("slate_d", "slate"), timber=True)
    facade(im, px, 166, 200, 140, 300, ("tan_d", "cream", "white"), ("red_d", "red"), timber=True)
    facade(im, px, 214, 270, 108, 300, ("brown", "tan", "cream"), ("slate_d", "slate"), side=14, side_dir=-1)
    # The square's floor: cobbles in perspective, rows getting bigger toward the viewer and
    # converging on the fountain; the sun-side lighter.
    vx, vy = 135, 250
    for y in range(300, H):
        t = (y - 300) / (H - 300)
        size = 3 + int(t * 9)
        row = int(((y - 300) ** 1.25) / 3.2)
        rowsize = max(2, int(2 + t * 7))
        for x in range(W):
            u = (x - vx) / (y - vy)
            cell = int(u * 14 + (row // rowsize % 2) * 0.5)
            edge_u = abs((u * 14 + (row // rowsize % 2) * 0.5) % 1) < 0.06 + t * 0.04
            edge_v = row % rowsize == 0
            shade = (cell * 7 + row // rowsize * 3) % 5
            c = "grey_l" if shade == 0 else "grey" if shade < 3 else "grey_d"
            if edge_u or edge_v:
                c = "slate"
            elif row % rowsize == 1:
                c = "white" if c == "grey_l" else "grey_l"
            px[x, y] = C(c)
        for x in range(W):
            tint(px, x, y, "ink", max(0.0, (x / W - 0.55) * 0.25))
    # Shadows: the left house falls across the cobbles, the eaves on the right.
    shadow_poly(im, [(72, 300), (104, 300), (160, 360), (96, 360)], a=0.3)
    shadow_poly(im, [(200, 300), (270, 300), (270, 340), (230, 330)], a=0.3)
    # The fountain: a stone basin with a lit rim, water with a reflection, the jets.
    shadow_poly(im, [(108, 332), (190, 332), (214, 352), (126, 352)], a=0.35)
    ellipse(im, (84, 298, 186, 340), "slate")
    ellipse(im, (84, 298, 186, 336), "grey")
    ellipse(im, (86, 298, 184, 330), "grey_l")
    ellipse(im, (92, 302, 178, 328), "blue_d")
    ellipse(im, (96, 304, 174, 324), "blue")
    ellipse(im, (100, 304, 150, 316), "cyan")
    for x in range(104, 168, 9):
        rect(px, x, 318, x + 5, 319, "white")
    shade_rect(px, 129, 268, 141, 314, "grey", "grey_l", "slate")
    ellipse(im, (116, 260, 154, 276), "slate")
    ellipse(im, (116, 258, 154, 272), "grey_l")
    for k in range(-3, 4):
        line(im, [(135, 258), (135 + k * 6, 246), (135 + k * 9, 268)], "white" if k % 2 else "cyan")
    # The flower arch: leaves dark behind, lit in front (top-left), flowers on the sunny side.
    for t in range(0, 181, 2):
        a = math.radians(t)
        ax, ay = 135 - 68 * math.cos(a), 300 - 106 * math.sin(a)
        ellipse(im, (ax - 7, ay - 6, ax + 7, ay + 7), "green_dd")
    for t in range(0, 181, 2):
        a = math.radians(t)
        ax, ay = 135 - 68 * math.cos(a), 300 - 106 * math.sin(a)
        ellipse(im, (ax - 6, ay - 7, ax + 4, ay + 3), "green_d")
        ellipse(im, (ax - 5, ay - 6, ax + 1, ay), "green")
    for t in range(0, 181, 9):
        a = math.radians(t)
        ax, ay = 135 - 68 * math.cos(a), 300 - 106 * math.sin(a)
        col = ("pink", "white", "red", "yellow")[t // 9 % 4]
        ellipse(im, (ax - 3, ay - 3, ax + 3, ay + 3), col)
        px[int(ax) - 1, int(ay) - 1] = C("white")
    shadow_poly(im, [(60, 300), (74, 300), (110, 340), (94, 340)], a=0.3)
    shadow_poly(im, [(196, 300), (210, 300), (246, 340), (230, 340)], a=0.3)
    # Bunting: strings sagging between the roofs, flags shaded on their far half; a big near string.
    for (x0, y0, x1, y1, sag, size) in ((0, 120, 270, 110, 30, 8), (0, 168, 270, 150, 24, 9), (-10, 0, 280, 10, 40, 16)):
        for x in range(max(0, x0), min(W, x1)):
            t = (x - x0) / (x1 - x0)
            y = int(y0 + (y1 - y0) * t + sag * math.sin(t * math.pi))
            px[x, y] = C("ink")
            if x % (size + 6) == 0:
                col = (("red", "red_d"), ("yellow", "amber"), ("cyan", "blue"), ("pink", "magenta"), ("green", "green_d"), ("white", "grey_l"))[(x // (size + 6)) % 6]
                poly(im, [(x, y + 1), (x + size // 2, y + 1), (x + size // 2, y + size)], col[0])
                poly(im, [(x + size // 2, y + 1), (x + size, y + 1), (x + size // 2, y + size)], col[1])
    # Lamp posts with ribbons and their shadows; Elias's bench, a posy on it, and its shadow.
    for lx in (34, 236):
        shadow_poly(im, [(lx, 380), (lx + 3, 380), (lx + 60, 420), (lx + 56, 422)], a=0.35)
        rect(px, lx, 250, lx + 3, 380, "ink")
        rect(px, lx, 250, lx + 1, 380, "slate")
        rect(px, lx - 3, 374, lx + 6, 382, "ink")
        shade_rect(px, lx - 5, 238, lx + 8, 252, "amber", "yellow", "rust")
        rect(px, lx - 2, 241, lx + 2, 246, "white")
        rect(px, lx - 6, 236, lx + 9, 239, "ink")
        for k in range(4):
            line(im, [(lx + 3, 256), (lx + 10 + k * 2, 276 + k * 6)], ("pink", "white", "red", "pink")[k])
    shadow_poly(im, [(196, 380), (252, 380), (270, 396), (214, 396)], a=0.35)
    for y, c in ((340, "brown_l"), (344, "brown"), (352, "brown_l"), (356, "brown"), (360, "brown_d"), (364, "ink")):
        rect(px, 196, y, 252, y + 4, c)
    rect(px, 196, 340, 252, 341, "tan")
    rect(px, 196, 352, 252, 353, "tan")
    rect(px, 200, 364, 204, 382, "ink"); rect(px, 244, 364, 248, 382, "ink")
    for k, c in enumerate(("pink", "yellow", "white", "red")):
        ellipse(im, (216 + k * 4, 346, 222 + k * 4, 352), c)
    rect(px, 220, 352, 230, 354, "green_d")
    # Petals: small far off, bigger near the viewer.
    for _ in range(170):
        x, y = rng.randrange(W), rng.randrange(330, H)
        near = (y - 330) / (H - 330)
        s = 1 + int(near * 2)
        col = rng.choice((("pink", "magenta"), ("white", "grey_l"), ("red", "red_d")))
        for dx in range(s):
            if x + dx < W:
                px[x + dx, y] = C(col[0])
                if y + 1 < H and dx == s - 1:
                    px[x + dx, y + 1] = C(col[1])
    return im


def main():
    art = sys.argv[1]
    pivots = {}
    sc = Sprites(art, "scenes")
    sc.save("bedroom", bedroom(), 0, 0)
    sc.save("porch", porch(), 0, 0)
    sc.save("bank_old", bank_old(), 0, 0)
    sc.save("hill", hill(), 0, 0)
    sc.save("square", square(), 0, 0)
    for name, word, ink, light, seed in (("stamp_refused", "REFUSED", "red_d", "red", 1), ("stamp_approved", "APPROVED", "green_d", "green", 2)):
        im = stamp(word, ink, light, seed)
        sc.save(name, im, im.size[0] // 2, im.size[1] // 2)
    sc.save("envelope_icon", envelope(16, 11), 9, 7)
    sc.save("envelope", envelope(70, 44), 36, 23)
    im = envelope(70, 44, open_=True)
    sc.save("envelope_open", im, 36, im.size[1] - 23)
    sc.save("letter", letter_paper(), 118, 150)
    pivots.update(sc.pivots)
    ti = Sprites(art, "title")
    for i in range(len(SKIES)):
        ti.save(f"sky_{i}", sky(i), 0, 0)
    ti.save("sun", sun(), 48, 48)
    ti.save("cloud_a", cloud(1), 29, 9)
    ti.save("cloud_b", cloud(4, 44, 14), 22, 7)
    ti.save("mountains", hills("mountains"), 0, 0)
    ti.save("hills_far", hills("far"), 0, 0)
    ti.save("hills", hills("near"), 0, 0)
    ti.save("rays", rays(), 150, 150)
    ti.save("fore", fore(), 0, 120)
    ti.save("village", village(), 0, 0)
    ti.save("ground", ground(), 0, 0)
    for word in ("REBUILD", "VILLAGE"):
        for i, ch in enumerate(word):
            for state, ruined in (("new", False), ("old", True)):
                im = letter(ch, ruined, seed=sum(map(ord, word)) * 10 + i)
                ti.save(f"{word.lower()}_{i}_{state}", im, im.size[0] // 2, im.size[1] - 4)
    ti.save("ribbon", ribbon(), 36, 11)
    pivots.update(ti.pivots)
    print(json.dumps(pivots))


if __name__ == "__main__":
    main()
