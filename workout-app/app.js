/*
 * Tonnage — screens, storage and interactions.
 * Plain browser JavaScript with no build step; depends on logic.js.
 *
 * Data lives in localStorage. When the page runs inside a Claude artifact
 * viewer that grants the `db` capability, the same data is also kept in the
 * viewer's private account store so it follows them across devices.
 */
(function () {
  'use strict';

  const L = window.TonnageLogic;
  const FRAMED = (() => { try { return window.self !== window.top; } catch (e) { return true; } })();
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
  const esc = v => String(v == null ? '' : v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const today = () => L.iso(new Date());
  const plural = (n, one, many) => n + ' ' + (n === 1 ? one : many || one + 's');
  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

  /* ---------- icons ---------- */

  const DOT = 'fill="currentColor" stroke="none"';
  const ICONS = {
    back: '<path d="M19 12H5M11 18l-6-6 6-6"/>',
    upRight: '<path d="M7 17 17 7M8.5 7H17v8.5"/>',
    left: '<path d="m15 18-6-6 6-6"/>',
    right: '<path d="m9 18 6-6-6-6"/>',
    dots: `<circle cx="5" cy="12" r="1.7" ${DOT}/><circle cx="12" cy="12" r="1.7" ${DOT}/><circle cx="19" cy="12" r="1.7" ${DOT}/>`,
    sliders: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
    camera: '<path d="M4 8.5h3.2L9 6h6l1.8 2.5H20V19H4z"/><circle cx="12" cy="13.2" r="3.4"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    minus: '<path d="M5 12h14"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    close: '<path d="M6 6l12 12M18 6 6 18"/>',
    trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    dumbbell: '<path d="M6.5 7v10M17.5 7v10M3.5 9.5v5M20.5 9.5v5M6.5 12h11"/>',
    trophy: '<path d="M8 4h8v5a4 4 0 0 1-8 0zM8 6H5v1a3 3 0 0 0 3 3M16 6h3v1a3 3 0 0 1-3 3M12 13v4M8.5 20h7M10 17h4"/>',
    repeat: '<path d="m17 3 3 3-3 3M4 11V9a3 3 0 0 1 3-3h13M7 21l-3-3 3-3M20 13v2a3 3 0 0 1-3 3H4"/>',
    download: '<path d="M12 4v11M7 10l5 5 5-5M5 20h14"/>',
    upload: '<path d="M12 15V4M7 9l5-5 5 5M5 20h14"/>',
  };
  const icon = name => '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true">' + ICONS[name] + '</svg>';

  /* ---------- state ---------- */

  const DEFAULT_PROFILE = { name: '', focus: '', unit: 'kg', goal: 4, rest: 90 };
  const PHOTO_RE = /^data:image\/(jpeg|png|webp|gif);base64,[A-Za-z0-9+/=]+$/;
  const REST_OPTIONS = [[0, 'Off'], [60, '1:00'], [90, '1:30'], [120, '2:00'], [180, '3:00']];
  const S = { profile: Object.assign({}, DEFAULT_PROFILE), photo: null, workouts: {}, custom: [], examples: true, anchor: null };
  const ui = { view: 'home', date: today(), weekOffset: 0, range: '30', animate: true, historyAll: false, selWeek: null, picker: { q: '', m: 'All' } };
  let ready = false;

  function cleanProfile(p) {
    p = p && typeof p === 'object' ? p : {};
    const rest = Number(p.rest);
    return {
      name: typeof p.name === 'string' ? p.name.slice(0, 40) : '',
      focus: typeof p.focus === 'string' ? p.focus.slice(0, 40) : '',
      unit: p.unit === 'lb' ? 'lb' : 'kg',
      goal: clamp(Math.round(Number(p.goal)) || DEFAULT_PROFILE.goal, 1, 7),
      rest: REST_OPTIONS.some(o => o[0] === rest) ? rest : DEFAULT_PROFILE.rest,
    };
  }

  function applyData(d, photo) {
    d = d && typeof d === 'object' ? d : {};
    S.profile = cleanProfile(d.profile);
    S.custom = (Array.isArray(d.custom) ? d.custom : [])
      .filter(c => c && typeof c.name === 'string' && c.name.trim())
      .slice(0, 200)
      .map(c => ({ name: c.name.trim().slice(0, 60), muscle: L.MUSCLES.includes(c.muscle) ? c.muscle : 'Other' }));
    S.examples = d.examples !== false;
    S.anchor = typeof d.anchor === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d.anchor) ? d.anchor : null;
    S.workouts = {};
    const ws = d.workouts && typeof d.workouts === 'object' ? d.workouts : {};
    for (const key of Object.keys(ws)) {
      const w = L.normalizeWorkout(ws[key]);
      if (w && w.date === key && (w.exercises.length || w.name)) S.workouts[key] = w;
    }
    S.photo = typeof photo === 'string' && PHOTO_RE.test(photo) ? photo : null;
    invalidate();
  }

  const hasOwnData = () => !!(S.profile.name || S.photo || S.custom.length || !S.examples || Object.keys(S.workouts).length);

  /* ---------- storage ---------- */

  const Store = (() => {
    const KEY = 'tonnage.v1';
    const PHOTO_KEY = 'tonnage.v1.photo';
    const pending = new Map();
    let remote = null;
    let chain = Promise.resolve();
    let abandoned = false;

    const meta = () => ({ v: 1, profile: S.profile, custom: S.custom, examples: S.examples, anchor: S.anchor });

    function readLocal() {
      let data = null, photo = null;
      try { const raw = localStorage.getItem(KEY); data = raw ? JSON.parse(raw) : null; } catch (e) { data = null; }
      try { photo = localStorage.getItem(PHOTO_KEY); } catch (e) { photo = null; }
      return { data, photo };
    }
    function writeLocal() {
      try { localStorage.setItem(KEY, JSON.stringify(Object.assign(meta(), { workouts: S.workouts }))); return true; } catch (e) { return false; }
    }
    function writeLocalPhoto() {
      try {
        if (S.photo) localStorage.setItem(PHOTO_KEY, S.photo);
        else localStorage.removeItem(PHOTO_KEY);
        return true;
      } catch (e) { return false; }
    }

    // Account writes run one at a time, in order.
    function run(task) {
      chain = chain.then(() => (remote ? task() : null)).catch(err => {
        const code = err && err.code;
        if (code === 'quota_exceeded') toast('Your synced log is full. Delete old workouts to keep syncing.');
        else if (['invalid_argument', 'revoked', 'not_granted', 'capability_disabled', 'capability_removed'].includes(code)) {
          remote = null;
          toast('Saving on this device only');
        } else console.warn('Tonnage: sync failed', err);
      });
      return chain;
    }
    function later(key, fn, ms) {
      const prev = pending.get(key);
      if (prev) clearTimeout(prev.id);
      pending.set(key, { fn, id: setTimeout(() => { pending.delete(key); fn(); }, ms) });
    }
    const metaRef = () => remote.db.doc(remote.base + '/profile');
    const photoRef = () => remote.db.doc(remote.base + '/photo');
    const workoutRef = date => metaRef().collection('workouts').doc(date);
    const pushMeta = () => run(() => metaRef().set(meta()));
    const pushPhoto = () => run(() => (S.photo ? photoRef().set({ dataUrl: S.photo }) : photoRef().delete()));
    const pushWorkout = date => run(() => {
      const w = S.workouts[date];
      return w ? workoutRef(date).set(w) : workoutRef(date).delete();
    });

    return {
      get synced() { return !!remote; },
      loadLocal() {
        const { data, photo } = readLocal();
        if (data || photo) applyData(data, photo);
      },
      saveMeta() {
        later('local', writeLocal, 250);
        if (remote) later('meta', pushMeta, 700);
      },
      saveWorkout(date) {
        later('local', writeLocal, 250);
        if (remote) later('w:' + date, () => pushWorkout(date), 900);
      },
      savePhoto() {
        const ok = writeLocalPhoto();
        if (remote) pushPhoto();
        return ok || !!remote;
      },
      flush() {
        for (const [key, p] of pending) { clearTimeout(p.id); pending.delete(key); p.fn(); }
      },
      replaceAll(oldDates) {
        this.flush();
        writeLocal();
        writeLocalPhoto();
        if (!remote) return;
        for (const d of oldDates) if (!S.workouts[d]) pushWorkout(d);
        for (const d of Object.keys(S.workouts)) pushWorkout(d);
        pushMeta();
        pushPhoto();
      },
      eraseAll(oldDates) {
        for (const [key, p] of pending) { clearTimeout(p.id); pending.delete(key); }
        try { localStorage.removeItem(KEY); localStorage.removeItem(PHOTO_KEY); } catch (e) { /* storage unavailable */ }
        if (!remote) return;
        for (const d of oldDates) run(() => workoutRef(d).delete());
        run(() => photoRef().delete());
        run(() => metaRef().delete());
      },
      abandon() { abandoned = true; },
      // Inside a Claude artifact viewer: load this viewer's private copy, or seed it from this device.
      async connect() {
        const c = window.claude;
        if (!c || typeof c.use !== 'function') return null;
        let db = null, user = null, id = null;
        try { [db, user] = await Promise.all([c.use('db'), c.use('user')]); } catch (e) { return null; }
        if (!db || !user || typeof user.id !== 'function') return null;
        try { id = await user.id(); } catch (e) { id = null; }
        if (!id || abandoned) return null;
        try {
          const base = 'data/users/' + id;
          const ref = db.doc(base + '/profile');
          const [m, p, ws] = await Promise.all([ref.get(), db.doc(base + '/photo').get(), ref.collection('workouts').limit(1000).get()]);
          if (abandoned) return null;
          remote = { db, base };
          if (m.exists) {
            const workouts = {};
            ws.docs.forEach(d => { if (d.exists) workouts[d.id] = d.data(); });
            applyData(Object.assign({}, m.data(), { workouts }), p.exists ? (p.data() || {}).dataUrl : null);
            writeLocal();
            writeLocalPhoto();
            return 'loaded';
          }
          if (hasOwnData()) {
            pushMeta();
            Object.keys(S.workouts).forEach(pushWorkout);
            if (S.photo) pushPhoto();
          }
          return 'empty';
        } catch (e) {
          remote = null;
          return null;
        }
      },
    };
  })();

  /* ---------- derived data ---------- */

  let memo = null;
  function invalidate() { memo = null; }
  // Real workouts merged over the example set (when examples are on), sorted by date.
  function data() {
    if (memo) return memo;
    const map = {};
    if (S.examples) Object.assign(map, L.sampleWorkouts(S.anchor || today()));
    Object.assign(map, S.workouts);
    const list = Object.values(map).sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
    memo = { map, list, examples: list.filter(w => w.example).length };
    return memo;
  }

  const unit = () => S.profile.unit;
  const W = kg => L.fmtWeight(kg, unit());
  const VOL = kg => L.fmtInt(L.fromKg(kg, unit()));
  const VOLC = kg => L.fmtCompact(L.fromKg(kg, unit()));
  const setShort = s => (Number(s.w) > 0 ? W(s.w) : 'BW') + '×' + s.r;
  const topLabel = top => (top.w > 0 ? W(top.w) + ' ' + unit() + ' × ' + top.r : plural(top.r, 'rep'));
  const signed = (n, text) => (n > 0 ? '+' : n < 0 ? '−' : '±') + text;

  /* ---------- mutations ---------- */

  function realWorkout(date, create) {
    let w = S.workouts[date];
    if (!w && create) w = S.workouts[date] = { date, name: '', exercises: [], updatedAt: Date.now() };
    return w || null;
  }

  // Call after any change to a workout. Empty, unnamed workouts are removed.
  function changed(date) {
    const w = S.workouts[date];
    if (w) {
      w.updatedAt = Date.now();
      if (!w.exercises.length && !w.name) delete S.workouts[date];
    }
    if (S.examples && !S.anchor) {
      S.anchor = today();
      Store.saveMeta();
    }
    invalidate();
    Store.saveWorkout(date);
  }

  function addExercise(date, name, muscle) {
    const w = realWorkout(date, true);
    const existing = w.exercises.find(e => e.name.toLowerCase() === name.toLowerCase());
    if (existing) return existing;
    const prev = L.exerciseSessions(data().list, name, date)[0];
    const sets = prev
      ? prev.sets.map(s => ({ w: s.w, r: s.r, done: false, t: null }))
      : [0, 1, 2].map(() => ({ w: null, r: null, done: false, t: null }));
    const ex = { id: L.uid(), name, muscle: L.MUSCLES.includes(muscle) ? muscle : 'Other', sets };
    w.exercises.push(ex);
    changed(date);
    return ex;
  }

  // Copies exercises (as sets still to do) from one day into another.
  function repeatInto(srcDate, destDate) {
    const src = data().map[srcDate];
    if (!src) return 0;
    const w = realWorkout(destDate, true);
    const have = new Set(w.exercises.map(e => e.name.toLowerCase()));
    let added = 0;
    for (const e of src.exercises) {
      if (have.has(e.name.toLowerCase())) continue;
      const done = e.sets.filter(L.isDone);
      w.exercises.push({
        id: L.uid(),
        name: e.name,
        muscle: e.muscle,
        sets: (done.length ? done : e.sets).map(s => ({ w: s.w, r: s.r, done: false, t: null })),
      });
      added++;
    }
    if (!w.name && src.name && added) w.name = src.name;
    changed(destDate);
    return added;
  }

  /* ---------- shared pieces ---------- */

  function initials() {
    const parts = S.profile.name.trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return '';
    return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  }
  function avatar() {
    return '<span class="avatar">' + (S.photo ? '<img src="' + esc(S.photo) + '" alt="">' : initials() ? esc(initials()) : icon('camera')) + '</span>';
  }
  function shortName() {
    const parts = S.profile.name.trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return '';
    return parts.length > 1 ? parts[0] + ' ' + parts[parts.length - 1][0] + '.' : parts[0];
  }
  const focusLine = () => S.profile.focus || 'Strength training';

  function topbar() {
    return `<div class="topbar">
      <button class="back" data-action="back"><span class="circle-btn">${icon('back')}</span>Back</button>
      <button class="me" data-action="profile" aria-label="Profile and settings">${avatar()}<span class="me-text"><span class="me-name">${esc(shortName() || 'Your profile')}</span><span class="me-sub">${esc(focusLine())}</span></span></button>
    </div>`;
  }

  function dateTile(date, example) {
    const d = L.parse(date);
    return `<span class="date-tile${example ? ' is-example' : ''}"><b>${d.getDate()}</b><small>${L.MONTHS[d.getMonth()]}</small></span>`;
  }

  function confirmBox(text, action, label) {
    return `<div class="confirm"><p>${esc(text)}</p><div class="btn-row">
      <button class="btn btn-dark" data-action="confirm-cancel">Cancel</button>
      <button class="btn btn-danger" data-action="${action}">${esc(label)}</button>
    </div></div>`;
  }

  function closeBtn() {
    return `<button class="circle-btn sm" data-action="close-sheet" aria-label="Close">${icon('close')}</button>`;
  }

  /* ---------- charts ---------- */

  let chartSeq = 0;
  // Needle ticks around a ring, starting at about eight o'clock like the reference chart.
  function radialSvg(ticks, label) {
    const id = 'rg' + ++chartSeq;
    const C = 150, R = 148, r0 = R * 0.57, span = R - r0;
    const start = (150 * Math.PI) / 180, step = (2 * Math.PI) / ticks.length;
    let polys = '';
    ticks.forEach((t, i) => {
      const a = start + (i + 0.5) * step, ux = Math.cos(a), uy = Math.sin(a);
      const r2 = r0 + span * Math.min(1, t.len);
      const pt = (r, w) => (C + ux * r - uy * w).toFixed(1) + ',' + (C + uy * r + ux * w).toFixed(1);
      polys += `<polygon points="${pt(r0, 0.45)} ${pt(r2, 1.7)} ${pt(r2, -1.7)} ${pt(r0, -0.45)}" fill="${t.color}"${t.dim ? ' fill-opacity=".26"' : ''} style="--i:${i}"/>`;
    });
    return `<svg viewBox="0 0 300 300" role="img" aria-label="${esc(label)}">
      <defs>
        <radialGradient id="${id}" cx="150" cy="150" r="${R}" gradientUnits="userSpaceOnUse">
          <stop offset="${(r0 / R).toFixed(3)}" stop-color="#fff" stop-opacity=".3"/>
          <stop offset="${((r0 + span * 0.5) / R).toFixed(3)}" stop-color="#fff"/>
        </radialGradient>
        <mask id="${id}m" maskUnits="userSpaceOnUse" x="0" y="0" width="300" height="300"><rect width="300" height="300" fill="url(#${id})"/></mask>
      </defs>
      <g class="ticks${ui.animate ? ' is-anim' : ''}" mask="url(#${id}m)">${polys}</g>
    </svg>`;
  }

  function radialBlock(ticks, value, caption, label) {
    return `<div class="radial">${radialSvg(ticks, label)}
      <div class="radial-c"><b class="${value.length > 6 ? 'is-long' : ''}">${esc(value)}</b><span>${esc(caption)}</span></div>
    </div>`;
  }

  function sparkSvg(values, fmt) {
    const w = 300, h = 100, padX = 6, top = 18, bottom = 14;
    const min = Math.min(...values), max = Math.max(...values), range = max - min || 1;
    const x = i => (values.length === 1 ? w / 2 : padX + (i * (w - padX * 2)) / (values.length - 1));
    const y = v => top + (1 - (v - min) / range) * (h - top - bottom);
    const pts = values.map((v, i) => [x(i).toFixed(1), y(v).toFixed(1)]);
    const line = pts.map(p => p.join(',')).join(' ');
    const base = h - bottom;
    const area = `M${pts[0][0]},${base} L${pts.map(p => p.join(',')).join(' L')} L${pts[pts.length - 1][0]},${base} Z`;
    const last = pts[pts.length - 1];
    const grid = max === min
      ? `<line x1="0" x2="${w}" y1="${y(max)}" y2="${y(max)}" stroke="#262626" stroke-dasharray="3 4"/><text x="0" y="${y(max) - 6}">${esc(fmt(max))}</text>`
      : `<line x1="0" x2="${w}" y1="${y(max)}" y2="${y(max)}" stroke="#262626" stroke-dasharray="3 4"/><text x="0" y="${y(max) - 6}">${esc(fmt(max))}</text>
         <line x1="0" x2="${w}" y1="${y(min)}" y2="${y(min)}" stroke="#262626" stroke-dasharray="3 4"/><text x="0" y="${y(min) + 13}">${esc(fmt(min))}</text>`;
    return `<svg viewBox="0 0 ${w} ${h}" role="img" aria-label="Top set per session, from ${esc(fmt(values[0]))} to ${esc(fmt(values[values.length - 1]))}">
      <defs><linearGradient id="spk" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6aa676" stop-opacity=".35"/><stop offset="1" stop-color="#6aa676" stop-opacity="0"/></linearGradient></defs>
      ${grid}
      ${values.length > 1 ? `<path d="${area}" fill="url(#spk)"/><polyline points="${line}" fill="none" stroke="#6aa676" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"/>` : ''}
      <circle cx="${last[0]}" cy="${last[1]}" r="5" fill="#ffffff" stroke="#6aa676" stroke-width="2.5"/>
    </svg>`;
  }

  /* ---------- views ---------- */

  const app = $('#app');
  const fab = $('#fab');

  function render() {
    app.innerHTML = ui.view === 'workout' ? viewWorkout() : ui.view === 'stats' ? viewStats() : viewHome();
    ui.animate = false;
    fab.hidden = ui.view !== 'home';
    if (ui.view === 'home') renderFab();
  }

  function renderFab() {
    const w = data().map[today()];
    const st = L.workoutStats(w);
    const started = !!(w && w.exercises.length);
    fab.innerHTML = `<span class="fab-ico">${icon(started ? 'right' : 'plus')}</span>` +
      (started ? `Continue workout <small>${plural(st.sets, 'set')}</small>` : 'Start today’s workout');
  }

  function tile(value, label, pct) {
    const cls = value.length >= 6 ? 'is-xlong' : value.length >= 4 ? 'is-long' : '';
    const delta = pct == null
      ? '<span class="flat">—</span>'
      : `<span class="${pct > 0 ? 'pos' : pct < 0 ? 'neg' : 'flat'}">${pct ? signed(pct, Math.abs(pct) + '%') : '0%'}</span>`;
    return `<div class="tile"><span class="big ${cls}">${esc(value)}</span><div class="tile-meta"><span>${esc(label)}</span>${delta}</div></div>`;
  }

  const PILL_TILT = [[-4, 0], [6, 3], [-12, -2], [3, 5], [14, 0], [-6, -3], [9, 4]];

  function viewHome() {
    const p = S.profile, D = data(), t = today();
    const first = p.name.trim().split(/\s+/)[0] || 'athlete';
    const logged = D.list.filter(w => L.workoutStats(w).sets).length;
    const wk = L.weekToDate(D.list, t);
    const weeks = L.weeklyVolumes(D.list, t, 8);
    const peak = Math.max(1, ...weeks.map(w => w.volume));
    const thisWeek = weeks[weeks.length - 1];
    const total = L.fmtInt(L.fromKg(L.summarize(D.list).volume, unit()));
    const lifts = L.topExercises(D.list, 7);

    const heroMedia = S.photo
      ? `<img class="hero-img" src="${esc(S.photo)}" alt="Your photo">`
      : `<button class="hero-empty" data-action="pick-photo">
          <span class="plate"><span>${icon('camera')}</span></span>
          <b>Add your photo</b>
          <small>It becomes your cover here and your avatar across the app.</small>
        </button>`;

    const days = [];
    const start = L.addDays(L.weekStart(t), ui.weekOffset * 7);
    let trained = 0;
    for (let i = 0; i < 7; i++) {
      const d = L.addDays(start, i), w = D.map[d], st = L.workoutStats(w);
      if (st.sets) trained++;
      const cls = [st.sets ? 'is-done' : w && w.exercises.length ? 'is-planned' : '', d === t ? 'is-today' : ''].join(' ');
      days.push(`<button class="day ${cls}" data-action="open-day" data-date="${d}"${d > t ? ' disabled' : ''} aria-label="${L.longDate(d)}${st.sets ? ', ' + plural(st.sets, 'set') : ''}">
        <span class="day-l">${'MTWTFSS'[i]}</span><span class="day-n">${L.parse(d).getDate()}</span></button>`);
    }

    return `<section class="view home">
      <div class="hero">
        ${heroMedia}
        <header class="home-head">
          <div>
            <h1 class="welcome">Welcome in,<span>${esc(first)}</span></h1>
            <p class="sub">${plural(logged, 'workout')} logged</p>
          </div>
          <button class="circle-btn" data-action="profile" aria-label="Profile and settings">${icon('sliders')}</button>
        </header>
      </div>
      <div class="card">
        ${D.examples ? `<div class="note"><p>Example workouts are shown so you can look around.</p><button data-action="clear-examples">Clear</button></div>` : ''}
        <div class="card-head">
          <button class="who" data-action="profile">
            <span class="name${p.name ? '' : ' is-empty'}">${p.name ? esc(p.name) : 'Add your name'}</span>
            <span class="sub">${esc(focusLine())}</span>
          </button>
          <button class="arrow-btn" data-action="stats" aria-label="Open progress">${icon('upRight')}</button>
        </div>

        <div class="row-label"><span>This week</span><span>vs. same days last week</span></div>
        <div class="tiles">
          ${tile(String(wk.cur.sets), 'Sets', L.pctChange(wk.cur.sets, wk.prev.sets))}
          ${tile(VOLC(wk.cur.volume), unit(), L.pctChange(wk.cur.volume, wk.prev.volume))}
        </div>
        <div class="tile tile-wide">
          <div>
            <div class="tw-label">Total lifted</div>
            <div class="tw-value"><span class="big${total.length >= 10 ? ' is-long' : ''}">${total}</span><span class="unit">${unit()}</span></div>
            <div class="tw-sub ${thisWeek.volume ? 'pos' : 'flat'}">${thisWeek.volume ? '+' + VOL(thisWeek.volume) + ' this week' : 'Nothing logged this week'}</div>
          </div>
          <div class="mini-bars" role="img" aria-label="Weekly volume for the last 8 weeks">
            ${weeks.map((w, i) => `<i class="${i === weeks.length - 1 ? 'is-cur' : ''}" style="height:${Math.max(7, Math.round((w.volume / peak) * 100))}%"></i>`).join('')}
          </div>
        </div>

        <div class="row-label"><span>Top lifts</span><span>Tap one for its history</span></div>
        <div class="pills">
          ${lifts.length
            ? lifts.map((n, i) => `<button class="pill" data-action="ex-history" data-name="${esc(n)}" style="--r:${(PILL_TILT[i][0] * Math.min(1, 11 / n.length)).toFixed(1)}deg;--y:${PILL_TILT[i][1]}px">${esc(n)}</button>`).join('')
            : '<p class="pills-empty">Your most-trained lifts will collect here.</p>'}
        </div>

        <div class="week-head">
          <h3>${L.weekRangeLabel(start)}<span>${trained}/${p.goal} workouts</span></h3>
          <div class="week-nav">
            <button class="circle-btn sm" data-action="week-prev" aria-label="Previous week">${icon('left')}</button>
            <button class="circle-btn sm" data-action="week-next" aria-label="Next week"${ui.weekOffset >= 0 ? ' disabled' : ''}>${icon('right')}</button>
          </div>
        </div>
        <div class="days">${days.join('')}</div>
      </div>
    </section>`;
  }

  function viewWorkout() {
    const date = ui.date, t = today(), D = data();
    const w = D.map[date] || null;
    const example = !!(w && w.example);
    const exercises = w ? w.exercises : [];
    const st = L.workoutStats(w);
    const title = (w && w.name) || (exercises.length ? L.autoName(w) : date === t ? 'Today’s workout' : 'Workout');
    const mins = L.durationMinutes(st);

    // One chart segment per exercise; each tick follows a set's volume (reps when nothing is loaded).
    const byVolume = exercises.some(e => e.sets.some(s => L.setVolume(s) > 0));
    const segs = exercises.map((e, i) => ({
      color: L.SERIES[i % L.SERIES.length],
      values: e.sets.map(s => (byVolume ? L.setVolume(s) : Number(s.r) || 0)),
      dims: e.sets.map(s => !L.isDone(s)),
    }));
    const center = VOL(st.volume);
    const chart = radialBlock(L.radialTicks(segs, 90), center, unit() + ' lifted',
      `${center} ${unit()} lifted across ${plural(st.sets, 'completed set')}`);

    let body;
    if (!exercises.length) {
      const recent = D.list.filter(x => x.date !== date && L.workoutStats(x).sets).slice(-3).reverse();
      body = `<h2 class="h2">No sets yet</h2>
        <p class="lead">Add your first exercise, or repeat a recent session.</p>
        <div class="stack"><button class="btn btn-light" data-action="add-exercise">${icon('plus')}Add exercise</button></div>
        ${recent.length ? `<h3 class="h3">Repeat a workout<small>Copies exercises and sets</small></h3>
          <ul class="list">${recent.map(r => {
            const rs = L.workoutStats(r);
            return `<li class="list-row">${dateTile(r.date, r.example)}
              <span class="list-main"><b>${esc(r.name || L.autoName(r))}</b><span class="list-sub">${plural(rs.exercises, 'exercise')} · ${plural(rs.sets, 'set')}</span></span>
              <button class="chip-btn" data-action="repeat-into" data-src="${r.date}">Repeat</button></li>`;
          }).join('')}</ul>` : ''}`;
    } else {
      const rows = exercises.map((e, i) => {
        const es = L.exerciseStats(e);
        const share = st.volume ? Math.round((es.volume / st.volume) * 100) : st.sets ? Math.round((es.sets / st.sets) * 100) : 0;
        return `<li><button class="legend-row" data-action="edit-ex" data-id="${esc(e.id)}">
          <span class="sq" style="background:${L.SERIES[i % L.SERIES.length]}"></span>
          <span class="lg-name">${esc(e.name)}</span>
          <span class="lg-a${es.sets < es.total ? ' is-partial' : ''}" aria-label="${es.sets} of ${es.total} sets done">${es.sets}/${es.total}</span>
          <span class="lg-b">${share}%</span>
          <span class="lg-dots">${icon('dots')}</span>
        </button></li>`;
      }).join('');

      const prog = L.progressFor(D.list, w).map(p => {
        let right;
        if (p.first) right = '<span class="flat">First time</span>';
        else if (p.kind === 'reps') right = `<span class="${p.delta > 0 ? 'pos' : p.delta < 0 ? 'neg' : 'flat'}">${p.delta ? signed(p.delta, plural(Math.abs(p.delta), 'rep')) : 'Same'}</span>`;
        else right = `<span class="${p.delta > 0 ? 'pos' : p.delta < 0 ? 'neg' : 'flat'}">${p.delta ? signed(p.delta, W(Math.abs(p.delta)) + ' ' + unit()) : 'Same'}</span>`;
        return `<li><button class="prog-row" data-action="ex-history" data-name="${esc(p.name)}">
          <span class="prog-ico">${icon('dumbbell')}</span>
          <span class="list-main"><span class="prog-name">${esc(p.name)}</span><span class="prog-meta">${icon('clock')}<span>Top set ${topLabel(p.top)}</span></span></span>
          <span class="prog-delta">${p.pr ? '<span class="pr">PR</span>' : ''}${right}</span>
        </button></li>`;
      }).join('');

      body = `<h2 class="h2">${plural(st.sets, 'Set')}<small>· ${plural(st.reps, 'rep')}</small></h2>
        <ul class="legend">${rows}</ul>
        <div class="stack">${example
          ? `<button class="btn btn-line" data-action="repeat-today">${icon('repeat')}Repeat this workout today</button>`
          : `<button class="btn btn-line" data-action="add-exercise">${icon('plus')}Add exercise</button>`}</div>
        ${prog ? `<h3 class="h3">Progress<small>Top set vs. last session</small></h3><ul class="prog">${prog}</ul>` : ''}`;
    }

    return `<section class="view page">
      ${topbar()}
      <div class="title-row">
        <div>
          <h1 class="title">${esc(title)}</h1>
          <p class="sub">${L.longDate(date)}${mins ? ' · ' + mins + ' min' : ''}</p>
          ${example ? '<span class="tag">Example</span>' : ''}
        </div>
        ${w ? `<button class="circle-btn" data-action="workout-menu" aria-label="Workout options">${icon('dots')}</button>` : ''}
      </div>
      ${chart}
      <div class="card">${body}</div>
    </section>`;
  }

  const RANGES = [['7', '7D', 'Last 7 days'], ['30', '30D', 'Last 30 days'], ['90', '90D', 'Last 90 days'], ['all', 'All', 'All time']];

  function viewStats() {
    const D = data(), t = today();
    const range = RANGES.find(r => r[0] === ui.range) || RANGES[1];
    const from = range[0] === 'all' ? null : L.addDays(t, -(Number(range[0]) - 1));
    const sum = L.summarize(D.list, from, t);
    const muscles = L.MUSCLE_ORDER.filter(m => sum.muscles[m]);
    const ticks = L.radialTicks(muscles.map(m => ({ color: L.MUSCLE_COLORS[m], values: sum.muscles[m].sessions })), 90);
    const chart = radialBlock(ticks, String(sum.workouts), sum.workouts === 1 ? 'Day' : 'Days',
      `${plural(sum.workouts, 'training day')}, ${range[2].toLowerCase()}`);

    const legend = muscles.map(m => {
      const b = sum.muscles[m];
      const share = sum.volume ? Math.round((b.volume / sum.volume) * 100) : Math.round((b.sets / sum.sets) * 100);
      return `<li class="legend-row is-static"><span class="sq" style="background:${L.MUSCLE_COLORS[m]}"></span>
        <span class="lg-name">${m}</span><span class="lg-a">${plural(b.sets, 'set')}</span><span class="lg-b">${share}%</span></li>`;
    }).join('');

    const weeks = L.weeklyVolumes(D.list, t, 12);
    const peak = Math.max(1, ...weeks.map(w => w.volume));
    const sel = weeks.find(w => w.start === ui.selWeek) || weeks[weeks.length - 1];

    const recs = L.records(D.list).slice(0, 6).map(r => {
      const gain = r.best.w > 0 ? r.gain : r.gainReps;
      const text = r.sessions < 2 || gain <= 0 ? '<span class="flat">—</span>'
        : `<span class="pos">+${r.best.w > 0 ? W(gain) + ' ' + unit() : plural(gain, 'rep')}</span>`;
      return `<li><button class="prog-row" data-action="ex-history" data-name="${esc(r.name)}">
        <span class="prog-ico">${icon('trophy')}</span>
        <span class="list-main"><span class="prog-name">${esc(r.name)}</span><span class="prog-meta">${icon('clock')}<span>${topLabel(r.best)} · ${L.shortDate(r.bestDate)}</span></span></span>
        <span class="prog-delta">${text}</span>
      </button></li>`;
    }).join('');

    const items = D.list.filter(w => (!from || w.date >= from) && w.date <= t && L.workoutStats(w).sets).reverse();
    const shown = ui.historyAll ? items : items.slice(0, 8);
    const history = shown.map(w => {
      const ws = L.workoutStats(w);
      return `<li><button class="list-row" data-action="open-day" data-date="${w.date}">${dateTile(w.date, w.example)}
        <span class="list-main"><b>${esc(w.name || L.autoName(w))}</b><span class="list-sub">${plural(ws.exercises, 'exercise')} · ${plural(ws.sets, 'set')}${w.example ? ' · Example' : ''}</span></span>
        <span class="list-end">${VOLC(ws.volume)} <span class="muted">${unit()}</span></span></button></li>`;
    }).join('');

    return `<section class="view page">
      ${topbar()}
      <div class="title-row"><div><h1 class="title">Progress</h1><p class="sub">${range[2]} · ${plural(sum.workouts, 'workout')}</p></div></div>
      <div class="seg" role="group" aria-label="Time range">
        ${RANGES.map(r => `<button data-action="range" data-r="${r[0]}" class="${r[0] === range[0] ? 'is-on' : ''}" aria-pressed="${r[0] === range[0]}">${r[1]}</button>`).join('')}
      </div>
      ${chart}
      <div class="card">
        <h2 class="h2">${VOL(sum.volume)} ${unit()}<small>lifted · ${plural(sum.sets, 'set')}</small></h2>
        ${legend ? `<ul class="legend">${legend}</ul>` : '<p class="lead">No completed sets in this range yet.</p>'}

        <h3 class="h3">Weekly volume<small>Last 12 weeks</small></h3>
        <div class="wk-caption" id="wkCaption">${weekCaption(sel)}</div>
        <div class="wk-bars">
          ${weeks.map((w, i) => `<button class="${i === weeks.length - 1 ? 'is-cur' : ''}${w.start === sel.start ? ' is-sel' : ''}" data-action="pick-week" data-week="${w.start}" aria-label="Week of ${L.shortDate(w.start)}: ${VOL(w.volume)} ${unit()}"><i style="height:${Math.max(3, Math.round((w.volume / peak) * 100))}%"></i></button>`).join('')}
        </div>
        <div class="wk-axis"><span>${L.shortDate(weeks[0].start)}</span><span>This week</span></div>

        ${recs ? `<h3 class="h3">Personal records<small>Gain since first session</small></h3><ul class="prog">${recs}</ul>` : ''}

        <h3 class="h3">History<small>${plural(items.length, 'workout')}</small></h3>
        ${history ? `<ul class="list">${history}</ul>` : '<p class="empty-list">No workouts in this range yet.</p>'}
        ${items.length > shown.length ? `<button class="more" data-action="history-all">Show all ${items.length}</button>` : ''}
      </div>
    </section>`;
  }

  function weekCaption(w) {
    const label = w.start === L.weekStart(today()) ? 'This week' : 'Week of ' + L.shortDate(w.start);
    return `<span>${label} · ${plural(w.sets, 'set')}</span><b>${VOL(w.volume)} ${unit()}</b>`;
  }

  /* ---------- sheets ---------- */

  const sheetRoot = $('#sheet-root');
  let sheet = null;

  const SHEET_LABELS = { profile: 'Profile', picker: 'Add exercise', editor: 'Sets', menu: 'Workout options', history: 'Exercise history' };

  const SHEETS = {
    profile() {
      const p = S.profile, D = data();
      return `<div class="sheet-head"><div><h3 class="sheet-title">Profile</h3><p class="sub">${Store.synced ? 'Synced to your Claude account' : 'Saved on this device'}</p></div>${closeBtn()}</div>
        <div class="photo-edit">${avatar()}
          <div class="btns">
            <button class="btn btn-light" data-action="pick-photo">${icon('camera')}${S.photo ? 'Change photo' : 'Upload photo'}</button>
            ${S.photo ? '<button class="btn btn-dark" data-action="remove-photo">Remove photo</button>' : ''}
          </div>
        </div>
        <div class="field"><label for="pfName">Name</label><input id="pfName" class="text-in" data-input="name" value="${esc(p.name)}" placeholder="Your name" autocomplete="name" maxlength="40"></div>
        <div class="field"><label for="pfFocus">Focus</label><input id="pfFocus" class="text-in" data-input="focus" value="${esc(p.focus)}" placeholder="Strength training" maxlength="40"></div>
        <div class="field"><span class="label">Units</span><div class="seg" role="group" aria-label="Units">
          ${[['kg', 'Kilograms'], ['lb', 'Pounds']].map(([u, l]) => `<button data-action="unit" data-u="${u}" class="${p.unit === u ? 'is-on' : ''}" aria-pressed="${p.unit === u}">${l}</button>`).join('')}
        </div></div>
        <div class="field"><span class="label">Weekly goal</span><div class="stepper"><b>${plural(p.goal, 'workout')}</b><div>
          <button class="circle-btn" data-action="goal" data-d="-1" aria-label="Lower weekly goal"${p.goal <= 1 ? ' disabled' : ''}>${icon('minus')}</button>
          <button class="circle-btn" data-action="goal" data-d="1" aria-label="Raise weekly goal"${p.goal >= 7 ? ' disabled' : ''}>${icon('plus')}</button>
        </div></div></div>
        <div class="field"><span class="label">Rest timer after each set</span><div class="seg" role="group" aria-label="Rest timer">
          ${REST_OPTIONS.map(([v, l]) => `<button data-action="rest" data-s="${v}" class="${p.rest === v ? 'is-on' : ''}" aria-pressed="${p.rest === v}">${l}</button>`).join('')}
        </div></div>
        <div class="divider"></div>
        ${D.examples ? `<button class="menu-row" data-action="clear-examples">${icon('close')}Clear example workouts<small>${D.examples}</small></button>` : ''}
        ${FRAMED ? '' : `<button class="menu-row" data-action="export">${icon('download')}Export backup<small>JSON</small></button>
          <button class="menu-row" data-action="import">${icon('upload')}Import backup</button>`}
        ${sheet.pendingImport ? confirmBox(`Replace your current log with this backup (${plural(Object.keys(sheet.pendingImport.workouts || {}).length, 'workout')})?`, 'import-confirm', 'Replace') : ''}
        ${sheet.confirmErase ? confirmBox('Erase every workout, your photo and your settings? This cannot be undone.', 'erase-confirm', 'Erase everything')
          : `<button class="menu-row is-danger" data-action="erase">${icon('trash')}Erase all data</button>`}
        <div class="stack"><button class="btn btn-light" data-action="close-sheet">Done</button></div>`;
    },

    picker() {
      return `<div class="sheet-head"><div><h3 class="sheet-title">Add exercise</h3><p class="sub">${L.longDate(sheet.date)}</p></div>${closeBtn()}</div>
        <label class="search">${icon('search')}<input id="pickQ" type="search" data-input="pick-q" placeholder="Search exercises" value="${esc(ui.picker.q)}" autocomplete="off" enterkeyhint="search" aria-label="Search exercises"></label>
        <div class="chips" role="group" aria-label="Muscle group">
          ${['All'].concat(L.MUSCLES).map(m => `<button class="chip${ui.picker.m === m ? ' is-on' : ''}" data-action="pick-m" data-m="${m}" aria-pressed="${ui.picker.m === m}">${m}</button>`).join('')}
        </div>
        <div id="pickList">${pickerList()}</div>`;
    },

    editor() {
      const w = data().map[sheet.date];
      const ex = w && w.exercises.find(e => e.id === sheet.id);
      if (!ex) return `<div class="sheet-head"><div><h3 class="sheet-title">Exercise removed</h3></div>${closeBtn()}</div>`;
      const ro = !!w.example;
      const prev = L.exerciseSessions(data().list, ex.name, sheet.date)[0];
      const ph = placeholders(ex, prev);
      return `<div class="sheet-head"><div><h3 class="sheet-title">${esc(ex.name)}</h3><p class="sub">${esc(ex.muscle)}${ro ? ' · Example' : ''}</p></div>
          <div class="pair">${ro ? '' : `<button class="circle-btn sm" data-action="ex-remove" aria-label="Remove exercise">${icon('trash')}</button>`}${closeBtn()}</div></div>
        ${sheet.confirmRemove ? confirmBox(`Remove ${ex.name} and its sets from this workout?`, 'ex-remove-confirm', 'Remove') : ''}
        ${prev ? `<div class="last-time">${icon('clock')}<span>Last time, ${L.shortDate(prev.date)}: ${prev.sets.map(setShort).join(', ')}</span></div>` : ''}
        <div class="set-grid set-head" aria-hidden="true"><span>Set</span><span>${unit()}</span><span>Reps</span><span></span><span></span></div>
        <div id="setRows">${ex.sets.map((s, i) => setRow(s, i, ph[i], ro)).join('')}</div>
        ${ro ? '' : `<div class="stack"><button class="btn btn-line" data-action="set-add">${icon('plus')}Add set</button></div>`}
        <div class="ex-sum" id="exSum">${exSummary(ex)}</div>
        <div class="stack"><button class="btn btn-light" data-action="close-sheet">Done</button></div>`;
    },

    menu() {
      const w = data().map[sheet.date];
      if (!w) return `<div class="sheet-head"><div><h3 class="sheet-title">Workout options</h3></div>${closeBtn()}</div><p class="empty-list">This workout no longer exists.</p>`;
      const ex = !!w.example;
      return `<div class="sheet-head"><div><h3 class="sheet-title">Workout options</h3><p class="sub">${L.longDate(sheet.date)}</p></div>${closeBtn()}</div>
        ${ex ? '<p class="lead" style="margin:0 0 6px">Example workouts can be repeated but not edited.</p>'
          : `<div class="field"><label for="wkName">Name</label><input id="wkName" class="text-in" data-input="rename" value="${esc(w.name)}" placeholder="${esc(L.autoName(w))}" maxlength="60"></div>`}
        ${sheet.date !== today() ? `<button class="menu-row" data-action="repeat-today">${icon('repeat')}Repeat this workout today</button>` : ''}
        ${ex ? '' : sheet.confirmDelete ? confirmBox('Delete this workout and all of its sets?', 'delete-workout-confirm', 'Delete')
          : `<button class="menu-row is-danger" data-action="delete-workout">${icon('trash')}Delete workout</button>`}
        <div class="stack"><button class="btn btn-light" data-action="close-sheet">Done</button></div>`;
    },

    history() {
      const sessions = L.exerciseSessions(data().list, sheet.ex);
      const head = `<div class="sheet-head"><div><h3 class="sheet-title">${esc(sheet.ex)}</h3><p class="sub">${sessions.length ? esc(sessions[0].muscle) + ' · ' + plural(sessions.length, 'session') : 'No sessions yet'}</p></div>${closeBtn()}</div>`;
      if (!sessions.length) return head + '<p class="empty-list">No completed sets for this exercise yet.</p>';
      const loaded = sessions.some(s => s.top.w > 0);
      const chrono = sessions.slice(0, 12).reverse();
      const values = chrono.map(s => (loaded ? L.round1(L.fromKg(s.top.w, unit())) : s.top.r));
      const best = Math.max(...sessions.map(s => (loaded ? s.top.w : s.top.r)));
      const firstTop = sessions[sessions.length - 1].top;
      const gain = loaded ? best - firstTop.w : best - firstTop.r;
      const best1rm = Math.max(...sessions.map(s => s.best1rm));
      const fmt = v => (loaded ? L.round1(v).toLocaleString('en-US') + ' ' + unit() : plural(v, 'rep'));
      return head + `<div class="mini-stats">
          <div><b>${loaded ? W(best) : best}</b><span>${loaded ? 'Best ' + unit() : 'Best reps'}</span></div>
          <div><b>${loaded && best1rm ? W(best1rm) : '—'}</b><span>Est. 1RM</span></div>
          <div><b class="${gain > 0 ? 'pos' : ''}">${gain > 0 ? '+' + (loaded ? W(gain) : gain) : '—'}</b><span>Since first</span></div>
        </div>
        <div class="spark">${sparkSvg(values, fmt)}</div>
        <p class="hint">Top set per session${chrono.length < sessions.length ? ', last ' + chrono.length : ''}</p>
        <ul class="sessions">${sessions.slice(0, 12).map(s => `<li><span>${L.shortDate(s.date)}</span><span>${s.sets.map(setShort).join(' · ')}</span><span>${VOLC(s.volume)} ${unit()}</span></li>`).join('')}</ul>
        <div class="stack"><button class="btn btn-light" data-action="close-sheet">Done</button></div>`;
    },
  };

  function library() {
    const map = new Map();
    for (const e of L.LIBRARY) map.set(e.name.toLowerCase(), e);
    for (const e of S.custom) map.set(e.name.toLowerCase(), e);
    for (const w of data().list) for (const e of w.exercises) if (!map.has(e.name.toLowerCase())) map.set(e.name.toLowerCase(), { name: e.name, muscle: e.muscle });
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
  }

  function pickerList() {
    const raw = ui.picker.q.trim(), q = raw.toLowerCase(), m = ui.picker.m;
    const w = data().map[sheet.date];
    const have = new Set(w ? w.exercises.map(e => e.name.toLowerCase()) : []);
    const lib = library();
    const item = e => {
      const added = have.has(e.name.toLowerCase());
      return `<li><button class="ex-item" data-action="pick-ex" data-name="${esc(e.name)}" data-m="${esc(e.muscle)}">
        <span class="sq" style="background:${L.MUSCLE_COLORS[e.muscle] || L.MUSCLE_COLORS.Other}"></span>
        <b>${esc(e.name)}</b><small>${added ? 'Added' : esc(e.muscle)}</small>${icon(added ? 'check' : 'plus')}</button></li>`;
    };
    let html = '';
    const matches = lib.filter(e => (m === 'All' || e.muscle === m) && (!q || e.name.toLowerCase().includes(q)));
    if (!q && m === 'All') {
      const recent = L.topExercises(data().list, 6).map(n => lib.find(e => e.name === n)).filter(Boolean);
      if (recent.length) html += `<p class="group">Most used</p><ul class="ex-list">${recent.map(item).join('')}</ul>`;
      for (const muscle of L.MUSCLES) {
        const group = matches.filter(e => e.muscle === muscle);
        if (group.length) html += `<p class="group">${muscle}</p><ul class="ex-list">${group.map(item).join('')}</ul>`;
      }
    } else if (matches.length) {
      html += `<ul class="ex-list">${matches.map(item).join('')}</ul>`;
    } else if (!raw) {
      html += '<p class="empty-list">No exercises in this group yet.</p>';
    }
    if (raw && !lib.some(e => e.name.toLowerCase() === q)) {
      const def = m === 'All' ? 'Other' : m;
      html += `<div class="create"><p>Create “${esc(raw)}”</p>
        <select id="newMuscle" class="select" aria-label="Muscle group for the new exercise">${L.MUSCLES.map(x => `<option${x === def ? ' selected' : ''}>${x}</option>`).join('')}</select>
        <button class="chip-btn" data-action="create-ex">Create</button></div>`;
    }
    return html;
  }

  // Grey hints for empty inputs: the row above, or the first set from last session.
  function placeholders(ex, prev) {
    let w = prev && prev.sets[0] ? prev.sets[0].w : null;
    let r = prev && prev.sets[0] ? prev.sets[0].r : null;
    return ex.sets.map(s => {
      const out = { w, r };
      if (s.w != null) w = s.w;
      if (s.r != null) r = s.r;
      return out;
    });
  }

  function setRow(s, i, ph, ro) {
    const done = L.isDone(s);
    const dis = ro ? ' disabled' : '';
    return `<div class="set-grid set-row${done ? ' is-done' : ''}" data-i="${i}">
      <span class="set-no">${i + 1}</span>
      <input class="num-in" type="text" data-input="set-w" data-i="${i}" inputmode="decimal" enterkeyhint="next" autocomplete="off" aria-label="Set ${i + 1} weight in ${unit()}" value="${L.inputWeight(s.w, unit())}" placeholder="${ph.w == null ? '–' : L.inputWeight(ph.w, unit())}"${dis}>
      <input class="num-in" type="text" data-input="set-r" data-i="${i}" inputmode="numeric" enterkeyhint="done" autocomplete="off" aria-label="Set ${i + 1} reps" value="${s.r == null ? '' : s.r}" placeholder="${ph.r == null ? '–' : ph.r}"${dis}>
      <button class="check" data-action="set-toggle" data-i="${i}" aria-pressed="${done}" aria-label="${done ? 'Undo set ' + (i + 1) : 'Complete set ' + (i + 1)}"${dis}>${icon('check')}</button>
      ${ro ? '<span></span>' : `<button class="del" data-action="set-del" data-i="${i}" aria-label="Delete set ${i + 1}">${icon('close')}</button>`}
    </div>`;
  }

  function exSummary(ex) {
    const es = L.exerciseStats(ex);
    const best = es.top ? `Best <b>${setShort(es.top)}</b>${es.best1rm ? ` · 1RM ≈ <b>${W(es.best1rm)}</b>` : ''}` : 'No sets done yet';
    return `<span>Volume <b>${VOL(es.volume)} ${unit()}</b></span><span>${best}</span>`;
  }

  const currentEx = () => {
    const w = sheet && S.workouts[sheet.date];
    return w ? w.exercises.find(e => e.id === sheet.id) || null : null;
  };

  function openSheet(name, props) {
    const fresh = !sheet;
    sheet = Object.assign({ name }, props || {});
    if (fresh) {
      sheetRoot.innerHTML = '<div class="sheet-layer"><div class="scrim" data-action="close-sheet"></div><div class="sheet-panel" role="dialog" aria-modal="true" tabindex="-1"></div></div>';
      document.documentElement.classList.add('is-locked');
      nav.push();
      bindDrag($('.sheet-panel', sheetRoot));
    }
    renderSheet();
    const panel = $('.sheet-panel', sheetRoot);
    panel.scrollTop = 0;
    if (fresh) panel.focus({ preventScroll: true });
  }

  function renderSheet() {
    if (!sheet) return;
    const panel = $('.sheet-panel', sheetRoot);
    panel.setAttribute('aria-label', SHEET_LABELS[sheet.name] || 'Details');
    panel.innerHTML = '<div class="grabber" aria-hidden="true"></div>' + SHEETS[sheet.name]();
  }

  function closeSheet(then) {
    if (!sheet) { if (then) then(); return; }
    if (nav.managed && nav.depth > 0) {
      nav.afterPop = then || null;
      history.back();
      return;
    }
    dismissSheet();
    if (then) then();
  }

  function dismissSheet() {
    sheet = null;
    sheetRoot.innerHTML = '';
    document.documentElement.classList.remove('is-locked');
    render();
  }

  // Pull the sheet down from its top edge to close it.
  function bindDrag(panel) {
    let y0 = null, dy = 0;
    panel.addEventListener('touchstart', e => {
      if (panel.scrollTop > 0 || e.touches.length !== 1) return;
      if (e.touches[0].clientY - panel.getBoundingClientRect().top > 64) return;
      y0 = e.touches[0].clientY;
      dy = 0;
      panel.style.transition = 'none';
    }, { passive: true });
    panel.addEventListener('touchmove', e => {
      if (y0 == null) return;
      dy = Math.max(0, e.touches[0].clientY - y0);
      panel.style.transform = 'translateY(' + dy + 'px)';
    }, { passive: true });
    panel.addEventListener('touchend', () => {
      if (y0 == null) return;
      y0 = null;
      panel.style.transition = 'transform .2s';
      if (dy > 110) closeSheet();
      else panel.style.transform = '';
    });
  }

  /* ---------- navigation ---------- */

  // In a standalone tab the system back gesture closes sheets and pages.
  // Inside a frame (the Claude viewer) navigation stays in memory.
  const nav = {
    stack: [],
    depth: 0,
    afterPop: null,
    managed: !FRAMED && !!(window.history && history.pushState),
    push() {
      if (!this.managed) return;
      try { history.pushState({ tonnage: ++this.depth }, ''); } catch (e) { this.managed = false; this.depth = 0; }
    },
  };

  window.addEventListener('popstate', e => {
    if (!nav.managed) return;
    const target = (e.state && e.state.tonnage) || 0;
    while (nav.depth > target) {
      nav.depth--;
      if (sheet) dismissSheet();
      else popView();
    }
    const fn = nav.afterPop;
    nav.afterPop = null;
    if (fn) fn();
  });

  function go(view, params) {
    nav.stack.push({ view: ui.view, date: ui.date, y: window.scrollY });
    Object.assign(ui, params || {}, { view, animate: true });
    if (view === 'stats') { ui.historyAll = false; ui.selWeek = null; }
    render();
    window.scrollTo(0, 0);
    nav.push();
    onView();
  }

  function popView() {
    const prev = nav.stack.pop();
    if (!prev) return;
    ui.view = prev.view;
    ui.date = prev.date;
    render();
    window.scrollTo(0, prev.y || 0);
    onView();
  }

  function back() {
    if (sheet) return closeSheet();
    if (!nav.stack.length) {
      if (ui.view !== 'home') { ui.view = 'home'; render(); onView(); }
      return;
    }
    if (nav.managed && nav.depth > 0) history.back();
    else popView();
  }

  function goHomeFresh() {
    nav.stack = [];
    ui.view = 'home';
    ui.weekOffset = 0;
    render();
    window.scrollTo(0, 0);
    onView();
  }

  /* ---------- rest timer, wake lock, toast ---------- */

  const restEl = $('#rest');
  const rest = { end: 0, total: 0, iv: null, hide: null };
  const RING = 2 * Math.PI * 8;

  function startRest() {
    const sec = S.profile.rest;
    if (!sec) return;
    rest.total = sec;
    rest.end = Date.now() + sec * 1000;
    clearTimeout(rest.hide);
    clearInterval(rest.iv);
    restEl.className = 'rest';
    restEl.innerHTML = `<svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="10" cy="10" r="8" fill="none" stroke="#333" stroke-width="2.5"/><circle class="rest-arc" cx="10" cy="10" r="8" fill="none" stroke="#6aa676" stroke-width="2.5" stroke-linecap="round"/></svg>
      <span>Rest</span><span class="rest-time"></span>
      <button data-action="rest-add" aria-label="Add 15 seconds">+15s</button>
      <button class="x" data-action="rest-stop" aria-label="Stop rest timer">${icon('close')}</button>`;
    restEl.hidden = false;
    tickRest();
    rest.iv = setInterval(tickRest, 250);
  }

  function tickRest() {
    const left = Math.max(0, Math.ceil((rest.end - Date.now()) / 1000));
    const timeEl = $('.rest-time', restEl);
    if (left > 0) {
      if (!timeEl) return;
      timeEl.textContent = Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0');
      $('.rest-arc', restEl).setAttribute('stroke-dasharray', (RING * Math.min(1, left / rest.total)).toFixed(2) + ' ' + RING.toFixed(2));
      return;
    }
    clearInterval(rest.iv);
    rest.iv = null;
    restEl.className = 'rest is-over';
    restEl.innerHTML = `<span>Rest over · next set</span><button class="x" data-action="rest-stop" aria-label="Dismiss">${icon('close')}</button>`;
    try { if (navigator.vibrate) navigator.vibrate([180, 90, 180]); } catch (e) { /* not supported */ }
    rest.hide = setTimeout(stopRest, 4000);
  }

  function stopRest() {
    clearInterval(rest.iv);
    clearTimeout(rest.hide);
    rest.iv = null;
    restEl.hidden = true;
    restEl.innerHTML = '';
  }

  // Keep the screen on while today's workout is open.
  let wakeLock = null;
  async function keepAwake(on) {
    try {
      if (on && !wakeLock && navigator.wakeLock && document.visibilityState === 'visible') {
        wakeLock = await navigator.wakeLock.request('screen');
        wakeLock.addEventListener('release', () => { wakeLock = null; });
      } else if (!on && wakeLock) {
        const lock = wakeLock;
        wakeLock = null;
        await lock.release();
      }
    } catch (e) { wakeLock = null; }
  }
  const onView = () => keepAwake(ui.view === 'workout' && ui.date === today());

  let toastTimer = null;
  function toast(msg) {
    const el = $('#toast');
    el.textContent = msg;
    el.classList.add('is-on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('is-on'), 2400);
  }

  /* ---------- photo ---------- */

  // Downscale to a JPEG small enough to store (about 230 KB as a data URL).
  function compressPhoto(file) {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const img = new Image();
      img.onload = () => {
        URL.revokeObjectURL(url);
        let max = 1080, quality = 0.84, out = '';
        for (let i = 0; i < 8; i++) {
          const scale = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight));
          const c = document.createElement('canvas');
          c.width = Math.max(1, Math.round(img.naturalWidth * scale));
          c.height = Math.max(1, Math.round(img.naturalHeight * scale));
          const g = c.getContext('2d');
          g.fillStyle = '#000';
          g.fillRect(0, 0, c.width, c.height);
          g.drawImage(img, 0, 0, c.width, c.height);
          out = c.toDataURL('image/jpeg', quality);
          if (out.length <= 230000) break;
          max = Math.round(max * 0.85);
          quality = Math.max(0.6, quality - 0.06);
        }
        resolve(out);
      };
      img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Image could not be decoded')); };
      img.src = url;
    });
  }

  async function setPhoto(file) {
    if (!ready) { toast('Loading your log…'); return; }
    if (!/^image\//.test(file.type) && !/\.(jpe?g|png|webp|gif|heic|heif|avif)$/i.test(file.name)) {
      toast('Choose an image file');
      return;
    }
    let url;
    try { url = await compressPhoto(file); } catch (e) { toast('That photo could not be opened. Try a JPG or PNG.'); return; }
    S.photo = url;
    const saved = Store.savePhoto();
    render();
    if (sheet) renderSheet();
    toast(saved ? 'Photo updated' : 'Photo shown, but this device could not save it');
  }

  const photoInput = $('#photoInput');
  photoInput.addEventListener('change', () => {
    const f = photoInput.files && photoInput.files[0];
    photoInput.value = '';
    if (f) setPhoto(f);
  });

  document.addEventListener('dragover', e => {
    if (e.dataTransfer && Array.from(e.dataTransfer.types || []).includes('Files')) e.preventDefault();
  });
  document.addEventListener('drop', e => {
    const f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
    if (!f || !/^image\//.test(f.type)) return;
    e.preventDefault();
    setPhoto(f);
  });

  /* ---------- backup ---------- */

  const importInput = $('#importInput');
  importInput.addEventListener('change', async () => {
    const f = importInput.files && importInput.files[0];
    importInput.value = '';
    if (!f || !sheet) return;
    try {
      const parsed = JSON.parse(await f.text());
      if (!parsed || parsed.app !== 'tonnage' || typeof parsed.workouts !== 'object') throw new Error('Not a Tonnage backup');
      sheet.pendingImport = parsed;
      renderSheet();
    } catch (e) {
      toast('That file is not a Tonnage backup');
    }
  });

  /* ---------- events ---------- */

  // Actions that change data wait until stored data has loaded.
  const NEEDS_READY = new Set(['profile', 'pick-photo', 'add-exercise', 'edit-ex', 'workout-menu', 'clear-examples', 'repeat-into', 'repeat-today', 'remove-photo']);

  const ACTIONS = {
    profile: () => openSheet('profile'),
    'pick-photo': () => photoInput.click(),
    'remove-photo': () => {
      S.photo = null;
      Store.savePhoto();
      render();
      renderSheet();
      toast('Photo removed');
    },
    stats: () => go('stats'),
    back: () => back(),
    'open-day': el => go('workout', { date: el.dataset.date }),
    'start-today': () => go('workout', { date: today() }),
    'week-prev': () => { ui.weekOffset--; render(); },
    'week-next': () => { ui.weekOffset = Math.min(0, ui.weekOffset + 1); render(); },
    'workout-menu': () => openSheet('menu', { date: ui.date }),
    'add-exercise': () => {
      ui.picker = { q: '', m: 'All' };
      openSheet('picker', { date: ui.date });
    },
    'edit-ex': el => openSheet('editor', { date: ui.date, id: el.dataset.id }),
    'ex-history': el => openSheet('history', { ex: el.dataset.name }),
    'clear-examples': () => {
      S.examples = false;
      S.anchor = null;
      invalidate();
      Store.saveMeta();
      render();
      if (sheet) renderSheet();
      toast('Example workouts cleared');
    },
    'close-sheet': () => closeSheet(),
    'confirm-cancel': () => {
      if (!sheet) return;
      sheet.confirmErase = sheet.confirmDelete = sheet.confirmRemove = false;
      sheet.pendingImport = null;
      renderSheet();
    },

    'pick-m': el => {
      ui.picker.m = el.dataset.m;
      $$('.chip', sheetRoot).forEach(c => {
        const on = c.dataset.m === ui.picker.m;
        c.classList.toggle('is-on', on);
        c.setAttribute('aria-pressed', String(on));
      });
      $('#pickList').innerHTML = pickerList();
    },
    'pick-ex': el => {
      const ex = addExercise(sheet.date, el.dataset.name, el.dataset.m);
      openSheet('editor', { date: sheet.date, id: ex.id });
    },
    'create-ex': () => {
      const raw = ui.picker.q.trim().replace(/\s+/g, ' ').slice(0, 60);
      if (!raw) return;
      const name = raw[0].toUpperCase() + raw.slice(1);
      const muscle = ($('#newMuscle') || {}).value || 'Other';
      if (!S.custom.some(c => c.name.toLowerCase() === name.toLowerCase())) {
        S.custom.push({ name, muscle });
        Store.saveMeta();
      }
      const ex = addExercise(sheet.date, name, muscle);
      openSheet('editor', { date: sheet.date, id: ex.id });
    },

    'set-toggle': el => {
      const ex = currentEx();
      if (!ex) return;
      const i = Number(el.dataset.i), s = ex.sets[i];
      if (!s) return;
      if (L.isDone(s)) {
        s.done = false;
        s.t = null;
      } else {
        const prev = L.exerciseSessions(data().list, ex.name, sheet.date)[0];
        const ph = placeholders(ex, prev)[i];
        const reps = s.r != null ? s.r : ph.r;
        if (!(reps > 0)) {
          const input = $(`.set-row[data-i="${i}"] [data-input="set-r"]`, sheetRoot);
          if (input) {
            input.classList.remove('shake');
            void input.offsetWidth;
            input.classList.add('shake');
            input.focus();
          }
          toast('Enter reps for this set first');
          return;
        }
        s.r = reps;
        if (s.w == null) s.w = ph.w != null ? ph.w : 0;
        s.done = true;
        s.t = Date.now();
        startRest();
        try { if (navigator.vibrate) navigator.vibrate(12); } catch (e) { /* not supported */ }
      }
      changed(sheet.date);
      renderSheet();
    },
    'set-add': () => {
      const ex = currentEx();
      if (!ex) return;
      if (ex.sets.length >= 50) { toast('That is the most sets one exercise can hold'); return; }
      const last = ex.sets[ex.sets.length - 1];
      ex.sets.push({ w: last ? last.w : null, r: last ? last.r : null, done: false, t: null });
      changed(sheet.date);
      renderSheet();
      const rows = $$('.set-row', sheetRoot);
      if (rows.length) rows[rows.length - 1].scrollIntoView({ block: 'nearest' });
    },
    'set-del': el => {
      const ex = currentEx();
      if (!ex) return;
      ex.sets.splice(Number(el.dataset.i), 1);
      changed(sheet.date);
      renderSheet();
    },
    'ex-remove': () => { sheet.confirmRemove = true; renderSheet(); },
    'ex-remove-confirm': () => {
      const w = S.workouts[sheet.date];
      if (w) w.exercises = w.exercises.filter(e => e.id !== sheet.id);
      changed(sheet.date);
      closeSheet();
      toast('Exercise removed');
    },

    'repeat-today': () => {
      const src = sheet ? sheet.date : ui.date, t = today();
      const n = repeatInto(src, t);
      const open = () => go('workout', { date: t });
      if (sheet) closeSheet(open);
      else open();
      toast(n ? plural(n, 'exercise') + ' added to today' : 'Those exercises are already in today’s workout');
    },
    'repeat-into': el => {
      const n = repeatInto(el.dataset.src, ui.date);
      ui.animate = true;
      render();
      toast(n ? plural(n, 'exercise') + ' added' : 'Those exercises are already here');
    },
    'delete-workout': () => { sheet.confirmDelete = true; renderSheet(); },
    'delete-workout-confirm': () => {
      const d = sheet.date;
      delete S.workouts[d];
      changed(d);
      closeSheet(() => back());
      toast('Workout deleted');
    },

    unit: el => { S.profile.unit = el.dataset.u === 'lb' ? 'lb' : 'kg'; Store.saveMeta(); render(); renderSheet(); },
    goal: el => { S.profile.goal = clamp(S.profile.goal + Number(el.dataset.d), 1, 7); Store.saveMeta(); render(); renderSheet(); },
    rest: el => { S.profile.rest = Number(el.dataset.s); Store.saveMeta(); renderSheet(); },

    range: el => { ui.range = el.dataset.r; ui.historyAll = false; ui.animate = true; render(); },
    'pick-week': el => {
      ui.selWeek = el.dataset.week;
      const weeks = L.weeklyVolumes(data().list, today(), 12);
      const w = weeks.find(x => x.start === ui.selWeek);
      if (!w) return;
      $$('.wk-bars button').forEach(b => b.classList.toggle('is-sel', b === el));
      $('#wkCaption').innerHTML = weekCaption(w);
    },
    'history-all': () => { ui.historyAll = true; render(); },

    export: () => {
      const backup = { app: 'tonnage', version: 1, exportedAt: new Date().toISOString(), profile: S.profile, custom: S.custom, examples: S.examples, anchor: S.anchor, workouts: S.workouts, photo: S.photo };
      const a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([JSON.stringify(backup)], { type: 'application/json' }));
      a.download = 'tonnage-backup-' + today() + '.json';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 4000);
      toast('Backup downloaded');
    },
    import: () => importInput.click(),
    'import-confirm': () => {
      const backup = sheet.pendingImport;
      if (!backup) return;
      const oldDates = Object.keys(S.workouts);
      applyData(backup, backup.photo);
      Store.replaceAll(oldDates);
      sheet.pendingImport = null;
      renderSheet();
      render();
      toast('Backup restored');
    },
    erase: () => { sheet.confirmErase = true; renderSheet(); },
    'erase-confirm': () => {
      const oldDates = Object.keys(S.workouts);
      applyData({}, null);
      Store.eraseAll(oldDates);
      stopRest();
      closeSheet(goHomeFresh);
      toast('All data erased');
    },

    'rest-add': () => { rest.end += 15000; rest.total += 15; tickRest(); },
    'rest-stop': () => stopRest(),
  };

  const INPUTS = {
    name: el => { S.profile.name = el.value.slice(0, 40); Store.saveMeta(); },
    focus: el => { S.profile.focus = el.value.slice(0, 40); Store.saveMeta(); },
    rename: el => {
      const w = S.workouts[sheet.date];
      if (!w) return;
      w.name = el.value.slice(0, 60);
      changed(sheet.date);
    },
    'pick-q': el => { ui.picker.q = el.value; $('#pickList').innerHTML = pickerList(); },
    'set-w': el => updateSet(el, 'w'),
    'set-r': el => updateSet(el, 'r'),
  };

  function updateSet(el, field) {
    const ex = currentEx();
    const s = ex && ex.sets[Number(el.dataset.i)];
    if (!s) return;
    const v = L.parseNum(el.value);
    if (field === 'w') s.w = v == null ? null : Math.min(2000, L.toKg(v, unit()));
    else s.r = v == null ? null : Math.min(1000, Math.round(v));
    if (s.done && !(s.r > 0)) {
      s.done = false;
      s.t = null;
      el.closest('.set-row').classList.remove('is-done');
    }
    changed(sheet.date);
    // Refresh hints and the summary without re-rendering the inputs being typed in.
    const prev = L.exerciseSessions(data().list, ex.name, sheet.date)[0];
    const ph = placeholders(ex, prev);
    $$('.set-row', sheetRoot).forEach((row, i) => {
      const ins = row.querySelectorAll('.num-in');
      if (!ph[i] || ins.length < 2) return;
      ins[0].placeholder = ph[i].w == null ? '–' : L.inputWeight(ph[i].w, unit());
      ins[1].placeholder = ph[i].r == null ? '–' : String(ph[i].r);
    });
    $('#exSum').innerHTML = exSummary(ex);
  }

  document.addEventListener('click', e => {
    const el = e.target.closest('[data-action]');
    if (!el || el.disabled) return;
    const action = ACTIONS[el.dataset.action];
    if (!action) return;
    if (NEEDS_READY.has(el.dataset.action) && !ready) { toast('Loading your log…'); return; }
    action(el, e);
  });

  document.addEventListener('input', e => {
    const fn = e.target.dataset && INPUTS[e.target.dataset.input];
    if (fn) fn(e.target);
  });

  document.addEventListener('focusin', e => {
    if (e.target.classList && e.target.classList.contains('num-in')) setTimeout(() => { try { e.target.select(); } catch (err) { /* ignore */ } }, 0);
  });

  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && sheet) { closeSheet(); return; }
    if (e.key !== 'Enter') return;
    const t = e.target;
    if (t.id === 'pickQ') {
      e.preventDefault();
      const first = $('#pickList .ex-item') || $('#pickList [data-action="create-ex"]');
      if (first) first.click();
    } else if (t.dataset && t.dataset.input === 'set-w') {
      e.preventDefault();
      const reps = $(`.set-row[data-i="${t.dataset.i}"] [data-input="set-r"]`, sheetRoot);
      if (reps) reps.focus();
    } else if (t.classList && (t.classList.contains('num-in') || t.classList.contains('text-in'))) {
      t.blur();
    }
  });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') { Store.flush(); return; }
    if (!sheet) render();
    onView();
  });
  window.addEventListener('pagehide', () => Store.flush());

  /* ---------- start ---------- */

  function boot() {
    Store.loadLocal();
    if (nav.managed) {
      try { history.replaceState({ tonnage: 0 }, ''); } catch (e) { nav.managed = false; }
    }
    render();
    onView();

    const c = window.claude;
    if (c && typeof c.use === 'function') {
      const timer = setTimeout(() => { Store.abandon(); ready = true; }, 12000);
      Store.connect().then(result => {
        clearTimeout(timer);
        ready = true;
        if (result === 'loaded') render();
      });
    } else {
      ready = true;
    }

    if (!FRAMED && 'serviceWorker' in navigator && /^https?:$/.test(location.protocol) && $('link[rel="manifest"]')) {
      navigator.serviceWorker.register('sw.js').catch(() => { /* offline support is optional */ });
    }
  }

  boot();
})();
