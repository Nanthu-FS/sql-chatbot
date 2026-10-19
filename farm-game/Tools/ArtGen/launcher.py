"""Builds the launcher icon: a red barn with a golden wheat sheaf on a sky and meadow.

The art is drawn at 2dp per pixel, so every size is an exact whole-pixel repeat (no uneven
pixels, no smoothing) at mdpi..xxxhdpi:

  legacy ic_launcher.png / ic_launcher_round.png   24 x 24 art px = 48dp (rounded square / circle)
  adaptive ic_launcher_background.png              54 x 54 art px = 108dp, sky and meadow
  adaptive ic_launcher_foreground.png              54 x 54 art px, barn and sheaf centred so
                                                   they sit inside the 66dp safe zone

The legacy icon is the centre 24 x 24 window of the adaptive canvas, so both match. Colours are
flat steps from the game palette (pixel/_pix.py E); nothing is dithered.

Writes into app/src/main/res (mipmap-* and mipmap-anydpi-v26) and a 192px preview to Docs/.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "pixel"))
from PIL import Image  # noqa: E402
from _pix import E  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DOCS = os.path.join(ROOT, "Docs")

LEGACY = 24          # art pixels in the legacy icon (48dp)
CANVAS = 54          # art pixels in the adaptive canvas (108dp)
OFF = (CANVAS - LEGACY) // 2
# Device pixels per art pixel (2dp) at each density: an integer, so scaling is exact.
DENSITY_PX = {"mdpi": 2, "hdpi": 3, "xhdpi": 4, "xxhdpi": 6, "xxxhdpi": 8}

# Barn and sheaf, drawn with their top-left at (1, 5) on the legacy grid. '.' is empty.
# Barn: slate roof, red walls with a white-framed door; sheaf: golden heads, tan stalks, brown tie.
BARN = [
    ".....SS.....",
    "....SSss....",
    "...SSSSss...",
    "..SSSSSSss..",
    ".SSSSSSSSss.",
    "SSSSSSSSSSSS",
    ".RRRRRRRRRRr",
    ".RRRRRRRRRRr",
    ".RRWrrrrWRRr",
    ".RRrWrrWrRRr",
    ".RRrrWWrrRRr",
    ".RRrrWWrrRRr",
    ".RRrWrrWrRRr",
    ".RRWrrrrWRRr",
    ".RRRRRRRRRRr",
]
SHEAF = [
    "....Y....",
    "...AYA...",
    "...AYA...",
    "..YAYAY..",
    ".AYAYAYA.",
    ".AYAYAYA.",
    ".NNNNNNN.",
    "..T.T.T..",
    "..T.T.T..",
    ".T..T..T.",
    ".T.....T.",
    "T.......T",
]
SUBJECT_AT = (1, 5)          # barn top-left
SHEAF_AT = (14, 5)           # sheaf top-left
SUN = [".aa.", "aYYa", "aYYa", ".aa."]
SUN_AT = (2, 1)

KEY = {
    "S": "slate", "s": "grey_d", "R": "red", "r": "red_d", "W": "white",
    "Y": "yellow", "A": "amber", "T": "tan", "N": "brown",
}


def background(x, y):
    """Sky in two steps, then meadow in three. Works outside the legacy window too."""
    if y < 8:
        name = "blue"
    elif y < 15:
        name = "cyan"
    elif y < 17:
        name = "green"
    elif y < 24:
        name = "green_d"
    else:
        name = "green_dd"
    return E[name]


def _blit(px, rows, at, key):
    x0, y0 = at
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == ".":
                continue
            px[x0 + dx, y0 + dy] = E[key[ch]]


def subject(canvas_px, ox=0, oy=0):
    """Draws the barn and sheaf (with an ink outline) onto a canvas, shifted by (ox, oy)."""
    layer = {}
    for rows, at, key in ((BARN, SUBJECT_AT, KEY), (SHEAF, SHEAF_AT, KEY)):
        x0, y0 = at
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != ".":
                    layer[(x0 + dx + ox, y0 + dy + oy)] = E[key[ch]]
    for (x, y), c in layer.items():
        canvas_px[x, y] = c
    # 1px ink outline on the empty pixels touching the subject.
    for (x, y) in list(layer):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in layer:
                canvas_px[p] = E["ink"]


def _sun(px, ox=0, oy=0):
    _blit(px, SUN, (SUN_AT[0] + ox, SUN_AT[1] + oy), {"a": "amber", "Y": "yellow"})


def _sky(im, off):
    """Fills an image whose top-left is design position (-off, -off)."""
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            px[x, y] = background(x - off, y - off)
    return px


def legacy_scene():
    """The 24 x 24 full icon before its rim: background, sun, barn and sheaf."""
    im = Image.new("RGBA", (LEGACY, LEGACY), E["ink"])
    px = _sky(im, 0)
    _sun(px)
    subject(px)
    return im


def round_mask(im, radius, circle):
    """Clears the corners (rounded square) or everything outside the circle, then rims it ink."""
    n = im.size[0]
    src = im.load()
    keep = set()
    for y in range(n):
        for x in range(n):
            if circle:
                inside = (x + 0.5 - n / 2) ** 2 + (y + 0.5 - n / 2) ** 2 <= (n / 2) ** 2
            else:
                cx = min(x, n - 1 - x)
                cy = min(y, n - 1 - y)
                inside = not (cx < radius and cy < radius and
                              (radius - cx - 0.5) ** 2 + (radius - cy - 0.5) ** 2 > radius ** 2)
            if inside:
                keep.add((x, y))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    dst = out.load()
    for (x, y) in keep:
        dst[x, y] = src[x, y]
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in keep:
                dst[x, y] = E["ink"]
                break
    return out


def legacy_icon(circle):
    scene = legacy_scene()
    return round_mask(scene, radius=6, circle=circle)


def adaptive_background():
    im = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    px = _sky(im, OFF)
    _sun(px, OFF, OFF)
    return im


def adaptive_foreground():
    im = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    subject(im.load(), OFF, OFF)
    return im


def write_xml(path):
    with open(path, "w", encoding="utf-8") as f:
        f.write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@mipmap/ic_launcher_background" />\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
            '</adaptive-icon>\n'
        )


def main():
    square = legacy_icon(circle=False)
    round_ = legacy_icon(circle=True)
    back = adaptive_background()
    fore = adaptive_foreground()
    for folder, k in DENSITY_PX.items():
        d = os.path.join(RES, f"mipmap-{folder}")
        os.makedirs(d, exist_ok=True)
        square.resize((LEGACY * k, LEGACY * k), Image.NEAREST).save(os.path.join(d, "ic_launcher.png"))
        round_.resize((LEGACY * k, LEGACY * k), Image.NEAREST).save(os.path.join(d, "ic_launcher_round.png"))
        back.resize((CANVAS * k, CANVAS * k), Image.NEAREST).save(os.path.join(d, "ic_launcher_background.png"))
        fore.resize((CANVAS * k, CANVAS * k), Image.NEAREST).save(os.path.join(d, "ic_launcher_foreground.png"))
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    write_xml(os.path.join(anydpi, "ic_launcher.xml"))
    write_xml(os.path.join(anydpi, "ic_launcher_round.xml"))
    os.makedirs(DOCS, exist_ok=True)
    square.resize((192, 192), Image.NEAREST).save(os.path.join(DOCS, "launcher_icon.png"))
    print("ok")


if __name__ == "__main__":
    main()
