// Synthesises the game's chiptune sound effects into app/src/main/assets/sfx/*.wav.
//   node sfx.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const RATE = 22050;
const out = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../app/src/main/assets/sfx');

// --- oscillators -----------------------------------------------------------
let seed = 1;
const noise = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x3fffffff) - 1;
const square = (ph, duty = 0.5) => ((ph % 1) < duty ? 1 : -1);
const tri = (ph) => 1 - 4 * Math.abs((ph % 1) - 0.5);

/**
 * One voice. f0→f1 pitch sweep (Hz), wave: 'square'|'tri'|'noise', envelope:
 * attack a, decay to sustain level over the note, release r (seconds).
 */
function tone({ f0 = 440, f1 = f0, dur = 0.1, wave = 'square', duty = 0.5, vol = 0.5, a = 0.004, r = 0.04, vib = 0, start = 0, crush = 0 }) {
  return { f0, f1, dur, wave, duty, vol, a, r, vib, start, crush };
}

function render(voices) {
  const end = Math.max(...voices.map((v) => v.start + v.dur + v.r));
  const n = Math.ceil(end * RATE);
  const buf = new Float32Array(n);
  for (const v of voices) {
    let ph = 0;
    let held = 0;
    const s0 = Math.floor(v.start * RATE);
    const len = Math.ceil((v.dur + v.r) * RATE);
    for (let i = 0; i < len && s0 + i < n; i++) {
      const t = i / RATE;
      const k = Math.min(1, t / v.dur);
      const f = v.f0 * Math.pow(v.f1 / v.f0, k) * (1 + v.vib * Math.sin(t * 2 * Math.PI * 7));
      ph += f / RATE;
      let s = v.wave === 'tri' ? tri(ph) : v.wave === 'noise' ? noise() : square(ph, v.duty);
      if (v.crush) { if (i % v.crush === 0) held = s; s = held; }
      const env = t < v.a ? t / v.a : t < v.dur ? 1 - 0.35 * ((t - v.a) / v.dur) : Math.max(0, 0.65 * (1 - (t - v.dur) / v.r));
      buf[s0 + i] += s * env * v.vol;
    }
  }
  return buf;
}

// Simple one-pole low-pass, for thuds and rumbles.
function lowpass(buf, k) {
  let y = 0;
  for (let i = 0; i < buf.length; i++) buf[i] = y += k * (buf[i] - y);
  return buf;
}

function wav(name, buf, gain = 1) {
  let peak = 0;
  for (const s of buf) peak = Math.max(peak, Math.abs(s));
  const norm = peak > 0 ? (0.85 * gain) / peak : 0;
  const data = Buffer.alloc(buf.length * 2);
  buf.forEach((s, i) => data.writeInt16LE(Math.max(-32767, Math.min(32767, Math.round(s * norm * 32767))), i * 2));
  const h = Buffer.alloc(44);
  h.write('RIFF', 0); h.writeUInt32LE(36 + data.length, 4); h.write('WAVE', 8);
  h.write('fmt ', 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22);
  h.writeUInt32LE(RATE, 24); h.writeUInt32LE(RATE * 2, 28); h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34);
  h.write('data', 36); h.writeUInt32LE(data.length, 40);
  fs.writeFileSync(path.join(out, `${name}.wav`), Buffer.concat([h, data]));
}

const note = (n) => 440 * Math.pow(2, (n - 69) / 12); // MIDI note → Hz
const arp = (notes, step, opts = {}) => notes.map((m, i) => tone({ f0: note(m), dur: step, start: i * step, ...opts }));

