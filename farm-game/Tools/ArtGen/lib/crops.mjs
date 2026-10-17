// Hand-built crop art: 4 growth stages per crop plus a harvested-produce icon.
// Stage canvases are 256×320 with the soil line at y=272 (pivot 0.5, 0.85).
import { C, Pic, ol, leaf, blade, blobs, star, f, asset, mulberry32 } from './svg.mjs';

const W = 256, H = 320, G = 272;

function mound(p, rx = 64, seeds = null) {
  p.add(`<ellipse cx="128" cy="${G}" rx="${rx}" ry="${rx * 0.3}" fill="${p.lg([C.soil2, C.soil3])}" ${ol(9)}/>`);
  p.add(`<ellipse cx="122" cy="${G - rx * 0.1}" rx="${rx * 0.7}" ry="${rx * 0.13}" fill="${C.soil1}" opacity="0.7"/>`);
  if (seeds) {
    [[104, G - 4, -20], [128, G - 10, 10], [151, G - 3, 30]].forEach(([x, y, a]) => {
      p.add(`<ellipse cx="${x}" cy="${y}" rx="8" ry="6" transform="rotate(${a} ${x} ${y})" fill="${seeds}" ${ol(5)}/>`);
      p.add(`<circle cx="${x - 2}" cy="${y - 2}" r="2" fill="#fff" opacity="0.7"/>`);
    });
  }
}

function soilLip(p, rx) {
  // Front lip of the soil drawn over the base of a plant so it looks planted.
  p.add(`<path d="M${128 - rx} ${G} Q128 ${G + rx * 0.42} ${128 + rx} ${G} Q128 ${G + rx * 0.18} ${128 - rx} ${G}Z" fill="${C.soil3}" ${ol(7)}/>`);
}

const stage = (crop, i, p) => asset(`crop_${crop}_${i}`, W, H, p.body(), { kind: 'crop' });

// ---------- Wheat ----------
function wheatHead(p, x, y, deg, len, cols) {
  const [light, mid, dark] = cols;
  const awns = [], grains = [];
  for (let i = 0; i < 6; i++) {
    const gy = -i * (len / 6.5);
    for (const s of [-1, 1]) {
      const gx = s * 7;
      awns.push(`<path d="M${gx} ${f(gy - 6)} l${s * 10} -30" stroke="${dark}" stroke-width="2.5" stroke-linecap="round"/>`);
      grains.push(`<ellipse cx="${gx}" cy="${f(gy - 6)}" rx="7.5" ry="12" transform="rotate(${s * 26} ${gx} ${f(gy - 6)})" fill="${p.lg([light, mid, dark])}" ${ol(5)}/>`);
    }
  }
  const tipY = -6 * (len / 6.5) - 6;
  awns.push(`<path d="M0 ${f(tipY)} l0 -34" stroke="${dark}" stroke-width="2.5" stroke-linecap="round"/>`);
  grains.push(`<ellipse cx="0" cy="${f(tipY)}" rx="7" ry="12" fill="${mid}" ${ol(5)}/>`);
  p.add(`<g transform="translate(${f(x)} ${f(y)}) rotate(${deg})">${awns.join('')}${grains.join('')}</g>`);
}

function stalk(p, x0, y0, x1, y1, color, w = 6) {
  const d = `M${x0} ${y0} Q${f((x0 + x1) / 2 + (x1 - x0) * 0.2)} ${f((y0 + y1) / 2)} ${x1} ${y1}`;
  p.add(`<path d="${d}" fill="none" stroke="${C.ink}" stroke-width="${w + 7}" stroke-linecap="round"/>`);
  p.add(`<path d="${d}" fill="none" stroke="${color}" stroke-width="${w}" stroke-linecap="round"/>`);
}

