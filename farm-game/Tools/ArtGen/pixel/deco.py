"""Standing decorations scattered on the ground: tall grass (2 sway frames), flower
clumps, bushes, rocks, stumps, logs, mushrooms, lanterns, iso fences, butterflies.
Pivot = where the object touches the ground (bottom centre), after the 2px pad.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, Sprites, finish, from_map, new  # noqa: E402

G = {"g": "green", "G": "green_d", "d": "green_dd", "y": "yellow", "Y": "amber", "w": "white"}


def save(s, name, body, pivot_x=None, pivot_y=None, outline=True, shadow=True):
    im, pad = finish(body, outline=outline, shadow=shadow)
    ax = (body.size[0] // 2 if pivot_x is None else pivot_x) + pad
    ay = (body.size[1] - 1 if pivot_y is None else pivot_y) + pad
    s.save(name, im, ax, ay)


TALL_A = [
    "....g.....",
    "..g.g..g..",
    "..g.gg.g..",
    ".gG.gG.gg.",
    ".gG.GG.Gg.",
    "gGG.GGgGG.",
    "GGGgGGGGGg",
    "dGGGGdGGGd",
]
TALL_B = [
    ".....g....",
    "...g.g..g.",
    "..g.gg.g..",
    ".gG.gG.gg.",
    ".gG.GG.Gg.",
    "gGG.GGgGG.",
    "GGGgGGGGGg",
    "dGGGGdGGGd",
]
REEDS = [
    "..Y....Y..",
    "..Y..Y.Y..",
    "..g..Y.g..",
    ".gg..g.gg.",
    ".gG.gG.Gg.",
    "gGG.GGgGG.",
    "GGGgGGGGGd",
]

FLOWERS = [
    ".p...p..",
    "pyp.pyp.",
    ".p.g.p..",
    "..gGg.p.",
    ".gGGGpyp",
    "gGGdGGp.",
    ".dGGGd..",
]

BUSH = [
    ".....gggg.......",
    "...ggggggGg.....",
    "..gggwggGGGgg...",
    ".ggggggGGGGGGg..",
    ".gggggGGGGrGGGg.",
    "gggggGGGGGGGGGG.",
    "ggggGGGrGGGGGdGg",
    "gGGGGGGGGGGdGGGG",
    ".GGGGGdGGGddGGd.",
    "..dGGGGddGGddd..",
    "....dddddddd....",
]

ROCK = [
    "...aaaa...",
    "..aAAAAa..",
    ".aAAAAAAs.",
    "aAAAAAAsss",
    "sAAAAAssss",
    ".sssssss..",
]
ROCK_BIG = [
    ".....aaaa.......",
    "...aaAAAAaa.....",
    "..aAAAAAAAAas...",
    ".aAAAAAAAAAAss..",
    "aAAAAAAAAAAsssa.",
    "sAAAAAAAAAsssAAs",
    "ssAAAAAAAssssAss",
    ".sssAAAssssssss.",
    "...ssssssssss...",
]
ROCK_KEY = {"a": "grey_l", "A": "grey", "s": "grey_d"}

STUMP = [
    "..tttttttt..",
    ".tTrrrrrrTt.",
    "tTrTTTTTTrTt",
    "tTrTrrrrTrTt",
    ".tTrrrrrrTt.",
    "bbttttttttbb",
    "bbBbbbbbbBbb",
    "bBBbbbbbBBbb",
    "BB.BBBBBB.BB",
]
STUMP_KEY = {"t": "tan_d", "T": "tan", "r": "brown_l", "b": "brown_l", "B": "brown"}

LOG = [
    "....bbbbbbbbbbb.",
    "..bbBbbbbbbbbbtt",
    ".bBBbbbbbbbbbtTr",
    "bBBBBBbbbbbbbtrT",
    "BBBBBBBBBBBBBtTr",
    ".BBBBBBBBBBBBBtt",
    "...BBBBBBBBBBB..",
]

MUSHROOMS = [
    ".rrr.....",
    "rwrrr....",
    "rrrwr.rr.",
    ".cc..rwrr",
    ".cc...cc.",
    ".cc...cc.",
]
MUSH_KEY = {"r": "red", "w": "white", "c": "cream"}

LANTERN = [
    ".iii.",
    "iyyyi",
    "iYwYi",
    "iyyyi",
    ".iii.",
    "..b..",
    "..b..",
    "..b..",
    "..b..",
    "..B..",
    "..B..",
    ".bBb.",
]
LANTERN_KEY = {"i": "ink", "y": "yellow", "Y": "amber", "w": "white", "b": "brown_l", "B": "brown"}

BUTTERFLY_A = ["p.p", "pkp", ".k."]
BUTTERFLY_B = [".p.", "pkp", ".k."]


def fence(direction):
    """Iso fence segment one tile long. direction 1: along +col (down-right), -1: along +row (down-left)."""
    w, h = 18, 16
    im = new(w, h)
    px = im.load()
    x0 = 0 if direction == 1 else w - 1
    for i in range(17):
        x = x0 + i * direction
        y = 7 + i // 2
        for rail in (0, 4):
            if 0 <= x < w and 0 <= y - rail < h:
                px[x, y - rail] = E["tan"] if rail == 4 else E["tan_d"]
    for i in (1, 15):
        x = x0 + i * direction
        top = 7 + i // 2 - 7
        for y in range(top, top + 9):
            for dx in (0, direction):
                xx = x + dx
                if 0 <= xx < w and 0 <= y < h:
                    px[xx, y] = E["brown_l"] if dx == 0 else E["brown"]
        if 0 <= x < w and 0 <= top < h:
            px[x, top] = E["tan"]
    return im


def snowy(body):
    """Winter version: snow caps on every upward-facing edge, greens frosted to grey-green."""
    im = body.copy()
    px = im.load()
    w, h = im.size
    frost = {E["green"]: E["grey_l"], E["green_d"]: E["green_d"], E["green_dd"]: E["slate"]}
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 0:
                continue
            if y == 0 or px[x, y - 1][3] == 0:
                px[x, y] = E["white"]
                if y + 1 < h and px[x, y + 1][3] and (x + y) % 2 == 0:
                    px[x, y + 1] = E["grey_l"]
            elif c in frost and (x + y) % 3 == 0:
                px[x, y] = frost[c]
    return im


def main():
    s = Sprites(sys.argv[1], "deco")
    save(s, "tallgrass_a", from_map(TALL_A, G))
    save(s, "tallgrass_b", from_map(TALL_B, G))
    for name, rows, key in (("tallgrass_a", TALL_A, G), ("tallgrass_b", TALL_B, G), ("bush", BUSH, {**G, "r": "green"}),
                            ("rock", ROCK, ROCK_KEY), ("rock_big", ROCK_BIG, ROCK_KEY), ("stump", STUMP, STUMP_KEY),
                            ("log", LOG, {**STUMP_KEY, "r": "brown_l"}), ("reeds", REEDS, G)):
        save(s, f"{name}_snow", snowy(from_map(rows, key)))
    save(s, "reeds", from_map(REEDS, G))
    for name, petal in (("red", "red"), ("white", "white"), ("pink", "pink"), ("blue", "cyan"), ("purple", "magenta")):
        save(s, f"flowers_{name}", from_map(FLOWERS, {**G, "p": petal}))
    save(s, "bush", from_map(BUSH, {**G, "r": "green"}))
    save(s, "bush_berry", from_map(BUSH, {**G, "r": "red"}))
    save(s, "rock", from_map(ROCK, ROCK_KEY))
    save(s, "rock_big", from_map(ROCK_BIG, ROCK_KEY))
    save(s, "stump", from_map(STUMP, STUMP_KEY))
    save(s, "log", from_map(LOG, {**STUMP_KEY, "r": "brown_l"}))
    save(s, "mushrooms", from_map(MUSHROOMS, MUSH_KEY))
    save(s, "lantern", from_map(LANTERN, LANTERN_KEY))
    # Fences: pivot at the segment's start tile corner so segments chain along tile edges.
    for name, d in (("fence_r", 1), ("fence_l", -1)):
        im, pad = finish(fence(d))
        s.save(name, im, (0 if d == 1 else 17) + pad, 7 + pad)
    for name, rows in (("butterfly_a", BUTTERFLY_A), ("butterfly_b", BUTTERFLY_B)):
        im, pad = finish(from_map(rows, {"p": "pink", "k": "ink"}), outline=False, shadow=False)
        s.save(name, im, 1 + pad, 1 + pad)
    s.done()


main()
