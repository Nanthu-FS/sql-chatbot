"""Builds the launcher icon (mipmap-*/ic_launcher.png) from game sprites: a 48px pixel scene, scaled nearest."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "pixel"))
from PIL import Image  # noqa: E402
from _pix import E, bayer  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ART = os.path.join(ROOT, "app", "src", "main", "assets", "art")
RES = os.path.join(ROOT, "app", "src", "main", "res")
N = 48


def scene():
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    for y in range(N):
        for x in range(N):
            # Rounded square mask (radius 8).
            cx = min(x, N - 1 - x); cy = min(y, N - 1 - y)
            if cx < 8 and cy < 8 and (8 - cx) ** 2 + (8 - cy) ** 2 > 64:
                continue
            # Sky: dithered from light to cyan; a rolling hill below.
            hill = 30 + int(3 * ((x - 24) / 24.0) ** 2 * 2)
            if y < hill:
                level = int(16 * y / hill)
                px[x, y] = E["cyan"] if bayer(x, y, level) else E["blue"]
            else:
                d = y - hill
                px[x, y] = E["green"] if d < 2 else (E["green_d"] if bayer(x, y, 16 - min(16, d * 2)) else E["green_dd"])
    # Sun with a dithered halo.
    for y in range(N):
        for x in range(N):
            r2 = (x - 37) ** 2 + (y - 11) ** 2
            if r2 <= 16:
                px[x, y] = E["yellow"]
            elif r2 <= 36 and bayer(x, y, 6) and px[x, y][3]:
                px[x, y] = E["white"]
    # A big pumpkin with a wheat sheaf behind it.
    wheat = Image.open(os.path.join(ART, "items_big", "wheat.png"))
    pumpkin = Image.open(os.path.join(ART, "items_big", "pumpkin.png"))
    im.alpha_composite(wheat, (4, 12))
    im.alpha_composite(pumpkin, (N // 2 - pumpkin.width // 2 + 3, N - pumpkin.height - 4))
    # 1px ink rim around the rounded square.
    out = im.copy()
    op = out.load()
    for y in range(N):
        for x in range(N):
            if px[x, y][3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < N and 0 <= ny < N) or px[nx, ny][3] == 0:
                    op[x, y] = E["ink"]
                    break
    return out


def main():
    base = scene()
    for folder, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        path = os.path.join(RES, f"mipmap-{folder}", "ic_launcher.png")
        base.resize((size, size), Image.NEAREST).save(path)
    preview = os.path.join(ROOT, "Docs", "launcher_icon.png")
    base.resize((192, 192), Image.NEAREST).save(preview)
    print("ok")


if __name__ == "__main__":
    main()
