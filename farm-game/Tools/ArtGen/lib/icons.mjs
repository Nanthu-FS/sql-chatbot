// Restyles game-icons.net glyphs (CC BY 3.0) into glossy, outlined game icons.
import fs from 'node:fs';
import path from 'node:path';
import { C, uid, lg, rg, dropShadow, asset } from './svg.mjs';

export function loadGlyph(dir, file) {
  const src = fs.readFileSync(path.join(dir, file + '.svg'), 'utf8');
  const ds = [...src.matchAll(/<path[^>]*\sd="([^"]+)"/g)].map((m) => m[1]).filter((d) => !/^M0 0h512v512H0z$/.test(d));
  if (!ds.length) throw new Error(`no glyph paths in ${file}`);
  return ds;
}

const paths = (ds, attrs) => ds.map((d) => `<path d="${d}" ${attrs}/>`).join('');

// Colored glyph with ink outline, gradient body and a soft top sheen.
export function glyphBody(ds, { top, mid, bottom, ink = C.ink, ow = 34, scale = 0.82, shadow = true }) {
  const g = uid('g'), m = uid('m'), mg = uid('mg'), sh = uid('sh');
  const t = `translate(256 256) scale(${scale}) translate(-256 -256)`;
  return `
  <defs>
    ${lg(g, [[0, top], [0.45, mid], [1, bottom]], 0, 40, 0, 470, 'userSpaceOnUse')}
    ${lg(mg, [[0, '#fff', 1], [0.42, '#fff', 0.55], [0.6, '#fff', 0]], 0, 0, 0, 512, 'userSpaceOnUse')}
    <mask id="${m}"><rect width="512" height="512" fill="url(#${mg})"/></mask>
    ${dropShadow(sh, 14, 6, 0.4)}
  </defs>
  <g ${shadow ? `filter="url(#${sh})"` : ''} transform="${t}">
    <g>${paths(ds, `fill="${ink}" stroke="${ink}" stroke-width="${ow}" stroke-linejoin="round"`)}</g>
    <g>${paths(ds, `fill="url(#${g})"`)}</g>
    <g mask="url(#${m})" opacity="0.38">${paths(ds, 'fill="#fff"')}</g>
  </g>`;
}

export function glyphIcon(name, ds, colors) {
  return asset(name, 512, 512, glyphBody(ds, colors), { kind: 'icon', out: 256 });
}

const BADGE = {
  green: [C.green1, C.green2, C.green3, C.green4],
  gold: [C.gold1, C.gold2, C.gold3, C.gold4],
  blue: [C.water1, C.water2, C.water3, C.water4],
  red: [C.red1, C.red2, C.red3, C.red4],
  wood: [C.wood1, C.wood2, C.wood3, C.wood4],
  purple: [C.purple1, C.purple2, C.purple3, C.purple4],
  steel: [C.steel1, C.steel2, C.steel3, C.steel4],
};

// Round 3D button badge with a cream glyph on top.
export function badgeIcon(name, ds, color = 'green') {
  const [c1, c2, c3, c4] = BADGE[color];
  const body = uid('bb'), rim = uid('br'), shine = uid('bs'), sh = uid('bsh');
  const glyph = glyphBody(ds, { top: '#ffffff', mid: C.cream, bottom: C.creamDark, ow: 40, scale: 0.56, shadow: false });
  return asset(
    name,
    512,
    512,
    `
  <defs>
    ${lg(body, [[0, c1], [0.5, c2], [1, c3]])}
    ${lg(rim, [[0, c3], [1, c4]])}
    ${rg(shine, [[0, '#fff', 0.75], [1, '#fff', 0]], 0.5, 0.2, 0.5)}
    ${dropShadow(sh, 12, 8, 0.45)}
  </defs>
  <g filter="url(#${sh})">
    <circle cx="256" cy="270" r="226" fill="${C.ink}"/>
    <circle cx="256" cy="268" r="212" fill="url(#${rim})"/>
    <circle cx="256" cy="246" r="226" fill="${C.ink}"/>
    <circle cx="256" cy="246" r="212" fill="url(#${body})"/>
    <circle cx="256" cy="246" r="196" fill="none" stroke="#fff" stroke-opacity="0.35" stroke-width="10"/>
    <ellipse cx="256" cy="150" rx="150" ry="80" fill="url(#${shine})"/>
  </g>
  <g transform="translate(0 -10)">${glyph}</g>`,
    { kind: 'badge', out: 256 },
  );
}

