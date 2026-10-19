"""Tiny particle sprites for world FX. Pivot = centre. No shadows; outlines only where they help."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, Sprites, finish, from_map  # noqa: E402


SPRITES = {
    "dust": ([".bb.", "bBBb", ".bb."], {"b": "tan", "B": "tan_d"}, False),
    "drop": (["..b..", ".bcb.", ".bcb.", "bcwbb", "bccbb", "bbbbb", ".bbb."], {"c": "cyan", "b": "blue", "w": "white"}, True),
    "sparkle": (["...y...", "...y...", "..yWy..", "yyWWWyy", "..yWy..", "...y...", "...y..."], {"y": "yellow", "W": "white"}, False),
    "star": ([".y.", "yWy", ".y."], {"y": "amber", "W": "white"}, False),
    "leaf": (["gG", ".g"], {"g": "green", "G": "green_d"}, True),
    "chip_wood": (["tT", "Tb"], {"t": "tan", "T": "tan_d", "b": "brown"}, True),
    "chip_stone": (["gG", "Gs"], {"g": "grey_l", "G": "grey", "s": "grey_d"}, True),
    "coin": ([".yy.", "yWYy", "yYYy", ".yy."], {"y": "amber", "Y": "yellow", "W": "white"}, True),
    "heart": (["rr.rr", "rWrrr", "rrrrr", ".rrr.", "..r.."], {"r": "red", "W": "white"}, True),
    "zap": (["..yy", ".yy.", "yyyy", ".yy.", "yy.."], {"y": "yellow"}, True),
}


def main():
    s = Sprites(sys.argv[1], "fx")
    for name, (rows, key, outline) in SPRITES.items():
        im, pad = finish(from_map(rows, key), outline=outline, shadow=False, pad=1)
        s.save(name, im, len(rows[0]) // 2 + pad, len(rows) // 2 + pad)
    s.done()


main()
