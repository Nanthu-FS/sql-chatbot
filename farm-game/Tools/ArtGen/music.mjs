// Synthesises calm lo-fi background music and ambience loops, then encodes them to OGG:
//   app/src/main/assets/music/<track>.ogg      (farm per season, night, forest, mine)
//   app/src/main/assets/ambience/<loop>.ogg    (day, night, forest, mine, rain, storm, snow)
// Every file loops seamlessly: tails that run past the end are wrapped onto the start.
//   node music.mjs            (needs ffmpeg with libvorbis on PATH)
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const RATE = 22050;
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../app/src/main/assets');
const TAU = Math.PI * 2;

// --- deterministic randomness -------------------------------------------------
function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

const hz = (m) => 440 * Math.pow(2, (m - 69) / 12);

// --- filters (RBJ biquads) ------------------------------------------------------
function biquad(buf, type, freq, q = 0.707) {
  const w = TAU * freq / RATE, cs = Math.cos(w), al = Math.sin(w) / (2 * q);
  let b0, b1, b2;
  if (type === 'lp') { b0 = (1 - cs) / 2; b1 = 1 - cs; b2 = b0; }
  else if (type === 'hp') { b0 = (1 + cs) / 2; b1 = -(1 + cs); b2 = b0; }
  else { b0 = al; b1 = 0; b2 = -al; }
  const a0 = 1 + al, a1 = -2 * cs, a2 = 1 - al;
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < buf.length; i++) {
    const x = buf[i];
    const y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
    x2 = x1; x1 = x; y2 = y1; y1 = y; buf[i] = y;
  }
  return buf;
}

/** Adds [src] into [dst] at sample [at], wrapping past the end (seamless loops). */
function mixWrap(dst, src, at, gain = 1) {
  for (let i = 0; i < src.length; i++) dst[(at + i) % dst.length] += src[i] * gain;
}

function delay(buf, seconds, feedback, wet) {
  const d = Math.floor(seconds * RATE);
  const out = Float32Array.from(buf);
  // Two passes around the loop so the echo tail wraps cleanly.
  for (let pass = 0; pass < 2; pass++) for (let i = 0; i < out.length; i++) out[i] += out[(i - d + out.length) % out.length] * feedback * (pass === 0 ? 1 : 0.5);
  for (let i = 0; i < buf.length; i++) buf[i] = buf[i] * (1 - wet) + out[i] * wet;
  return buf;
}

// --- instruments ----------------------------------------------------------------
function epiano(m, dur, vel, r) {
  const n = Math.floor((dur + 1.2) * RATE);
  const out = new Float32Array(n);
  const f = hz(m), det = 1 + (r() - 0.5) * 0.004;
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const env = Math.min(1, t / 0.012) * Math.exp(-t * (t < dur ? 1.6 : 5));
    const trem = 1 + 0.06 * Math.sin(TAU * 4.5 * t);
    out[i] = vel * env * trem * (Math.sin(TAU * f * t) + 0.5 * Math.sin(TAU * f * det * t) + 0.22 * Math.sin(TAU * 2 * f * t) * Math.exp(-t * 3));
  }
  return out;
}

function bass(m, dur, vel) {
  const n = Math.floor((dur + 0.3) * RATE);
  const out = new Float32Array(n);
  const f = hz(m);
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const env = Math.min(1, t / 0.02) * (t < dur ? Math.exp(-t * 0.8) : Math.exp(-dur * 0.8) * Math.exp(-(t - dur) * 12));
    out[i] = vel * env * (Math.sin(TAU * f * t) + 0.15 * Math.sin(TAU * 2 * f * t));
  }
  return out;
}

function flute(m, dur, vel) {
  const n = Math.floor((dur + 0.4) * RATE);
  const out = new Float32Array(n);
  const f = hz(m);
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    const env = Math.min(1, t / 0.08) * (t < dur ? 1 : Math.exp(-(t - dur) * 8));
    const vib = 1 + 0.004 * Math.sin(TAU * 5 * t) * Math.min(1, t / 0.3);
    out[i] = vel * env * (Math.sin(TAU * f * vib * t) + 0.12 * Math.sin(TAU * 3 * f * vib * t));
  }
  return out;
}

function kick(vel) {
  const n = Math.floor(0.35 * RATE);
  const out = new Float32Array(n);
  let ph = 0;
  for (let i = 0; i < n; i++) {
    const t = i / RATE;
    ph += (45 + 75 * Math.exp(-t * 28)) / RATE;
    out[i] = vel * Math.sin(TAU * ph) * Math.exp(-t * 9);
  }
  return out;
}

