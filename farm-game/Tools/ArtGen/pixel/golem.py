"""The Crystal Golem (round 6, folder "golem"): the boss of mine floor 80. A hulking slate-rock
body seen from the front (crystals and its heart are separate sprites the engine places on it),
the armour crystal whole and broken, and the body flashing red as it winds up a slam.
Pivot = between the feet on the ground (body), centre (crystals).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, Sprites, finish, from_map, new  # noqa: E402

W, H = 44, 50
ROCK = ["ink", "slate_d", "slate", "grey_d", "grey", "grey_l"]
HOT = ["ink", "purple", "red_d", "red_d", "red", "pink"]
L = (-0.55, -0.7, 0.6)


def blob(px, shapes, ramp):
    """Shaded rounded rocks: each (cx, cy, rx, ry) is lit like a sphere from the top-left."""
    n = len(ramp) - 1
    for cx, cy, rx, ry in shapes:
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
                u, v = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                d = u * u + v * v
                if d > 1 or not (0 <= x < W and 0 <= y < H):
                    continue
                z = math.sqrt(1 - d)
                lv = (u * L[0] + v * L[1] + z * L[2] + 0.4) / 1.4
                k = max(1, min(n, int(round(1 + lv * (n - 1)))))
                if d > 0.82:
                    k = max(1, k - 1)
                px[x, y] = E[ramp[k]]


def body(ramp):
    im = new(W, H)
    px = im.load()
    shapes = [
        (14, 44, 6, 6), (30, 44, 6, 6),           # feet / legs
        (22, 30, 13, 12),                          # torso
        (6, 27, 6, 9), (38, 27, 6, 9),             # arms
        (5, 37, 5, 4), (39, 37, 5, 4),             # fists
        (22, 13, 8, 7),                            # head
    ]
    blob(px, shapes, ramp)
    # Eyes: two glowing slits; a crack down the chest where the heart sits.
    for x in (19, 20, 24, 25):
        px[x, 13] = E["cyan"]
    px[19, 14] = E["blue"]; px[25, 14] = E["blue"]
    for y in range(24, 33):
        if px[22, y][3]:
            px[22, y] = E["ink"]
    return im


def main():
    s = Sprites(sys.argv[1], "golem")
    for name, ramp in (("body", ROCK), ("body_hot", HOT)):
        out, pad = finish(body(ramp))
        s.save(name, out, W // 2 + pad, H - 1 + pad)
    key = {"c": "cyan", "C": "white", "b": "blue", "d": "blue_d"}
    crystal = ["..c..", ".cCc.", ".cCb.", "ccbbd", ".cbd.", ".bdd.", "..d.."]
    broken = [".....", ".....", ".....", ".....", "..b..", ".bdd.", "dd.dd"]
    for name, rows in (("crystal", crystal), ("crystal_broken", broken)):
        out, pad = finish(from_map(rows, key), shadow=False)
        s.save(name, out, len(rows[0]) // 2 + pad, len(rows) // 2 + pad)
    print(json.dumps(s.pivots))


if __name__ == "__main__":
    main()