function wheat() {
  const out = [];
  let p = new Pic(); mound(p, 64, C.gold2); out.push(stage('wheat', 0, p));

  p = new Pic(); mound(p, 52);
  [[-14, 58, -16], [6, 74, 8], [-2, 50, -4], [18, 56, 18]].forEach(([dx, h, b]) => p.add(blade(128 + dx, G - 4, h, b, 7, p.lg([C.leaf1, C.leaf2]))));
  out.push(stage('wheat', 1, p));

  p = new Pic(); mound(p, 60);
  [[-30, 110, -30], [30, 108, 30], [-16, 130, -12], [16, 130, 14], [0, 140, 2], [-40, 84, -40], [40, 86, 42]].forEach(([dx, h, b]) =>
    p.add(blade(128 + dx * 0.5, G - 4, h, b, 9, p.lg([C.leaf1, C.leaf2, C.leaf3]))));
  [[-22, 150, -10], [10, 164, 4], [32, 144, 14]].forEach(([dx, h, a]) => {
    stalk(p, 128 + dx * 0.4, G - 6, 128 + dx, G - h, C.leaf2, 5);
    wheatHead(p, 128 + dx, G - h, a, 50, [C.green1, C.leaf2, C.leaf4]);
  });
  out.push(stage('wheat', 2, p));

  p = new Pic(); mound(p, 66);
  [[-46, 96, -50], [46, 96, 50], [-26, 120, -30], [26, 118, 30]].forEach(([dx, h, b]) => p.add(blade(128 + dx * 0.4, G - 4, h, b, 9, p.lg([C.gold2, C.gold3, C.gold4]))));
  const heads = [[-58, 140, -22], [-30, 168, -10], [0, 180, 0], [30, 168, 10], [58, 142, 22], [-14, 150, -6], [16, 152, 6]];
  heads.forEach(([dx, h]) => stalk(p, 128 + dx * 0.25, G - 6, 128 + dx, G - h + 6, C.gold3, 5));
  heads.forEach(([dx, h, a]) => wheatHead(p, 128 + dx, G - h + 6, a, 56, [C.gold1, C.gold2, C.gold4]));
  out.push(stage('wheat', 3, p));
  return out;
}

// ---------- Carrot ----------
function frond(p, x, y, h, bend, col, leafSize = 1) {
  const tx = x + bend, ty = y - h;
  const d = `M${x} ${y} Q${f(x + bend * 0.1)} ${f(y - h * 0.6)} ${f(tx)} ${f(ty)}`;
  p.add(`<path d="${d}" fill="none" stroke="${C.ink}" stroke-width="10" stroke-linecap="round"/><path d="${d}" fill="none" stroke="${C.leaf3}" stroke-width="4.5" stroke-linecap="round"/>`);
  const leaves = [];
  for (let t = 0.4; t <= 1.001; t += 0.15) {
    const px = (1 - t) * (1 - t) * x + 2 * (1 - t) * t * (x + bend * 0.1) + t * t * tx;
    const py = (1 - t) * (1 - t) * y + 2 * (1 - t) * t * (y - h * 0.6) + t * t * ty;
    const s = leafSize * (0.7 + t * 0.4);
    if (t > 0.95) {
      [-35, 0, 35].forEach((a) => leaves.push(leaf(px, py, 26 * s, 9 * s, a + bend * 0.3, col, { ow: 6 })));
    } else {
      leaves.push(leaf(px, py, 22 * s, 8 * s, -62 + bend * 0.3, col, { ow: 6 }));
      leaves.push(leaf(px, py, 22 * s, 8 * s, 62 + bend * 0.3, col, { ow: 6 }));
    }
  }
  p.add(leaves.join(''));
}