function noiseHit(vel, decay, lo, hi, r, len = 0.25) {
  const n = Math.floor(len * RATE);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) out[i] = (r() * 2 - 1) * vel * Math.exp(-(i / RATE) * decay);
  biquad(out, 'hp', lo); biquad(out, 'lp', hi);
  return out;
}

// --- lo-fi tracks -----------------------------------------------------------------
/**
 * A track: chords (MIDI voicings, one per bar unless [barsPer]), bass roots, tempo, and
 * flags for drums and melody. Rendered as [loops] passes of the progression.
 */
function track({ seed, bpm, chords, roots, barsPer = 1, loops = 4, drums = true, melody = true, scale, echo = 0, cutoff = 3200, gain = 1 }) {
  const r = rng(seed);
  const beat = 60 / bpm;
  const bar = beat * 4;
  const total = chords.length * barsPer * loops;
  const len = Math.floor(total * bar * RATE);
  const mix = new Float32Array(len);
  for (let b = 0; b < total; b++) {
    const ci = Math.floor(b / barsPer) % chords.length;
    const t0 = b * bar;
    const at = (t) => Math.floor((t0 + t) * RATE);
    // Chord: soft strum, held for the bar (or re-struck on beat 3 sometimes).
    chords[ci].forEach((m, k) => mixWrap(mix, epiano(m, bar * 0.9, 0.11, r), at(k * 0.018)));
    if (r() < 0.35) chords[ci].slice(1).forEach((m, k) => mixWrap(mix, epiano(m, beat * 1.2, 0.06, r), at(beat * 2.5 + k * 0.02)));
    // Bass: root on 1, a passing note on 3.
    mixWrap(mix, bass(roots[ci], beat * 1.7, 0.32), at(0));
    mixWrap(mix, bass(roots[ci] + (r() < 0.5 ? 7 : 12), beat * 1.2, 0.22), at(beat * 2));
    if (drums) {
      const swing = beat * 0.58;
      mixWrap(mix, kick(0.55), at(0));
      if (r() < 0.7) mixWrap(mix, kick(0.4), at(beat * 2 + swing));
      for (const s of [1, 3]) mixWrap(mix, noiseHit(0.22, 22, 900, 4200, r), at(beat * s));
      for (let e = 0; e < 8; e++) {
        const t = Math.floor(e / 2) * beat + (e % 2 ? swing : 0);
        if (r() < 0.85) mixWrap(mix, noiseHit(e % 2 ? 0.05 : 0.07, 60, 5000, 9000, r, 0.06), at(t));
      }
    }
    if (melody && scale && r() < 0.75) {
      let t = (r() < 0.5 ? 0 : 0.5) * beat;
      while (t < bar - beat * 0.5) {
        const dur = beat * [0.5, 1, 1, 1.5][Math.floor(r() * 4)];
        if (r() < 0.75) mixWrap(mix, flute(scale[Math.floor(r() * scale.length)], dur * 0.9, 0.09), at(t));
        t += dur;
      }
    }
  }
  if (echo) delay(mix, echo, 0.35, 0.3);
  // Vinyl: hiss and sparse crackle.
  for (let i = 0; i < len; i++) mix[i] += (r() * 2 - 1) * 0.004 + (r() < 0.0004 ? (r() * 2 - 1) * 0.12 : 0);
  biquad(mix, 'lp', cutoff, 0.6);
  biquad(mix, 'hp', 40);
  for (let i = 0; i < len; i++) mix[i] = Math.tanh(mix[i] * 1.4 * gain);
  return mix;
}

// --- ambience ----------------------------------------------------------------------
function noiseBed(len, r, lo, hi, vel, wobble = 0.15, rate = 0.08) {
  const b = new Float32Array(len);
  for (let i = 0; i < len; i++) b[i] = (r() * 2 - 1);
  biquad(b, 'hp', lo); biquad(b, 'lp', hi);
  // Loop-periodic swell so the bed breathes without a seam.
  const cycles = Math.max(1, Math.round(len / RATE * rate));
  for (let i = 0; i < len; i++) b[i] *= vel * (1 + wobble * Math.sin(TAU * cycles * i / len));
  return b;
}

function chirp(r, f0, f1, dur, vel, trill = 0) {
  const n = Math.floor(dur * RATE);
  const out = new Float32Array(n);
  let ph = 0;
  for (let i = 0; i < n; i++) {
    const t = i / RATE, k = t / dur;
    ph += (f0 + (f1 - f0) * k) * (1 + trill * Math.sin(TAU * 28 * t)) / RATE;
    out[i] = vel * Math.sin(TAU * ph) * Math.sin(Math.PI * k);
  }
  return out;
}

