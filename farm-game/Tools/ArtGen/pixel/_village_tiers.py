"""Village houses for the four restoration tiers (wooden huts, stone, brick, concrete) and their
road surfaces. Same ray-caster and conventions as village.py (1x1 footprint, pivot = tile centre).

house(spec) builds one house from a spec: wall material, roof kind, storeys, chimney, trim.
STYLES holds three candidate styles per tier for review; the chosen ones become the art.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)


def _village():
    path = os.path.join(HERE, "village.py")
    src = open(path).read()
    src = src[: src.rstrip().rfind("main()")]
    ns = {"__file__": path, "__name__": "tiers_village"}
    exec(compile(src, path, "exec"), ns)
    return ns


V = _village()
box, gable, Solid, lit, render = V["box"], V["gable"], V["Solid"], V["lit"], V["render"]


def glass():
    return V["GLASS"]


def h(*k):
    """Small deterministic hash in 0..1."""
    v = 0
    for a in k:
        v = (v * 73856093) ^ (int(a) * 19349663)
    return ((v * 2654435761) & 0xFFFF) / 65535.0


# ---------------------------------------------------------------- wall materials (s along the face, z up)

def logs(s, z, n, x, y):
    if z % 2.4 < 0.6:
        return "brown_d"
    return lit(["brown", "brown", "brown_l", "tan_d"], n, x, y, crisp=True)


def planks(s, z, n, x, y):
    if (s * 14.0) % 1.0 < 0.14:
        return "brown_d"
    return lit(["brown", "brown_l", "tan_d", "tan"], n, x, y)


def mud(s, z, n, x, y):
    """Whitewashed wattle and daub between dark posts."""
    if abs(abs(s) - 0.48) < 0.05 or abs(s) < 0.03 or z % 6.5 < 0.7:
        return "brown_d"
    return lit(["tan", "cream", "cream", "white"], n, x, y, crisp=True)


def fieldstone(s, z, n, x, y):
    row = int(z // 2.6)
    col = int(s * 6.0 + (0.5 if row % 2 else 0.0) + 10)
    fz, fs = z % 2.6, (s * 6.0 + (0.5 if row % 2 else 0.0)) % 1.0
    if fz < 0.6 or fs < 0.1:
        return "slate"
    return lit(["grey_d", "grey", "grey_l"] if h(row, col) > 0.4 else ["grey_d", "grey_d", "grey"], n, x, y, crisp=True)


def cutstone(s, z, n, x, y):
    row = int(z // 3.0)
    fz, fs = z % 3.0, (s * 4.0 + (0.5 if row % 2 else 0.0)) % 1.0
    if fz < 0.55 or fs < 0.07:
        return "tan_d"
    return lit(["tan_d", "tan", "cream"], n, x, y, crisp=True)


def brick(ramp, mortar):
    def f(s, z, n, x, y):
        row = int(z // 1.6)
        fz, fs = z % 1.6, (s * 8.0 + (0.5 if row % 2 else 0.0)) % 1.0
        if fz < 0.45 or fs < 0.1:
            return mortar
        return lit(ramp, n, x, y, crisp=True)
    return f


def render_wall(ramp):
    def f(s, z, n, x, y):
        return lit(ramp, n, x, y, crisp=True)
    return f


def panels(s, z, n, x, y):
    if (s * 3.0) % 1.0 < 0.05 or z % 7.0 < 0.5:
        return "grey_d"
    return lit(["grey", "grey_l", "grey_l"], n, x, y)


# ---------------------------------------------------------------- roofs

def roof(kind):
    def thatch(tag, n, u, v, z, x, y):
        if tag in ("A", "B"):
            if z % 2.6 < 0.6:
                return "brown_d"
            return "tan_d" if (int(math.floor(u * 9.0)) * 7 + int(z // 2.6) * 5) % 6 == 0 else lit(["brown", "brown_l", "tan", "cream"], n, x, y)
        return lit(["brown", "tan", "cream"], n, x, y) if tag == "E" else "brown_d"

    def rows(ramp, line, step=2.2, stagger=True):
        def f(tag, n, u, v, z, x, y):
            if tag in ("A", "B"):
                if z % step < 0.6:
                    return line
                if stagger and (u * 10.0 + (0.5 if int(z // step) % 2 else 0.0)) % 1.0 < 0.1:
                    return line
                return lit(ramp, n, x, y, crisp=True)
            return lit(ramp, n, x, y)
        return f

    def tin(tag, n, u, v, z, x, y):
        if tag in ("A", "B"):
            if (u * 12.0) % 1.0 < 0.2:
                return "grey_d"
            return "rust" if h(int(u * 12), int(z // 3)) > 0.85 else lit(["grey_d", "grey", "grey_l"], n, x, y, crisp=True)
        return lit(["grey_d", "grey"], n, x, y)

    return {
        "thatch": thatch, "tin": tin,
        "slate": rows(["slate_d", "slate", "grey_d"], "ink"),
        "red_tile": rows(["red_d", "rust", "clay"], "brown_d"),
        "shingle": rows(["brown_d", "brown", "brown_l"], "ink"),
        "blue_tile": rows(["slate", "blue_d", "blue"], "slate_d"),
        "green_tile": rows(["green_dd", "green_d", "green"], "teal_dd"),
    }[kind]


def flat_roof(tag, n, u, v, z, x, y):
    """A flat roof with a darker parapet round its edge."""
    if tag == "Zp":
        return "grey" if max(abs(u), abs(v)) > 0.44 else "grey_l"
    return lit(["grey_d", "grey", "grey_l"], n, x, y, crisp=True)


# ---------------------------------------------------------------- house

def house(spec):
    WH = spec.get("wh", 13.0)          # wall height per storey
    st = spec.get("storeys", 1)
    top = WH * st
    PK = spec.get("peak", 9.0)
    wall = spec["wall"]
    trim = spec.get("trim", "cream")
    door = spec.get("door", ["brown", "brown", "brown_l", "tan"])
    base = spec.get("base")
    hw = spec.get("half", 0.5)

    def window(s, c, z, z0, n, x, y):
        if abs(s - c) > 0.11 or not (z0 + 5.6 < z < z0 + 10.6):
            return None
        if abs(abs(s - c) - 0.11) < 0.02 or abs(z - (z0 + 5.6)) < 0.5 or abs(z - (z0 + 10.6)) < 0.5:
            return trim
        if abs(s - c) < 0.012 or abs(z - (z0 + 8.1)) < 0.4:
            return trim
        return lit(glass(), n, x, y)

    def walls(tag, n, u, v, z, x, y):
        if tag == "Zp":
            return "tan_d"
        if tag not in ("Yp", "Xp"):
            return "tan_d"
        s = u if tag == "Yp" else v
        if base and z < 1.4:
            return "grey_d" if (x + y) % 2 else "grey"
        for k in range(st):
            z0 = k * WH
            if tag == "Yp":
                if k == 0 and 0.04 < s < 0.26 and z < 9.8:
                    if abs(s - 0.04) < 0.02 or abs(s - 0.26) < 0.02 or z > 9.3:
                        return trim
                    if 0.2 < s < 0.23 and 4.6 < z < 5.4:
                        return "yellow"
                    return lit(door, n, x, y)
                c = window(s, -0.25, z, z0, n, x, y) if k == 0 else (window(s, -0.25, z, z0, n, x, y) or window(s, 0.15, z, z0, n, x, y))
            else:
                c = window(s, -0.2, z, z0, n, x, y) or window(s, 0.2, z, z0, n, x, y)
            if c:
                return c
        if spec.get("band") and abs(z - WH) < 0.6 and st > 1:
            return trim
        return wall(s, z, n, x, y)

    parts = [Solid(box(-hw, hw, -hw, hw, 0.0, top), walls)]
    if spec.get("roof") == "flat":
        parts.append(Solid(box(-hw - 0.03, hw + 0.03, -hw - 0.03, hw + 0.03, top, top + 1.6), flat_roof))
    else:
        parts.append(Solid(gable(1.0, 0.0, hw + 0.12, hw + 0.12, top, PK), roof(spec["roof"])))
    if spec.get("chimney"):
        ch = spec.get("chimney_mat", fieldstone)
        parts.append(Solid(box(0.14, 0.3, -0.33, -0.17, top + 2.0, top + PK + 4.0),
                           lambda tag, n, u, v, z, x, y: ch(u + v, z, n, x, y)))
    if spec.get("awning"):
        col = spec["awning"]
        parts.append(Solid(box(-hw, hw, hw, hw + 0.16, 10.2, 10.8), lambda tag, n, u, v, z, x, y: col if (int(u * 10)) % 2 else "white"))
    return parts


# ---------------------------------------------------------------- the candidates: three per tier

STYLES = {
    "hut": [
        ("log cabin", dict(wall=logs, roof="shingle", trim="brown_d", chimney=True)),
        ("plank shack", dict(wall=planks, roof="tin", trim="brown_d")),
        ("wattle hut", dict(wall=mud, roof="thatch", trim="brown", wh=12.0)),
    ],
    "stone": [
        ("fieldstone", dict(wall=fieldstone, roof="slate", chimney=True, trim="cream")),
        ("cut stone", dict(wall=cutstone, roof="red_tile", chimney=True, chimney_mat=cutstone, trim="white")),
        ("stone + timber", dict(wall=lambda s, z, n, x, y: fieldstone(s, z, n, x, y) if z < 13 else planks(s, z, n, x, y),
                                roof="shingle", storeys=2, wh=11.0, trim="cream", band=True)),
    ],
    "brick": [
        ("red brick", dict(wall=brick(["red_d", "rust", "red"], "tan"), roof="slate", chimney=True, chimney_mat=brick(["red_d", "rust", "red"], "tan"), trim="white", base=True)),
        ("brick townhouse", dict(wall=brick(["brown", "rust", "clay"], "cream"), roof="shingle", storeys=2, wh=11.0, trim="white", band=True, base=True)),
        ("yellow brick", dict(wall=brick(["clay", "amber", "yellow"], "cream"), roof="green_tile", trim="white", base=True, awning="green")),
    ],
    "concrete": [
        ("white modern", dict(wall=render_wall(["grey_l", "white", "white"]), roof="flat", storeys=2, wh=11.0, trim="slate", door=["slate", "slate", "grey_d", "grey"])),
        ("concrete panels", dict(wall=panels, roof="flat", storeys=2, wh=11.0, trim="slate_d", band=True, door=["blue_d", "blue_d", "blue", "cyan"])),
        ("pastel stucco", dict(wall=render_wall(["pink", "skin", "cream"]), roof="red_tile", storeys=2, wh=11.0, trim="white", awning="red")),
    ],
}
