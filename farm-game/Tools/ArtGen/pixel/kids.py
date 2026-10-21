"""The farmer's kid (round 7, Part 5): folder "people", kid_<stage>_<g>_<pose> with stage baby,
toddler or child, g = b (boy) or g (girl), pose idle, walk0, walk1 (the baby has idle only: on a
blanket, waving). Character maps facing the viewer, lit from the top-left; finish() adds the ink
outline and drop shadow. Pivot = between the feet on the ground (the code flips them for left).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import Sprites, finish, from_map  # noqa: E402

BASE = {"s": "skin", "S": "tan", "e": "ink", "c": "pink", "w": "white", "W": "grey_l", "B": "brown_d"}
LOOK = {
    "b": {"h": "brown", "H": "brown_d", "o": "blue", "O": "blue_d", "t": "yellow", "T": "amber", "f": "brown_d"},
    "g": {"h": "amber", "H": "rust", "o": "pink", "O": "magenta", "t": "white", "T": "grey_l", "f": "red_d"},
}

BABY = [  # sitting on a little quilt, one arm waving
    "......hhh.......",
    ".....hsssh......",
    ".....sesesS.....",
    ".....sscssS..s..",
    "......SSS...ss..",
    "....tttttttts...",
    "...stttttttT....",
    "...s.tTtttT.....",
    "....ssttttss....",
    "..rrrrrrrrrrrr..",
    ".ryrryrryrryrrr.",
    "rrrrrrrrrrrrrrrr",
]
TODDLER = {
    "idle": [
        "...hhhh...",
        "..hhhhhh..",
        "..hsssss..",
        "..seSseS..",
        "..ssscsS..",
        "...SSSS...",
        "..tttttt..",
        ".sotttttS.",
        ".soooooOS.",
        "..oooooO..",
        "..oO.oO...",
        "..ss..ss..",
        "..ff..ff..",
    ],
    "walk0": [
        "...hhhh...",
        "..hhhhhh..",
        "..hsssss..",
        "..seSseS..",
        "..ssscsS..",
        "...SSSS...",
        "..tttttt..",
        "sootttttS.",
        "..oooooOS.",
        "..oooooO..",
        ".oO...oO..",
        ".ss...ss..",
        ".ff....ff.",
    ],
    "walk1": [
        "...hhhh...",
        "..hhhhhh..",
        "..hsssss..",
        "..seSseS..",
        "..ssscsS..",
        "...SSSS...",
        "..tttttt..",
        ".sottttoos",
        ".soooooO..",
        "..oooooO..",
        "...oOoO...",
        "...ssss...",
        "...ffff...",
    ],
}
CHILD = {
    "idle": [
        "....hhhh....",
        "...hhhhhH...",
        "..hhhhhhhH..",
        "..hssssssH..",
        "..sseSseSS..",
        "..ssssssSS..",
        "...sscsSS...",
        "....SSSS....",
        "...tttttT...",
        "..ttttttTT..",
        ".sotttttToS.",
        ".soottttooS.",
        ".s.ooooooOs.",
        "...ooooooO..",
        "...oOO.oOO..",
        "...ss...ss..",
        "...SS...SS..",
        "..fff..fff..",
    ],
    "walk0": [
        "....hhhh....",
        "...hhhhhH...",
        "..hhhhhhhH..",
        "..hssssssH..",
        "..sseSseSS..",
        "..ssssssSS..",
        "...sscsSS...",
        "....SSSS....",
        "...tttttT...",
        "..ttttttTT..",
        "sootttttToS.",
        "..oottttooS.",
        "...ooooooOs.",
        "...ooooooO..",
        "..oOO...oOO.",
        "..ss.....ss.",
        "..SS.....SS.",
        ".fff.....fff",
    ],
    "walk1": [
        "....hhhh....",
        "...hhhhhH...",
        "..hhhhhhhH..",
        "..hssssssH..",
        "..sseSseSS..",
        "..ssssssSS..",
        "...sscsSS...",
        "....SSSS....",
        "...tttttT...",
        "..ttttttTT..",
        ".sotttttTooo",
        ".soottttoo..",
        ".s.ooooooO..",
        "...ooooooO..",
        "....oOoOO...",
        "....ssss....",
        "....SSSS....",
        "...ffffff...",
    ],
}


def girlify(rows, stage):
    """Girls get pigtails (toddler) or long hair (child), and a dress instead of overalls."""
    out = [list(r) for r in rows]
    if stage == "toddler":
        for y in (1, 2):
            out[y][0] = "h"; out[y][-1] = "h"; out[y][1] = "H"; out[y][-2] = "H"
    else:
        for y in range(3, 9):
            out[y][1] = "h"; out[y][-2] = "H"
    return ["".join(r) for r in out]


def main():
    sp = Sprites(sys.argv[1], "people")
    for g, look in LOOK.items():
        key = {**BASE, **look, "r": "red", "y": "yellow"}
        rows = [r.replace("t", "o") if g == "g" else r for r in BABY]
        body = from_map(rows, key)
        im, pad = finish(body)
        sp.save(f"kid_baby_{g}_idle", im, body.size[0] // 2 + pad, body.size[1] - 1 + pad)
        for stage, frames in (("toddler", TODDLER), ("child", CHILD)):
            for pose, rows in frames.items():
                if g == "g":
                    rows = girlify(rows, stage)
                assert len({len(r) for r in rows}) == 1, (stage, pose)
                body = from_map(rows, key)
                im, pad = finish(body)
                sp.save(f"kid_{stage}_{g}_{pose}", im, body.size[0] // 2 + pad, body.size[1] - 1 + pad)
    print(json.dumps(sp.pivots))


if __name__ == "__main__":
    main()
