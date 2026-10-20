"""Best-friend keepsakes (round 6, folder "decor"): one decoration from each villager at ten
hearts, small props drawn as character maps. Pivot = bottom middle.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import Sprites, finish, from_map  # noqa: E402

K = {"b": "brown_d", "B": "brown", "W": "brown_l", "T": "tan", "c": "clay", "C": "tan_d", "k": "ink", "o": "orange",
     "y": "amber", "Y": "yellow", "g": "grey_d", "G": "grey", "L": "grey_l", "w": "white", "r": "red_d", "R": "red",
     "p": "magenta", "P": "pink", "e": "green_d", "E": "green", "s": "slate", "S": "slate_d", "u": "blue", "U": "cyan",
     "v": "purple", "x": "cream", "K": "brown_d"}

KEEPSAKES = {
    "ks_mara": [  # Mara's bread oven
        ".....cccccc.....",
        "...cCCCCCCCCc...",
        "..cCCCCCCCCCCc..",
        ".cCCCCkkkkCCCCc.",
        ".cCCCkooooKCCCc.",
        "cCCCCkoYYoKCCCCc",
        "cCCCCkoYYoKCCCCc",
        "cCCCCkkkkkkCCCCc",
        "bbbbbbbbbbbbbbbb",
        "bBWBBWBBWBBWBBWb",
        "bbbbbbbbbbbbbbbb",
    ],
    "ks_tobias": [  # the carved farm sign
        "bbbbbbbbbbbbbbbb",
        "bWWWWWWWWWWWWWWb",
        "bWyWWkkWkWkkWWWb",
        "bWyyWkWWkWkWkWWb",
        "bWyWWkkWkWkkWWWb",
        "bWWWWWWWWWWWWWWb",
        "bbbbbbbbbbbbbbbb",
        ".......bW.......",
        ".......bW.......",
        ".......bW.......",
        ".......bW.......",
        "......bbWb......",
    ],
    "ks_ivy": [  # a flower cart
        "..pPp.RRr.yYy.pPp.",
        ".pPPpRRRryYYypPPp.",
        "..eEe.eEe.eEe.eEe.",
        "bBBBBBBBBBBBBBBBBb",
        "bWWWWWWWWWWWWWWWWb",
        "bbbbbbbbbbbbbbbbbb",
        "..kkk.........kkk.",
        ".kBBBk.......kBBBk",
        ".kBkBk.......kBkBk",
        "..kkk.........kkk.",
    ],
    "ks_bram": [  # a lucky horseshoe on a post
        "..gG......Gg..",
        "..gG......Gg..",
        "..gGg....gGg..",
        "...gGggggGg...",
        "....gGGGGg....",
        "......bW......",
        "......bW......",
        "......bW......",
        "......bW......",
        "......bW......",
        ".....bbWb.....",
    ],
    "ks_juniper": [  # a tea table
        "......ww......",
        "....wLLLLw.w..",
        "...wLLLLLLww..",
        "...wLLLLLLw...",
        ".xx.wwwwww.xx.",
        "bWWWWWWWWWWWWb",
        "bbbbbbbbbbbbbb",
        ".bB........Bb.",
        ".bB........Bb.",
        ".bB........Bb.",
    ],
    "ks_pip": [  # Pip's "diamond" on a stand
        "....wU....",
        "...wUUu...",
        "..wUUUuu..",
        "..UUUuuS..",
        "...Uuuu...",
        "....uS....",
        "..GGGGGG..",
        "..LGGGGg..",
        "...GGGg...",
        "...GGGg...",
        "..gggggg..",
    ],
    "ks_sela": [  # a wine barrel
        "...bbbbbbbb...",
        "..bWWWWWWWWb..",
        ".bgggggggggggb",
        ".bWWWWWWWWWWBb",
        "bWWWWWvvWWWWBb",
        "bWWWWvPPvWWWBb",
        "bWWWWWvvWWWWBb",
        ".bgggggggggggb",
        ".bWWWWWWWWWWBb",
        "..bbbbbbbbbbb.",
    ],
    "ks_fennel": [  # a rack of drying herbs
        "bbbbbbbbbbbbbbbb",
        "bW.k...k...k..Wb",
        "bW.E..eEe..E..Wb",
        "bWeEe.eEe.eEe.Wb",
        "bWeEe..e..eEe.Wb",
        "bW.e.......e..Wb",
        "bW............Wb",
        "bW............Wb",
        "bb............bb",
    ],
    "ks_rook": [  # an anvil on a block
        "SsssssssssssS...",
        ".SsLLLLLLLLsSSSS",
        "..SsssssssssSS..",
        "....SsssssS.....",
        "....bbbbbbb.....",
        "...bWWWWWWWb....",
        "...bWWWWWWWb....",
        "...bbbbbbbbb....",
    ],
    "ks_willa": [  # a carved bench with a heart
        "bbbbbbbbbbbbbbbbbb",
        "bWWWWWWWRRWWWWWWWb",
        "bWWWWWWRRRRWWWWWWb",
        "bWWWWWWWRRWWWWWWWb",
        "bbbbbbbbbbbbbbbbbb",
        "bWWWWWWWWWWWWWWWWb",
        "bbbbbbbbbbbbbbbbbb",
        ".bW............bW.",
        ".bW............bW.",
    ],
    "ks_coco": [  # a cafe table with a parasol
        "....RRwwRRww....",
        "..RRwwRRwwRRww..",
        ".RRwwRRwwRRwwRR.",
        "kkkkkkkbkkkkkkkk",
        ".......bW.......",
        ".......bW.......",
        ".......bW.......",
        "...xxbbbbbbxx...",
        "..oWWWWWWWWWWo..",
        "..bbbbbbbbbbbb..",
        ".......bW.......",
        ".......bW.......",
        "......bbbbb.....",
    ],
    "festival_statue": [  # the valley's statue (Story Act 2 finale): a golden farmer with a sheaf
        "......yYy.......",
        ".....yYYYy......",
        ".....yYYYy..y...",
        "......yYy..yYy..",
        "....yyYYYyyYy...",
        "...yYYYYYYYy....",
        "...yYyYYYyy.....",
        "...yY.yYYy......",
        "......yYYy......",
        "......yYYy......",
        ".....yYyYYy.....",
        ".....yY..Yy.....",
        ".....yY..Yy.....",
        "....yyy..yyy....",
        "..GGGGGGGGGGGG..",
        "..LGGGGGGGGGGg..",
        "...GGGGGGGGGg...",
        "...GGkkkkkkGg...",
        "...GGGGGGGGGg...",
        "..gggggggggggg..",
    ],
}


def main():
    s = Sprites(sys.argv[1], "decor")
    for name, rows in KEEPSAKES.items():
        assert len({len(r) for r in rows}) == 1, (name, [len(r) for r in rows])
        im, pad = finish(from_map(rows, K))
        s.save(name, im, len(rows[0]) // 2 + pad, len(rows) - 1 + pad)
    print(json.dumps(s.pivots))


if __name__ == "__main__":
    main()
