"""Deep-mine critters (round 6, folder "critters"): a purple cave bat (wings up and down), a
crystal-blue slime (tall and squashed) and an armoured beetle (two leg steps), side-on facing right;
the engine mirrors them. Pivot = bottom middle (the bat hovers above its cell).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import Sprites, finish, from_map  # noqa: E402

BAT_KEY = {"P": "magenta", "p": "pink", "e": "white"}
SLIME_KEY = {"g": "blue_d", "G": "blue", "h": "cyan", "w": "white", "k": "ink"}
BEETLE_KEY = {"o": "slate", "O": "grey_d", "y": "amber", "h": "slate_d", "e": "red", "l": "ink"}

CRITTERS = {
    "bat": ([
        "P.........P",
        "PP.......PP",
        "PPP.ppp.PPP",
        ".PPpeppePP.",
        "..PPpppPP..",
        "....p.p....",
    ], [
        "...........",
        "....ppp....",
        "...peppe...",
        ".PPPpppPPP.",
        "PPPP...PPPP",
        "P.........P",
    ], BAT_KEY),
    "slime": ([
        "...gggg...",
        "..gGhhGg..",
        ".gGwGGwGg.",
        ".gGkGGkGg.",
        "gGGGGGGGGg",
        "gGGGGGGGGg",
        ".gggggggg.",
    ], [
        "..........",
        "...gggg...",
        ".ggGhhGgg.",
        "gGwGGGGwGg",
        "gGkGGGGkGg",
        "gGGGGGGGGg",
        ".gggggggg.",
    ], SLIME_KEY),
    "beetle": ([
        "....oooo.....",
        "..ooOOOOoo...",
        ".oOyOOOOyOohh",
        ".oOOOOOOOOohe",
        "..oooooooohh.",
        "..l.l...l.l..",
    ], [
        "....oooo.....",
        "..ooOOOOoo...",
        ".oOyOOOOyOohh",
        ".oOOOOOOOOohe",
        "..oooooooohh.",
        "...l.l.l.l...",
    ], BEETLE_KEY),
}


def main():
    s = Sprites(sys.argv[1], "critters")
    for kind, (f0, f1, key) in CRITTERS.items():
        for k, rows in enumerate((f0, f1)):
            assert len({len(r) for r in rows}) == 1, (kind, k)
            im, pad = finish(from_map(rows, key))
            s.save(f"{kind}_{k}", im, len(rows[0]) // 2 + pad, len(rows) - 1 + pad)
    print(json.dumps(s.pivots))


if __name__ == "__main__":
    main()