function carrotShoulder(p, rx, ry) {
  const clip = p.clip(`<rect x="0" y="0" width="${W}" height="${G + 4}"/>`);
  p.add(`<g clip-path="${clip}"><ellipse cx="128" cy="${G + ry * 0.35}" rx="${rx}" ry="${ry}" fill="${p.lg([C.orange1, C.orange2, C.orange3])}" ${ol(8)}/>
    <path d="M${128 - rx * 0.6} ${G - ry * 0.25} q${rx * 0.25} 5 ${rx * 0.5} 0 M${128 + rx * 0.1} ${G - ry * 0.05} q${rx * 0.25} 5 ${rx * 0.45} 0" stroke="${C.orange4}" stroke-width="3.5" fill="none" stroke-linecap="round"/>
    <ellipse cx="${128 - rx * 0.35}" cy="${G - ry * 0.35}" rx="${rx * 0.25}" ry="${ry * 0.18}" fill="#fff" opacity="0.5"/></g>`);
}

function carrot() {
  const out = [];
  let p = new Pic(); mound(p, 64, '#e8c48a'); out.push(stage('carrot', 0, p));

  p = new Pic(); mound(p, 50);
  [[-6, 50, -18], [4, 62, 6], [10, 46, 22]].forEach(([dx, h, b]) => frond(p, 128 + dx, G - 4, h, b, p.lg([C.leaf1, C.leaf2]), 0.75));
  out.push(stage('carrot', 1, p));

  p = new Pic(); mound(p, 58);
  carrotShoulder(p, 24, 22);
  [[-10, 92, -46], [-4, 118, -18], [2, 130, 4], [8, 116, 24], [12, 90, 48]].forEach(([dx, h, b]) => frond(p, 128 + dx, G - 14, h, b, p.lg([C.leaf1, C.leaf2, C.leaf3])));
  soilLip(p, 44);
  out.push(stage('carrot', 2, p));

  p = new Pic(); mound(p, 66);
  carrotShoulder(p, 46, 46);
  [[-14, 118, -64], [-8, 150, -30], [-2, 168, -8], [4, 160, 14], [10, 146, 38], [14, 112, 66]].forEach(([dx, h, b], i) =>
    frond(p, 128 + dx, G - 26, h, b, p.lg(i % 2 ? [C.leaf1, C.leaf2, C.leaf3] : [C.green1, C.leaf2, C.leaf3]), 1.15));
  soilLip(p, 54);
  out.push(stage('carrot', 3, p));
  return out;
}

// ---------- Tomato ----------
function stake(p, top) {
  p.add(`<path d="M120 ${G + 4} L120 ${top + 12} L128 ${top} L136 ${top + 12} L136 ${G + 4}Z" fill="${p.lg([C.wood1, C.wood2, C.wood3], 0, 0, 1, 0)}" ${ol(8)}/>`);
}

function tomatoFruit(p, x, y, r, ripe = true) {
  const cols = ripe ? [C.red1, C.red2, C.red3] : [C.green1, C.green2, C.green3];
  p.add(`<circle cx="${x}" cy="${y}" r="${r}" fill="${p.rg([[0, cols[0]], [0.55, cols[1]], [1, cols[2]]], 0.38, 0.32, 0.75)}" ${ol(7)}/>`);
  p.add(`<ellipse cx="${f(x - r * 0.35)}" cy="${f(y - r * 0.4)}" rx="${f(r * 0.28)}" ry="${f(r * 0.18)}" transform="rotate(-30 ${f(x - r * 0.35)} ${f(y - r * 0.4)})" fill="#fff" opacity="0.75"/>`);
  p.add(`<polygon points="${star(x, y - r * 0.85, r * 0.6, r * 0.22, 5)}" fill="${C.leaf2}" ${ol(5)}/>`);
}

function tomatoBush(p, scale, seed) {
  const rnd = mulberry32(seed);
  const d = `M128 ${G} C 124 ${G - 60 * scale} 136 ${G - 110 * scale} 128 ${G - 170 * scale}`;
  p.add(`<path d="${d}" fill="none" stroke="${C.ink}" stroke-width="15" stroke-linecap="round"/><path d="${d}" fill="none" stroke="${C.leaf3}" stroke-width="7" stroke-linecap="round"/>`);
  const circles = [];
  for (let i = 0; i < 16; i++) {
    const t = i / 15;
    const y = G - (40 + t * 140) * scale;
    const spread = (52 - t * 22) * scale;
    circles.push([128 + (rnd() * 2 - 1) * spread, y + (rnd() - 0.5) * 16, (16 + rnd() * 10) * scale]);
  }
  p.add(blobs(circles, { fill: p.lg([C.leaf1, C.leaf2, C.leaf3]), shade: C.leaf3, hi: C.leaf1, hiScale: 0.35, ow: 6 }));
}

