"""Builds the Play Store feature graphic, Docs/store/feature_graphic.png (1024 x 500).

The scene underneath is a real frame of the game: StoreShotsTest.storeFeatureScene renders the
summer farm at zoom 1 and saves the 256 x 125 band clear of the HUD, 4x, as
Docs/store/feature_scene.png (run gradle test -Pshots --tests '*StoreShotsTest*' first).

This script works at game pixels: it takes the scene back to 256 x 125, lays the game's own wood
panel (art/ui/panel, 9-slice, 6 px border) across the top with the title "DITHER FARM" in
Silkscreen Bold and a tagline in Silkscreen, outlined and shadowed like the in-game titles, and
scales the result 4x with nearest neighbour. No smoothing anywhere: text is drawn with the
font's 1-bit mode, so every pixel stays a crisp square.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "pixel"))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
from _pix import E  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
STORE = os.path.join(ROOT, "Docs", "store")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
FONT_BOLD = os.path.join(ASSETS, "fonts", "Silkscreen-Bold.ttf")
FONT = os.path.join(ASSETS, "fonts", "Silkscreen-Regular.ttf")

SCALE = 4
W, H = 1024 // SCALE, 500 // SCALE      # 256 x 125 game pixels
TITLE = "REBUILD THE VILLAGE"
TAGLINE = "GROW. BUILD. RESTORE THE VALLEY."


def slice9(src, w, h, b):
    """The 9-slice panel at w x h: corners 1:1, edges and centre stretched (nearest)."""
    sw, sh = src.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    xs, dx = (0, b, sw - b, sw), (0, b, w - b, w)
    ys, dy = (0, b, sh - b, sh), (0, b, h - b, h)
    for j in range(3):
        for i in range(3):
            piece = src.crop((xs[i], ys[j], xs[i + 1], ys[j + 1]))
            size = (dx[i + 1] - dx[i], dy[j + 1] - dy[j])
            if size[0] > 0 and size[1] > 0:
                out.paste(piece.resize(size, Image.NEAREST), (dx[i], dy[j]))
    return out


def outlined(im, x, y, s, font, fill, outline, shadow):
    """Text with a 1 px outline ring and a 2 px drop shadow, the way Gfx.text draws titles."""
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    for ox, oy in ((0, 2), (1, 2)):
        d.text((x + ox, y + oy), s, font=font, fill=shadow)
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            if ox or oy:
                d.text((x + ox, y + oy), s, font=font, fill=outline)
    d.text((x, y), s, font=font, fill=fill)


def main():
    src = os.path.join(STORE, "feature_scene.png")
    if not os.path.isfile(src):
        sys.exit("Docs/store/feature_scene.png is missing: run gradle test -Pshots --tests '*StoreShotsTest*' first")
    scene = Image.open(src).convert("RGBA")
    scene = scene.resize((scene.width // SCALE, scene.height // SCALE), Image.NEAREST)
    assert scene.size == (W, H), scene.size
    out = scene.copy()

    title_font = ImageFont.truetype(FONT_BOLD, 16)
    tag_font = ImageFont.truetype(FONT, 8)
    probe = ImageDraw.Draw(out)
    tw = round(probe.textlength(TITLE, font=title_font))
    gw = round(probe.textlength(TAGLINE, font=tag_font))

    # The wood panel across the top centre, sized to the longer line.
    pw = max(tw, gw) + 28
    ph = 48
    px, py = (W - pw) // 2, 6
    panel = slice9(Image.open(os.path.join(ASSETS, "art", "ui", "panel.png")).convert("RGBA"), pw, ph, 6)
    out.alpha_composite(panel, (px, py))

    outlined(out, (W - tw) // 2, py + 6, TITLE, title_font, E["yellow"], E["brown_d"], E["brown_d"])
    d = ImageDraw.Draw(out)
    d.fontmode = "1"
    d.text(((W - gw) // 2, py + 30), TAGLINE, font=tag_font, fill=E["brown"])

    big = out.resize((W * SCALE, H * SCALE), Image.NEAREST).convert("RGB")
    dst = os.path.join(STORE, "feature_graphic.png")
    big.save(dst)
    print("wrote", os.path.relpath(dst, ROOT), big.size)


main()