// --- the sounds --------------------------------------------------------------
const SOUNDS = {
  click: () => render([tone({ f0: 1400, f1: 1100, dur: 0.025, vol: 0.35, duty: 0.25, r: 0.01 })]),
  open: () => render([tone({ f0: 660, f1: 990, dur: 0.06, vol: 0.3, duty: 0.25, r: 0.03 })]),
  close: () => render([tone({ f0: 990, f1: 560, dur: 0.06, vol: 0.3, duty: 0.25, r: 0.03 })]),
  plant: () => render([tone({ f0: 260, f1: 520, dur: 0.06, wave: 'tri', vol: 0.8, r: 0.05 }), tone({ wave: 'noise', dur: 0.02, vol: 0.2, r: 0.01, crush: 3 })]),
  water: () => lowpass(render([
    tone({ wave: 'noise', dur: 0.18, vol: 0.6, r: 0.12, crush: 2 }),
    tone({ f0: 1500, f1: 900, dur: 0.04, wave: 'tri', vol: 0.3, start: 0.12 }),
    tone({ f0: 1700, f1: 1000, dur: 0.04, wave: 'tri', vol: 0.25, start: 0.2 }),
  ]), 0.35),
  harvest: () => render(arp([72, 76, 79, 84], 0.05, { vol: 0.35, duty: 0.5, r: 0.06 })),
  coin: () => render([tone({ f0: note(83), dur: 0.06, vol: 0.35, duty: 0.5, r: 0.01 }), tone({ f0: note(88), dur: 0.18, vol: 0.35, start: 0.06, r: 0.12 })]),
  sell: () => render([
    tone({ f0: note(83), dur: 0.05, vol: 0.3, r: 0.01 }), tone({ f0: note(88), dur: 0.1, vol: 0.3, start: 0.05, r: 0.06 }),
    tone({ f0: note(86), dur: 0.05, vol: 0.3, start: 0.14, r: 0.01 }), tone({ f0: note(91), dur: 0.2, vol: 0.3, start: 0.19, r: 0.15 }),
  ]),
  buy: () => render([tone({ wave: 'noise', dur: 0.03, vol: 0.4, crush: 4 }), tone({ f0: note(79), dur: 0.05, vol: 0.3, start: 0.03 }), tone({ f0: note(84), dur: 0.16, vol: 0.3, start: 0.08, r: 0.1 })]),
  error: () => render([tone({ f0: 160, dur: 0.07, vol: 0.4, duty: 0.3 }), tone({ f0: 130, dur: 0.1, vol: 0.4, duty: 0.3, start: 0.1 })]),
  chop: () => lowpass(render([tone({ f0: 180, f1: 90, dur: 0.07, wave: 'tri', vol: 1 }), tone({ wave: 'noise', dur: 0.05, vol: 0.6, crush: 5 })]), 0.4),
  tree_fall: () => lowpass(render([
    tone({ wave: 'noise', dur: 0.35, vol: 0.5, crush: 6, r: 0.2 }),
    tone({ f0: 110, f1: 50, dur: 0.25, wave: 'tri', vol: 1, start: 0.3, r: 0.2 }),
  ]), 0.25),
  mine: () => render([tone({ f0: 1900, f1: 1500, dur: 0.03, vol: 0.35, duty: 0.2, r: 0.04 }), tone({ wave: 'noise', dur: 0.04, vol: 0.4, crush: 2 }), tone({ f0: 160, f1: 90, dur: 0.06, wave: 'tri', vol: 0.6 })]),
  rock_break: () => lowpass(render([tone({ wave: 'noise', dur: 0.22, vol: 0.8, crush: 4, r: 0.12 }), tone({ f0: 140, f1: 60, dur: 0.15, wave: 'tri', vol: 0.8 })]), 0.5),
  ore: () => render([...arp([88, 91, 96], 0.04, { vol: 0.25, duty: 0.25, r: 0.05 }), tone({ f0: 2200, dur: 0.02, vol: 0.2, duty: 0.2 })]),
  gem: () => render([...arp([84, 88, 91, 96, 100], 0.045, { wave: 'tri', vol: 0.4, r: 0.08, vib: 0.01 })]),
  craft: () => lowpass(render([
    tone({ f0: 1200, f1: 700, dur: 0.03, vol: 0.4, duty: 0.2 }), tone({ wave: 'noise', dur: 0.03, vol: 0.4, crush: 3 }),
    tone({ f0: 1300, f1: 800, dur: 0.03, vol: 0.4, duty: 0.2, start: 0.15 }), tone({ wave: 'noise', dur: 0.03, vol: 0.4, crush: 3, start: 0.15 }),
    ...arp([79, 84], 0.06, { vol: 0.25, r: 0.08 }).map((v) => ({ ...v, start: v.start + 0.3 })),
  ]), 0.8),
  machine_done: () => render([tone({ f0: note(91), dur: 0.08, wave: 'tri', vol: 0.5, r: 0.25 }), tone({ f0: note(96), dur: 0.1, wave: 'tri', vol: 0.5, start: 0.12, r: 0.3 })]),
  levelup: () => render([
    ...arp([72, 76, 79, 84], 0.07, { vol: 0.3, duty: 0.5, r: 0.02 }),
    tone({ f0: note(88), dur: 0.35, vol: 0.3, start: 0.28, vib: 0.02, r: 0.25 }),
    tone({ f0: note(76), dur: 0.35, wave: 'tri', vol: 0.4, start: 0.28, r: 0.25 }),
  ]),
  unlock: () => render(arp([76, 81, 85, 88, 93], 0.05, { wave: 'tri', vol: 0.4, vib: 0.015, r: 0.1 })),
  eat: () => lowpass(render([tone({ wave: 'noise', dur: 0.05, vol: 0.6, crush: 8 }), tone({ wave: 'noise', dur: 0.05, vol: 0.5, crush: 8, start: 0.11 }), tone({ f0: 300, f1: 500, dur: 0.06, wave: 'tri', vol: 0.4, start: 0.22 })]), 0.6),
  order: () => render([tone({ f0: note(79), dur: 0.08, wave: 'tri', vol: 0.5 }), tone({ f0: note(84), dur: 0.16, wave: 'tri', vol: 0.5, start: 0.09, r: 0.12 })]),
  swap: () => lowpass(render([tone({ wave: 'noise', dur: 0.25, vol: 0.5, crush: 2, a: 0.08, r: 0.08 })]), 0.18),
  thunder: () => lowpass(render([tone({ wave: 'noise', dur: 1.1, vol: 1, crush: 9, a: 0.01, r: 0.6 })]), 0.06),
  energy_low: () => render(arp([76, 72, 67], 0.07, { vol: 0.3, duty: 0.3, r: 0.03 })),
  drip: () => render([tone({ f0: 1800, f1: 900, dur: 0.05, wave: 'tri', vol: 0.5, r: 0.03 })]),
  pickup: () => render([tone({ f0: 700, f1: 1400, dur: 0.05, vol: 0.25, duty: 0.25, r: 0.02 })]),
  // Round 7: the bank's rubber stamp coming down hard, and the hammer on a nail as the title is rebuilt.
  stamp: () => lowpass(render([tone({ f0: 120, f1: 45, dur: 0.2, wave: 'tri', vol: 1 }), tone({ wave: 'noise', dur: 0.09, vol: 0.9, crush: 6, r: 0.07 })]), 0.35),
  tink: () => render([tone({ f0: 2400, f1: 2280, dur: 0.03, wave: 'tri', vol: 0.5, r: 0.16 }), tone({ f0: 3600, dur: 0.02, duty: 0.2, vol: 0.22, r: 0.08 }), tone({ wave: 'noise', dur: 0.015, vol: 0.4, crush: 2 })]),
};

