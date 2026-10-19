"""The farm pets (round 5, folder "pets"): a tan farm dog and an orange tabby cat, both with the
farm's red collar. Each has a walk cycle seen from the side going down-right (se_0/1), from behind
going up-right (ne_0/1), and a sitting pose (sit); the engine mirrors them for the left-hand
directions. About knee-high to a farmhand. Pivot = between the feet on the ground.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import Sprites, finish, from_map  # noqa: E402

DOG_KEY = {"T": "tan", "t": "tan_d", "c": "cream", "b": "brown", "k": "ink", "r": "red", "p": "pink"}
CAT_KEY = {"O": "orange", "o": "rust", "c": "cream", "k": "ink", "r": "red", "p": "pink", "g": "green"}

DOG = {
    "se_0": [
        "........bTb..",
        "........TTTT.",
        "t.......TkTcc",
        "t.......TTTck",
        ".tTTTTTTrrTp.",
        ".tTTTTTTTTt..",
        "..tccccccct..",
        "..T.T...T.T..",
        "..t.t...t.t..",
    ],
    "se_1": [
        "........bTb..",
        "........TTTT.",
        ".t......TkTcc",
        ".t......TTTck",
        ".tTTTTTTrrTp.",
        ".tTTTTTTTTt..",
        "..tccccccct..",
        "...TT....TT..",
        "...tt....tt..",
    ],
    "ne_0": [
        "........bTb..",
        "........TTTb.",
        ".......tTTT..",
        "t......rrT...",
        "t.tTTTTTTT...",
        ".tTTTTTTTt...",
        "..tTTTTTt....",
        "..T.T..T.T...",
        "..t.t..t.t...",
    ],
    "ne_1": [
        "........bTb..",
        "........TTTb.",
        ".......tTTT..",
        ".t.....rrT...",
        ".ttTTTTTTT...",
        ".tTTTTTTTt...",
        "..tTTTTTt....",
        "...TT...TT...",
        "...tt...tt...",
    ],
    "sit": [
        "......bTb..",
        "......TTTT.",
        "......TkTcc",
        "......TTTck",
        ".....rrTp..",
        "...TTTTT...",
        "..TTTTcT...",
        "t.TTTTcT...",
        "ttTTTT.T...",
        "..ttt..t...",
    ],
}

CAT = {
    "se_0": [
        "........O.O.",
        "........OOOO",
        "O.......OkOc",
        "O.......OOpc",
        ".O.oOoOOrr..",
        "..OOOOOOOO..",
        "..OccccccO..",
        "..O.O..O.O..",
        "..o.o..o.o..",
    ],
    "se_1": [
        "........O.O.",
        "........OOOO",
        ".O......OkOc",
        ".O......OOpc",
        ".O.oOoOOrr..",
        "..OOOOOOOO..",
        "..OccccccO..",
        "...OO...OO..",
        "...oo...oo..",
    ],
    "ne_0": [
        "........O.O.",
        "........OOOO",
        ".......oOOO.",
        "O......rrO..",
        "O.OoOoOOOO..",
        ".OOOOOOOOo..",
        "..OOOOOOo...",
        "..O.O..O.O..",
        "..o.o..o.o..",
    ],
    "ne_1": [
        "........O.O.",
        "........OOOO",
        ".......oOOO.",
        ".O.....rrO..",
        ".OOoOoOOOO..",
        ".OOOOOOOOo..",
        "..OOOOOOo...",
        "...OO...OO..",
        "...oo...oo..",
    ],
    "sit": [
        "......O.O.",
        "......OOOO",
        "......OkOc",
        "......OOpc",
        ".....rrO..",
        "...OoOO...",
        "..OOOOcO..",
        ".OOoOOcO..",
        "OOOOOO.O..",
        "O.ooo..o..",
    ],
}


def sprites():
    for kind, frames, key in (("dog", DOG, DOG_KEY), ("cat", CAT, CAT_KEY)):
        for name, rows in frames.items():
            assert len({len(r) for r in rows}) == 1, (kind, name, [len(r) for r in rows])
            yield f"{kind}_{name}", rows, key


def main():
    s = Sprites(sys.argv[1], "pets")
    for name, rows, key in sprites():
        im, pad = finish(from_map(rows, key))
        # Pivot between the front and back feet, on the ground row.
        s.save(name, im, len(rows[0]) // 2 + pad, len(rows) - 1 + pad)
    print(json.dumps(s.pivots))


if __name__ == "__main__":
    main()