function sprinkle(mix, r, every, make) {
  let t = r() * every;
  const dur = mix.length / RATE;
  while (t < dur) { mixWrap(mix, make(), Math.floor(t * RATE)); t += every * (0.5 + r()); }
}

function ambience(kind, seconds = 30) {
  const r = rng(kind.length * 977 + seconds);
  const len = Math.floor(seconds * RATE);
  let mix;
  switch (kind) {
    case 'day': {
      mix = noiseBed(len, r, 200, 900, 0.05);
      sprinkle(mix, r, 2.2, () => {
        const base = 2600 + r() * 1800, notes = 2 + Math.floor(r() * 4), out = new Float32Array(Math.floor(notes * 0.13 * RATE));
        for (let k = 0; k < notes; k++) mixWrap(out, chirp(r, base * (1 + r() * 0.2), base * (0.8 + r() * 0.5), 0.08, 0.1, r() < 0.3 ? 0.05 : 0), Math.floor(k * 0.12 * RATE));
        return out;
      });
      break;
    }
    case 'night': {
      mix = noiseBed(len, r, 150, 600, 0.035);
      // Crickets: a steady pulsing chirp in two voices.
      for (const [f, rate, off] of [[4400, 3.1, 0], [4800, 2.6, 0.3]]) {
        const pulses = Math.round(seconds * rate);
        for (let p = 0; p < pulses; p++) mixWrap(mix, chirp(r, f, f, 0.05, 0.03, 0.2), Math.floor(((p + off) / pulses) * len));
      }
      sprinkle(mix, r, 11, () => {
        const out = new Float32Array(Math.floor(1.1 * RATE));
        mixWrap(out, chirp(r, 420, 380, 0.35, 0.12), 0); mixWrap(out, chirp(r, 410, 360, 0.45, 0.12), Math.floor(0.55 * RATE));
        return out;
      });
      break;
    }
    case 'forest': {
      mix = noiseBed(len, r, 500, 2400, 0.06, 0.5, 0.2);
      sprinkle(mix, r, 3, () => chirp(r, 1800 + r() * 900, 2600 + r() * 900, 0.18, 0.08, 0.1));
      sprinkle(mix, r, 13, () => {
        const out = new Float32Array(Math.floor(0.8 * RATE));
        for (let k = 0; k < 8; k++) mixWrap(out, noiseHit(0.25, 90, 600, 2500, r, 0.04), Math.floor(k * 0.07 * RATE));
        return out;
      });
      break;
    }
    case 'mine': {
      mix = new Float32Array(len);
      for (let i = 0; i < len; i++) mix[i] = 0.06 * Math.sin(TAU * 55 * i / RATE) * (1 + 0.3 * Math.sin(TAU * 2 * i / len));
      const rumble = noiseBed(len, r, 30, 160, 0.12, 0.4);
      for (let i = 0; i < len; i++) mix[i] += rumble[i];
      sprinkle(mix, r, 2.5, () => chirp(r, 1400 + r() * 900, 700, 0.06, 0.12));
      delay(mix, 0.29, 0.45, 0.45);
      break;
    }
    case 'rain': {
      mix = noiseBed(len, r, 400, 5000, 0.12, 0.1);
      sprinkle(mix, r, 0.05, () => noiseHit(0.08 + r() * 0.08, 120, 1500, 7000, r, 0.03));
      break;
    }
    case 'storm': {
      mix = noiseBed(len, r, 250, 4500, 0.2, 0.25, 0.1);
      const rumble = noiseBed(len, r, 25, 120, 0.25, 0.9, 0.07);
      for (let i = 0; i < len; i++) mix[i] += rumble[i];
      sprinkle(mix, r, 0.035, () => noiseHit(0.1 + r() * 0.1, 120, 1500, 7000, r, 0.03));
      break;
    }
    case 'snow': {
      mix = noiseBed(len, r, 600, 1400, 0.1, 0.8, 0.12);
      const whistle = noiseBed(len, r, 1100, 1300, 0.05, 1, 0.05);
      for (let i = 0; i < len; i++) mix[i] += whistle[i];
      break;
    }
    default: throw new Error(kind);
  }
  return mix;
}

