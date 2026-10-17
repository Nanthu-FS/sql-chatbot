"""Contact sheet: every PNG in a folder at 4x (nearest) with its name, for review.

usage: python3 -I sheet.py <sprite dir> <out.png>
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

src, out = sys.argv[1], sys.argv[2]
files = sorted(f for f in os.listdir(src) if f.endswith(".png"))
if not files:
    sys.exit(0)
K, PAD, LABEL = 4, 10, 14
sprites = [(f[:-4], Image.open(os.path.join(src, f)).convert("RGBA")) for f in files]
cell_w = max(max(s.width for _, s in sprites) * K, 120) + PAD * 2
cell_h = max(s.height for _, s in sprites) * K + PAD * 2 + LABEL
cols = max(1, min(len(sprites), 1600 // cell_w))
rows = (len(sprites) + cols - 1) // cols
sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (38, 43, 68, 255))
draw = ImageDraw.Draw(sheet)
font_path = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "fonts", "Silkscreen-Regular.ttf")
font = ImageFont.truetype(font_path, 8) if os.path.exists(font_path) else ImageFont.load_default()
for i, (name, im) in enumerate(sprites):
    x, y = (i % cols) * cell_w, (i // cols) * cell_h
    # Checker behind sprites so outlines and shadows are visible.
    for cy in range(y + 2, y + cell_h - LABEL, 8):
        for cx in range(x + 2, x + cell_w - 2, 8):
            if (cx // 8 + cy // 8) % 2:
                draw.rectangle([cx, cy, cx + 7, cy + 7], fill=(58, 68, 102, 255))
    big = im.resize((im.width * K, im.height * K), Image.NEAREST)
    sheet.alpha_composite(big, (x + (cell_w - big.width) // 2, y + PAD + (cell_h - PAD * 2 - LABEL - big.height) // 2))
    draw.text((x + 4, y + cell_h - LABEL + 2), name[:26], font=font, fill=(255, 255, 255, 255))
os.makedirs(os.path.dirname(out), exist_ok=True)
sheet.save(out)
