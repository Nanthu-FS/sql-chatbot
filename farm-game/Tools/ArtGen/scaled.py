"""Re-renders ray-cast sprites (buildings, machines, vehicles, village deco) at a world scale.

The pixel modules model every object in world units (u, v in tiles, z in pixels) and ray-cast
each screen pixel. Rendering with the ray grid stretched by 1/s draws the same model s times
smaller with fresh, crisp pixels (not a downscale). Screen-space touches some sprites paint after
the render (sails, smoke, leaves) are drawn on their own layer and scaled about the pivot.

  python3 scaled.py <out_art_dir> [name|module ...]    renders SCALE entries (all, or those named)

generate.mjs runs it after the pixel groups it draws, so the art always lands at this scale.

SCALE is the agreed size of each object relative to its modelled size; see Docs/DECISIONS.md
("World scale").
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PIX = os.path.join(HERE, "pixel")
sys.path.insert(0, PIX)
from _pix import Sprites, finish, new  # noqa: E402

# name: (module, folder, scale)
SCALE = {
    "orders": ("buildings", "buildings", 0.7),
    "compost_bin": ("machines_farm", "buildings", 0.6),
    "bakery_oven": ("machines_farm", "buildings", 0.6),
    "preserves_jar": ("machines_farm", "buildings", 0.55),
    "beehive": ("machines_farm", "buildings", 0.55),
    "fruit_press": ("machines_farm", "buildings", 0.62),
    "seed_maker": ("machines_farm", "buildings", 0.62),
    "oil_press": ("machines_farm", "buildings", 0.62),
    "market_cart": ("machines_farm", "buildings", 0.65),
    "sawmill": ("machines_wild", "buildings", 0.78),
    "wood_chipper": ("machines_wild", "buildings", 0.6),
    "charcoal_kiln": ("machines_wild", "buildings", 0.75),
    "syrup_kettle": ("machines_wild", "buildings", 0.6),
    "furnace": ("machines_wild", "buildings", 0.65),
    "geode_anvil": ("machines_wild", "buildings", 0.52),
    "drill_rig": ("machines_wild", "buildings", 0.78),
    "merchant_cart": ("machines_wild", "buildings", 0.65),
    "logging_hut": ("machines_wild", "buildings", 0.9),
    "tree_nursery": ("machines_wild", "buildings", 0.72),
    "harvester": ("village", "vehicles", 0.7),
    "crate": ("village", "deco", 0.72),
}

_mods = {}


def module(name):
    """The module's namespace without running its main()."""
    if name not in _mods:
        path = os.path.join(PIX, name + ".py")
        src = open(path).read()
        src = src[: src.rstrip().rfind("main()")]
        ns = {"__file__": path, "__name__": "scaled_" + name}
        exec(compile(src, path, "exec"), ns)
        _mods[name] = ns
    return _mods[name]


def render(ns, solids, s):
    W = ns.get("W") or ns["CANVAS"]
    H = ns.get("H") or ns["CANVAS"]
    CX, CY, hit, col = ns["CX"], ns["CY"], ns["hit"], ns["col"]
    im = new(W, H)
    px = im.load()
    for y in range(H):
        Y = (y - CY) / s
        for x in range(W):
            D = (x - CX) / (16.0 * s)
            best = None
            for sol in solids:
                h = hit(sol.cons, D, Y)
                if h is None or h[0] < -1e-6:
                    continue
                if best is None or h[0] >= best[0][0] - 1e-9:
                    best = (h, sol)
            if best is None:
                continue
            (z, tag, nrm), sol = best
            u = (Y + z) / 16.0 + D / 2.0
            v = (Y + z) / 16.0 - D / 2.0
            fr = getattr(sol, "frame", None)
            if fr:
                f, g = fr
                u, v = f[0] * u + f[1] * v, g[0] * u + g[1] * v
            px[x, y] = col(sol.mat(tag, nrm, u, v, z, x, y))
    return im


def post_layer(ns, paint, s, size):
    """A screen-space paint, drawn alone at full size, then scaled about the pivot (nearest)."""
    layer = new(*size)
    paint(layer)
    W, H = size
    CX, CY = ns["CX"], ns["CY"]
    out = new(W, H)
    src, dst = layer.load(), out.load()
    for y in range(H):
        for x in range(W):
            sx, sy = int(round(CX + (x - CX) / s)), int(round(CY + (y - CY) / s))
            if 0 <= sx < W and 0 <= sy < H and src[sx, sy][3]:
                dst[x, y] = src[sx, sy]
    return out


def emit(sp, ns, name, solids, s, paint=None):
    im = render(ns, solids, s)
    if paint is not None:
        im.alpha_composite(post_layer(ns, paint, s, im.size))
    x0, y0, x1, y1 = im.getbbox()
    out, pad = finish(im.crop((x0, y0, x1, y1)))
    sp.save(name, out, ns["CX"] - x0 + pad, ns["CY"] - y0 + pad)


def jobs(name):
    """(sprite name, module ns, solids, paint) for every sprite an entry produces."""
    mod, _, _ = SCALE[name]
    ns = module(mod)
    if mod == "buildings":
        fn = dict(ns["BUILDINGS"])[name]
        yield name, ns, fn(False), None
        yield name + "_snow", ns, fn(True), None
    elif mod == "machines_farm":
        solids, paint = dict(ns["SPRITES"])[name]()
        yield name, ns, solids, paint
    elif mod == "machines_wild":
        yield name, ns, dict(ns["SPRITES"])[name](), ns["POST"].get(name)
    elif name in ("harvester", "truck"):
        for heading in ("se", "ne"):
            for frame in (0, 1):
                yield f"{name}_{heading}_{frame}", ns, ns[name](heading, frame), None
        if name == "truck":
            yield "truck_load", ns, ns["truck_load"](), None
    elif name == "crate":
        yield "crate_empty", ns, ns["crate"](False), None
        yield "crate_full", ns, ns["crate"](True), None


def main():
    out = sys.argv[1]
    # Arguments are sprite names or module names (every entry drawn by that module).
    args = sys.argv[2:]
    names = [n for n, (m, _, _) in SCALE.items() if not args or n in args or m in args]
    pivots = {}
    for name in names:
        _, folder, s = SCALE[name]
        sp = Sprites(out, folder)
        for sprite, ns, solids, paint in jobs(name):
            emit(sp, ns, sprite, solids, s, paint)
            sys.stderr.write(f"{folder}/{sprite} x{s}\n")
        pivots.update(sp.pivots)
    print(json.dumps(pivots))


if __name__ == "__main__":
    main()
