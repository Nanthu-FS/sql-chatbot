"""Romance items (round 7): the bouquet that starts dating and the ring that proposes.

items/<id> (14 px body) and items_big/<id> (the same map at twice the size), character maps lit from
the top-left, finished with the ink outline and drop shadow. Pivot = centre.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image  # noqa: E402
from _pix import Sprites, finish, from_map  # noqa: E402

K = {"y": "yellow", "a": "amber", "o": "orange", "b": "brown_d", "B": "brown", "g": "green_d", "G": "green", "e": "green_dd",
     "p": "pink", "P": "magenta", "r": "red", "R": "red_d", "w": "white", "c": "cyan", "C": "blue", "l": "grey_l",
     "u": "blue", "v": "purple", "t": "tan", "k": "ink"}

ITEMS = {
    "bouquet": [  # sunflowers and wildflowers tied with a pink ribbon
        "...yay..pPp...",
        "..yabay.PwP.c.",
        "..yaBay.pPpcwc",
        "...yay.u..g.c.",
        ".pPp.gGuwu.G..",
        ".PwP..GGuG.G..",
        ".pPpG..GGGGg..",
        "...GgG.GGgG...",
        "....GgGGgG....",
        ".....pPPp.....",
        "....pPPPPp....",
        ".....gGgG.....",
        ".....gGgG.....",
        "......eg......",
    ],
    "ring": [  # a gold band with a diamond
        ".....clc......",
        "....cwwlc.....",
        "....cllCc.....",
        ".....cCc......",
        "....ayya......",
        "...ay..ya.....",
        "..ay....ya....",
        "..a......a....",
        "..a......o....",
        "..ay....yo....",
        "...ay..yo.....",
        "....aooo......",
        "..............",
        "..............",
    ],
}


def main():
    small = Sprites(sys.argv[1], "items")
    big = Sprites(sys.argv[1], "items_big")
    for name, rows in ITEMS.items():
        assert len({len(r) for r in rows}) == 1, (name, [len(r) for r in rows])
        body = from_map(rows, K)
        im, pad = finish(body)
        small.save(name, im, body.size[0] // 2 + pad, body.size[1] // 2 + pad)
        body2 = body.resize((body.size[0] * 2, body.size[1] * 2), Image.NEAREST)
        im, pad = finish(body2)
        big.save(name, im, body2.size[0] // 2 + pad, body2.size[1] // 2 + pad)
    print(json.dumps({**small.pivots, **big.pivots}))


if __name__ == "__main__":
    main()
