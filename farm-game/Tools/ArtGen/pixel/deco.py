"""Standing decorations scattered on the ground: tall grass (2 sway frames), flower
clumps, bushes, rocks, stumps, logs, mushrooms, butterflies (lamp posts and fences: farm_hand.py).
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

BUTTERFLY_A = ["p.p", "pkp", ".k."]
BUTTERFLY_B = [".p.", "pkp", ".k."]


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


_TREES = {}


def _trees():
    """trees.py's foliage helpers (its main() left out), for bushes drawn the same way."""
    if not _TREES:
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "trees.py")
        src = open(path).read()
        src = src[: src.rstrip().rfind("main()")]
        _TREES.update({"__file__": path, "__name__": "deco_trees"})
        exec(compile(src, path, "exec"), _TREES)
    return _TREES


def leafy_bush(berries):
    """A small round bush of leaf clusters, like the tree canopies."""
    T = _trees()
    c = T["Canvas"]()
    T["canopy"](c, T["Geo"](1.0), [(32, 62, 7.5, 4.8), (26.5, 63.5, 4.2, 3.3), (37.5, 63.5, 4.2, 3.3), (32, 58.2, 5, 3.2)], T["LEAF"])
    if berries:
        for x, y in ((29, 59), (35, 60), (27, 63), (33, 64), (38, 63)):
            if c.on(x, y) and c.on(x + 1, y + 1):
                c.put(x, y, "pink")
                c.put(x + 1, y, "red")
                c.put(x, y + 1, "red")
                c.put(x + 1, y + 1, "red_d")
    return c.im.crop(c.im.getbbox())


def main():
    s = Sprites(sys.argv[1], "deco")
    save(s, "tallgrass_a", from_map(TALL_A, G))
    save(s, "tallgrass_b", from_map(TALL_B, G))
    save(s, "bush_snow", snowy(leafy_bush(False)))
    for name, rows, key in (("tallgrass_a", TALL_A, G), ("tallgrass_b", TALL_B, G),
                            ("rock", ROCK, ROCK_KEY), ("rock_big", ROCK_BIG, ROCK_KEY), ("stump", STUMP, STUMP_KEY),
                            ("log", LOG, {**STUMP_KEY, "r": "brown_l"}), ("reeds", REEDS, G)):
        save(s, f"{name}_snow", snowy(from_map(rows, key)))
    save(s, "reeds", from_map(REEDS, G))
    for name, petal in (("red", "red"), ("white", "white"), ("pink", "pink"), ("blue", "cyan"), ("purple", "magenta")):
        save(s, f"flowers_{name}", from_map(FLOWERS, {**G, "p": petal}))
    save(s, "bush", leafy_bush(False))
    save(s, "bush_berry", leafy_bush(True))
    save(s, "rock", from_map(ROCK, ROCK_KEY))
    save(s, "rock_big", from_map(ROCK_BIG, ROCK_KEY))
    save(s, "stump", from_map(STUMP, STUMP_KEY))
    save(s, "log", from_map(LOG, {**STUMP_KEY, "r": "brown_l"}))
    save(s, "mushrooms", from_map(MUSHROOMS, MUSH_KEY))
    # The lamp post and the fences are hand-pixeled in farm_hand.py (art round 5).
    for name, rows in (("butterfly_a", BUTTERFLY_A), ("butterfly_b", BUTTERFLY_B)):
        im, pad = finish(from_map(rows, {"p": "pink", "k": "ink"}), outline=False, shadow=False)
        s.save(name, im, 1 + pad, 1 + pad)
    s.done()


main()
