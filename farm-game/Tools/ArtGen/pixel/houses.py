"""Village houses and shops for the restoration tiers (folder "buildings").

house_<tier>_<a|b|c>   tier 0 wooden huts, 1 stone, 2 brick, 3 concrete; the village mixes the
                       three styles of its tier (picked in playtest round 2)
shop_seed, shop_smith, shop_carpenter, shop_cafe   the shops the tiers bring
<name>_lit             night overlay: only the window panes, warm (see night.py)

Built with _village_tiers.house(): same ray-caster, 1x1 footprint, pivot = tile centre.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _village_tiers as T  # noqa: E402
from _pix import E, Sprites, finish, new  # noqa: E402

TIERS = ["hut", "stone", "brick", "concrete"]
PAD = 2
MARK = "pink_r"
WARM = {"slate": "amber", "blue_d": "amber", "blue": "yellow", "cyan": "cream"}
RGB = {v[:3]: k for k, v in E.items()}
CX, CY = T.V["CX"], T.V["CY"]


def emit_with_night(sp, name, make):
    """Renders make() by day, then again with the panes in a marker colour for the _lit overlay."""
    day = T.render(make())
    x0, y0, x1, y1 = day.getbbox()
    out, pad = finish(day.crop((x0, y0, x1, y1)))
    sp.save(name, out, CX - x0 + pad, CY - y0 + pad)
    glass = T.V["GLASS"]
    T.V["GLASS"] = [MARK] * len(glass)
    marked = T.render(make())
    T.V["GLASS"] = glass
    lit = new(x1 - x0 + PAD * 2, y1 - y0 + PAD * 2)
    src, mk, dst = day.load(), marked.load(), lit.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            if mk[x, y][3] and mk[x, y][:3] == E[MARK][:3]:
                dst[x - x0 + PAD, y - y0 + PAD] = E[WARM.get(RGB.get(src[x, y][:3]), "yellow")]
    sp.save(name + "_lit", lit, CX - x0 + PAD, CY - y0 + PAD)


# ---------------------------------------------------------------- shops

def sign_board(icon):
    """A small board over the door (front-left face) with a two-colour icon."""
    def mat(tag, n, u, v, z, x, y):
        if tag == "Yp":
            cu = (u - 0.15) * 30
            cz = z - 12.6
            if icon == "sprout" and abs(cu) < 1.2 and -1.5 < cz < 1.5:
                return "green" if cz > 0 else "green_d"
            if icon == "cup" and abs(cu) < 1.5 and -1.2 < cz < 1.0:
                return "white" if cz > -0.6 else "brown_l"
            if icon == "saw" and abs(cu) < 2.0 and abs(cz) < 0.6:
                return "grey_l"
            return "brown_l"
        return "brown"
    return T.Solid(T.box(-0.02, 0.32, 0.5, 0.56, 11.0, 14.4), mat)


def shop(kind):
    if kind == "seed":
        parts = T.house(dict(wall=T.planks, roof="red_tile", trim="cream", awning="green"))
        parts.append(sign_board("sprout"))
        parts.append(T.Solid(T.box(-0.42, -0.18, 0.52, 0.72, 0.0, 3.2), lambda t, n, u, v, z, x, y: "brown_l" if t == "Zp" else T.lit(["brown_d", "brown", "brown_l"], n, x, y)))
        parts.append(T.Solid(T.box(-0.4, -0.2, 0.54, 0.7, 3.2, 4.4), lambda t, n, u, v, z, x, y: "green" if (int(u * 20)) % 2 else "orange"))
        return parts
    if kind == "smith":
        parts = T.house(dict(wall=T.fieldstone, roof="slate", trim="slate", door=["slate_d", "slate", "grey_d", "grey"]))
        parts.append(T.Solid(T.box(0.08, 0.36, -0.36, -0.08, 15.0, 30.0), lambda t, n, u, v, z, x, y: ("orange" if (x + y) % 2 else "red") if (t == "Zp") else T.fieldstone(u + v, z, n, x, y)))
        parts.append(T.Solid(T.box(-0.38, -0.2, 0.56, 0.72, 0.0, 3.0), lambda t, n, u, v, z, x, y: T.lit(["ink", "slate_d", "slate", "grey_d"], n, x, y)))
        parts.append(T.Solid(T.box(-0.42, -0.16, 0.6, 0.68, 3.0, 4.0), lambda t, n, u, v, z, x, y: T.lit(["slate", "grey_d", "grey", "grey_l"], n, x, y)))
        return parts
    if kind == "carpenter":
        parts = T.house(dict(wall=T.planks, roof="shingle", trim="tan", chimney=True))
        parts.append(sign_board("saw"))
        for i, z0 in enumerate((0.0, 2.2, 0.0)):
            u0 = -0.44 + i * 0.12
            parts.append(T.Solid(T.box(u0, u0 + 0.12, 0.54, 0.86, z0, z0 + 2.2),
                                 lambda t, n, u, v, z, x, y: ("tan" if (x + y) % 3 else "brown_l") if t == "Xp" else T.lit(["brown_d", "brown", "brown_l"], n, x, y)))
        return parts
    if kind == "cafe":
        parts = T.house(dict(wall=T.render_wall(["pink", "skin", "cream"]), roof="red_tile", trim="white", awning="red", storeys=2, wh=11.0))
        parts.append(sign_board("cup"))
        parts.append(T.Solid(T.box(-0.42, -0.24, 0.6, 0.78, 3.0, 3.6), lambda t, n, u, v, z, x, y: "white"))
        parts.append(T.Solid(T.box(-0.35, -0.31, 0.67, 0.71, 0.0, 3.0), lambda t, n, u, v, z, x, y: "slate"))
        return parts
    raise ValueError(kind)


def main():
    sp = Sprites(sys.argv[1], "buildings")
    for t, tier in enumerate(TIERS):
        for i, (_, spec) in enumerate(T.STYLES[tier]):
            emit_with_night(sp, f"house_{t}_{'abc'[i]}", lambda spec=spec: T.house(spec))
    for kind in ("seed", "smith", "carpenter", "cafe"):
        emit_with_night(sp, f"shop_{kind}", lambda kind=kind: shop(kind))
    print(json.dumps(sp.pivots))


main()
