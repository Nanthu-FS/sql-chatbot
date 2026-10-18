"""Farmhand bodies (style D3 "tall", picked in playtest round 1), hand-placed pixels.

A letter is a colour role, its case the light (upper lit, lower shadow, ^ ! highlights), lit
from the top-left like the rest of the art; each farmhand is a palette for the roles (PEOPLE).
Bodies are 11 wide and 21 tall (7 head, 6 torso, 8 leg rows) on a 13-wide canvas so a hoe or a
crate can stick out. finish() adds the 1px ink outline and the hard drop shadow.

Frames: se_idle, se_walk0/1, se_work0/1 (hoe up, hoe struck), se_carry0/1 (crate held in
front, walking), ne_idle, ne_walk0/1, ne_carry0/1. se faces the viewer. Pivot: between the feet.
people.py saves them (with the portraits).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, finish, new  # noqa: E402

CANVAS_W = 13

# ------------------------------------------------------------------ D3: tall, 11 wide, 20 tall
STYLE = dict(
    W=11,
    head={
        "bun": (["....H^H....", "..H^HHHhh..", "..RRRRRrrr.", "..hSeSeSh..", "..hSpxpSh..", "...SSmSs...", "....sSs...."],
                ["....H^H....", "..H^HHHhh..", "..RRRRRrrr.", "..HHHHHhh..", "..HHHHhhh..", "...HhHhh...", "....sSs...."]),
        "straw": (["...G!GGg...", "..RRRRRrr..", "NNNNNNNNNnn", "..hSeSeSh..", "..hzSxSzh..", "...SSmSs...", "....sSs...."],
                  ["...G!GGg...", "..RRRRRrr..", "NNNNNNNNNnn", "..HHHHHhh..", "..HHHHhhh..", "...hHhhh...", "....sSs...."]),
        "bob": (["...H^HHh...", "..H^HHHhh..", "..HHhHhHh..", "..HSeSeSh..", "..HSSxSSh..", "..HSpmpSh..", "..HhsSshh.."],
                ["...H^HHh...", "..H^HHHhh..", "..HHHHHhh..", "..HHHHHhh..", "..HHHHhhh..", "..HHHhhhh..", "..hhhhhhh.."]),
        "cap": (["...GGGGg...", "..G!GGGgg..", ".NNNNNNNnn.", "..hSeSeSh..", "..hSSxSSh..", "..FFFmFff..", "...FFFff..."],
                ["...GGGGg...", "..G!GGGgg..", "..NNNNNnn..", "..hhhhhhh..", "..hhhhhhh..", "...hhhhh...", "....sSs...."]),
        "braid": (["...H^HHh...", "..H^HHHhh..", "..H^HHHhh..", "..HSeSeSh..", "..HSSxSSh..", "...SpmpS...", "....sSs...."],
                  ["...H^HHh...", "..H^HHHhh..", "..HHHHHhh..", "..HHHHHhh..", "..HHHHhhh..", "...HTThh...", "....TtS...."]),
    },
    torso={
        "bib": ([".ACOOsOOCa.", ".ACBCCCBca.", ".ABBoBoBba.", ".ABBqqqBba.", ".SBBBBBBbs.", "..LLLLLll.."],
                [".ACCCCCCca.", ".ACBCCCBca.", ".ACCBCBcca.", ".ABBBBBBba.", ".SBBBBBBbs.", "..LLLLLll.."]),
        "plain": ([".ACOOsOOCa.", ".ACCCCCCca.", ".ACCCCCcca.", ".ACCCCCcca.", ".SYYYoYYYs.", "..LLLLLll.."],
                  [".ACCCCCCca.", ".ACCCCCCca.", ".ACCCCCcca.", ".ACCCCCcca.", ".SYYYYYYYs.", "..LLLLLll.."]),
        "apron": ([".ACOOsOOCa.", ".ACBBBBBca.", ".ABBqqBBba.", ".ABBBBBBba.", ".SBBBBBBbs.", "..BBBBBbb.."],
                  [".ACCCCCCca.", ".ACCCCCCca.", ".ACCCCCcca.", ".ACCCCCcca.", ".SBYYYYYBs.", "..LLLLLll.."]),
    },
    legs={"idle": ["..LLL.Lll..", "..LLL.Lll..", "..LPL.Lll..", "..LLL.Lll..", "..LLL.Lll..", "..KKK.KKk..", "..KKK.KKk..", ".KKKu.uKkk."],
          "walk": [".LLL...Lll.", ".LLL...Lll.", ".LPL...Lll.", ".LLL...Lll.", ".LLL...KKk.", ".KKK...KKk.", ".KKK..KKkk.", "KKKu......."]},
    braid_fr=[(3, 0, "T"), (3, 1, "T"), (3, 2, "v"), (4, 3, "t")], braid_bk=[(5, 0, "T"), (5, 1, "t"), (5, 2, "v")],
)

BASE = {"e": "ink", "w": "white", "K": "brown_d", "k": "ink", "u": "brown", "W": "brown_l", "w2": "brown",
        "Q": "green", "Y": "brown_d", "o": "amber", "O": "cream", "P": "brown_l", "v": "red"}


def look(**r):
    lk = dict(BASE)
    groups = {"hair": "Hh^", "skin": "Ssx", "shirt": "Cc", "bib": "Bbq", "legs": "Ll", "hat": "Gg!",
              "brim": "Nn", "band": "Rr", "beard": "Ff", "braid": "Tt"}
    for k, v in r.items():
        if k in groups:
            for L, c in zip(groups[k], v):
                lk[L] = c
            if k == "shirt":
                lk["A"], lk["a"] = v[0], v[1]
        else:
            lk[k] = v
    return lk


PEOPLE = {
    "rosa": ("bun", "bib", look(hair=("brown", "brown_d", "brown_l"), skin=("brown_l", "brown", "brown"), shirt=("cream", "tan"),
                                bib=("green_d", "green_dd", "green_dd"), legs=("green_d", "green_dd"), band=("red", "red_d"),
                                p="rust", m="brown_d", O="white", P="green", V="red")),
    "milo": ("straw", "bib", look(hair=("rust", "brown", "clay"), skin=("skin", "tan", "tan_d"), shirt=("white", "grey_l"),
                                  bib=("blue", "blue_d", "blue_d"), legs=("blue", "blue_d"), hat=("amber", "clay", "yellow"),
                                  brim=("amber", "tan_d"), band=("red", "red_d"), z="tan_d", m="red_d", O="grey_l", P="cyan", V="orange")),
    "hana": ("bob", "plain", look(hair=("slate_d", "ink", "slate"), skin=("tan", "tan_d", "tan_d"), shirt=("yellow", "amber"),
                                  legs=("blue_d", "slate"), p="pink", m="red_d", O="white", P="blue", V="green")),
    "otis": ("cap", "plain", look(hair=("grey_l", "grey"), skin=("tan", "tan_d", "brown_l"), shirt=("orange", "rust"),
                                  legs=("brown", "brown_d"), hat=("brown", "brown_d", "brown_l"), brim=("brown_d", "ink"),
                                  beard=("grey_l", "grey"), Y="ink", m="brown_d", O="cream", P="tan_d", V="orange")),
    "wren": ("braid", "apron", look(hair=("yellow", "amber", "cream"), skin=("skin", "tan", "tan_d"), shirt=("magenta", "purple"),
                                    bib=("brown_l", "brown", "brown"), legs=("slate", "slate_d"), braid=("yellow", "amber"),
                                    p="pink", m="red_d", O="pink", P="slate_d", v="pink", V="magenta")),
}


FRAMES = [
    ("se", "idle", 0, "idle"), ("se", "walk", 0, "walk0"), ("se", "walk", 1, "walk1"),
    ("se", "work", 0, "work0"), ("se", "work", 1, "work1"),
    ("se", "carry", 0, "carry0"), ("se", "carry", 1, "carry1"),
    ("ne", "idle", 0, "idle"), ("ne", "walk", 0, "walk0"), ("ne", "walk", 1, "walk1"),
    ("ne", "carry", 0, "carry0"), ("ne", "carry", 1, "carry1"),
]

# Crate of produce (V produce, Q leaves, W/X wood) over torso rows 2-4; from behind its ends show.
CRATE_FR = [".SQVVVVVQs.", ".AWWWWWWXa.", "..XXXXXXX.."]
CRATE_BK = ["W.........W", "W.........W", "W.........W"]


def legs(pose, phase, n):
    rows = STYLE["legs"]["walk" if pose in ("walk", "carry") else "idle"]
    rows = rows[len(rows) - n:] if n < len(rows) else rows
    return [r[::-1] for r in rows] if phase and pose in ("walk", "carry") else rows


def grid(name, view, pose, phase):
    head_k, torso_k, lk = PEOPLE[name]
    back = view == "ne"
    head = STYLE["head"][head_k][1 if back else 0]
    torso = list(STYLE["torso"][torso_k][1 if back else 0])
    if pose == "carry":
        over = CRATE_BK if back else CRATE_FR
        for i, o in enumerate(over):
            torso[2 + i] = "".join(c if c != "." else b for c, b in zip(o, torso[2 + i]))
    work = pose == "work"
    body = (["." * 11] if work else []) + head + torso + legs(pose, phase, 7 if work else 8)
    g = [["."] * CANVAS_W for _ in body]
    for y, row in enumerate(body):
        assert len(row) == 11, (name, row)
        for x, ch in enumerate(row):
            if ch != ".":
                g[y][x + 1] = ch
    ty = (1 if work else 0) + len(head)
    if head_k == "braid" and not (pose == "carry" and not back):
        for x, dy, ch in STYLE["braid_bk" if back else "braid_fr"]:
            g[ty + dy][x + 1] = ch
    if work:
        hy = ty + 4                     # hands row
        g[hy][2] = "A"                  # the left hand moves onto the handle
        g[hy][10] = "S"
        H = len(g)
        hoe = ([(11, hy - 1, "d"), (11, hy - 2, "d"), (12, hy - 3, "d"), (12, hy - 4, "d"), (12, hy - 5, "d"),
                (11, hy - 6, "I"), (12, hy - 6, "i")] if phase == 0 else
               [(11, hy + 1, "d"), (11, hy + 2, "d"), (12, hy + 3, "d"), (12, hy + 4, "d"), (12, H - 2, "d"),
                (11, H - 1, "I"), (12, H - 1, "i")])
        for x, y, ch in hoe:
            if 0 <= y < H:
                g[y][x] = ch
    return g, lk


TOOLS = {"V": "red", "Q": "green", "W": "brown_l", "X": "brown", "d": "brown_l", "I": "grey_l", "i": "grey"}


def sprite(name, view, pose, phase):
    """(image, pad, body height) of one frame."""
    g, lk = grid(name, view, pose, phase)
    H = len(g)
    im = new(CANVAS_W, H)
    px = im.load()
    for y in range(H):
        for x in range(CANVAS_W):
            ch = g[y][x]
            if ch != ".":
                px[x, y] = E[lk.get(ch) or TOOLS[ch]]
    im, pad = finish(im)
    return im, pad, H
