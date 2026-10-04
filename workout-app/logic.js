/*
 * Tonnage — training logic with no DOM access.
 * Loaded as a classic script in the browser (window.TonnageLogic) and
 * required from Node for the unit tests (module.exports).
 *
 * Weights are always stored in kilograms; the UI converts to the
 * user's display unit. A set counts toward totals once it is marked done.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.TonnageLogic = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const LB_PER_KG = 2.2046226218;

  const MUSCLES = ['Chest', 'Back', 'Legs', 'Shoulders', 'Arms', 'Core', 'Other'];

  // Muscle colours come from the theme: sage, mint, white, sand, tan, olive.
  // MUSCLE_ORDER keeps neighbouring colours close so the radial chart blends smoothly.
  const MUSCLE_COLORS = {
    Chest: '#6aa377',
    Arms: '#a9c8af',
    Back: '#f1f1ec',
    Shoulders: '#dccbb7',
    Legs: '#a88b70',
    Core: '#7f9670',
    Other: '#8a8580',
  };
  const MUSCLE_ORDER = ['Chest', 'Arms', 'Back', 'Shoulders', 'Legs', 'Core', 'Other'];

  // Colours for exercises inside one workout, in the order they were added.
  const SERIES = ['#6aa377', '#f1f1ec', '#a88b70', '#a9c8af', '#dccbb7', '#7f9670', '#c08a7c', '#8a8580'];

  const LIBRARY = [
    ['Bench Press', 'Chest'], ['Incline Bench Press', 'Chest'], ['Dumbbell Bench Press', 'Chest'],
    ['Incline Dumbbell Press', 'Chest'], ['Chest Fly', 'Chest'], ['Cable Crossover', 'Chest'],
    ['Push-up', 'Chest'], ['Dips', 'Chest'],
    ['Deadlift', 'Back'], ['Pull-up', 'Back'], ['Chin-up', 'Back'], ['Barbell Row', 'Back'],
    ['Dumbbell Row', 'Back'], ['Lat Pulldown', 'Back'], ['Seated Cable Row', 'Back'],
    ['T-Bar Row', 'Back'], ['Face Pull', 'Back'],
    ['Back Squat', 'Legs'], ['Front Squat', 'Legs'], ['Leg Press', 'Legs'], ['Romanian Deadlift', 'Legs'],
    ['Walking Lunge', 'Legs'], ['Bulgarian Split Squat', 'Legs'], ['Leg Extension', 'Legs'],
    ['Leg Curl', 'Legs'], ['Hip Thrust', 'Legs'], ['Calf Raise', 'Legs'],
    ['Overhead Press', 'Shoulders'], ['Dumbbell Shoulder Press', 'Shoulders'], ['Arnold Press', 'Shoulders'],
    ['Lateral Raise', 'Shoulders'], ['Rear Delt Fly', 'Shoulders'], ['Upright Row', 'Shoulders'],
    ['Shrug', 'Shoulders'],
    ['Barbell Curl', 'Arms'], ['Dumbbell Curl', 'Arms'], ['Hammer Curl', 'Arms'], ['Preacher Curl', 'Arms'],
    ['Tricep Pushdown', 'Arms'], ['Skull Crusher', 'Arms'], ['Overhead Tricep Extension', 'Arms'],
    ['Close-Grip Bench Press', 'Arms'],
    ['Hanging Leg Raise', 'Core'], ['Cable Crunch', 'Core'], ['Ab Wheel Rollout', 'Core'],
    ['Russian Twist', 'Core'], ['Decline Sit-up', 'Core'],
  ].map(([name, muscle]) => ({ name, muscle }));

  /* ---------- dates (local calendar days as YYYY-MM-DD) ---------- */

  const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const DOW_LONG = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const pad = n => String(n).padStart(2, '0');
  const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

  function iso(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }
  // Noon keeps date arithmetic clear of daylight-saving transitions.
  function parse(s) {
    const [y, m, d] = s.split('-').map(Number);
    return new Date(y, m - 1, d, 12);
  }
  function addDays(s, n) {
    const d = parse(s);
    d.setDate(d.getDate() + n);
    return iso(d);
  }
  // Weeks start on Monday.
  function weekStart(s) {
    const d = parse(s);
    d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
    return iso(d);
  }
  function diffDays(a, b) {
    return Math.round((parse(b) - parse(a)) / 864e5);
  }
  function longDate(s) {
    const d = parse(s);
    return DOW_LONG[d.getDay()] + ', ' + MONTHS[d.getMonth()] + ' ' + d.getDate();
  }
  function shortDate(s) {
    const d = parse(s);
    return MONTHS[d.getMonth()] + ' ' + d.getDate();
  }
  function weekRangeLabel(start) {
    const a = parse(start), b = parse(addDays(start, 6));
    return a.getMonth() === b.getMonth()
      ? MONTHS[a.getMonth()] + ' ' + a.getDate() + ' – ' + b.getDate()
      : MONTHS[a.getMonth()] + ' ' + a.getDate() + ' – ' + MONTHS[b.getMonth()] + ' ' + b.getDate();
  }

  /* ---------- numbers and units ---------- */

  const fromKg = (kg, unit) => (unit === 'lb' ? kg * LB_PER_KG : kg);
  const toKg = (v, unit) => (unit === 'lb' ? v / LB_PER_KG : v);
  const round1 = x => Math.round(x * 10) / 10;

  function fmtInt(x) {
    return Math.round(x).toLocaleString('en-US');
  }
  // Display weight: up to one decimal, no trailing ".0".
  function fmtWeight(kg, unit) {
    return round1(fromKg(kg, unit)).toLocaleString('en-US', { maximumFractionDigits: 1 });
  }
  // Same, without grouping separators, for editable inputs.
  function inputWeight(kg, unit) {
    return kg == null ? '' : String(round1(fromKg(kg, unit)));
  }
  function fmtCompact(x) {
    const a = Math.abs(x);
    if (a < 1000) return String(Math.round(x));
    if (a < 99950) return (x / 1e3).toFixed(1).replace(/\.0$/, '') + 'k';
    if (a < 999500) return Math.round(x / 1e3) + 'k';
    return (x / 1e6).toFixed(a < 9.995e6 ? 2 : 1).replace(/\.?0+$/, '') + 'M';
  }
  // Accepts "82,5" as well as "82.5". Returns null for empty or invalid input.
  function parseNum(str) {
    if (str == null) return null;
    const s = String(str).trim().replace(',', '.');
    if (!s) return null;
    const v = Number(s);
    return Number.isFinite(v) && v >= 0 ? v : null;
  }
  function pctChange(cur, prev) {
    if (!prev) return null;
    return Math.round(((cur - prev) / prev) * 100);
  }

  /* ---------- sets, exercises, workouts ---------- */

  const isDone = s => !!(s && s.done && Number(s.r) > 0);
  const setVolume = s => (Number(s.w) || 0) * (Number(s.r) || 0);

  // Epley estimate of a one-rep max.
  function e1rm(w, r) {
    if (!(w > 0) || !(r > 0)) return 0;
    return r === 1 ? w : w * (1 + r / 30);
  }
  const heavier = (a, b) => a.w > b.w || (a.w === b.w && a.r > b.r);

  function exerciseStats(ex) {
    const out = { sets: 0, reps: 0, volume: 0, planned: 0, total: ex.sets.length, top: null, best1rm: 0 };
    for (const s of ex.sets) {
      if (!isDone(s)) { out.planned++; continue; }
      const w = Number(s.w) || 0, r = Number(s.r);
      out.sets++;
      out.reps += r;
      out.volume += w * r;
      if (!out.top || heavier({ w, r }, out.top)) out.top = { w, r };
      out.best1rm = Math.max(out.best1rm, e1rm(w, r));
    }
    return out;
  }

  function workoutStats(w) {
    const out = { sets: 0, reps: 0, volume: 0, exercises: 0, planned: 0, start: null, end: null };
    if (!w) return out;
    for (const ex of w.exercises) {
      const st = exerciseStats(ex);
      out.sets += st.sets;
      out.reps += st.reps;
      out.volume += st.volume;
      out.planned += st.planned;
      if (st.sets) out.exercises++;
      for (const s of ex.sets) {
        if (!isDone(s) || !s.t) continue;
        out.start = out.start == null ? s.t : Math.min(out.start, s.t);
        out.end = out.end == null ? s.t : Math.max(out.end, s.t);
      }
    }
    return out;
  }

  // Minutes between the first and last completed set, when that looks like a real session.
  function durationMinutes(st) {
    if (st.start == null || st.end == null || st.sets < 2) return null;
    const m = Math.round((st.end - st.start) / 60000);
    return m >= 3 && m <= 300 ? m : null;
  }

  function autoName(w) {
    if (!w || !w.exercises.length) return 'Workout';
    const sets = {};
    for (const ex of w.exercises) sets[ex.muscle] = (sets[ex.muscle] || 0) + Math.max(1, ex.sets.length);
    const has = m => (sets[m] || 0) > 0;
    if (has('Chest') && has('Back') && has('Legs')) return 'Full body';
    if (has('Legs') && sets.Legs >= (sets.Chest || 0) + (sets.Back || 0)) return 'Leg day';
    if (has('Chest') && has('Back')) return 'Upper body';
    if (has('Chest')) return 'Push day';
    if (has('Back')) return 'Pull day';
    const top = Object.keys(sets).sort((a, b) => sets[b] - sets[a])[0];
    return { Shoulders: 'Shoulder day', Arms: 'Arm day', Core: 'Core day', Legs: 'Leg day' }[top] || 'Workout';
  }

  /* ---------- aggregates over a date-sorted list of workouts ---------- */

  function summarize(list, from, to) {
    const out = { workouts: 0, sets: 0, reps: 0, volume: 0, muscles: {} };
    for (const w of list) {
      if ((from && w.date < from) || (to && w.date > to)) continue;
      const perMuscle = {};
      for (const ex of w.exercises) {
        const st = exerciseStats(ex);
        if (!st.sets) continue;
        out.sets += st.sets;
        out.reps += st.reps;
        out.volume += st.volume;
        const m = MUSCLES.includes(ex.muscle) ? ex.muscle : 'Other';
        const b = out.muscles[m] || (out.muscles[m] = { sets: 0, volume: 0, sessions: [] });
        b.sets += st.sets;
        b.volume += st.volume;
        perMuscle[m] = (perMuscle[m] || 0) + st.volume;
      }
      const trained = Object.keys(perMuscle);
      for (const m of trained) out.muscles[m].sessions.push(perMuscle[m]);
      if (trained.length) out.workouts++;
    }
    return out;
  }

  // This week so far versus the same days of last week.
  function weekToDate(list, today) {
    const start = weekStart(today);
    const n = diffDays(start, today);
    const prevStart = addDays(start, -7);
    return {
      start,
      cur: summarize(list, start, today),
      prev: summarize(list, prevStart, addDays(prevStart, n)),
    };
  }

  function weeklyVolumes(list, today, weeks) {
    const last = weekStart(today);
    const out = [];
    for (let i = weeks - 1; i >= 0; i--) out.push({ start: addDays(last, -7 * i), volume: 0, sets: 0, workouts: 0 });
    const index = new Map(out.map((w, i) => [w.start, i]));
    for (const w of list) {
      const i = index.get(weekStart(w.date));
      if (i == null) continue;
      const st = workoutStats(w);
      out[i].volume += st.volume;
      out[i].sets += st.sets;
      if (st.sets) out[i].workouts++;
    }
    return out;
  }

  // Completed sessions of one exercise, newest first, optionally only before a date.
  function exerciseSessions(list, name, before) {
    const key = name.toLowerCase();
    const out = [];
    for (const w of list) {
      if (before && w.date >= before) continue;
      for (const ex of w.exercises) {
        if (ex.name.toLowerCase() !== key) continue;
        const st = exerciseStats(ex);
        if (!st.sets) continue;
        // `count` is the number of completed sets; `sets` holds the sets themselves.
        out.push(Object.assign({}, st, { date: w.date, muscle: ex.muscle, count: st.sets, sets: ex.sets.filter(isDone) }));
      }
    }
    return out.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
  }

  // For each exercise in a workout: top set compared with the previous session.
  function progressFor(list, workout) {
    const out = [];
    for (const ex of workout.exercises) {
      const st = exerciseStats(ex);
      if (!st.sets) continue;
      const prev = exerciseSessions(list, ex.name, workout.date);
      const last = prev[0] || null;
      const bodyweight = st.top.w === 0 && (!last || last.top.w === 0);
      const bestBefore = prev.reduce((m, s) => Math.max(m, s.top.w), 0);
      out.push({
        name: ex.name,
        muscle: ex.muscle,
        top: st.top,
        sets: st.sets,
        first: !last,
        kind: bodyweight ? 'reps' : 'weight',
        delta: last ? (bodyweight ? st.top.r - last.top.r : st.top.w - last.top.w) : null,
        pr: !!last && !bodyweight && st.top.w > bestBefore,
      });
    }
    return out;
  }

  // Best top set per exercise and how far it has moved since the first session.
  function records(list) {
    const map = new Map();
    for (const w of list) {
      for (const ex of w.exercises) {
        const st = exerciseStats(ex);
        if (!st.sets) continue;
        const key = ex.name.toLowerCase();
        let rec = map.get(key);
        if (!rec) {
          rec = { name: ex.name, muscle: ex.muscle, sessions: 0, first: st.top, best: st.top, bestDate: w.date, best1rm: 0, last: w.date };
          map.set(key, rec);
        }
        rec.sessions++;
        if (heavier(st.top, rec.best)) { rec.best = st.top; rec.bestDate = w.date; }
        rec.best1rm = Math.max(rec.best1rm, st.best1rm);
        if (w.date > rec.last) rec.last = w.date;
      }
    }
    return [...map.values()]
      .map(r => Object.assign(r, { gain: r.best.w - r.first.w, gainReps: r.best.r - r.first.r }))
      .sort((a, b) => (a.last < b.last ? 1 : a.last > b.last ? -1 : b.sessions - a.sessions));
  }

  function topExercises(list, n) {
    const map = new Map();
    for (const w of list) {
      for (const ex of w.exercises) {
        if (!ex.sets.some(isDone)) continue;
        const e = map.get(ex.name) || { name: ex.name, count: 0, last: '' };
        e.count++;
        if (w.date > e.last) e.last = w.date;
        map.set(ex.name, e);
      }
    }
    return [...map.values()]
      .sort((a, b) => b.count - a.count || (a.last < b.last ? 1 : -1))
      .slice(0, n)
      .map(e => e.name);
  }

  /* ---------- radial tick chart ---------- */

  // Largest-remainder split of n slots by weight, with a minimum per bucket.
  function allocate(weights, n, minEach) {
    const k = weights.length;
    if (!k || n <= 0) return [];
    const base = Math.min(minEach, Math.floor(n / k));
    const rest = n - base * k;
    const total = weights.reduce((a, b) => a + Math.max(0, b), 0);
    const raw = weights.map(w => (total > 0 ? Math.max(0, w) / total : 1 / k) * rest);
    const out = raw.map(r => base + Math.floor(r));
    let left = n - out.reduce((a, b) => a + b, 0);
    const order = raw.map((r, i) => [r - Math.floor(r), i]).sort((a, b) => b[0] - a[0]);
    for (let j = 0; left > 0; j = (j + 1) % k, left--) out[order[j][1]]++;
    return out;
  }

  function hexToRgb(h) {
    const v = parseInt(h.slice(1), 16);
    return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
  }
  function rgbToHex(r, g, b) {
    return '#' + [r, g, b].map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('');
  }

  /*
   * segments: [{ color, values: [number per set or session], dims: [bool] }]
   * Each segment gets a share of n ticks proportional to its total value.
   * Tick length follows the values inside the segment, interpolated so the
   * outline reads as a smooth ridge; colours blend across segment edges.
   * Returns [{ len: 0..1, color, dim }].
   */
  function radialTicks(segments, n) {
    const segs = segments.filter(s => s.values && s.values.length);
    if (!segs.length) {
      return Array.from({ length: n }, (_, i) => ({ len: 0.3 + 0.16 * (0.5 + 0.5 * Math.sin(i * 0.42)), color: '#262626', dim: false }));
    }
    let vmax = 0;
    for (const s of segs) for (const v of s.values) vmax = Math.max(vmax, v);
    const counts = allocate(segs.map(s => s.values.reduce((a, b) => a + b, 0)), n, 2);
    const raw = [];
    segs.forEach((s, si) => {
      const m = counts[si], k = s.values.length;
      for (let i = 0; i < m; i++) {
        const pos = Math.min(k - 1, Math.max(0, ((i + 0.5) / m) * k - 0.5));
        const a = Math.floor(pos), b = Math.min(k - 1, a + 1), t = pos - a;
        const v = vmax > 0 ? (s.values[a] + (s.values[b] - s.values[a]) * t) / vmax : 0.5;
        raw.push({ v, color: s.color, dim: !!(s.dims && s.dims[Math.round(pos)]) });
      }
    });
    // Blend lengths and colours with neighbours so the ring reads as one ridge.
    const rgb = raw.map(t => hexToRgb(t.color));
    return raw.map((t, i) => {
      let v = 0, vw = 0, r = 0, g = 0, b = 0, cw = 0;
      for (let d = -3; d <= 3; d++) {
        const j = (i + d + n) % n, wt = 4 - Math.abs(d);
        if (Math.abs(d) <= 2) { v += raw[j].v * (3 - Math.abs(d)); vw += 3 - Math.abs(d); }
        r += rgb[j][0] * wt; g += rgb[j][1] * wt; b += rgb[j][2] * wt; cw += wt;
      }
      // Fixed per-tick texture so the ridge is not perfectly smooth.
      const grain = 0.9 + 0.1 * Math.abs(Math.sin((i + 1) * 12.9898));
      return { len: 0.24 + 0.76 * (v / vw) * grain, color: rgbToHex(r / cw, g / cw, b / cw), dim: t.dim };
    });
  }

  /* ---------- example data ---------- */

  const PLANS = {
    Push: [
      ['Bench Press', 'Chest', 70, [8, 8, 8, 6]],
      ['Overhead Press', 'Shoulders', 40, [8, 8, 7]],
      ['Incline Dumbbell Press', 'Chest', 24, [10, 10, 9]],
      ['Lateral Raise', 'Shoulders', 10, [15, 12, 12]],
      ['Tricep Pushdown', 'Arms', 25, [12, 12, 10]],
    ],
    Pull: [
      ['Deadlift', 'Back', 120, [5, 5, 5]],
      ['Pull-up', 'Back', 0, [10, 9, 8]],
      ['Barbell Row', 'Back', 60, [8, 8, 8]],
      ['Face Pull', 'Back', 20, [15, 15]],
      ['Barbell Curl', 'Arms', 30, [10, 10, 8]],
    ],
    Legs: [
      ['Back Squat', 'Legs', 95, [6, 6, 6, 5]],
      ['Romanian Deadlift', 'Legs', 80, [8, 8, 8]],
      ['Leg Press', 'Legs', 160, [10, 10, 10]],
      ['Leg Curl', 'Legs', 40, [12, 12, 10]],
      ['Calf Raise', 'Legs', 60, [15, 15, 12]],
    ],
  };
  const NAMES = { Push: 'Push day', Pull: 'Pull day', Legs: 'Leg day' };

  // Four weeks of a push / pull / legs split before `anchor` (never on or after it),
  // with steady progressive overload. Marked example so the UI can label and clear them.
  function sampleWorkouts(anchor) {
    const out = {};
    const order = ['Push', 'Pull', 'Legs'];
    const seen = { Push: 0, Pull: 0, Legs: 0 };
    let k = 0;
    for (let back = 27; back >= 1; back--) {
      const date = addDays(anchor, -back);
      if (![1, 2, 4, 6].includes(parse(date).getDay())) continue;
      const type = order[k++ % 3];
      const n = seen[type]++;
      const start = parse(date);
      start.setHours(18, 4, 0, 0);
      let t = start.getTime();
      const exercises = PLANS[type].map(([name, muscle, base, reps], i) => ({
        id: 'ex-' + date + '-' + i,
        name,
        muscle,
        sets: reps.map((r, j) => {
          t += (140 + ((i * 37 + j * 53 + back * 11) % 70)) * 1000;
          // Big lifts add 2.5 kg every session, accessories a little every other session.
          const inc = base >= 60 ? 2.5 : base >= 20 ? 1 : 0.5;
          const every = base >= 60 ? 1 : 2;
          return { w: base ? base + Math.floor(n / every) * inc : 0, r, done: true, t };
        }),
      }));
      out[date] = { date, name: NAMES[type], exercises, example: true, updatedAt: 0 };
    }
    return out;
  }

  /* ---------- validation for stored, synced or imported data ---------- */

  const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);

  function cleanWeight(v) {
    const n = Number(v);
    return v == null || v === '' || !Number.isFinite(n) || n < 0 ? null : Math.min(n, 2000);
  }
  function cleanReps(v) {
    const n = Math.round(Number(v));
    return v == null || v === '' || !Number.isFinite(n) || n < 0 ? null : Math.min(n, 1000);
  }

  function normalizeWorkout(raw) {
    if (!raw || typeof raw !== 'object' || !DATE_RE.test(raw.date)) return null;
    const exercises = (Array.isArray(raw.exercises) ? raw.exercises : [])
      .filter(e => e && typeof e.name === 'string' && e.name.trim())
      .slice(0, 60)
      .map(e => ({
        id: typeof e.id === 'string' && e.id ? e.id.slice(0, 40) : uid(),
        name: e.name.trim().slice(0, 60),
        muscle: MUSCLES.includes(e.muscle) ? e.muscle : 'Other',
        sets: (Array.isArray(e.sets) ? e.sets : []).slice(0, 50).map(s => ({
          w: cleanWeight(s && s.w),
          r: cleanReps(s && s.r),
          done: !!(s && s.done),
          t: s && Number.isFinite(s.t) ? s.t : null,
        })),
      }));
    return {
      date: raw.date,
      name: typeof raw.name === 'string' ? raw.name.trim().slice(0, 60) : '',
      exercises,
      updatedAt: Number(raw.updatedAt) || 0,
    };
  }

  return {
    LB_PER_KG, MUSCLES, MUSCLE_COLORS, MUSCLE_ORDER, SERIES, LIBRARY, DOW, MONTHS,
    iso, parse, addDays, weekStart, diffDays, longDate, shortDate, weekRangeLabel,
    fromKg, toKg, round1, fmtInt, fmtWeight, inputWeight, fmtCompact, parseNum, pctChange,
    isDone, setVolume, e1rm, exerciseStats, workoutStats, durationMinutes, autoName,
    summarize, weekToDate, weeklyVolumes, exerciseSessions, progressFor, records, topExercises,
    allocate, hexToRgb, rgbToHex, radialTicks, sampleWorkouts, uid, normalizeWorkout,
  };
});