function tomato() {
  const out = [];
  let p = new Pic(); mound(p, 64, '#f2e3b5'); out.push(stage('tomato', 0, p));

  p = new Pic(); mound(p, 50);
  p.add(`<path d="M128 ${G - 4} L128 ${G - 50}" stroke="${C.ink}" stroke-width="12" stroke-linecap="round"/><path d="M128 ${G - 4} L128 ${G - 50}" stroke="${C.leaf3}" stroke-width="5" stroke-linecap="round"/>`);
  p.add(leaf(128, G - 46, 38, 18, -58, p.lg([C.leaf1, C.leaf2])), leaf(128, G - 46, 38, 18, 58, p.lg([C.leaf1, C.leaf2])));
  out.push(stage('tomato', 1, p));

  p = new Pic(); mound(p, 58); stake(p, 92);
  tomatoBush(p, 0.95, 7);
  [[100, G - 120], [160, G - 150], [118, G - 70]].forEach(([x, y]) => {
    p.add(`<polygon points="${star(x, y, 11, 5, 5)}" fill="${C.gold2}" ${ol(5)}/><circle cx="${x}" cy="${y}" r="3.5" fill="${C.orange3}"/>`);
  });
  tomatoFruit(p, 150, G - 92, 11, false);
  out.push(stage('tomato', 2, p));

  p = new Pic(); mound(p, 66); stake(p, 70);
  tomatoBush(p, 1.12, 11);
  p.add(`<path d="M116 ${G - 110} h24 M116 ${G - 170} h24" stroke="${C.gold4}" stroke-width="5" stroke-linecap="round"/>`);
  [[92, G - 82, 21], [164, G - 104, 23], [108, G - 150, 19], [150, G - 176, 18], [130, G - 58, 20]].forEach(([x, y, r]) => tomatoFruit(p, x, y, r));
  out.push(stage('tomato', 3, p));
  return out;
}

// ---------- Pumpkin ----------
function lobedLeaf(p, x, y, r, rot = 0) {
  const fill = p.lg([C.leaf1, C.leaf2, C.leaf3]);
  [-64, 64, -32, 32, 0].forEach((a) => p.add(leaf(x, y, r * (a === 0 ? 1.05 : 0.85), r * 0.42, rot + a, fill, { ow: 7 })));
}

function pumpkinBody(p, cx, by, w, h, cols = [C.orange1, C.orange2, C.orange3]) {
  const cy = by - h / 2;
  const ribs = [[-0.34, 0.3], [0.34, 0.3], [-0.17, 0.32], [0.17, 0.32], [0, 0.3]];
  ribs.forEach(([dx, rw], i) => {
    const fill = p.lg([[0, cols[0]], [0.4, cols[1]], [1, cols[2]]]);
    p.add(`<ellipse cx="${f(cx + dx * w)}" cy="${f(cy)}" rx="${f(rw * w)}" ry="${f(h / 2)}" fill="${fill}" ${ol(7)}/>`);
    if (i === 4) p.add(`<ellipse cx="${f(cx - w * 0.06)}" cy="${f(cy - h * 0.22)}" rx="${f(w * 0.06)}" ry="${f(h * 0.15)}" fill="#fff" opacity="0.45"/>`);
  });
  const sx = cx, sy = by - h + 6;
  p.add(`<path d="M${sx - 7} ${sy + 4} C ${sx - 8} ${sy - 18} ${sx + 4} ${sy - 30} ${sx + 18} ${sy - 32} L ${sx + 20} ${sy - 22} C ${sx + 8} ${sy - 18} ${sx + 7} ${sy - 8} ${sx + 8} ${sy + 4}Z" fill="${p.lg([C.wood1, C.wood3])}" ${ol(7)}/>`);
  p.add(`<path d="M${sx + 10} ${sy - 10} c 22 -6 30 10 18 18 c -10 6 -18 -4 -10 -9" fill="none" stroke="${C.ink}" stroke-width="8" stroke-linecap="round"/><path d="M${sx + 10} ${sy - 10} c 22 -6 30 10 18 18 c -10 6 -18 -4 -10 -9" fill="none" stroke="${C.leaf2}" stroke-width="3.5" stroke-linecap="round"/>`);
}

