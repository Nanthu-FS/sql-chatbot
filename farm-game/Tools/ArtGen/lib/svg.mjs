// Shared SVG helpers, palette and seeded RNG for the art generator.
import fs from 'node:fs';

export const C = {
  ink: '#2a1a12',
  inkSoft: '#4a2f1f',
  cream: '#fff6dc',
  creamDark: '#f2d9a6',
  white: '#ffffff',

  grass1: '#8fd14f', grass2: '#6abe30', grass3: '#4f9a2a', grass4: '#3b7a22',
  leaf1: '#9be15d', leaf2: '#5cbf3a', leaf3: '#2f8a2f', leaf4: '#1f6526',

  soil1: '#c48a55', soil2: '#a66c3c', soil3: '#7d4b27', soil4: '#5a331b',
  wet1: '#8a5634', wet2: '#6b3f24', wet3: '#4e2c18', wet4: '#3a1f10',

  wood1: '#e0a86a', wood2: '#c47f42', wood3: '#93572a', wood4: '#6a3b1c',
  gold1: '#fff3a0', gold2: '#ffd54a', gold3: '#f0a020', gold4: '#b8650f',
  water1: '#bdf0ff', water2: '#5ecbff', water3: '#1e88e5', water4: '#0d4f9e',
  red1: '#ff9b8a', red2: '#ff5a4e', red3: '#d6312a', red4: '#8e1b18',
  orange1: '#ffd08a', orange2: '#ff9a2e', orange3: '#e0661a', orange4: '#9c3d0d',
  green1: '#c8f78a', green2: '#7ed957', green3: '#3fa83a', green4: '#22662a',
  steel1: '#f4f8fb', steel2: '#c9d6e2', steel3: '#8aa4b8', steel4: '#4c6275',
  purple1: '#e9c7ff', purple2: '#b57cff', purple3: '#7a3fd6', purple4: '#4a2190',
  sky1: '#ffe9b8', sky2: '#ffc27a', sky3: '#7fd3ff', sky4: '#3a9be8',
};

let _id = 0;
export const uid = (p = 'id') => `${p}${++_id}`;

export function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export const f = (n) => +n.toFixed(2);

// Vertical (default) linear gradient. stops: [[offset, color, opacity?], ...] or [color, color, ...]
export function lg(id, stops, x1 = 0, y1 = 0, x2 = 0, y2 = 1, units = 'objectBoundingBox') {
  return `<linearGradient id="${id}" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" gradientUnits="${units}">${stopList(stops)}</linearGradient>`;
}

export function rg(id, stops, cx = 0.5, cy = 0.5, r = 0.5, fx = cx, fy = cy, units = 'objectBoundingBox') {
  return `<radialGradient id="${id}" cx="${cx}" cy="${cy}" r="${r}" fx="${fx}" fy="${fy}" gradientUnits="${units}">${stopList(stops)}</radialGradient>`;
}

function stopList(stops) {
  return stops
    .map((s, i) => {
      if (typeof s === 'string') s = [i / Math.max(1, stops.length - 1), s];
      const [o, c, op = 1] = s;
      return `<stop offset="${o}" stop-color="${c}" stop-opacity="${op}"/>`;
    })
    .join('');
}

export function dropShadow(id, dy = 6, blur = 4, opacity = 0.35, color = '#1a0d05') {
  return `<filter id="${id}" x="-30%" y="-30%" width="160%" height="170%"><feDropShadow dx="0" dy="${dy}" stdDeviation="${blur}" flood-color="${color}" flood-opacity="${opacity}"/></filter>`;
}

export function blur(id, sd) {
  return `<filter id="${id}" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="${sd}"/></filter>`;
}

// Sticker outline: stroke drawn under the fill so only the outer half shows.
export const ol = (w = 10, color = C.ink) =>
  `stroke="${color}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round" paint-order="stroke"`;

// Blobby silhouette from a set of circles: ink pass, fill pass, then highlight pass.
export function blobs(circles, { fill, ink = C.ink, ow = 5, hi = null, hiOffset = [-0.25, -0.3], hiScale = 0.45, shade = null }) {
  const out = [];
  out.push(`<g fill="${ink}">${circles.map(([x, y, r]) => `<circle cx="${f(x)}" cy="${f(y)}" r="${f(r + ow)}"/>`).join('')}</g>`);
  out.push(`<g fill="${fill}">${circles.map(([x, y, r]) => `<circle cx="${f(x)}" cy="${f(y)}" r="${f(r)}"/>`).join('')}</g>`);
  if (shade) {
    out.push(`<g fill="${shade}">${circles.map(([x, y, r]) => `<circle cx="${f(x + r * 0.18)}" cy="${f(y + r * 0.28)}" r="${f(r * 0.55)}"/>`).join('')}</g>`);
  }
  if (hi) {
    out.push(`<g fill="${hi}">${circles.map(([x, y, r]) => `<circle cx="${f(x + r * hiOffset[0])}" cy="${f(y + r * hiOffset[1])}" r="${f(r * hiScale)}"/>`).join('')}</g>`);
  }
  return out.join('');
}