// --- output --------------------------------------------------------------------------
function writeOgg(file, buf, peakTarget, quality = 1) {
  let peak = 0;
  for (const s of buf) peak = Math.max(peak, Math.abs(s));
  const norm = peak > 0 ? peakTarget / peak : 0;
  const data = Buffer.alloc(buf.length * 2);
  buf.forEach((s, i) => data.writeInt16LE(Math.max(-32767, Math.min(32767, Math.round(s * norm * 32767))), i * 2));
  const h = Buffer.alloc(44);
  h.write('RIFF', 0); h.writeUInt32LE(36 + data.length, 4); h.write('WAVE', 8);
  h.write('fmt ', 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22);
  h.writeUInt32LE(RATE, 24); h.writeUInt32LE(RATE * 2, 28); h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34);
  h.write('data', 36); h.writeUInt32LE(data.length, 40);
  const tmp = path.join(os.tmpdir(), `df-${path.basename(file)}.wav`);
  fs.writeFileSync(tmp, Buffer.concat([h, data]));
  fs.mkdirSync(path.dirname(file), { recursive: true });
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', String(quality), file]);
  fs.unlinkSync(tmp);
}

const P = (...m) => m; // readability for voicings
const TRACKS = {
  farm_spring: { seed: 11, bpm: 72, chords: [P(53, 57, 60, 64), P(52, 55, 59, 62), P(50, 53, 57, 60), P(48, 52, 55, 59)], roots: [41, 40, 38, 36], scale: [72, 74, 76, 79, 81, 84] },
  farm_summer: { seed: 12, bpm: 76, chords: [P(48, 52, 55, 59, 62), P(45, 48, 52, 55, 59), P(50, 53, 57, 60, 64), P(43, 47, 53, 57, 64)], roots: [36, 33, 38, 31], scale: [72, 74, 76, 79, 81, 84, 86] },
  farm_fall: { seed: 13, bpm: 70, chords: [P(57, 60, 64, 67), P(50, 53, 57, 60), P(55, 59, 62, 65), P(48, 52, 55, 59)], roots: [33, 38, 31, 36], scale: [69, 72, 74, 76, 79, 81] },
  farm_winter: { seed: 14, bpm: 64, chords: [P(51, 55, 58, 62), P(48, 51, 55, 58), P(44, 48, 51, 55), P(46, 51, 53, 56)], roots: [39, 36, 32, 34], scale: [70, 72, 75, 77, 79, 82], cutoff: 2600 },
  night: { seed: 15, bpm: 58, chords: [P(49, 53, 56, 60), P(51, 54, 58, 61)], roots: [37, 39], barsPer: 2, loops: 4, drums: false, scale: [68, 70, 73, 75, 77, 80], cutoff: 2200 },
  forest: { seed: 16, bpm: 68, chords: [P(55, 59, 62, 66), P(47, 50, 54, 57), P(52, 55, 59, 62), P(48, 52, 55, 59)], roots: [43, 35, 40, 36], scale: [74, 76, 78, 81, 83, 86], echo: 0.31 },
  // Round 7: the launch title (hopeful, a light beat) and the opening's elegy for Grandpa Elias (slow, minor, no drums).
  title: { seed: 21, bpm: 80, chords: [P(48, 52, 55, 59, 64), P(53, 57, 60, 65), P(45, 52, 57, 60), P(43, 50, 55, 59)], roots: [36, 41, 33, 31], scale: [72, 74, 76, 79, 81, 84], echo: 0.25 },
  elegy: { seed: 22, bpm: 50, chords: [P(45, 48, 52, 57), P(41, 48, 53, 57), P(48, 52, 55, 60), P(40, 47, 52, 56)], roots: [33, 29, 36, 28], barsPer: 2, loops: 3, drums: false, scale: [69, 71, 72, 76, 77, 81], echo: 0.45, cutoff: 2400 },
  mine: { seed: 17, bpm: 62, chords: [P(50, 53, 57, 60, 64), P(46, 50, 53, 57), P(43, 46, 50, 53, 57), P(45, 49, 52, 55, 58)], roots: [38, 34, 31, 33], scale: [62, 65, 67, 69, 72, 74], echo: 0.43, cutoff: 2000 },
};

const only = process.argv.slice(2);
for (const [name, spec] of Object.entries(TRACKS)) {
  if (only.length && !only.includes(name)) continue;
  writeOgg(path.join(root, 'music', `${name}.ogg`), track(spec), 0.7, 1);
  console.log(`music/${name}.ogg`);
}
for (const kind of ['day', 'night', 'forest', 'mine', 'rain', 'storm', 'snow']) {
  if (only.length && !only.includes(kind)) continue;
  writeOgg(path.join(root, 'ambience', `${kind}.ogg`), ambience(kind), 0.6, 0);
  console.log(`ambience/${kind}.ogg`);
}