function vine(p, d) {
  p.add(`<path d="${d}" fill="none" stroke="${C.ink}" stroke-width="13" stroke-linecap="round"/><path d="${d}" fill="none" stroke="${C.leaf3}" stroke-width="6" stroke-linecap="round"/>`);
}

function pumpkin() {
  const out = [];
  let p = new Pic(); mound(p, 64, '#fff0c8'); out.push(stage('pumpkin', 0, p));

  p = new Pic(); mound(p, 50);
  p.add(`<path d="M128 ${G - 4} L128 ${G - 40}" stroke="${C.ink}" stroke-width="12" stroke-linecap="round"/><path d="M128 ${G - 4} L128 ${G - 40}" stroke="${C.leaf3}" stroke-width="5" stroke-linecap="round"/>`);
  p.add(`<ellipse cx="100" cy="${G - 52}" rx="30" ry="20" transform="rotate(-24 100 ${G - 52})" fill="${p.lg([C.leaf1, C.leaf2])}" ${ol(7)}/>`);
  p.add(`<ellipse cx="156" cy="${G - 52}" rx="30" ry="20" transform="rotate(24 156 ${G - 52})" fill="${p.lg([C.leaf1, C.leaf2])}" ${ol(7)}/>`);
  out.push(stage('pumpkin', 1, p));

  p = new Pic(); mound(p, 62);
  vine(p, `M128 ${G - 6} C 90 ${G - 30} 60 ${G - 60} 70 ${G - 110} M128 ${G - 6} C 170 ${G - 40} 200 ${G - 70} 190 ${G - 120}`);
  lobedLeaf(p, 72, G - 120, 48, -20); lobedLeaf(p, 188, G - 128, 46, 20); lobedLeaf(p, 128, G - 150, 52, 0);
  pumpkinBody(p, 136, G - 2, 64, 46, [C.green1, C.green2, C.green3]);
  out.push(stage('pumpkin', 2, p));

  p = new Pic(); mound(p, 76);
  vine(p, `M128 ${G - 10} C 70 ${G - 40} 40 ${G - 90} 56 ${G - 150} M128 ${G - 10} C 190 ${G - 40} 220 ${G - 90} 204 ${G - 150}`);
  lobedLeaf(p, 54, G - 150, 46, -24); lobedLeaf(p, 204, G - 156, 46, 24);
  pumpkinBody(p, 128, G + 4, 168, 124);
  out.push(stage('pumpkin', 3, p));
  return out;
}

// ---------- Produce icons (256×256) ----------
function itemWheat() {
  const p = new Pic(); const sh = p.shadow(8, 5, 0.35);
  p.add(`<g filter="${sh}">`);
  const stalks = [-56, -40, -22, -8, 8, 22, 40, 56];
  stalks.forEach((a) => stalk(p, 128 + a * 0.5, 236, 128 + a * 0.9, 112 + Math.abs(a) * 0.5, C.gold3, 5));
  stalks.forEach((a) => wheatHead(p, 128 + a * 0.9, 112 + Math.abs(a) * 0.5, a * 0.45, 50, [C.gold1, C.gold2, C.gold4]));
  p.add(`<path d="M96 168 Q128 184 160 168 L162 186 Q128 202 94 186Z" fill="${p.lg([C.red1, C.red2, C.red3])}" ${ol(7)}/>`);
  p.add(`<path d="M128 182 l-26 30 l14 2 l12 -26 l12 26 l14 -2Z" fill="${C.red3}" ${ol(6)}/>`);
  p.add('</g>');
  return asset('item_wheat', 256, 256, p.body(), { kind: 'item' });
}

