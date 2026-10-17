// Generates every sprite in app/src/main/assets/art.
//   cd farm-game/Tools/ArtGen && npm install && node generate.mjs [group ...]
//
// Vector modules (lib/<group>.mjs) export build() -> [{ name, asset, px, pivot?, style? }]:
//   name   output path under assets/art without .png, e.g. "crops/wheat_0"
//   asset  vector art from svg.mjs asset(); drawn soft (no outlines) — the pixel pass adds them
//   px     target sprite width in pixels before the 2px outline padding
//   pivot  [fx, fy] fraction of the vector box placed on the draw position (default [0.5, 1])
//   style  pixelize overrides: { outline: 'black'|'selout'|'none', flags: ['dither','shadow',...] }
// Pixel modules (pixel/<group>.py) draw sprites pixel-by-pixel and print their pivots as JSON.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { Resvg } from '@resvg/resvg-js';
import { toSvg } from './lib/svg.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const artDir = path.join(root, 'app/src/main/assets/art');
const sheetDir = path.join(root, 'Docs/sheets');
const manifestFile = path.join(artDir, 'manifest.json');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'artgen-'));

const VECTOR = fs.readdirSync(path.join(here, 'lib')).filter((f) => f.endsWith('.mjs') && f !== 'svg.mjs').map((f) => f.replace('.mjs', ''));
const PIXEL = fs.existsSync(path.join(here, 'pixel')) ? fs.readdirSync(path.join(here, 'pixel')).filter((f) => f.endsWith('.py') && !f.startsWith('_')).map((f) => f.replace('.py', '')) : [];
const wanted = process.argv.slice(2);
const pick = (g) => wanted.length === 0 || wanted.includes(g);

const manifest = fs.existsSync(manifestFile) ? JSON.parse(fs.readFileSync(manifestFile, 'utf8')) : {};
const DEFAULT_STYLE = { palette: 'endesga32', outline: 'black', flags: ['dither', 'shadow'], pad: 2 };
const touched = new Set();

for (const group of VECTOR.filter(pick)) {
  const mod = await import(path.join(here, 'lib', `${group}.mjs`));
  if (typeof mod.build !== 'function') continue;
  const sprites = mod.build();
  const jobs = [];
  for (const s of sprites) {
    const style = { ...DEFAULT_STYLE, ...(s.style || {}) };
    const w = s.px;
    const h = Math.max(1, Math.round((w * s.asset.h) / s.asset.w));
    const src = path.join(tmp, `${s.name.replaceAll('/', '__')}.png`);
    const png = new Resvg(toSvg(s.asset), { fitTo: { mode: 'width', value: w } }).render().asPng();
    fs.writeFileSync(src, png);
    const out = path.join(artDir, `${s.name}.png`);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    jobs.push({ src, out, ...style });
    const [fx, fy] = s.pivot || [0.5, 1];
    manifest[s.name] = { ax: Math.round(fx * w) + style.pad, ay: Math.round(fy * h) + style.pad };
    touched.add(s.name.split('/')[0]);
  }
  const jobFile = path.join(tmp, `${group}.json`);
  fs.writeFileSync(jobFile, JSON.stringify(jobs));
  execFileSync('python3', ['-I', path.join(here, 'pixelize.py'), jobFile], { stdio: 'inherit' });
  console.log(`${group}: ${sprites.length} sprites`);
}

for (const group of PIXEL.filter(pick)) {
  const out = execFileSync('python3', ['-I', path.join(here, 'pixel', `${group}.py`), artDir], { encoding: 'utf8' });
  const pivots = JSON.parse(out);
  Object.assign(manifest, pivots);
  for (const k of Object.keys(pivots)) touched.add(k.split('/')[0]);
  console.log(`${group}: ${Object.keys(pivots).length} sprites`);
}

const sorted = Object.fromEntries(Object.entries(manifest).sort(([a], [b]) => a.localeCompare(b)));
fs.writeFileSync(manifestFile, JSON.stringify(sorted, null, 1) + '\n');

// Contact sheets (4x, nearest) for review; not shipped in the APK.
for (const dir of touched) {
  execFileSync('python3', ['-I', path.join(here, 'sheet.py'), path.join(artDir, dir), path.join(sheetDir, `${dir}.png`)], { stdio: 'inherit' });
}
fs.rmSync(tmp, { recursive: true, force: true });
