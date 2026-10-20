"""Deep-mine relics (round 6, folder "relics"): eight collectibles for the relic page, about 12px.
Pivot = centre.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import Sprites, finish, from_map  # noqa: E402

RELICS = {
    "idol": ([
        "....gggg....",
        "...gGGGGg...",
        "...GkGGkG...",
        "...GGGGGG...",
        "....GmmG....",
        "...gGGGGg...",
        "..gGGGGGGg..",
        "..GG.GG.GG..",
        "...GGGGGG...",
        "...GG..GG...",
        "..gggggggg..",
    ], {"G": "grey", "g": "grey_d", "k": "ink", "m": "slate_d"}),
    "fossil": ([
        "..tttttttt..",
        ".tTTTTTTTTt.",
        "tTTTcTcTcTTt",
        "tTcTcTcTcTcc",
        "tccccccccccc",
        "tTcTcTcTcTcc",
        "tTTTcTcTcTTt",
        ".tTTTTTTTTt.",
        "..tttttttt..",
    ], {"t": "tan_d", "T": "tan", "c": "cream"}),
    "sword": ([
        "..........gG",
        ".........gGg",
        "........gGg.",
        ".......gGg..",
        "......gGg...",
        ".....gGg....",
        "..y.gGg.....",
        "...yGg......",
        "...yy.......",
        "..b.yy......",
        ".b....y.....",
        "b...........",
    ], {"g": "grey", "G": "white", "y": "amber", "b": "brown"}),
    "skull": ([
        "...cccccc...",
        "..cCCCCCCc..",
        ".cCWCCCCWCc.",
        ".cCkkCCkkCc.",
        ".cCkkCCkkCc.",
        ".cCCCkkCCCc.",
        "..cCCCCCCc..",
        "...cWcWcWc..",
        "...cccccc...",
    ], {"C": "cyan", "c": "blue", "W": "white", "k": "blue_d"}),
    "scarab": ([
        "....y..y....",
        ".....yy.....",
        "...yYYYYy...",
        "..yYYoYYYy..",
        ".yYYYoYYYYy.",
        "yYYYYoYYYYYy",
        ".yYYYoYYYYy.",
        "..yYYoYYYy..",
        "...yyyyyy...",
        "..y..y...y..",
    ], {"y": "amber", "Y": "yellow", "o": "rust"}),
    "tablet": ([
        ".ssssssssss.",
        "sSSSSSSSSSSs",
        "sScSScScSSSs",
        "sScccSccScSs",
        "sSSSSSSSSSSs",
        "sSccSScSccSs",
        "sScSScccSSSs",
        "sSSSSSSSSSSs",
        "sScScSSccSSs",
        ".ssssssssss.",
    ], {"S": "slate", "s": "slate_d", "c": "cyan"}),
    "crown": ([
        "y....y....y.",
        "yy..yYy..yy.",
        "yYyyYYYyyYy.",
        "yYYYYrYYYYy.",
        "yYYYrrrYYYy.",
        "yyyyyyyyyyy.",
    ], {"y": "amber", "Y": "yellow", "r": "red"}),
    "golem_heart": ([
        ".cc....cc...",
        "cCWc..cCCc..",
        "cCCCccCCCCc.",
        "cCCCCCCCCCc.",
        ".cCCCmCCCc..",
        "..cCCmCCc...",
        "...cCmCc....",
        "....cCc.....",
        ".....c......",
    ], {"C": "cyan", "c": "blue_d", "W": "white", "m": "magenta"}),
}


def main():
    s = Sprites(sys.argv[1], "relics")
    for name, (rows, key) in RELICS.items():
        assert len({len(r) for r in rows}) == 1, name
        im, pad = finish(from_map(rows, key))
        s.save(name, im, len(rows[0]) // 2 + pad, len(rows) // 2 + pad)
    print(json.dumps(s.pivots))


if __name__ == "__main__":
    main()