// Tapered leaf pointing "up" from its base, then rotated by deg around the base.
export function leaf(x, y, len, wid, deg, fill, { ink = C.ink, ow = 8, vein = 'rgba(255,255,255,0.35)', curl = 0 } = {}) {
  const d = `M0 0 C ${f(wid)} ${f(-len * 0.25)} ${f(wid * 0.7 + curl)} ${f(-len * 0.8)} ${f(curl)} ${f(-len)} C ${f(-wid * 0.7 + curl)} ${f(-len * 0.8)} ${f(-wid)} ${f(-len * 0.25)} 0 0Z`;
  const v = `M0 ${f(-len * 0.08)} Q ${f(curl * 0.4)} ${f(-len * 0.55)} ${f(curl * 0.9)} ${f(-len * 0.9)}`;
  return `<g transform="translate(${f(x)} ${f(y)}) rotate(${f(deg)})"><path d="${d}" fill="${fill}" ${ol(ow, ink)}/><path d="${v}" fill="none" stroke="${vein}" stroke-width="${f(Math.max(2, wid * 0.12))}" stroke-linecap="round"/></g>`;
}

// Tapered blade (grass, wheat leaves) from base to tip with a bend.
export function blade(x, y, h, bend, w, fill, { ink = C.ink, ow = 7 } = {}) {
  const tx = x + bend, ty = y - h;
  const d = `M${f(x - w)} ${f(y)} Q ${f(x - w * 0.6 + bend * 0.3)} ${f(y - h * 0.6)} ${f(tx)} ${f(ty)} Q ${f(x + w * 0.6 + bend * 0.5)} ${f(y - h * 0.55)} ${f(x + w)} ${f(y)}Z`;
  return `<path d="${d}" fill="${fill}" ${ol(ow, ink)}/>`;
}

export function star(cx, cy, rOuter, rInner, points = 4, rot = -90) {
  const pts = [];
  for (let i = 0; i < points * 2; i++) {
    const r = i % 2 === 0 ? rOuter : rInner;
    const a = ((rot + (i * 180) / points) * Math.PI) / 180;
    pts.push(`${f(cx + r * Math.cos(a))},${f(cy + r * Math.sin(a))}`);
  }
  return pts.join(' ');
}

// Rounded rect path.
export function rr(x, y, w, h, r) {
  return `<rect x="${f(x)}" y="${f(y)}" width="${f(w)}" height="${f(h)}" rx="${f(r)}" ry="${f(r)}"`;
}

// Small builder that collects <defs> alongside drawing parts.
export class Pic {
  constructor() { this.defs = []; this.parts = []; }
  lg(stops, x1 = 0, y1 = 0, x2 = 0, y2 = 1, units) { const id = uid('lg'); this.defs.push(lg(id, stops, x1, y1, x2, y2, units)); return `url(#${id})`; }
  rg(stops, cx, cy, r, fx, fy, units) { const id = uid('rg'); this.defs.push(rg(id, stops, cx, cy, r, fx, fy, units)); return `url(#${id})`; }
  shadow(dy, b, op) { const id = uid('ds'); this.defs.push(dropShadow(id, dy, b, op)); return `url(#${id})`; }
  blur(sd) { const id = uid('bl'); this.defs.push(blur(id, sd)); return `url(#${id})`; }
  clip(shape) { const id = uid('cp'); this.defs.push(`<clipPath id="${id}">${shape}</clipPath>`); return `url(#${id})`; }
  add(...s) { this.parts.push(...s); return this; }
  body() { return `<defs>${this.defs.join('')}</defs>${this.parts.join('')}`; }
}

// Asset container. body is SVG content in a w×h coordinate space.
export function asset(name, w, h, body, meta = {}) {
  return { name, w, h, body, ...meta };
}

export function toSvg(a, outW = a.w, outH = a.h) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${outW}" height="${outH}" viewBox="0 0 ${a.w} ${a.h}">${a.body}</svg>`;
}

// Place an asset inside another SVG (unique ids are global, so nesting is safe).
export function place(a, x, y, w = a.w, h = a.h, extra = '') {
  return `<svg x="${f(x)}" y="${f(y)}" width="${f(w)}" height="${f(h)}" viewBox="0 0 ${a.w} ${a.h}" overflow="visible" ${extra}>${a.body}</svg>`;
}

// Silhouette paths of a game-icons.net glyph (CC BY 3.0, credit in CREDITS.md) in a 512 box.
export function loadGlyph(name) {
  const file = new URL(`../game-icons/${name}.svg`, import.meta.url);
  const src = fs.readFileSync(file, 'utf8');
  const ds = [...src.matchAll(/<path[^>]*\sd="([^"]+)"/g)].map((m) => m[1]).filter((d) => !/^M0 0h512v512H0z$/.test(d));
  if (!ds.length) throw new Error(`no glyph paths in ${name}`);
  return ds;
}

// Removes the vector ink outlines so the pixel pass can add its own 1px outline.
export function noInk(a) {
  return { ...a, body: a.body.replaceAll(`stroke="${C.ink}"`, 'stroke="none"').replaceAll(`fill="${C.ink}"`, 'fill="none"') };
}
