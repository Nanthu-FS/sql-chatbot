"""Story backdrops (pixel-direct), written to art/scenes/.

scenes/bank       the loan office at Goldcrest Bank, 270x480: green wallpaper, a window with
                  blinds, the Rural Growth Scheme poster, a clock and the bank's plaque
scenes/bank_desk  the officer's desk on its own layer (transparent above it), drawn over his
                  portrait so he sits behind it

Clean shading (no dither) lit from the top-left. Pivot = top-left corner.
"""
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # noqa: E401,E702
from _pix import E, Sprites, new  # noqa: E402
from PIL import ImageDraw, ImageFont  # noqa: E402
import math  # noqa: E402

W, H = 270, 480
FONT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "app", "src", "main", "assets", "fonts", "Silkscreen-Regular.ttf")
WALL_TOP = 300      # wainscot starts here
DESK_TOP = 380      # the desk's top edge


def rect(px, x0, y0, x1, y1, c):
    for y in range(max(0, y0), min(H, y1)):
        for x in range(max(0, x0), min(W, x1)):
            px[x, y] = E[c] if isinstance(c, str) else c


def text(im, x, y, s, c, center=False):
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    f = ImageFont.truetype(FONT, 8)
    if center:
        x -= d.textlength(s, font=f) / 2
    d.text((x, y), s, font=f, fill=E[c])


