"""Fishing in the farm river (round 6, folder "fishing"): the float bobbing on the water, the same
float pulled under by a bite, and the ring a cast leaves. Pivot = the waterline under the float.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import Sprites, finish, from_map  # noqa: E402

KEY = {"r": "red", "R": "red_d", "w": "white", "g": "grey_l", "k": "ink", "c": "cyan"}
SPRITES = {
    "float": [".k.", "rrr", "rRr", "www", "gwg"],
    "float_bite": [".k.", "rrr", "cRc"],
    "ring": ["..ccc..", ".c...c.", "c.....c", ".c...c.", "..ccc.."],
}


def main():
    s = Sprites(sys.argv[1], "fishing")
    for name, rows in SPRITES.items():
        im, pad = finish(from_map(rows, KEY), shadow=False, outline=name != "ring", pad=1)
        s.save(name, im, len(rows[0]) // 2 + pad, len(rows) - 1 + pad)
    print(json.dumps(s.pivots))


if __name__ == "__main__":
    main()