// Extra takes of the most frequent sounds (name_2, name_3) so repeats don't grate; the
// game picks one at random each time, on top of a pitch and volume wobble.
const VARIANTS = {
  harvest: [
    () => render(arp([74, 77, 81, 86], 0.05, { vol: 0.35, duty: 0.5, r: 0.06 })),
    () => render(arp([71, 76, 79, 83], 0.045, { wave: 'tri', vol: 0.45, r: 0.07 })),
  ],
  chop: [
    () => lowpass(render([tone({ f0: 210, f1: 100, dur: 0.06, wave: 'tri', vol: 1 }), tone({ wave: 'noise', dur: 0.06, vol: 0.55, crush: 4 })]), 0.45),
    () => lowpass(render([tone({ f0: 160, f1: 80, dur: 0.08, wave: 'tri', vol: 1 }), tone({ wave: 'noise', dur: 0.04, vol: 0.7, crush: 6 })]), 0.35),
  ],
  mine: [
    () => render([tone({ f0: 2100, f1: 1700, dur: 0.03, vol: 0.3, duty: 0.2, r: 0.05 }), tone({ wave: 'noise', dur: 0.05, vol: 0.4, crush: 3 }), tone({ f0: 150, f1: 80, dur: 0.06, wave: 'tri', vol: 0.6 })]),
    () => render([tone({ f0: 1700, f1: 1300, dur: 0.035, vol: 0.35, duty: 0.25, r: 0.03 }), tone({ wave: 'noise', dur: 0.035, vol: 0.45, crush: 2 }), tone({ f0: 180, f1: 100, dur: 0.05, wave: 'tri', vol: 0.6 })]),
  ],
  plant: [
    () => render([tone({ f0: 300, f1: 600, dur: 0.05, wave: 'tri', vol: 0.8, r: 0.05 }), tone({ wave: 'noise', dur: 0.025, vol: 0.2, r: 0.01, crush: 4 })]),
    () => render([tone({ f0: 230, f1: 460, dur: 0.07, wave: 'tri', vol: 0.8, r: 0.06 }), tone({ wave: 'noise', dur: 0.02, vol: 0.25, r: 0.01, crush: 2 })]),
  ],
  water: [
    () => lowpass(render([tone({ wave: 'noise', dur: 0.15, vol: 0.6, r: 0.1, crush: 2 }), tone({ f0: 1300, f1: 800, dur: 0.04, wave: 'tri', vol: 0.3, start: 0.09 })]), 0.3),
    () => lowpass(render([tone({ wave: 'noise', dur: 0.2, vol: 0.55, r: 0.14, crush: 3 }), tone({ f0: 1900, f1: 1100, dur: 0.035, wave: 'tri', vol: 0.25, start: 0.15 }), tone({ f0: 1600, f1: 950, dur: 0.035, wave: 'tri', vol: 0.2, start: 0.24 })]), 0.4),
  ],
  pickup: [
    () => render([tone({ f0: 800, f1: 1600, dur: 0.045, vol: 0.25, duty: 0.25, r: 0.02 })]),
    () => render([tone({ f0: 600, f1: 1200, dur: 0.055, wave: 'tri', vol: 0.4, r: 0.03 })]),
  ],
  coin: [
    () => render([tone({ f0: note(84), dur: 0.06, vol: 0.35, duty: 0.5, r: 0.01 }), tone({ f0: note(89), dur: 0.18, vol: 0.35, start: 0.06, r: 0.12 })]),
    () => render([tone({ f0: note(81), dur: 0.05, vol: 0.35, duty: 0.25, r: 0.01 }), tone({ f0: note(88), dur: 0.2, vol: 0.3, start: 0.05, r: 0.14 })]),
  ],
  click: [
    () => render([tone({ f0: 1250, f1: 1000, dur: 0.025, vol: 0.35, duty: 0.25, r: 0.01 })]),
    () => render([tone({ f0: 1550, f1: 1200, dur: 0.02, vol: 0.3, duty: 0.3, r: 0.01 })]),
  ],
  rock_break: [
    () => lowpass(render([tone({ wave: 'noise', dur: 0.2, vol: 0.8, crush: 5, r: 0.14 }), tone({ f0: 120, f1: 55, dur: 0.17, wave: 'tri', vol: 0.8 })]), 0.45),
    () => lowpass(render([tone({ wave: 'noise', dur: 0.25, vol: 0.8, crush: 3, r: 0.1 }), tone({ f0: 160, f1: 70, dur: 0.13, wave: 'tri', vol: 0.8 })]), 0.55),
  ],
};

fs.mkdirSync(out, { recursive: true });
for (const [name, make] of Object.entries(SOUNDS)) wav(name, make());
let extra = 0;
for (const [name, takes] of Object.entries(VARIANTS)) takes.forEach((make, i) => { wav(`${name}_${i + 2}`, make()); extra++; });
console.log(`wrote ${Object.keys(SOUNDS).length} sounds and ${extra} variants to ${out}`);