def wall(px):
    for y in range(WALL_TOP):
        for x in range(W):
            stripe = (x // 9) % 2
            c = "green_dd" if stripe else "teal_dd"
            if y < 12:
                c = "brown" if y > 3 else "brown_d"
            elif y == 12:
                c = "amber"
            px[x, y] = E[c]
    # Shadow under the crown moulding.
    for x in range(W):
        px[x, 13] = E["teal_dd"]
    # Wainscot: wood panels with a lit top rail.
    rect(px, 0, WALL_TOP, W, H, "brown")
    rect(px, 0, WALL_TOP, W, WALL_TOP + 3, "tan_d")
    rect(px, 0, WALL_TOP + 3, W, WALL_TOP + 5, "brown_d")
    for x0 in range(6, W, 52):
        rect(px, x0, WALL_TOP + 14, x0 + 44, H - 10, "brown_d")
        rect(px, x0 + 1, WALL_TOP + 15, x0 + 43, H - 11, "brown_l")
        rect(px, x0 + 3, WALL_TOP + 17, x0 + 41, H - 13, "brown")


def window(px):
    x0, y0, x1, y1 = 16, 58, 118, 206
    rect(px, x0 - 4, y0 - 4, x1 + 4, y1 + 6, "cream")
    rect(px, x0 - 4, y1 + 2, x1 + 4, y1 + 6, "tan_d")
    rect(px, x0 - 6, y1 + 6, x1 + 6, y1 + 9, "tan")
    # Sky, puffy clouds, rolling hills in two greens.
    for y in range(y0, y1):
        t = (y - y0) / (y1 - y0)
        rect(px, x0, y, x1, y + 1, "blue" if t < 0.5 else "cyan")
    for cx, cy, rx in ((44, 128, 10), (56, 124, 12), (70, 129, 9), (96, 146, 7), (104, 143, 9)):
        for y in range(cy - rx, cy + 4):
            for x in range(cx - rx, cx + rx + 1):
                if (x - cx) ** 2 + ((y - cy) * 1.5) ** 2 <= rx * rx and x0 <= x < x1:
                    px[x, y] = E["white"] if y < cy else E["grey_l"]
    for x in range(x0, x1):
        far = int(y0 + (y1 - y0) * 0.66 + 5 * math.sin((x - x0) * 0.08))
        near = int(y0 + (y1 - y0) * 0.78 + 4 * math.sin((x - x0) * 0.11 + 2.0))
        rect(px, x, far, x + 1, y1, "green_d")
        rect(px, x, far, x + 1, far + 1, "green")
        rect(px, x, near, x + 1, y1, "green")
        rect(px, x, near, x + 1, near + 1, "yellow")
    # Blinds half down, a cord, the cross bars.
    for y in range(y0, y0 + 56):
        rect(px, x0, y, x1, y + 1, "grey_l" if (y - y0) % 4 < 3 else "grey")
    rect(px, x0, y0 + 56, x1, y0 + 58, "grey_d")
    rect(px, x1 - 8, y0 + 58, x1 - 7, y0 + 90, "grey_l")
    rect(px, (x0 + x1) // 2 - 1, y0, (x0 + x1) // 2 + 1, y1, "cream")
    rect(px, x0, y0 + 96, x1, y0 + 98, "cream")


def poster(im, px):
    x0, y0, x1, y1 = 158, 74, 252, 196
    rect(px, x0 + 2, y0 + 2, x1 + 2, y1 + 2, "teal_dd")
    rect(px, x0, y0, x1, y1, "brown_l")
    rect(px, x0 + 2, y0 + 2, x1 - 2, y1 - 2, "cream")
    rect(px, x0 + 2, y0 + 2, x1 - 2, y0 + 26, "amber")
    text(im, (x0 + x1) // 2, y0 + 5, "RURAL GROWTH", "brown_d", center=True)
    text(im, (x0 + x1) // 2, y0 + 15, "SCHEME", "brown_d", center=True)
    # Bars climbing, an arrow up, a wheat sheaf.
    base = y1 - 30
    for i, h in enumerate((14, 24, 36, 50)):
        bx = x0 + 12 + i * 17
        rect(px, bx, base - h, bx + 11, base, "green_d")
        rect(px, bx, base - h, bx + 3, base, "green")
    for i in range(30):
        ax, ay = x0 + 12 + i * 2, base - 18 - i
        rect(px, ax, ay, ax + 3, ay + 2, "red")
    rect(px, x0 + 66, base - 52, x0 + 76, base - 49, "red")
    rect(px, x0 + 72, base - 52, x0 + 75, base - 42, "red")
    rect(px, x0 + 8, base, x1 - 8, base + 1, "brown_d")
    text(im, (x0 + x1) // 2, y1 - 20, "FOR VILLAGES", "brown", center=True)
    text(im, (x0 + x1) // 2, y1 - 11, "THAT GIVE BACK", "brown", center=True)


def plaque(im, px):
    x0, y0, x1, y1 = 76, 22, 194, 42
    rect(px, x0, y0, x1, y1, "brown_d")
    rect(px, x0 + 1, y0 + 1, x1 - 1, y1 - 1, "brown")
    rect(px, x0 + 1, y0 + 1, x1 - 1, y0 + 2, "brown_l")
    for cx in (x0 + 9, x1 - 9):
        for y in range(y0 + 5, y0 + 15):
            for x in range(cx - 5, cx + 6):
                if abs(x - cx) * 2 + max(0, y - (y0 + 10)) * 2 <= 10:
                    px[x, y] = E["amber"] if x < cx else E["orange"]
    text(im, (x0 + x1) // 2, y0 + 6, "GOLDCREST BANK", "yellow", center=True)


def clock(px):
    cx, cy, r = 222, 46, 12
    for y in range(cy - r - 2, cy + r + 3):
        for x in range(cx - r - 2, cx + r + 3):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r + 2:
                px[x, y] = E["amber"] if d > r else E["white"] if (x - cx) + (y - cy) < 4 else E["grey_l"]
    for i in range(9):
        px[cx, cy - i] = E["ink"]
    for i in range(6):
        px[cx + i, cy] = E["ink"]
    for k in range(12):
        a = k / 12 * 2 * math.pi
        px[int(cx + math.sin(a) * (r - 1.5)), int(cy - math.cos(a) * (r - 1.5))] = E["slate"]


def desk_layer():
    im = new(W, H)
    px = im.load()
    rect(px, 0, DESK_TOP, W, DESK_TOP + 4, "tan")
    rect(px, 0, DESK_TOP + 4, W, DESK_TOP + 10, "brown_l")
    rect(px, 0, DESK_TOP + 10, W, DESK_TOP + 12, "brown_d")
    rect(px, 0, DESK_TOP + 12, W, H, "brown")
    for x0 in range(10, W, 64):
        rect(px, x0, DESK_TOP + 22, x0 + 54, H - 8, "brown_d")
        rect(px, x0 + 1, DESK_TOP + 23, x0 + 53, H - 9, "brown_l")
        rect(px, x0 + 3, DESK_TOP + 25, x0 + 51, H - 11, "brown")
        rect(px, x0 + 24, DESK_TOP + 38, x0 + 30, DESK_TOP + 41, "amber")
    # Paper stack, the REFUSED stamp and the nameplate on the desk top.
    for i in range(4):
        rect(px, 120 + i, DESK_TOP - 6 + i * 1, 158 + i, DESK_TOP - 4 + i * 1, "white" if i % 2 else "grey_l")
    rect(px, 168, DESK_TOP - 5, 182, DESK_TOP, "red_d")
    rect(px, 168, DESK_TOP - 5, 182, DESK_TOP - 4, "red")
    rect(px, 173, DESK_TOP - 14, 177, DESK_TOP - 5, "brown_l")
    rect(px, 171, DESK_TOP - 17, 179, DESK_TOP - 13, "brown")
    rect(px, 192, DESK_TOP - 12, 262, DESK_TOP, "orange")
    rect(px, 192, DESK_TOP - 12, 262, DESK_TOP - 10, "yellow")
    rect(px, 194, DESK_TOP - 9, 260, DESK_TOP - 1, "amber")
    text(im, 227, DESK_TOP - 9, "MR. STERLING", "brown_d", center=True)
    return im


def main():
    sp = Sprites(sys.argv[1], "scenes")
    im = new(W, H)
    px = im.load()
    wall(px)
    window(px)
    poster(im, px)
    plaque(im, px)
    clock(px)
    sp.save("bank", im, 0, 0)
    sp.save("bank_desk", desk_layer(), 0, 0)
    sp.done()


main()
