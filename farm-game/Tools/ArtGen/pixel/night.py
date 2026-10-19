"""Night overlays (folders "buildings", "deco"): only the parts that glow after dark, drawn over
the night tint so they stay bright. <name>_lit has the same size and pivot as <name>.

  cottage_a/b/c_lit   the window panes, warm instead of blue: the cottages are ray-cast again
                      with every pane in a marker colour, so frames and walls still hide them
  lantern_lit         the lantern's glass
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, Sprites, new  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
PAD = 2   # finish() padding of the daytime sprites
MARK = "pink_r"
# Daytime glass tone -> lit tone.
WARM = {"slate": "amber", "blue_d": "amber", "blue": "yellow", "cyan": "cream"}
RGB = {v[:3]: k for k, v in E.items()}


def village():
    path = os.path.join(HERE, "village.py")
    src = open(path).read()
    src = src[: src.rstrip().rfind("main()")]
    ns = {"__file__": path, "__name__": "night_village"}
    exec(compile(src, path, "exec"), ns)
    return ns


def windows(ns, fn):
    """(overlay image, pivot) for one cottage drawn by village.py's fn()."""
    day = ns["render"](fn())
    x0, y0, x1, y1 = day.getbbox()
    glass = ns["GLASS"]
    ns["GLASS"] = [MARK] * len(glass)
    marked = ns["render"](fn())
    ns["GLASS"] = glass
    out = new(x1 - x0 + PAD * 2, y1 - y0 + PAD * 2)
    src, mk, dst = day.load(), marked.load(), out.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            if mk[x, y][3] and mk[x, y][:3] == E[MARK][:3]:
                tone = WARM.get(RGB.get(src[x, y][:3]), "yellow")
                dst[x - x0 + PAD, y - y0 + PAD] = E[tone]
    return out, (ns["CX"] - x0 + PAD, ns["CY"] - y0 + PAD)


def lamp_glass(art):
    from PIL import Image
    im = Image.open(os.path.join(art, "deco", "lantern.png")).convert("RGBA")
    out = new(*im.size)
    src, dst = im.load(), out.load()
    # The glass: bright, warm pixels (the art is hue-shifted, so match by look, not exact colour).
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = src[x, y]
            if a == 255 and 0.299 * r + 0.587 * g + 0.114 * b > 165 and r > b + 50:
                dst[x, y] = src[x, y]
    return out


def main():
    art = sys.argv[1]
    bld, deco = Sprites(art, "buildings"), Sprites(art, "deco")
    ns = village()
    for name in ("cottage_a", "cottage_b", "cottage_c"):
        im, (ax, ay) = windows(ns, ns[name])
        bld.save(name + "_lit", im, ax, ay)
    manifest = json.load(open(os.path.join(art, "manifest.json")))
    p = manifest["deco/lantern"]
    deco.save("lantern_lit", lamp_glass(art), p["ax"], p["ay"])
    print(json.dumps({**bld.pivots, **deco.pivots}))


main()
