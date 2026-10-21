"""Portraits, round 7 redo (owner: "the portraits look bad, make them better").

48x48 busts built from parts instead of hand-typed 30x30 maps: a shaded face (jaw shape, ears, neck),
proper eyes (lash line, white, iris, pupil and a shine), a nose shadow, lips, cheeks; hair as a back
layer behind the head and a front layer over it, with a shine band and strands; hats; beards and
glasses; clothes with folds, collars, straps and aprons. Lit from the top-left in the Endesga 32 ramps,
outlined in ink. Expressions (talk, blink, blush, determined, shocked) are the same parts with a
different mouth, eyes or brows.

Each character is a dict of parts (see CHARACTERS). render(spec, frame, bust) -> RGBA image.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image  # noqa: E402
from _pix import E, Sprites  # noqa: E402

S = 48
CX = 24


def C(c):
    return E[c] if isinstance(c, str) else c


SKINS = {
    "fair": ["tan_d", "tan", "skin", "cream"],
    "tan": ["brown_l", "tan_d", "tan", "skin"],
    "olive": ["brown_l", "tan_d", "tan", "skin"],
    "deep": ["brown", "brown_l", "tan_d", "tan"],
    "brown": ["brown_d", "brown", "brown_l", "tan_d"],
    "pale": ["tan_d", "tan", "skin", "cream"],
}
HAIRS = {
    "auburn": ["brown_d", "brown", "rust", "clay"],
    "brown": ["brown_d", "brown", "brown_l", "tan_d"],
    "blonde": ["brown_l", "amber", "yellow", "cream"],
    "amber": ["rust", "clay", "amber", "yellow"],
    "black": ["ink", "slate_d", "slate", "grey_d"],
    "dark": ["ink", "brown_d", "brown", "brown_l"],
    "grey": ["slate", "grey_d", "grey", "grey_l"],
    "white": ["grey_d", "grey", "grey_l", "white"],
    "sandy": ["brown_l", "tan_d", "tan", "cream"],
    "ginger": ["red_d", "rust", "orange", "amber"],
    "plum": ["ink", "purple", "magenta", "pink"],
}
CLOTH = {
    "red": ["purple", "red_d", "red", "pink"],
    "blue": ["slate_d", "blue_d", "blue", "cyan"],
    "denim": ["slate_d", "blue_d", "blue", "cyan"],
    "green": ["green_dd", "green_d", "green", "yellow"],
    "teal": ["teal_dd", "green_dd", "green_d", "green"],
    "grey": ["slate", "grey_d", "grey", "grey_l"],
    "charcoal": ["ink", "slate_d", "slate", "grey_d"],
    "brown": ["brown_d", "brown", "brown_l", "tan_d"],
    "cream": ["tan_d", "tan", "cream", "white"],
    "white": ["grey", "grey_l", "white", "white"],
    "pink": ["purple", "magenta", "pink", "pink"],
    "orange": ["rust", "orange", "amber", "yellow"],
    "yellow": ["clay", "amber", "yellow", "cream"],
    "wine": ["ink", "brown_d", "red_d", "red"],
    "plum": ["slate_d", "purple", "magenta", "pink"],
    "rust": ["brown_d", "rust", "clay", "tan_d"],
    "ink": ["ink", "ink", "slate_d", "slate"],
}
IRIS = {"brown": ("brown_d", "brown"), "blue": ("blue_d", "blue"), "green": ("green_dd", "green_d"), "grey": ("slate", "grey_d"),
        "dark": ("ink", "brown_d"), "hazel": ("brown", "clay")}


class Canvas:
    """Regions (skin, hair, cloth...) are filled first and shaded as rounded forms at the end; features
    (eyes, lips, buttons) are flat colours put on top."""
    def __init__(self):
        self.fill_at = {}      # (x, y) -> (region key, ramp)
        self.flat = {}         # (x, y) -> colour
        self.region = {}

    def put(self, x, y, c, region=None):
        if not (0 <= x < S and 0 <= y < S) or c is None:
            return
        if region and isinstance(c, list):
            self.fill_at[(x, y)] = (region, tuple(c))
            self.flat.pop((x, y), None)
        else:
            self.flat[(x, y)] = C(c)
        if region:
            self.region[(x, y)] = region

    def area(self, pts, region, ramp):
        for (x, y) in pts:
            self.put(x, y, list(ramp), region)

    def filled(self, x, y):
        return (x, y) in self.fill_at or (x, y) in self.flat

    def get(self, x, y):
        return (255, 255, 255, 255) if self.filled(x, y) else (0, 0, 0, 0)

    def bake(self, light=(-0.6, -0.75, 0.55)):
        """Dome-shade every region (by distance to its edge), then lay the flat features on top."""
        im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        px = im.load()
        groups = {}
        for p, key in self.fill_at.items():
            groups.setdefault(key, set()).add(p)
        ln = math.sqrt(sum(v * v for v in light))
        L = tuple(v / ln for v in light)
        for (region, ramp), pts in groups.items():
            dist = {}
            q = [p for p in pts if any((p[0] + dx, p[1] + dy) not in pts for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
            for p in q:
                dist[p] = 1
            i = 0
            while i < len(q):
                x, y = q[i]; i += 1
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    m = (x + dx, y + dy)
                    if m in pts and m not in dist:
                        dist[m] = dist[(x, y)] + 1
                        q.append(m)
            depth = 4.0
            hgt = {p: depth * math.sqrt(max(0.0, 1 - (1 - min(1.0, (d - 0.5) / depth)) ** 2)) for p, d in dist.items()}
            n = len(ramp) - 1
            for (x, y) in pts:
                gx = (hgt.get((x + 1, y), 0) - hgt.get((x - 1, y), 0)) / 2
                gy = (hgt.get((x, y + 1), 0) - hgt.get((x, y - 1), 0)) / 2
                nl = math.sqrt(gx * gx + gy * gy + 1)
                lv = (-gx * L[0] - gy * L[1] + L[2]) / nl
                v = ((lv + 0.15) / 1.15) * n
                px[x, y] = C(ramp[max(0, min(n, int(round(v))))])
        for (x, y), c in self.flat.items():
            px[x, y] = c
        return im


def shade(ramp, x, y, cx, cy, rx, ry, base=1.6):
    """A ramp step for a point on a rounded form lit from the top-left."""
    nx = (x - cx) / max(1.0, rx)
    ny = (y - cy) / max(1.0, ry)
    lit = base - 1.1 * nx - 0.9 * ny
    return ramp[max(0, min(len(ramp) - 1, int(round(lit))))]


def ellipse_pts(cx, cy, rx, ry):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                yield x, y


# ---------------------------------------------------------------- the face

TOP, CHIN = 9, 32          # head top and chin rows
HW = 9.5                   # half the face's widest width


def face_rows(jaw):
    """Half-width of the face per row: round forehead, full cheeks, the jaw tapering to the chin."""
    rows = {}
    for y in range(TOP, CHIN + 1):
        t = (y - TOP) / (CHIN - TOP)
        if t < 0.5:
            w = HW * math.sqrt(max(0.0, 1 - ((0.5 - t) / 0.55) ** 2))
        else:
            k = (t - 0.5) / 0.5
            chin = {"round": 0.38, "square": 0.55, "soft": 0.3, "narrow": 0.25}[jaw]
            p = {"round": 1.7, "square": 3.0, "soft": 1.5, "narrow": 1.3}[jaw]
            w = HW * (1 - (k ** p) * (1 - chin))
        rows[y] = w
    return rows


def draw_face(cv, sp, fr):
    skin = SKINS[sp["skin"]]
    rows = face_rows(sp.get("jaw", "round"))
    neck = {(x, y) for y in range(CHIN - 3, CHIN + 7) for x in range(CX - 4, CX + 4)}
    cv.area(neck, "neck", [skin[0], skin[1], skin[1], skin[2]])
    ey = TOP + 11
    for side, ex in ((-1, int(CX - HW - 2)), (1, int(CX + HW))):
        ear = {(x, y) for y in range(ey, ey + 6) for x in range(ex, ex + 3) if not (y in (ey, ey + 5) and x == (ex if side < 0 else ex + 2))}
        cv.area(ear, "ear%d" % side, skin)
        cv.put(ex + 1, ey + 2, skin[0]); cv.put(ex + 1, ey + 3, skin[1])
    face = set()
    for y, w in rows.items():
        for x in range(int(round(CX - w)), int(round(CX + w))):
            face.add((x, y))
    cv.area(face, "face", skin)
    # The chin's shadow on the neck.
    for x in range(CX - 3, CX + 3):
        cv.put(x, CHIN + 1, skin[0])
        cv.put(x, CHIN + 2, skin[1])
    return rows


EYE_Y = TOP + 11


def draw_eyes(cv, sp, fr):
    skin = SKINS[sp["skin"]]
    iris_d, iris_l = IRIS[sp.get("eyes", "brown")]
    female = sp.get("female", False)
    lash = "ink"
    for side in (-1, 1):
        ex = CX + side * 5 - (3 if side < 0 else 0)          # left edge of a 4 px eye
        if fr.get("blink"):
            for x in range(ex, ex + 4):
                cv.put(x, EYE_Y + 1, lash)
            cv.put(ex + (0 if side < 0 else 3), EYE_Y + 2, skin[0])
            continue
        if sp.get("tired"):
            for x in range(ex, ex + 4):
                cv.put(x, EYE_Y, skin[1]); cv.put(x, EYE_Y + 1, lash)
                cv.put(x, EYE_Y + 2, "white")
            cv.put(ex + 1, EYE_Y + 2, iris_d); cv.put(ex + 2, EYE_Y + 2, "ink")
            continue
        wide = fr.get("shock")
        top = EYE_Y - (1 if wide else 0)
        # Lash line (a flick at the outer corner for women), then the eye: white, a 2-wide iris with
        # the pupil and a shine, the lower lid.
        for x in range(ex, ex + 4):
            cv.put(x, top, lash)
        if female:
            cv.put(ex - 1 if side < 0 else ex + 4, top - 1, lash)
            cv.put(ex - 1 if side < 0 else ex + 4, top, lash)
        ix = ex + 1
        rows = [top + 1, top + 2, top + 3]
        for k, y in enumerate(rows):
            for x in range(ex, ex + 4):
                cv.put(x, y, "white" if k < 2 else skin[2])
            if k == 0:
                cv.put(ix, y, "white"); cv.put(ix + 1, y, "ink")
            elif k == 1:
                cv.put(ix, y, iris_d); cv.put(ix + 1, y, iris_d)
            else:
                cv.put(ix, y, iris_l); cv.put(ix + 1, y, iris_l)
        if not female:
            cv.put(ex + (0 if side < 0 else 3), top + 2, skin[2])
        for x in range(ex + 1, ex + 3):
            cv.put(x, top + 4, skin[1])


def draw_age(cv, sp):
    if not sp.get("old"):
        return
    skin = SKINS[sp["skin"]]
    for side in (-1, 1):
        ox = CX + side * 9 - (1 if side < 0 else 0)
        cv.put(ox, EYE_Y + 1, skin[1]); cv.put(ox, EYE_Y + 3, skin[1])
        cv.put(CX + side * 4 - (1 if side < 0 else 0), EYE_Y + 8, skin[1])
        cv.put(CX + side * 4 - (1 if side < 0 else 0), EYE_Y + 9, skin[1])
    for x in range(CX - 3, CX + 3):
        if x % 2 == 0:
            cv.put(x, TOP + 6, skin[1])


def draw_brows(cv, sp, fr):
    hair = HAIRS[sp["hair"]]
    for side in (-1, 1):
        ex = CX + side * 5 - (3 if side < 0 else 0)
        y = EYE_Y - 3
        for k in range(5):
            x = ex - (1 if side < 0 else 0) + k
            dy = 0
            if fr.get("det"):
                # Pulled down toward the nose.
                dy = (k if side < 0 else 4 - k) // 2
            elif fr.get("shock"):
                dy = -1
            elif sp.get("smug") and side > 0:
                dy = -1
            cv.put(x, y + dy, hair[0] if k in (1, 2, 3) else hair[1])


def draw_nose(cv, sp, fr):
    skin = SKINS[sp["skin"]]
    ny = EYE_Y + 6
    cv.put(CX, ny - 2, skin[1])
    cv.put(CX, ny - 1, skin[1])
    cv.put(CX + 1, ny, skin[0])
    cv.put(CX - 1, ny, skin[1])
    cv.put(CX - 1, ny - 3, skin[3])


def draw_mouth(cv, sp, fr):
    skin = SKINS[sp["skin"]]
    lip = sp.get("lips", "red_d" if sp.get("female") else {"fair": "brown_l", "pale": "brown_l", "tan": "brown", "olive": "brown"}.get(sp["skin"], "brown_d"))
    my = EYE_Y + 9
    kind = "talk" if fr.get("talk") else "shock" if fr.get("shock") else "flat" if fr.get("det") else sp.get("mouth", "smile")
    if kind == "smile":
        for x in range(CX - 2, CX + 3):
            cv.put(x, my, lip)
        cv.put(CX - 3, my - 1, lip); cv.put(CX + 3, my - 1, lip)
        cv.put(CX, my + 1, skin[1])
    elif kind == "grin":
        for x in range(CX - 2, CX + 3):
            cv.put(x, my - 1, lip)
        for x in range(CX - 1, CX + 2):
            cv.put(x, my, "white")
            cv.put(x, my + 1, lip)
        cv.put(CX - 2, my, lip); cv.put(CX + 2, my, lip)
        cv.put(CX - 3, my - 2, lip); cv.put(CX + 3, my - 2, lip)
    elif kind == "smirk":
        for x in range(CX - 2, CX + 3):
            cv.put(x, my, lip)
        cv.put(CX + 3, my - 1, lip)
    elif kind == "flat":
        for x in range(CX - 2, CX + 3):
            cv.put(x, my, lip)
    elif kind == "talk":
        for x in range(CX - 2, CX + 3):
            cv.put(x, my - 1, lip)
            cv.put(x, my + 1, lip)
        for x in range(CX - 1, CX + 2):
            cv.put(x, my, "red_d")
        cv.put(CX - 2, my, "ink"); cv.put(CX + 2, my, "ink")
    elif kind == "shock":
        for (x, y) in ((CX - 1, my - 1), (CX, my - 1), (CX + 1, my - 1), (CX - 2, my), (CX + 2, my), (CX - 1, my + 1), (CX, my + 1), (CX + 1, my + 1)):
            cv.put(x, y, lip)
        cv.put(CX - 1, my, "ink"); cv.put(CX, my, "ink"); cv.put(CX + 1, my, "ink")


def draw_cheeks(cv, sp, fr):
    if not (fr.get("blush") or sp.get("rosy") or sp.get("freckles")):
        return
    strong = fr.get("blush")
    for side in (-1, 1):
        cx = CX + side * 6
        if strong or sp.get("rosy"):
            for (dx, dy) in ((0, 0), (1, 0), (-1, 0), (0, 1)) if strong else ((0, 0), (side, 0)):
                cv.put(cx + dx, EYE_Y + 6 + dy, "pink" if strong else "red")
    if sp.get("freckles"):
        for (x, y) in ((CX - 7, EYE_Y + 6), (CX - 5, EYE_Y + 7), (CX + 4, EYE_Y + 7), (CX + 6, EYE_Y + 6)):
            cv.put(x, y, SKINS[sp["skin"]][1])


# ---------------------------------------------------------------- hair

def hair_shape(style, front):
    """Pixels of the hair's back (behind the head) or front (over the forehead) layer."""
    pts = set()
    if not front:
        if style in ("long", "curly", "braids"):
            for x, y in ellipse_pts(CX, TOP + 8, HW + 4, 11):
                pts.add((x, y))
            for y in range(TOP + 8, 42 if style != "braids" else 30):
                for x in range(int(CX - HW - 4), int(CX + HW + 4)):
                    pts.add((x, y))
            if style == "curly":
                for k in range(16):
                    a = k / 16 * math.pi * 2
                    for x, y in ellipse_pts(CX + math.cos(a) * (HW + 5), TOP + 18 + math.sin(a) * 14, 4.5, 4.5):
                        if y > TOP - 2:
                            pts.add((x, y))
        elif style in ("bob", "wavy"):
            for x, y in ellipse_pts(CX, TOP + 9, HW + 3, 12):
                pts.add((x, y))
            for y in range(TOP + 9, TOP + (22 if style == "bob" else 24)):
                for x in range(int(CX - HW - 3), int(CX + HW + 3)):
                    pts.add((x, y))
        elif style in ("bun", "ponytail"):
            for x, y in ellipse_pts(CX, TOP + 7, HW + 2, 9):
                pts.add((x, y))
            if style == "bun":
                for x, y in ellipse_pts(CX, TOP - 3, 5.5, 4.5):
                    pts.add((x, y))
            else:
                for x, y in ellipse_pts(CX + 9, TOP - 1, 3.5, 5):
                    pts.add((x, y))
                for x, y in ellipse_pts(CX + 12, TOP + 6, 3, 6):
                    pts.add((x, y))
        else:
            for x, y in ellipse_pts(CX, TOP + 6, HW + 1.5, 8):
                pts.add((x, y))
            if style == "bald":
                pts = {(x, y) for (x, y) in pts if y > TOP + 5}
        return pts
    # Front layers: the fringe over the forehead.
    if style == "bald":
        return {(x, y) for x, y in ellipse_pts(CX, TOP + 7, HW + 1.5, 6) if abs(x + 0.5 - CX) > HW - 2 and y > TOP + 4}
    for x, y in ellipse_pts(CX, TOP + 3, HW + 1.2, 6.5):
        pts.add((x, y))
    if style == "messy":
        for x in range(int(CX - HW), int(CX + HW) + 1):
            depth = TOP + 5 + ((x * 7) % 5 > 2) * 2 + (1 if x % 3 == 0 else 0)
            for y in range(TOP, depth):
                pts.add((x, y))
        for (x, y) in ((CX - 6, TOP - 3), (CX - 2, TOP - 5), (CX + 2, TOP - 4), (CX + 6, TOP - 3), (CX - 9, TOP)):
            for k in range(5):
                for dx in range(0, 2 + k // 2):
                    pts.add((x + dx - k // 4, y + k))
    elif style == "sidepart":
        for x in range(int(CX - HW), int(CX + HW) + 1):
            depth = TOP + 4 + min(2, int(max(0, (x - (CX - 3)) * 0.45)))
            for y in range(TOP, depth):
                pts.add((x, y))
    elif style in ("long", "bob", "wavy", "curly", "braids"):
        # Parted in the middle, falling to the sides of the face.
        for x in range(int(CX - HW - 1), int(CX + HW + 2)):
            d = abs(x + 0.5 - CX)
            depth = TOP + 3 + int(d * 0.6)
            for y in range(TOP - 1, depth):
                pts.add((x, y))
        for y in range(TOP + 4, TOP + (20 if style == "bob" else 24)):
            for x in (int(CX - HW - 1), int(CX - HW), int(CX + HW), int(CX + HW) + 1 if style != "bob" else int(CX + HW)):
                pts.add((x, y))
    elif style in ("bun", "ponytail"):
        for x in range(int(CX - HW), int(CX + HW) + 1):
            d = abs(x + 0.5 - CX)
            depth = TOP + 3 + int(d * 0.35)
            for y in range(TOP - 1, depth):
                pts.add((x, y))
    elif style == "short":
        for x in range(int(CX - HW), int(CX + HW) + 1):
            for y in range(TOP - 1, TOP + 4):
                pts.add((x, y))
    return pts


def draw_braids(cv, sp):
    if sp.get("hairstyle") != "braids":
        return
    ramp = HAIRS[sp["hair"]]
    for side in (-1, 1):
        bx = int(CX - HW - 2) if side < 0 else int(CX + HW)
        pts = {(x, y) for y in range(TOP + 18, 42) for x in range(bx, bx + 3)}
        cv.area(pts, "braid%d" % side, ramp)
        for y in range(TOP + 18, 42, 3):
            cv.put(bx, y, ramp[0]); cv.put(bx + 1, y + 1, ramp[0]); cv.put(bx + 2, y + 2, ramp[0])
        band = sp.get("band", "red")
        for x in range(bx, bx + 3):
            cv.put(x, 40, band)


def draw_pillow(cv, sp):
    if sp.get("pillow"):
        cv.area(set(ellipse_pts(CX, TOP + 15, 21, 11)), "pillow", ["grey", "grey_l", "grey_l", "white"])


def draw_hair(cv, sp, front):
    style = sp.get("hairstyle", "short")
    ramp = HAIRS[sp["hair"]]
    pts = hair_shape(style, front)
    if not pts:
        return
    if not front:
        pts = {p for p in pts if cv.region.get(p) not in ("face",)}
    cv.area(pts, "hair_front" if front else "hair_back", ramp)
    # Strands in long hair, curls in curly hair, a shine near the crown.
    for (x, y) in pts:
        if style in ("long", "wavy", "bob", "braids") and (x * 3 + y // 3) % 7 == 0 and y > TOP + 4:
            cv.put(x, y, ramp[0])
        if style == "curly" and ((x * 2 + y * 3) % 7 == 0):
            cv.put(x, y, ramp[2] if (x + y) % 2 else ramp[0])
        if front and style in ("short", "sidepart", "bun", "ponytail") and (x, y + 1) not in pts and x % 3 == 0:
            cv.put(x, y, ramp[0])
        if front and style in ("short", "sidepart") and (x, y + 2) not in pts and (x * 7) % 5 == 1:
            cv.put(x, y, ramp[1])
        if front and style == "sidepart" and x == CX - 4 and y < TOP + 3:
            cv.put(x, y, ramp[0])
        r = math.hypot(x + 0.5 - (CX - 3), y + 0.5 - (TOP + 2))
        if front and 4.5 < r < 6 and y < TOP + 3 and x < CX + 1:
            cv.put(x, y, ramp[-1])


# ---------------------------------------------------------------- hats, beards, glasses

def draw_hat(cv, sp):
    hat = sp.get("hat")
    if not hat:
        return
    kind, cols = hat
    ramp = CLOTH.get(cols) or {"straw": ["brown_l", "tan_d", "tan", "cream"]}[cols]
    if kind == "flatcap":
        cv.area(set(ellipse_pts(CX, TOP + 1, HW + 2, 5)), "hat", ramp)
        brim = {(x, y) for x in range(int(CX - HW + 1), int(CX + HW + 3)) for y in (TOP + 5, TOP + 6)}
        cv.area(brim, "brim", [ramp[0], ramp[0], ramp[1], ramp[1]])
    elif kind in ("sunhat", "straw", "bucket"):
        wide = {"sunhat": 9, "straw": 7, "bucket": 4}[kind]
        cv.area(set(ellipse_pts(CX, TOP + 4, HW + wide, 3 if kind != "bucket" else 2.5)), "brim", ramp)
        cv.area({p for p in ellipse_pts(CX, TOP, HW - (1 if kind != "bucket" else -1), 5) if p[1] < TOP + 4}, "hat", ramp)
        if kind != "bucket":
            band = sp.get("band", "green" if kind == "sunhat" else "red")
            for x in range(int(CX - HW + 1), int(CX + HW)):
                cv.put(x, TOP + 2, band)
        if kind == "sunhat":
            for (x, y) in ((CX + 5, TOP + 1), (CX + 6, TOP + 2), (CX + 5, TOP + 3), (CX + 4, TOP + 2)):
                cv.put(x, y, "pink")
            cv.put(CX + 5, TOP + 2, "yellow")
    elif kind == "cap":
        cv.area({p for p in ellipse_pts(CX, TOP + 2, HW + 1, 6.5) if p[1] < TOP + 5}, "hat", ramp)
        cv.area({(x, y) for x in range(int(CX - HW + 2), int(CX + HW + 4)) for y in (TOP + 4, TOP + 5)}, "brim", [ramp[0], ramp[0], ramp[1], ramp[1]])
        cv.put(CX, TOP - 4, ramp[0])
    elif kind == "beanie":
        cv.area({p for p in ellipse_pts(CX, TOP + 1, HW + 1.5, 7) if p[1] < TOP + 5}, "hat", ramp)
        for x in range(int(CX - HW - 1), int(CX + HW + 2)):
            for y in (TOP + 4, TOP + 5):
                cv.put(x, y, ramp[0] if x % 2 else ramp[1])
    elif kind == "headband":
        for x in range(int(CX - HW - 1), int(CX + HW + 2)):
            cv.put(x, TOP + 4, ramp[2]); cv.put(x, TOP + 5, ramp[1])


def draw_beard(cv, sp):
    beard = sp.get("beard")
    if not beard:
        return
    ramp = HAIRS[sp.get("beard_hair", sp["hair"])]
    my = EYE_Y + 9
    rows = face_rows(sp.get("jaw", "round"))
    if beard in ("full", "stubble"):
        for y, w in rows.items():
            if y < EYE_Y + 6:
                continue
            for x in range(int(CX - w), int(math.ceil(CX + w))):
                if beard == "stubble" and (x + y) % 2:
                    continue
                if abs(y - my) <= 1 and abs(x - CX) <= 3:
                    continue
                if beard == "full":
                    cv.put(x, y, list(ramp), "beard")
                else:
                    cv.put(x, y, SKINS[sp["skin"]][0])
        if beard == "full":
            for y in range(CHIN, CHIN + 4):
                for x in range(CX - 4 + (y - CHIN), CX + 5 - (y - CHIN)):
                    cv.put(x, y, list(ramp), "beard")
    if beard in ("full", "mustache"):
        for x in range(CX - 4, CX + 5):
            cv.put(x, my - 2, ramp[2] if x < CX else ramp[1], "beard")
            if abs(x - CX) > 1:
                cv.put(x, my - 1, ramp[1], "beard")
    if beard == "goatee":
        for y in range(my + 2, CHIN + 2):
            for x in range(CX - 1, CX + 2):
                cv.put(x, y, ramp[1], "beard")
        for x in range(CX - 3, CX + 4):
            cv.put(x, my - 2, ramp[1], "beard")


def draw_glasses(cv, sp):
    g = sp.get("glasses")
    if not g:
        return
    frame = "slate_d" if g == "ink" else g
    for side in (-1, 1):
        ex = CX + side * 5 - (3 if side < 0 else 0)
        for x in range(ex, ex + 4):
            cv.put(x, EYE_Y, frame)
            cv.put(x, EYE_Y + 4, frame)
        for y in range(EYE_Y + 1, EYE_Y + 4):
            cv.put(ex - 1, y, frame)
            cv.put(ex + 4, y, frame)
        cv.put(ex + 3, EYE_Y + 1, "white")      # a glint on the lens
    cv.put(CX - 1, EYE_Y, frame); cv.put(CX, EYE_Y, frame)


# ---------------------------------------------------------------- clothes

def draw_clothes(cv, sp):
    outfit = sp.get("outfit", "tee")
    main = CLOTH[sp.get("cloth", "blue")]
    second = CLOTH[sp.get("cloth2", "cream")]
    y0 = CHIN + 4
    body, front = set(), set()
    for y in range(y0, S):
        t = (y - y0) / (S - y0)
        w = 9 + 13 * min(1.0, t * 2.2)
        for x in range(int(CX - w), int(math.ceil(CX + w))):
            d = x + 0.5 - CX
            if (outfit in ("overalls", "apron") and abs(d) < 7 and y >= y0 + 4) or (outfit in ("vest", "open", "suit") and abs(d) < 3):
                front.add((x, y))
            else:
                body.add((x, y))
    cv.area(body, "cloth", main)
    if front:
        cv.area(front, "cloth2", second if outfit not in ("suit",) else CLOTH["white"])
    if outfit == "overalls":
        for y in range(y0, y0 + 4):
            for x in (CX - 7, CX - 6, CX + 5, CX + 6):
                cv.put(x, y, second[1])
        for x in (CX - 6, CX + 5):
            cv.put(x, y0 + 4, "yellow")
    if outfit == "open" and not sp.get("plain_shirt"):
        for (x, y) in front:
            if y % 2 == 0:
                cv.put(x, y, second[1])
    if outfit == "suit":
        for y in range(y0, S):
            for x in (CX - 3, CX + 2):
                cv.put(x, y, main[0])
    if outfit == "suspenders":
        for y in range(y0 + 1, S):
            for x in (CX - 7, CX + 6):
                cv.put(x, y, second[1]); cv.put(x + 1, y, second[2])
        cv.put(CX - 7, y0 + 7, "yellow"); cv.put(CX + 7, y0 + 7, "yellow")
    if sp.get("knit"):
        for (x, y) in body:
            if (x + y) % 4 == 0 and y > y0 + 1:
                cv.put(x, y, main[1])
            if (x - y) % 4 == 0 and y > y0 + 1:
                cv.put(x, y, main[3])
    if sp.get("stripes"):
        for (x, y) in body | front:
            if y % 3 == 0 and y > y0 + 1:
                cv.put(x, y, sp["stripes"])
    if outfit == "quilt":
        cols = ["red", "amber", "green_d", "pink", "cyan", "red_d"]
        for y in range(y0 + 4, S):
            for x in range(0, S):
                if (x, y) in body or (x, y) in front or y > y0 + 4:
                    k = (x // 5 + (y // 4) * 2) % len(cols)
                    c = "cream" if x % 5 == 0 or y % 4 == 0 else cols[k]
                    cv.put(x, y, c)
    # Arm folds.
    for y in range(y0 + 6, S):
        for fx in (CX - 14, CX + 13):
            if (fx, y) in body:
                cv.put(fx, y, main[0])
    # Collar / neckline.
    neck = sp.get("neck", "round")
    skin = SKINS[sp["skin"]]
    if neck == "v":
        for k in range(5):
            cv.put(CX - 2 + k // 2, y0 + k, skin[1]); cv.put(CX + 1 - k // 2, y0 + k, skin[1])
            cv.put(CX - 3 + k // 2, y0 + k, main[0]); cv.put(CX + 2 - k // 2, y0 + k, main[0])
    elif neck == "collar":
        col = CLOTH[sp.get("collar", "white")]
        for k in range(3):
            cv.put(CX - 4 + k, y0 + k, col[2]); cv.put(CX + 3 - k, y0 + k, col[2])
            cv.put(CX - 5 + k, y0 + k, col[1]); cv.put(CX + 4 - k, y0 + k, col[1])
    elif neck == "scarf":
        col = CLOTH[sp.get("collar", "green")]
        cv.area({(x, y) for y in range(y0 - 1, y0 + 3) for x in range(CX - 7, CX + 7)}, "scarf", col)
        for y in range(y0 + 3, y0 + 9):
            cv.put(CX - 3, y, col[1]); cv.put(CX - 2, y, col[2])
    elif neck == "kerchief":
        col = CLOTH[sp.get("collar", "red")]
        for y in range(y0 - 1, y0 + 2):
            for x in range(CX - 6, CX + 6):
                cv.put(x, y, col[2] if (x + y) % 3 else col[1])
        for k in range(4):
            for x in range(CX - 3 + k, CX + 3 - k):
                cv.put(x, y0 + 2 + k, col[1])
    else:
        for x in range(CX - 4, CX + 4):
            cv.put(x, y0, main[0])
    if sp.get("tie"):
        tie = CLOTH[sp["tie"]]
        for y in range(y0 + 1, y0 + 11):
            for x in range(CX - 1, CX + 1 + (1 if y > y0 + 2 else 0)):
                cv.put(x + (1 if sp.get("loose_tie") and y > y0 + 4 else 0), y, tie[2] if x == CX - 1 else tie[1])
    for (x, y, c) in sp.get("details", []):
        cv.put(x, y, c)


# ---------------------------------------------------------------- putting it together

def outline(im):
    w, h = im.size
    px = im.load()
    out = im.copy()
    o = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3]:
                continue
            if any(0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                o[x, y] = C("ink")
    return out


def background(bg):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = im.load()
    r = 9
    for y in range(S):
        for x in range(S):
            dx = max(0.0, r - (x + 0.5), (x + 0.5) - (S - r))
            dy = max(0.0, r - (y + 0.5), (y + 0.5) - (S - r))
            if dx * dx + dy * dy <= r * r:
                # Mostly the middle tone: light in the top-left corner, dark at the bottom-right.
                t = (x + y) / (2 * S)
                px[x, y] = C(bg[2] if t < 0.22 else bg[0] if t > 0.82 else bg[1])
    return im


def render(sp, frame=None, bust=False):
    fr = frame or {}
    cv = Canvas()
    draw_pillow(cv, sp)
    draw_hair(cv, sp, front=False)
    draw_clothes(cv, sp)
    draw_face(cv, sp, fr)
    draw_cheeks(cv, sp, fr)
    draw_eyes(cv, sp, fr)
    draw_brows(cv, sp, fr)
    draw_age(cv, sp)
    draw_nose(cv, sp, fr)
    draw_beard(cv, sp)
    draw_mouth(cv, sp, fr)
    draw_hair(cv, sp, front=True)
    draw_braids(cv, sp)
    draw_hat(cv, sp)
    draw_glasses(cv, sp)
    if sp.get("earrings"):
        for side in (-1, 1):
            ex = int(CX - HW - 1) if side < 0 else int(CX + HW + 1)
            cv.put(ex, TOP + 17, sp["earrings"]); cv.put(ex, TOP + 18, sp["earrings"])
    if sp.get("pencil"):
        for k in range(4):
            cv.put(int(CX + HW + 1) + k, TOP + 10 - k, "yellow" if k < 3 else "pink")
    for (x, y, c) in sp.get("extras", []):
        cv.put(x, y, c)
    fig = outline(cv.bake())
    if bust:
        return fig
    tile = background(sp.get("bg", ["slate", "grey_d", "grey"]))
    # The figure, clipped to the tile, then a dark rim round the tile.
    mask = tile.split()[3]
    tile.alpha_composite(fig)
    tile.putalpha(mask)
    return tile


FLOWER = [(CX + 7, TOP + 1, "pink"), (CX + 8, TOP + 2, "pink"), (CX + 7, TOP + 3, "pink"), (CX + 6, TOP + 2, "pink"), (CX + 7, TOP + 2, "yellow")]
BG_GREEN = ["green_dd", "green_d", "green"]
BG_SUN = ["amber", "yellow", "white"]
BG_FIRE = ["rust", "orange", "amber"]
BG_SKY = ["blue_d", "blue", "cyan"]
BG_ROSE = ["purple", "magenta", "pink"]
BG_SLATE = ["slate", "grey_d", "grey"]
BG_SEA = ["teal_dd", "blue_d", "blue"]
BG_WINE = ["slate_d", "purple", "magenta"]

CHARACTERS = {
    # The villagers.
    "mara": dict(skin="fair", hair="auburn", hairstyle="bun", eyes="hazel", female=True, rosy=True, mouth="grin", jaw="soft",
                 cloth="red", cloth2="cream", outfit="apron", neck="v", bg=BG_SUN),
    "tobias": dict(skin="deep", hair="brown", hairstyle="short", eyes="brown", jaw="square", beard="goatee", mouth="smile",
                   cloth="blue", cloth2="brown", outfit="apron", neck="round", pencil=True, bg=BG_SLATE),
    "ivy": dict(skin="fair", hair="amber", hairstyle="long", eyes="green", female=True, rosy=True, freckles=True, mouth="grin",
                cloth="pink", outfit="tee", neck="collar", collar="green", bg=BG_GREEN, extras=FLOWER),
    "bram": dict(skin="tan", hair="black", hairstyle="bald", eyes="dark", jaw="square", beard="mustache", mouth="flat",
                 cloth="charcoal", cloth2="brown", outfit="apron", neck="round", bg=BG_FIRE),
    "juniper": dict(skin="fair", hair="grey", hairstyle="bun", eyes="grey", female=True, old=True, glasses="cyan", mouth="smile",
                    cloth="plum", cloth2="red", outfit="tee", neck="scarf", collar="red", rosy=True, bg=BG_ROSE),
    "pip": dict(skin="tan", hair="dark", hairstyle="short", eyes="brown", freckles=True, mouth="grin", jaw="round",
                hat=("cap", "orange"), cloth="white", outfit="tee", stripes="red", neck="round", bg=BG_SKY),
    "sela": dict(skin="pale", hair="black", hairstyle="bob", eyes="dark", female=True, mouth="smirk", smug=True, lips="red",
                 cloth="wine", outfit="tee", neck="v", bg=BG_SLATE, earrings="yellow"),
    "fennel": dict(skin="tan", hair="white", hairstyle="bald", eyes="hazel", old=True, beard="full", glasses="amber", mouth="smile",
                   cloth="brown", cloth2="cream", outfit="open", neck="collar", collar="cream", bg=BG_SEA,
                   extras=[(CX + 3, EYE_Y + 10, "green"), (CX + 4, EYE_Y + 11, "green"), (CX + 5, EYE_Y + 12, "yellow")]),
    "rook": dict(skin="deep", hair="black", hairstyle="messy", eyes="dark", jaw="square", mouth="smirk", cloth="grey", cloth2="brown",
                 outfit="apron", neck="kerchief", collar="red", bg=BG_FIRE,
                 extras=[(CX - 7, EYE_Y + 5, "slate_d"), (CX - 6, EYE_Y + 6, "slate_d")]),
    "willa": dict(skin="fair", hair="blonde", hairstyle="ponytail", eyes="blue", female=True, freckles=True, mouth="grin",
                  cloth="red", cloth2="denim", outfit="overalls", neck="round", pencil=True, bg=BG_GREEN),
    "coco": dict(skin="brown", hair="dark", hairstyle="bun", eyes="dark", female=True, mouth="grin", lips="red", earrings="yellow",
                 cloth="orange", cloth2="white", outfit="apron", neck="v", bg=BG_ROSE),
    "finn": dict(skin="fair", hair="sandy", hairstyle="short", eyes="blue", mouth="smile", hat=("beanie", "blue"),
                 cloth="yellow", cloth2="white", outfit="open", plain_shirt=True, stripes=None, neck="collar", collar="yellow", bg=BG_SEA,
                 details=[(x, y, "blue") for y in range(39, 48, 2) for x in range(CX - 2, CX + 2)]),
    "jasper": dict(skin="tan", hair="auburn", hairstyle="messy", eyes="green", freckles=True, mouth="grin", hat=("headband", "red"),
                   cloth="cream", cloth2="brown", outfit="vest", neck="scarf", collar="green", beard="stubble", bg=BG_FIRE),
    "marco": dict(skin="olive", hair="black", hairstyle="sidepart", eyes="brown", jaw="square", mouth="smile", beard="stubble",
                  cloth="green", outfit="suit", neck="collar", collar="white", tie="red", loose_tie=True, bg=BG_SLATE),
    # The bank.
    "officer": dict(skin="fair", hair="black", hairstyle="sidepart", eyes="grey", jaw="narrow", mouth="flat", glasses="ink",
                    cloth="charcoal", outfit="suit", neck="collar", collar="white", tie="red", bg=BG_SKY),
    "kessler": dict(skin="fair", hair="auburn", hairstyle="sidepart", eyes="green", jaw="round", mouth="smirk", glasses="red",
                    cloth="green", outfit="suit", neck="collar", collar="white", tie="orange", bg=BG_GREEN),
    "sterling_sr_young": dict(skin="fair", hair="black", hairstyle="sidepart", eyes="grey", jaw="narrow", mouth="flat", glasses="ink",
                              cloth="charcoal", outfit="suit", neck="collar", collar="white", tie="ink", bg=BG_WINE),
    "sterling_sr_mid": dict(skin="fair", hair="grey", hairstyle="sidepart", eyes="grey", jaw="narrow", mouth="flat", glasses="ink",
                            beard="mustache", cloth="charcoal", outfit="suit", neck="collar", collar="white", tie="ink", bg=BG_WINE),
    "sterling_sr_old": dict(skin="fair", hair="white", hairstyle="bald", eyes="grey", jaw="narrow", mouth="flat", glasses="ink", old=True,
                            beard="mustache", cloth="charcoal", outfit="suit", neck="collar", collar="white", tie="ink", bg=BG_WINE),
    # Grandpa Elias through the years.
    "elias_young": dict(skin="tan", hair="dark", hairstyle="messy", eyes="blue", jaw="square", mouth="grin",
                        cloth="blue", cloth2="brown", outfit="suspenders", neck="collar", collar="blue", bg=BG_FIRE),
    "elias_mid": dict(skin="tan", hair="grey", hairstyle="short", eyes="blue", jaw="square", mouth="smile", beard="mustache",
                      cloth="green", cloth2="brown", outfit="suspenders", neck="collar", collar="green", bg=BG_SLATE),
    "elias_old": dict(skin="tan", hair="white", hairstyle="short", eyes="blue", jaw="square", beard="full", glasses="slate_d", old=True,
                      hat=("flatcap", "grey"), cloth="rust", cloth2="rust", outfit="open", neck="collar", collar="cream", bg=BG_GREEN),
    "elias_bed": dict(skin="tan", hair="white", hairstyle="short", eyes="blue", jaw="square", beard="full", glasses="slate_d", old=True,
                      tired=True, pillow=True, cloth="cream", outfit="quilt", neck="round", mouth="smile", bg=BG_SLATE),
    # The farmer's four looks (0, 3 male; 2, 1 female).
    "farmer_0": dict(skin="tan", hair="brown", hairstyle="short", eyes="brown", mouth="smile", hat=("straw", "straw"), band="red",
                     cloth="red", cloth2="denim", outfit="overalls", neck="round", bg=BG_GREEN),
    "farmer_1": dict(skin="brown", hair="black", hairstyle="curly", eyes="dark", female=True, mouth="smile", lips="red",
                     hat=("sunhat", "straw"), cloth="pink", outfit="tee", neck="v", earrings="yellow", bg=BG_GREEN),
    "farmer_2": dict(skin="fair", hair="blonde", hairstyle="braids", eyes="blue", female=True, rosy=True, freckles=True, mouth="smile",
                     cloth="orange", outfit="tee", neck="round", band="green", knit=True, bg=BG_SKY),
    "farmer_3": dict(skin="deep", hair="black", hairstyle="short", eyes="dark", mouth="smile", hat=("beanie", "teal"),
                     cloth="cream", cloth2="green", outfit="overalls", neck="round", bg=BG_SUN),
    # The farmhands.
    "fh_rosa": dict(skin="brown", hair="dark", hairstyle="braids", eyes="dark", female=True, mouth="smile", hat=("headband", "red"),
                    cloth="green", outfit="tee", neck="round", band="yellow", bg=BG_FIRE),
    "fh_milo": dict(skin="fair", hair="sandy", hairstyle="short", eyes="blue", mouth="smile", freckles=True, hat=("straw", "straw"),
                    cloth="blue", outfit="tee", neck="collar", collar="blue", bg=BG_GREEN),
    "fh_otis": dict(skin="tan", hair="white", hairstyle="bald", eyes="grey", old=True, beard="full", mouth="smile",
                    cloth="orange", outfit="tee", neck="round", bg=BG_SKY),
    "fh_wren": dict(skin="pale", hair="blonde", hairstyle="long", eyes="green", female=True, rosy=True, mouth="smile",
                    cloth="plum", outfit="tee", neck="v", bg=BG_SUN),
    "fh_hana": dict(skin="fair", hair="black", hairstyle="bob", eyes="dark", female=True, mouth="smile",
                    cloth="yellow", outfit="tee", neck="round", bg=BG_SLATE),
}

# Extra expressions saved under their own names.
MOODS = {"officer_shock": ("officer", {"shock": 1}), "kessler_shock": ("kessler", {"shock": 1})}
MOODS.update({f"farmer_{i}_det": (f"farmer_{i}", {"det": 1}) for i in range(4)})
ROMANCE = ["ivy", "sela", "willa", "coco", "rook", "finn", "jasper", "marco"]


def main():
    """Folder "portraits": <name> is the 50x50 tile (pivot = centre) and <name>_bust the cut-out
    figure for the story scenes (pivot = bottom centre). Every face also has _talk and _blink
    frames; the romance candidates have _blush ones; MOODS are extra faces under their own names."""
    sp = Sprites(sys.argv[1], "portraits")
    faces = [(n, CHARACTERS[n], {}) for n in CHARACTERS] + [(n, CHARACTERS[src], fr) for n, (src, fr) in MOODS.items()]
    for name, spec, base in faces:
        moods = [("", {})] + ([("_blush", {"blush": 1})] if name in ROMANCE else [])
        for mood_suffix, mood in moods:
            for suffix, anim in (("", {}), ("_talk", {"talk": 1}), ("_blink", {"blink": 1})):
                fr = {**base, **mood, **anim}
                sp.save(name + mood_suffix + suffix, tile(spec, fr), S // 2 + 1, S // 2 + 1)
                sp.save(name + "_bust" + mood_suffix + suffix, render(spec, fr, bust=True), S // 2, S - 1)
    sp.done()


def tile(spec, fr):
    """The portrait tile with a 1 px ink rim."""
    im = Image.new("RGBA", (S + 2, S + 2), (0, 0, 0, 0))
    im.alpha_composite(render(spec, fr), (1, 1))
    return outline(im)


if __name__ == "__main__":
    main()
