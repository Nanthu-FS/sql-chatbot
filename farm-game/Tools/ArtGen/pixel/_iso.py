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

    def box(self, u0, u1, v0, v1, z0, z1, top, left, right):
        """An axis box: top face, the +v (left, mid) face and the +u (right, dark) face."""
        self.poly([(u0, v1, z1), (u1, v1, z1), (u1, v1, z0), (u0, v1, z0)], left)
        self.poly([(u1, v0, z1), (u1, v1, z1), (u1, v1, z0), (u1, v0, z0)], right)
        self.poly([(u0, v0, z1), (u1, v0, z1), (u1, v1, z1), (u0, v1, z1)], top)

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