// [assetName, glyphFile, glyph colors, badge color or null]
export const ICONS = [
  ['coin', 'delapouite_two-coins', [C.gold1, C.gold2, C.gold4], 'gold'],
  ['coins_pile', 'delapouite_coins-pile', [C.gold1, C.gold2, C.gold4], null],
  ['water', 'delapouite_watering-can', [C.water1, C.water2, C.water4], 'blue'],
  ['harvest', 'delapouite_sickle', [C.steel1, C.steel2, C.steel4], 'gold'],
  ['seed', 'delapouite_plant-seed', [C.green1, C.green2, C.green4], 'green'],
  ['bag', 'lorc_knapsack', [C.wood1, C.wood2, C.wood4], 'wood'],
  ['shop', 'delapouite_shop', [C.red1, C.red2, C.red4], 'red'],
  ['sell', 'delapouite_receive-money', [C.green1, C.green2, C.green4], 'green'],
  ['save', 'delapouite_save', [C.water1, C.water2, C.water4], 'blue'],
  ['settings', 'lorc_cog', [C.steel1, C.steel2, C.steel4], 'steel'],
  ['calendar', 'delapouite_calendar', [C.red1, C.red2, C.red4], 'red'],
  ['sun', 'lorc_sun', [C.gold1, C.gold2, C.orange3], 'gold'],
  ['sun_cloud', 'delapouite_sun-cloud', [C.gold1, C.gold2, C.orange3], null],
  ['rain', 'lorc_raining', [C.water1, C.water2, C.water4], 'blue'],
  ['hourglass', 'lorc_hourglass', [C.gold1, C.wood1, C.wood4], null],
  ['trophy', 'lorc_trophy', [C.gold1, C.gold2, C.gold4], 'gold'],
  ['lock', 'lorc_padlock', [C.steel1, C.steel2, C.steel4], 'steel'],
  ['upgrade', 'delapouite_upgrade', [C.green1, C.green2, C.green4], 'green'],
  ['barn', 'delapouite_barn', [C.red1, C.red2, C.red4], 'red'],
  ['check', 'delapouite_check-mark', [C.green1, C.green2, C.green4], 'green'],
  ['sparkles', 'delapouite_sparkles', [C.gold1, C.gold2, C.gold3], null],
  ['tractor', 'delapouite_farm-tractor', [C.green1, C.green2, C.green4], 'green'],
  ['price', 'delapouite_price-tag', [C.gold1, C.gold2, C.gold4], null],
  ['fast_forward', 'delapouite_fast-forward-button', [C.water1, C.water2, C.water4], 'blue'],
  ['level', 'delapouite_star-medal', [C.gold1, C.gold2, C.gold4], 'purple'],
  ['menu', 'delapouite_hamburger-menu', [C.cream, C.creamDark, C.wood3], 'wood'],
  ['basket', 'delapouite_basket', [C.wood1, C.wood2, C.wood4], 'wood'],
  ['wheat_glyph', 'lorc_wheat', [C.gold1, C.gold2, C.gold4], 'gold'],
];

export function buildIcons(glyphDir) {
  const out = [];
  for (const [name, file, [top, mid, bottom], badge] of ICONS) {
    const ds = loadGlyph(glyphDir, file);
    out.push(glyphIcon(`icon_${name}`, ds, { top, mid, bottom }));
    if (badge) out.push(badgeIcon(`btn_${name}`, ds, badge));
  }
  return out;
}
