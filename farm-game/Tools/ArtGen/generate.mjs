// Generates every sprite in Assets/Art from code.
//   cd farm-game/Tools/ArtGen && npm install && npm run generate
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Resvg } from '@resvg/resvg-js';
import { toSvg, place, C } from './lib/svg.mjs';
import { buildIcons } from './lib/icons.mjs';
import { buildCrops } from './lib/crops.mjs';
import { buildWorld } from './lib/world.mjs';
import { buildUi } from './lib/ui.mjs';
import { buildFx } from './lib/fx.mjs';
import { buildAppIcon, buildMockup } from './lib/scenes.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const artDir = path.join(root, 'Assets/Art');
const fontFile = path.join(artDir, 'Fonts/LilitaOne-Regular.ttf');
const only = process.argv[2];

function render(svg, outW, file) {
  const png = new Resvg(svg, {
    fitTo: { mode: 'width', value: outW },
    font: { fontFiles: [fontFile], loadSystemFonts: false, defaultFontFamily: 'Lilita One' },
  }).render().asPng();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, png);
}

const groups = {
  Icons: buildIcons(path.join(here, 'game-icons')),
  Crops: buildCrops(),
  World: buildWorld(),
  UI: buildUi(),
  FX: buildFx(),
};

let count = 0;
for (const [dir, assets] of Object.entries(groups)) {
  if (only && only !== dir) continue;
  for (const a of assets) {
    const outW = a.out ?? a.w;
    render(toSvg(a), outW, path.join(artDir, dir, `${a.name}.png`));
    count++;
  }
  // Contact sheet per group for quick review (not imported by Unity: lives in Docs).
  const cell = 200, cols = 6, rows = Math.ceil(assets.length / cols);
  const cells = assets
    .map((a, i) => {
      const x = (i % cols) * cell, y = Math.floor(i / cols) * cell;
      const s = Math.min((cell - 40) / a.w, (cell - 40) / a.h);
      const w = a.w * s, h = a.h * s;
      return `${place(a, x + (cell - w) / 2, y + 6 + (cell - 34 - h) / 2, w, h)}<text x="${x + cell / 2}" y="${y + cell - 8}" font-size="13" text-anchor="middle" fill="#fff">${a.name}</text>`;
    })
    .join('');
  const sheet = `<svg xmlns="http://www.w3.org/2000/svg" width="${cols * cell}" height="${rows * cell}"><rect width="100%" height="100%" fill="#3d6b35"/>${cells}</svg>`;
  render(sheet, cols * cell, path.join(root, 'Docs/sheets', `${dir}.png`));
}

if (!only || only === 'Scenes') {
  const appIcon = buildAppIcon(groups);
  render(toSvg(appIcon), 1024, path.join(artDir, 'AppIcon/app_icon.png'));
  const mock = buildMockup(groups);
  render(toSvg(mock), 1080, path.join(root, 'Docs/preview.png'));
  count += 2;
}

console.log(`Rendered ${count} images.`);
