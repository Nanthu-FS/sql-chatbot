"""Hand-pixel helpers for isometric props (art round 5: the hand-pixeled farm).

A prop is drawn straight onto a small canvas, pixel by pixel, in iso units: +u runs down-right
(2 px across, 1 px down), +v runs down-left (2 px back, 1 px down) and z is height in pixels, so
a footprint edge is a clean 2:1 stair. Faces are flat colours lit from the top left: the top is
lightest, the +v face (looking down-left) mid, the +u face (looking down-right) darkest. Details
(seams, trims, glints, hardware) are then placed one pixel at a time with the line and dot calls.

The finished body goes through _pix.finish and the polish pass like every other world sprite.
"""
from PIL import ImageDraw

from _pix import E, new

# The painted-toy look: every top gets a lit edge one step lighter than its face.
LIGHT = {"red": "pink", "red_d": "red", "purple": "red_d", "tan": "cream", "cream": "white",
         "brown_l": "tan", "brown": "brown_l", "brown_d": "brown", "tan_d": "tan",
         "grey": "grey_l", "grey_l": "white", "grey_d": "grey", "slate": "grey_d",
         "blue_d": "blue", "blue": "cyan", "green_d": "green", "green": "yellow",
         "amber": "yellow", "orange": "amber", "white": "white", "slate_d": "slate"}

# A 3x5 pixel font for the painted names (only the letters the farm needs).
FONT = {
    "A": ["010", "101", "111", "101", "101"], "B": ["110", "101", "110", "101", "110"],
    "C": ["011", "100", "100", "100", "011"], "D": ["110", "101", "101", "101", "110"],
    "E": ["111", "100", "110", "100", "111"], "F": ["111", "100", "110", "100", "100"],
    "H": ["101", "101", "111", "101", "101"], "I": ["111", "010", "010", "010", "111"],
    "K": ["101", "110", "100", "110", "101"], "L": ["100", "100", "100", "100", "111"],
    "M": ["101", "111", "111", "101", "101"], "N": ["101", "111", "111", "111", "101"],
    "O": ["010", "101", "101", "101", "010"], "R": ["110", "101", "110", "101", "101"],
    "S": ["011", "100", "010", "001", "110"], "T": ["111", "010", "010", "010", "010"],
    "W": ["101", "101", "111", "111", "101"], "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"], "2": ["110", "001", "010", "100", "111"],
    "3": ["110", "001", "010", "001", "110"], "5": ["111", "100", "110", "001", "110"],
    "C2": ["011", "100", "100", "100", "011"], " ": ["000", "000", "000", "000", "000"],
}

# The farm's wheat-sheaf emblem (matches the app icon), 5 x 6.
WHEAT = ["a.a.a", "yaYay", ".yay.", "..b..", ".b.b.", "b...b"]


class Iso:
    def __init__(self, w, h, ox, oy):
        """A w x h canvas with world point (0, 0, 0) at pixel (ox, oy)."""
        self.im = new(w, h)
        self.px = self.im.load()
        self.d = ImageDraw.Draw(self.im)
        self.ox, self.oy = ox, oy
        self.w, self.h = w, h

    def p(self, u, v, z=0.0):
        """Pixel of world point (u, v, z)."""
        return (round(self.ox + 2 * u - 2 * v), round(self.oy + u + v - z))

    @staticmethod
    def c(col):
        return E[col] if isinstance(col, str) else col

    def dot(self, x, y, col):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = self.c(col)

    def poly(self, pts, col):
        self.d.polygon([self.p(*q) for q in pts], fill=self.c(col))

    # ---------------------------------------------------------------- solids

    def box(self, u0, u1, v0, v1, z0, z1, top, left, right, lit=True):
        """An axis box: top face, the +v (left, mid) face and the +u (right, dark) face, with a
        lit edge along the top's two front edges (the painted-toy look) unless lit is False."""
        self.poly([(u0, v1, z1), (u1, v1, z1), (u1, v1, z0), (u0, v1, z0)], left)
        self.poly([(u1, v0, z1), (u1, v1, z1), (u1, v1, z0), (u1, v0, z0)], right)
        self.poly([(u0, v0, z1), (u1, v0, z1), (u1, v1, z1), (u0, v1, z1)], top)
        if lit and isinstance(top, str) and top in LIGHT:
            self.uline(u0, u1, v1, z1, LIGHT[top])
            self.vline(u1, v1 - v0, v0, z1, LIGHT[top])

    # ---------------------------------------------------------------- lines on faces

    def uline(self, u0, u1, v, z, col):
        """A 2:1 line along +u at (v, z): on the left (+v) face or a top edge."""
        x, y = self.p(u0, v, z)
        for i in range(round(u1 - u0)):
            self.dot(x + 2 * i, y + i, col)
            self.dot(x + 2 * i + 1, y + i, col)

    def vline(self, u0, u1, v, z, col):
        """A 2:1 line along +v at (u, z): on the right (+u) face or a top edge (u is u0; u1 unused)."""
        x, y = self.p(u0, v, z)
        for i in range(round(u1)):
            self.dot(x - 2 * i - 1, y + i, col)
            self.dot(x - 2 * i - 2, y + i, col)

    def zline(self, u, v, z0, z1, col, dx=0):
        """A vertical run of pixels at (u, v) from z0 up to z1."""
        x, y = self.p(u, v, z0)
        for k in range(round(z1 - z0)):
            self.dot(x + dx, y - k, col)

    def ellipse(self, cx, cy, rx, ry, col, outline=None):
        self.d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=self.c(col), outline=self.c(outline) if outline else None)

    def pivot(self, u, v):
        return self.p(u, v, 0)


    # ---------------------------------------------------------------- signature marks

    def text(self, x, y, s, col, shadow=None):
        """Painted letters in the 3x5 font, upright, starting at pixel (x, y)."""
        for ch in s.upper():
            g = FONT.get(ch, FONT[" "])
            for j, row in enumerate(g):
                for i, b in enumerate(row):
                    if b == "1":
                        if shadow:
                            self.dot(x + i, y + j + 1, shadow)
                        self.dot(x + i, y + j, col)
            x += 4

    def wheat(self, x, y, light="yellow", mid="amber", stem="brown_l"):
        """The wheat-sheaf emblem with its top-left at pixel (x, y)."""
        cols = {"a": mid, "y": light, "Y": "white", "b": stem}
        for j, row in enumerate(WHEAT):
            for i, ch in enumerate(row):
                if ch in cols:
                    self.dot(x + i, y + j, cols[ch])


def soften(im):
    """Rounds the silhouette: drops convex corner pixels (opaque, with exactly two opaque
    neighbours that meet at a corner), so boxes read as chunky painted toys, not sharp crates."""
    w, h = im.size
    px = im.load()
    on = lambda x, y: 0 <= x < w and 0 <= y < h and px[x, y][3] > 0  # noqa: E731
    drop = []
    for y in range(h):
        for x in range(w):
            if not on(x, y):
                continue
            n = (on(x, y - 1), on(x + 1, y), on(x, y + 1), on(x - 1, y))
            if sum(n) == 2 and ((n[0] and n[1]) or (n[1] and n[2]) or (n[2] and n[3]) or (n[3] and n[0])):
                if not on(x - n[3] * 0 + (1 if n[1] else -1), y + (1 if n[2] else -1)):
                    continue
                drop.append((x, y))
    for x, y in drop:
        px[x, y] = (0, 0, 0, 0)
    return im
