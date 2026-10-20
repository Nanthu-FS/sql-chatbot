"""Farm animals (round 6, folder "animals"): a white hen, a woolly sheep (and the same sheep shorn)
and a black-and-white cow, side-on facing right; the engine mirrors them for the left. Each has two
walk frames (_0, _1) and an eating pose (_eat, head down). Pivot = between the feet on the ground.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import Sprites, finish, from_map  # noqa: E402

HEN_KEY = {"w": "white", "g": "grey_l", "r": "red", "a": "amber", "k": "ink"}
SHEEP_KEY = {"W": "white", "c": "cream", "t": "tan", "s": "slate_d", "k": "ink", "p": "pink"}
COW_KEY = {"W": "white", "g": "grey_l", "k": "ink", "p": "pink", "c": "cream", "P": "red"}

HEN = {
    "0": [
        ".......r..",
        "......rww.",
        "......wkwa",
        "w.....wwr.",
        "ww..wwwww.",
        "wwwwwgwww.",
        ".wwggggw..",
        "..wwwww...",
        "...a..a...",
    ],
    "1": [
        ".......r..",
        "......rww.",
        "......wkwa",
        "w.....wwr.",
        "ww..wwwww.",
        "wwwwwgwww.",
        ".wwggggw..",
        "..wwwww...",
        "....aa....",
    ],
    "eat": [
        "..........",
        "..........",
        "..........",
        "w.........",
        "ww..wwww..",
        "wwwwwggwwr",
        ".wwgggwwkw",
        "..wwwww.ra",
        "...a..a...",
    ],
}

SHEEP = {
    "0": [
        "...cWWcWWc....",
        "..cWWWWWWWWcss",
        ".cWWWWWWWWWsss",
        "cWWWWWWWWWWsks",
        "cWWWWWWWWWWcsp",
        ".cWWWWWWWWWc..",
        "..ccccccccc...",
        "..s.s....s.s..",
        "..s.s....s.s..",
    ],
    "1": [
        "...cWWcWWc....",
        "..cWWWWWWWWcss",
        ".cWWWWWWWWWsss",
        "cWWWWWWWWWWsks",
        "cWWWWWWWWWWcsp",
        ".cWWWWWWWWWc..",
        "..ccccccccc...",
        "...ss....ss...",
        "...ss....ss...",
    ],
    "eat": [
        "...cWWcWWc....",
        "..cWWWWWWWWc..",
        ".cWWWWWWWWWc..",
        "cWWWWWWWWWWcss",
        "cWWWWWWWWWWsss",
        ".cWWWWWWWWWsks",
        "..ccccccccc.sp",
        "..s.s....s.s..",
        "..s.s....s.s..",
    ],
}

# The same sheep after shearing: a slim pale body until its wool grows back.
SHORN = {
    k: [row.replace("W", "t").replace("c", "c") for row in rows]
    for k, rows in SHEEP.items()
}

COW = {
    "0": [
        ".............c..c.",
        ".............kkkk.",
        "k...........kWkWk.",
        ".kWWWkkWWWWWWWWWWp",
        ".WWWkkkWWWWWkkWWpk",
        ".WWWWkWWWWWkkkWW..",
        ".WWWWWWWWWWWkWWW..",
        ".gWWWWWWWWWWWWWg..",
        "..gWW.gppg..gWW...",
        "..WW........WW....",
        "..kk........kk....",
    ],
    "1": [
        ".............c..c.",
        ".............kkkk.",
        "k...........kWkWk.",
        ".kWWWkkWWWWWWWWWWp",
        ".WWWkkkWWWWWkkWWpk",
        ".WWWWkWWWWWkkkWW..",
        ".WWWWWWWWWWWkWWW..",
        ".gWWWWWWWWWWWWWg..",
        "..gWW.gppg..gWW...",
        "...WW......WW.....",
        "...kk......kk.....",
    ],
    "eat": [
        "..................",
        "..................",
        "k.................",
        ".kWWWkkWWWWWWWW.c.",
        ".WWWkkkWWWWWkkWkkk",
        ".WWWWkWWWWWkkkWkWk",
        ".WWWWWWWWWWWkWWWWW",
        ".gWWWWWWWWWWWWWgWp",
        "..gWW.gppg..gWW.pk",
        "..WW........WW....",
        "..kk........kk....",
    ],
}


def sprites():
    for kind, frames, key in (("hen", HEN, HEN_KEY), ("sheep", SHEEP, SHEEP_KEY),
                              ("sheep_shorn", SHORN, SHEEP_KEY), ("cow", COW, COW_KEY)):
        for name, rows in frames.items():
            assert len({len(r) for r in rows}) == 1, (kind, name, [len(r) for r in rows])
            yield f"{kind}_{name}", rows, key


def main():
    s = Sprites(sys.argv[1], "animals")
    for name, rows, key in sprites():
        im, pad = finish(from_map(rows, key))
        s.save(name, im, len(rows[0]) // 2 + pad, len(rows) - 1 + pad)
    print(json.dumps(s.pivots))


if __name__ == "__main__":
    main()
