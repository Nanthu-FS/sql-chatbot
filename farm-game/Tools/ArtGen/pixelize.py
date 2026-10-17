"""Pixel-art pass for the art generator.

Takes small anti-aliased renders of the vector art and turns them into clean
pixel sprites: Endesga-32 palette, checker dithering between the two nearest
palette colours, 1px outline and a hard drop shadow.

usage: python3 -I pixelize.py jobs.json
jobs.json: [{"src": ..., "out": ..., "palette": "endesga32", "outline": "black",
             "flags": ["dither", "shadow"], "pad": 2}, ...]
outline: black | selout | none     flags: dither, shadow, rim, shade, nodither
"""
import json
import sys
from PIL import Image

PALETTES = {
    "endesga32": "be4a2f d77643 ead4aa e4a672 b86f50 733e39 3e2731 a22633 e43b44 f77622 feae34 fee761 63c74d 3e8948 265c42 193c3e 124e89 0099db 2ce8f5 ffffff c0cbdc 8b9bb4 5a6988 3a4466 262b44 181425 ff0044 68386c b55088 f6757a e8b796 c28569",
}
INK = (24, 20, 37)
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def hx(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def dist(a, b):
    rm = (a[0] + b[0]) / 2
    dr, dg, db = a[0] - b[0], a[1] - b[1], a[2] - b[2]
    return (2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db


class Quant:
    def __init__(self, pal):
        self.pal = pal
        self.cache = {}

    def near2(self, c):
        hit = self.cache.get(c)
        if hit is None:
            s = sorted(self.pal, key=lambda p: dist(c, p))
            hit = (s[0], s[1], dist(c, s[0]), dist(c, s[1]))
            self.cache[c] = hit
        return hit

    def near(self, c):
        return self.near2(c)[0]


def mix(c, t, k):
    return tuple(int(c[i] + (t[i] - c[i]) * k) for i in range(3))


def run(job, quants):
    pal_name = job.get("palette", "endesga32")
    q = quants.setdefault(pal_name, Quant([hx(h) for h in PALETTES[pal_name].split()]))
    flags = set(job.get("flags", ["dither", "shadow"]))
    outline = job.get("outline", "black")
    pad = job.get("pad", 2)
    im = Image.open(job["src"]).convert("RGBA")
    w, h = im.size[0] + pad * 2, im.size[1] + pad * 2
    px = {}
    src = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = src[x, y]
            if a <= 120:
                continue
            c = (r, g, b)
            if "dither" in flags and "nodither" not in flags:
                c1, c2, d1, d2 = q.near2(c)
                t = d1 / (d1 + d2 + 1e-6)
                c = c2 if t * 2 > (BAYER[y % 4][x % 4] + 0.5) / 16 * 1.4 + 0.3 else c1
            else:
                c = q.near(c)
            px[(x + pad, y + pad)] = c

    on = lambda x, y: (x, y) in px
    base = dict(px)
    for (x, y), c in base.items():
        if "rim" in flags and (not on(x, y - 1) or not on(x - 1, y)) and on(x, y + 1):
            px[(x, y)] = q.near(mix(c, (255, 255, 255), 0.35))
        if "shade" in flags and (not on(x, y + 1) or not on(x + 1, y)) and on(x, y - 1):
            px[(x, y)] = q.near(mix(c, (20, 10, 20), 0.35))

    res = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out = res.load()
    if "shadow" in flags:
        for (x, y) in base:
            for dx, dy in ((1, 2), (0, 2), (1, 1)):
                nx, ny = min(w - 1, x + dx), min(h - 1, y + dy)
                if not on(nx, ny):
                    out[nx, ny] = INK + (90,)
    for (x, y), c in px.items():
        out[x, y] = c + (255,)
    if outline != "none":
        for y in range(h):
            for x in range(w):
                if on(x, y):
                    continue
                nb = [base[(x + dx, y + dy)] for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)) if on(x + dx, y + dy)]
                if not nb:
                    continue
                if outline == "selout":
                    c = q.near(mix(nb[0], (10, 5, 15), 0.62))
                    out[x, y] = (INK if dist(c, nb[0]) < 1500 else c) + (255,)
                else:
                    out[x, y] = INK + (255,)
    res.save(job["out"])


def main():
    jobs = json.load(open(sys.argv[1]))
    quants = {}
    for job in jobs:
        run(job, quants)
    print(f"pixelized {len(jobs)}")


main()
