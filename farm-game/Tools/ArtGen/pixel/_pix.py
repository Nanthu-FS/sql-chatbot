"""Shared helpers for pixel-direct sprite modules (Endesga-32 palette, iso diamonds)."""
import json
import os
import random
from PIL import Image

E = {name: tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,) for name, h in {
    "rust": "be4a2f", "clay": "d77643", "cream": "ead4aa", "tan": "e4a672", "brown_l": "b86f50", "brown": "733e39",
    "brown_d": "3e2731", "red_d": "a22633", "red": "e43b44", "orange": "f77622", "amber": "feae34", "yellow": "fee761",
    "green": "63c74d", "green_d": "3e8948", "green_dd": "265c42", "teal_dd": "193c3e", "blue_d": "124e89", "blue": "0099db",
    "cyan": "2ce8f5", "white": "ffffff", "grey_l": "c0cbdc", "grey": "8b9bb4", "grey_d": "5a6988", "slate": "3a4466",
    "slate_d": "262b44", "ink": "181425", "pink_r": "ff0044", "purple": "68386c", "magenta": "b55088", "pink": "f6757a",
    "skin": "e8b796", "tan_d": "c28569",
}.items()}
CLEAR = (0, 0, 0, 0)
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def alpha(c, a):
    return c[:3] + (a,)


def bayer(x, y, level):
    """True when pixel (x, y) is 'on' for a dither level 0..16."""
    return BAYER[y % 4][x % 4] < level


def diamond_rows(h=16):
    """(y, x0, x1) spans of a 2:1 iso diamond (x1 exclusive), width 2*h."""
    half = h // 2
    for y in range(h):
        hw = (y + 1) * 2 if y < half else (h - y) * 2
        yield y, h - hw, h + hw


def in_diamond(x, y, h=16):
    half = h // 2
    if y < 0 or y >= h:
        return False
    hw = (y + 1) * 2 if y < half else (h - y) * 2
    return h - hw <= x < h + hw


class Sprites:
    """Collects sprites for one module and prints the pivot manifest at the end."""

    def __init__(self, art_dir, group):
        self.dir = os.path.join(art_dir, group)
        self.group = group
        self.pivots = {}
        os.makedirs(self.dir, exist_ok=True)

    def save(self, name, im, ax, ay):
        im.save(os.path.join(self.dir, name + ".png"))
        self.pivots[f"{self.group}/{name}"] = {"ax": ax, "ay": ay}

    def done(self):
        print(json.dumps(self.pivots))


def new(w, h):
    return Image.new("RGBA", (w, h), CLEAR)


def rng(seed):
    return random.Random(seed)
