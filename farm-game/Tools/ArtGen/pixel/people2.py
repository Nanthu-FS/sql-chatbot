"""Farmhands, small and hand-shaded (pixel-direct). Replaces people.py's 14x22 bodies.

Every pixel is placed by hand in the templates below: a letter is a colour role and its case
is the light (upper = lit, lower = shadow, ^ ! = highlight), lit from the top-left like the rest
of the art. Each farmhand is a palette for those roles. finish() adds the 1px ink outline and
the hard drop shadow.

Bodies are 11 wide; HEIGHTS picks the torso and leg rows (variant "A" 16 tall, "B" chibi 13
tall, "C" 19 tall). The canvas is 13 wide so a hoe or crate can stick out.

Frames (folder "people", pivot between the feet): se_idle, se_walk0/1, se_work0/1 (hoe up,
hoe down), se_carry0/1, ne_idle, ne_walk0/1, ne_carry0/1. se faces the viewer.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, Sprites, finish, new  # noqa: E402
import json  # noqa: E402

W = 13
HEIGHTS = {"A": (5, 5), "B": (4, 3), "C": (6, 7)}  # torso rows, leg rows (head is 6)

# ---------------------------------------------------------------- heads (6 rows, 11 wide)
# H h ^ hair (lit, shade, highlight), G g ! hat crown, N n brim, R r band, S s skin,
# e eyes, m mouth, p blush, z freckles, F f beard, T t braid.
HEAD = {
    "bun": {"fr": ["....H^H....", "..H^HHHhh..", "..RRRRRrr..", "..HSeSeSh..", "..HpSmSph..", "...SSSSs..."],
            "bk": ["....H^H....", "..H^HHHhh..", "..RRRRRrr..", "..HHHHHhh..", "..HHHHhhh..", "...sSSSs..."]},
    "straw": {"fr": ["...G!GGg...", "..RRRRRrr..", "NNNNNNNNnnn", "..HSeSeSh..", "..HzSmSzh..", "...SSSSs..."],
              "bk": ["...G!GGg...", "..RRRRRrr..", "NNNNNNNNnnn", "..HHHHHhh..", "..HHHHhhh..", "...sSSSs..."]},
    "bob": {"fr": ["...H^HHh...", "..H^HHHhh..", "..HHHHHhh..", "..HSeSeSh..", "..HpSmSph..", "..HHSSShh.."],
            "bk": ["...H^HHh...", "..H^HHHhh..", "..HHHHHhh..", "..HHHHHhh..", "..HHHHhhh..", "..HHHHhhh.."]},
    "cap": {"fr": ["...GGGGg...", "..G!GGGgg..", ".NNNNNNNnn.", "..hSeSeSh..", "..FFFmFff..", "...FFFFf..."],
            "bk": ["...GGGGg...", "..G!GGGgg..", "..NNNNNnn..", "..hhhhhhh..", "..hhhhhhh..", "...sSSSs..."]},
    "braid": {"fr": ["...H^HHh...", "..H^HHHhh..", "..HHHHHhh..", "..HSeSeSh..", "..HpSmSph..", "...SSSSs..."],
              "bk": ["...H^HHh...", "..H^HHHhh..", "..HHHHHhh..", "..HHHHHhh..", "..HHHHhhh..", "...STtSs..."]},
}

# ---------------------------------------------------------------- torsos: shoulders, middles, hands, hips
# A a arms (shirt), C c shirt, B b bib/apron, Y belt, y buckle, L l trousers, S s hands.
TORSO = {
    "bib": (".AcBCsCBca.", [".AcBBBBbca.", ".ABBBBBbba."], ".SBBBBBbbs.", "..LLLLLll.."),
    "bib_bk": (".AcBCCCBca.", [".AcCBCBcca.", ".ABBBBBbba."], ".SBBBBBbbs.", "..LLLLLll.."),
    "plain": (".AcCCsCCca.", [".AcCCCCCca.", ".AcCCCCcca."], ".SYYYyYYys.", "..LLLLLll.."),
    "plain_bk": (".AcCCCCCca.", [".AcCCCCCca.", ".AcCCCCcca."], ".SYYYYYYys.", "..LLLLLll.."),
    "apron": (".AcCCsCCca.", [".AcBBBBbca.", ".ABBBBBbba."], ".SBBBBBbbs.", "..BBBBBbb.."),
    "apron_bk": (".AcCCCCCca.", [".AcCCCCCca.", ".AcCCCCcca."], ".SBYYYYYBs.", "..LLLLLll.."),
}


def torso_rows(kind, n):
    top, mids, hands, hip = TORSO[kind]
    k = n - 3
    mid = [mids[min(i + max(0, len(mids) - k), len(mids) - 1)] for i in range(k)] if k > 0 else []
    return [top] + mid + [hands, hip]


def legs_rows(n, pose, phase):
    if pose == "walk":
        rows = ["..LL...Ll.."] * (n - 3) + ["..LL...Kk..", "..KK..KKk..", ".KKk......."]
        return [r[::-1] for r in rows] if phase else rows
    return ["...LL.Ll..."] * (n - 2) + ["...KK.Kk...", "..KKk.Kkk.."]


# A crate of produce held in front: V produce, Q leaves, W w wood. From behind only its ends show.
def carry_over(n, back):
    k = n - 2
    if back:
        return ["..........."] + ["W.........W"] * k + ["..........."]
    rows = [".SQVVVVVQs.", ".AWWWWWWwa.", "..wwwwwww.."]
    rows = ["..........."] * (k - 3) + rows if k >= 3 else [rows[0], rows[2]][:k]
    return ["..........."] + rows + ["..........."]


def merge(base, over):
    return ["".join(o if o != "." else b for o, b in zip(ov.ljust(len(bs), "."), bs)) for bs, ov in zip(base, over)]


def frame(p, view, pose, phase, nt, nl):
    back = view == "ne"
    head = HEAD[p["head"]]["bk" if back else "fr"]
    torso = torso_rows(p["torso"] + ("_bk" if back else ""), nt)
    if pose == "carry":
        torso = merge(torso, carry_over(nt, back))
    if pose == "work":
        # Bent over the hoe: head one row lower, knees bent (one leg row shorter).
        body = [".........."] + head + torso + legs_rows(nl - 1, "idle", 0)
    else:
        body = head + torso + legs_rows(nl, pose, phase)
    H = len(body)
    g = [["."] * W for _ in range(H)]
    for y, row in enumerate(body):
        for x, ch in enumerate(row):
            if ch != ".":
                g[y][x + 1] = ch
    oy = 1 if pose == "work" else 0
    if p["head"] == "braid" and not (pose == "carry" and not back):
        # Over the left shoulder in front, down the back from behind.
        pts = [(5, 6, "T"), (5, 7, "t")] if back else [(3, 6, "T"), (3, 7, "T"), (4, 8, "t")]
        for x, y, ch in pts:
            g[y + oy][x + 1] = ch
    if pose == "work":
        hy = oy + 6 + nt - 2           # hands row
        g[hy][2] = "A"                 # left hand moves onto the handle
        g[hy][10] = "S"
        if phase == 0:
            pts = [(11, hy - 1, "d"), (11, hy - 2, "d"), (12, hy - 3, "d"), (12, hy - 4, "d"),
                   (11, hy - 5, "I"), (12, hy - 5, "i")]
        else:
            pts = [(11, hy + 1, "d"), (11, hy + 2, "d"), (12, hy + 3, "d"), (12, H - 2, "d"),
                   (11, H - 1, "I"), (12, H - 1, "i")]
        for x, y, ch in pts:
            if 0 <= y < H:
                g[y][x] = ch
    return g


# ---------------------------------------------------------------- the five farmhands (role -> colour)
BASE = {"e": "ink", "K": "brown_d", "k": "ink", "W": "brown_l", "w": "brown", "Q": "green",
        "d": "brown_l", "I": "grey_l", "i": "grey", "Y": "brown_d", "y": "amber"}


def look(**roles):
    lk = dict(BASE)
    pairs = {"hair": "Hh^", "skin": "Ss", "shirt": "CcAa", "bib": "Bb", "legs": "Ll", "hat": "Gg!",
             "brim": "Nn", "band": "Rr", "beard": "Ff", "braid": "Tt"}
    for role, cols in roles.items():
        if role in pairs:
            letters = pairs[role]
            if role == "shirt":
                lk["C"], lk["c"], lk["A"], lk["a"] = cols[0], cols[1], cols[0], cols[1]
            else:
                for L, c in zip(letters, cols):
                    lk[L] = c
        else:
            lk[role] = cols
    return lk


PEOPLE = {
    "rosa": dict(head="bun", torso="bib", lk=look(hair=("brown", "brown_d", "brown_l"), skin=("brown_l", "brown"),
                 shirt=("cream", "tan"), bib=("green_d", "green_dd"), legs=("green_d", "green_dd"),
                 band=("red", "red_d"), p="rust", m="brown_d", V="red")),
    "milo": dict(head="straw", torso="bib", lk=look(hair=("rust", "brown", "clay"), skin=("skin", "tan"),
                 shirt=("white", "grey_l"), bib=("blue", "blue_d"), legs=("blue", "blue_d"),
                 hat=("amber", "clay", "yellow"), brim=("amber", "tan_d"), band=("red", "red_d"),
                 z="tan_d", m="red_d", V="orange")),
    "hana": dict(head="bob", torso="plain", lk=look(hair=("slate_d", "ink", "slate"), skin=("tan", "tan_d"),
                 shirt=("yellow", "amber"), legs=("blue_d", "slate"), p="pink", m="red_d", V="green")),
    "otis": dict(head="cap", torso="plain", lk=look(hair=("grey_l", "grey"), skin=("tan", "tan_d"),
                 shirt=("orange", "rust"), legs=("brown", "brown_d"), hat=("brown", "brown_d", "brown_l"),
                 brim=("brown_d", "ink"), beard=("grey_l", "grey"), Y="ink", m="brown_d", V="orange")),
    "wren": dict(head="braid", torso="apron", lk=look(hair=("yellow", "amber", "cream"), skin=("skin", "tan"),
                 shirt=("magenta", "purple"), bib=("brown_l", "brown"), legs=("slate", "slate_d"),
                 braid=("yellow", "amber"), p="pink", m="red_d", V="magenta")),
}

FRAMES = [
    ("se", "idle", 0, "idle"), ("se", "walk", 0, "walk0"), ("se", "walk", 1, "walk1"),
    ("se", "work", 0, "work0"), ("se", "work", 1, "work1"),
    ("se", "carry", 0, "carry0"), ("se", "carry", 1, "carry1"),
    ("ne", "idle", 0, "idle"), ("ne", "walk", 0, "walk0"), ("ne", "walk", 1, "walk1"),
    ("ne", "carry", 0, "carry0"), ("ne", "carry", 1, "carry1"),
]


def paint(g, lk):
    H = len(g)
    im = new(W, H)
    px = im.load()
    for y in range(H):
        for x in range(W):
            ch = g[y][x]
            if ch != ".":
                px[x, y] = E[lk[ch]]
    return finish(im)


def sprite(name, view, pose, phase, variant="A"):
    """(image, pad, height of the body grid) for one frame."""
    p = PEOPLE[name]
    nt, nl = HEIGHTS[variant]
    g = frame(p, view, pose, phase, nt, nl)
    if pose == "carry":
        # Carry frames alternate the legs like a walk.
        body_h = len(g)
        walk = legs_rows(nl, "walk", phase)
        for i, row in enumerate(walk):
            y = body_h - nl + i
            for x in range(W):
                if g[y][x] in "LlKk":
                    g[y][x] = "."
            for x, ch in enumerate(row):
                if ch != ".":
                    g[y][x + 1] = ch
    im, pad = paint(g, p["lk"])
    return im, pad, len(g)


def main():
    art = sys.argv[1]
    variant = sys.argv[2] if len(sys.argv) > 2 else "A"
    sp = Sprites(art, "people")
    for name in PEOPLE:
        for view, pose, phase, suffix in FRAMES:
            im, pad, h = sprite(name, view, pose, phase, variant)
            sp.save(f"{name}_{view}_{suffix}", im, W // 2 + pad, (h - 1) + pad)
    print(json.dumps(sp.pivots))


if __name__ == "__main__":
    main()
