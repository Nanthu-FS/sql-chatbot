"""Pixel UI kit: 9-slice frames, buttons (+ pressed), slots, bars, tabs, toggles.

9-slice sprites are drawn small and stretched by Gfx.slice9 at runtime; the border
width each one expects is listed in UI_BORDERS (mirrored in ui/Ui.kt).
Pivot is the top-left corner for frames.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pix import E, Sprites, alpha, bayer, new  # noqa: E402


def rounded(w, h, r=2):
    """Yields (x, y) inside a rounded rect with corner cut r (pixel staircase)."""
    for y in range(h):
        for x in range(w):
            dx = min(x, w - 1 - x)
            dy = min(y, h - 1 - y)
            if dx + dy < r - 1 and dx < r and dy < r:
                continue
            yield x, y


def frame(w, h, outer, fills, r=2):
    """Concentric rounded rings: outer colour, then each fill colour inset by 1px."""
    im = new(w, h)
    px = im.load()
    for x, y in rounded(w, h, r):
        px[x, y] = outer
    for i, c in enumerate(fills, start=1):
        for x, y in rounded(w - 2 * i, h - 2 * i, max(1, r - i)):
            px[x + i, y + i] = c
    return im


def panel_wood():
    w = h = 24
    im = frame(w, h, E["ink"], [E["amber"], E["clay"], E["clay"], E["rust"], E["ink"]], r=3)
    px = im.load()
    # Parchment interior with a soft dither towards the bottom.
    for y in range(6, h - 6):
        for x in range(6, w - 6):
            px[x, y] = E["cream"]
    # Wood grain flecks and brass nails in the corners.
    for x, y in ((3, 2), (12, 2), (18, 2), (2, 10), (21, 14), (8, 21), (16, 21)):
        px[x, y] = E["rust"]
    for x, y in ((3, 3), (20, 3), (3, 20), (20, 20)):
        px[x, y] = E["yellow"]
    return im


def panel_dark():
    im = frame(16, 16, E["ink"], [E["grey_d"], E["slate"]], r=2)
    px = im.load()
    for y in range(3, 13):
        for x in range(3, 13):
            px[x, y] = alpha(E["slate_d"], 235)
    return im


def pill():
    im = new(12, 12)
    px = im.load()
    for x, y in rounded(12, 12, 4):
        px[x, y] = alpha(E["ink"], 200)
    for x, y in rounded(10, 10, 3):
        px[x + 1, y + 1] = alpha(E["slate_d"], 210)
    for x in range(3, 9):
        px[x, 1] = alpha(E["grey_d"], 230)
    return im


def button(light, mid, dark, down=False):
    w, h = 16, 16
    im = new(w, h)
    px = im.load()
    lip = 0 if down else 2
    top = 2 if down else 0
    for x, y in rounded(w, h - top, 3):
        px[x, y + top] = E["ink"]
    body_h = h - top - 2
    for x, y in rounded(w - 2, body_h, 2):
        yy = y + top + 1
        if y >= body_h - lip:
            px[x + 1, yy] = dark
        else:
            px[x + 1, yy] = mid
    # Top highlight row (flat body: 9-slice stretches it, dither would smear).
    for x in range(3, w - 3):
        px[x, top + 2] = light
    return im


def slot(selected=False):
    w = h = 20
    edge = E["yellow"] if selected else E["ink"]
    im = frame(w, h, edge, [E["amber"] if selected else E["tan_d"]], r=2)
    px = im.load()
    for y in range(2, h - 2):
        for x in range(2, w - 2):
            px[x, y] = E["cream"]
    for x in range(2, w - 2):
        px[x, 2] = E["tan_d"]
    for y in range(2, h - 2):
        px[2, y] = E["tan_d"]
    if selected:
        for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            px[x, y] = E["white"]
    return im


def bar_bg():
    im = frame(8, 7, E["ink"], [E["slate_d"]], r=2)
    return im


def bar_fill(light, mid, dark):
    im = new(6, 5)
    px = im.load()
    for y in range(5):
        for x in range(6):
            px[x, y] = light if y == 0 else dark if y == 4 else mid
    return im


def tab(on):
    w, h = 16, 14
    im = new(w, h)
    px = im.load()
    for x, y in rounded(w, h + 3, 3):
        if y < h:
            px[x, y] = E["ink"]
    fill = E["cream"] if on else E["tan_d"]
    shade = E["tan"] if on else E["brown_l"]
    for x, y in rounded(w - 2, h + 2, 2):
        if y < h - 1:
            px[x + 1, y + 1] = fill
    return im


def toggle(on):
    w, h = 20, 10
    im = new(w, h)
    px = im.load()
    for x, y in rounded(w, h, 4):
        px[x, y] = E["ink"]
    for x, y in rounded(w - 2, h - 2, 3):
        px[x + 1, y + 1] = E["green"] if on else E["grey_d"]
    kx = 11 if on else 1
    for x, y in rounded(8, 8, 3):
        px[x + kx, y + 1] = E["white"] if y < 5 else E["grey_l"]
    return im


def bubble():
    return frame(16, 16, E["ink"], [E["white"]], r=3)


def bubble_tail():
    im = new(7, 5)
    px = im.load()
    rows = ["iwwwwwi", ".iwwwi.", "..iwi..", "...i...", "......."]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "i":
                px[x, y] = E["ink"]
            elif ch == "w":
                px[x, y] = E["white"]
    return im


def glyph(rows, colors):
    h, w = len(rows), len(rows[0])
    im = new(w, h)
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colors:
                px[x, y] = colors[ch]
    return im


def main():
    s = Sprites(sys.argv[1], "ui")
    s.save("panel", panel_wood(), 0, 0)
    s.save("panel_dark", panel_dark(), 0, 0)
    s.save("pill", pill(), 0, 0)
    colors = {
        "green": (E["green"], E["green"], E["green_d"]),
        "gold": (E["yellow"], E["amber"], E["orange"]),
        "red": (E["pink"], E["red"], E["red_d"]),
        "blue": (E["cyan"], E["blue"], E["blue_d"]),
        "wood": (E["tan"], E["tan_d"], E["brown_l"]),
        "grey": (E["grey_l"], E["grey"], E["grey_d"]),
    }
    for name, (l, m, d) in colors.items():
        s.save(f"btn_{name}", button(l, m, d), 0, 0)
        s.save(f"btn_{name}_down", button(l, m, d, down=True), 0, 0)
    s.save("slot", slot(), 0, 0)
    s.save("slot_sel", slot(True), 0, 0)
    s.save("bar_bg", bar_bg(), 0, 0)
    s.save("bar_green", bar_fill(E["yellow"], E["green"], E["green_d"]), 0, 0)
    s.save("bar_blue", bar_fill(E["cyan"], E["blue"], E["blue_d"]), 0, 0)
    s.save("bar_gold", bar_fill(E["white"], E["yellow"], E["amber"]), 0, 0)
    s.save("bar_red", bar_fill(E["pink"], E["red"], E["red_d"]), 0, 0)
    s.save("bar_purple", bar_fill(E["pink"], E["magenta"], E["purple"]), 0, 0)
    s.save("tab", tab(False), 0, 0)
    s.save("tab_on", tab(True), 0, 0)
    s.save("toggle_on", toggle(True), 0, 0)
    s.save("toggle_off", toggle(False), 0, 0)
    s.save("bubble", bubble(), 0, 0)
    s.save("bubble_tail", bubble_tail(), 3, 0)
    ink, w, r, g, y = E["ink"], E["white"], E["red"], E["green"], E["yellow"]
    s.save("x", glyph(["i....i", ".i..i.", "..ii..", "..ii..", ".i..i.", "i....i"], {"i": w}), 3, 3)
    s.save("arrow_up", glyph(["..i..", ".igi.", "iggg i".replace(" ", ""), "iiiii"], {"i": ink, "g": g}), 2, 2)
    s.save("arrow_down", glyph(["iiiii", "irrri", ".iri.", "..i.."], {"i": ink, "r": r}), 2, 2)
    s.save("badge", glyph([".iii.", "irrri", "irrri", "irrri", ".iii."], {"i": ink, "r": r}), 2, 2)
    s.save("dot", glyph(["iii", "iyi", "iii"], {"i": ink, "y": y}), 1, 1)
    s.done()


main()