function itemCarrot() {
  const p = new Pic(); const sh = p.shadow(8, 5, 0.35);
  p.add(`<g filter="${sh}">`);
  p.add(leaf(84, 92, 70, 16, -70, p.lg([C.leaf1, C.leaf2, C.leaf3]), { ow: 8 }), leaf(84, 92, 80, 18, -30, p.lg([C.green1, C.leaf2, C.leaf3]), { ow: 8 }), leaf(84, 92, 64, 15, 4, p.lg([C.leaf1, C.leaf2, C.leaf3]), { ow: 8 }));
  p.add(`<path d="M62 96 C 70 66 110 64 122 86 C 160 140 190 180 214 222 C 170 206 120 170 74 126 C 64 116 60 106 62 96Z" fill="${p.lg([C.orange1, C.orange2, C.orange3], 0, 0, 1, 1)}" ${ol(9)}/>`);
  p.add(`<path d="M96 104 q14 0 20 -10 M118 140 q14 2 22 -10 M148 170 q12 0 18 -10" stroke="${C.orange4}" stroke-width="4" fill="none" stroke-linecap="round"/>`);
  p.add(`<path d="M76 100 C 80 86 92 82 102 86" stroke="#fff" stroke-opacity="0.6" stroke-width="7" fill="none" stroke-linecap="round"/>`);
  p.add('</g>');
  return asset('item_carrot', 256, 256, p.body(), { kind: 'item' });
}

function itemTomato() {
  const p = new Pic(); const sh = p.shadow(8, 5, 0.35);
  p.add(`<g filter="${sh}">`);
  p.add(`<ellipse cx="128" cy="146" rx="88" ry="80" fill="${p.rg([[0, C.red1], [0.5, C.red2], [1, C.red3]], 0.38, 0.32, 0.75)}" ${ol(10)}/>`);
  p.add(`<path d="M86 96 q-20 26 -14 58" stroke="${C.red3}" stroke-width="5" fill="none" opacity="0.5" stroke-linecap="round"/><path d="M170 96 q20 26 14 58" stroke="${C.red3}" stroke-width="5" fill="none" opacity="0.5" stroke-linecap="round"/>`);
  p.add(`<ellipse cx="90" cy="112" rx="24" ry="14" transform="rotate(-35 90 112)" fill="#fff" opacity="0.75"/>`);
  p.add(`<polygon points="${star(128, 76, 46, 16, 5)}" fill="${p.lg([C.leaf1, C.leaf2, C.leaf3])}" ${ol(8)}/>`);
  p.add(`<path d="M128 70 q2 -26 16 -36" stroke="${C.ink}" stroke-width="16" fill="none" stroke-linecap="round"/><path d="M128 70 q2 -26 16 -36" stroke="${C.leaf3}" stroke-width="8" fill="none" stroke-linecap="round"/>`);
  p.add('</g>');
  return asset('item_tomato', 256, 256, p.body(), { kind: 'item' });
}

function itemPumpkin() {
  const p = new Pic(); const sh = p.shadow(8, 5, 0.35);
  p.add(`<g filter="${sh}">`);
  pumpkinBody(p, 128, 226, 200, 150);
  p.add('</g>');
  return asset('item_pumpkin', 256, 256, p.body(), { kind: 'item' });
}

export function buildCrops() {
  return [...wheat(), ...carrot(), ...tomato(), ...pumpkin(), itemWheat(), itemCarrot(), itemTomato(), itemPumpkin()];
}
