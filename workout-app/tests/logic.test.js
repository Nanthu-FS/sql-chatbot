// Run with: node --test 'workout-app/tests/*.test.js'
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../logic.js');

const set = (w, r, done = true, t = null) => ({ w, r, done, t });
const ex = (name, muscle, sets) => ({ id: name, name, muscle, sets });
const workout = (date, exercises, name = '') => ({ date, name, exercises, updatedAt: 0 });

test('dates use local calendar days and Monday weeks', () => {
  assert.equal(L.addDays('2026-02-27', 2), '2026-03-01');
  assert.equal(L.addDays('2024-02-28', 1), '2024-02-29');
  assert.equal(L.weekStart('2026-10-04'), '2026-09-28'); // Sunday belongs to the week that began Monday
  assert.equal(L.weekStart('2026-09-28'), '2026-09-28');
  assert.equal(L.diffDays('2026-03-07', '2026-03-10'), 3); // across a DST change in many zones
  assert.equal(L.longDate('2026-10-04'), 'Sunday, Oct 4');
  assert.equal(L.weekRangeLabel('2026-09-28'), 'Sep 28 – Oct 4');
});

test('pounds convert to kilograms and back without drift', () => {
  const kg = L.toKg(135, 'lb');
  assert.ok(Math.abs(kg - 61.235) < 0.001);
  assert.equal(L.inputWeight(kg, 'lb'), '135');
  assert.equal(L.fmtWeight(82.5, 'kg'), '82.5');
  assert.equal(L.fmtWeight(80, 'kg'), '80');
  assert.equal(L.inputWeight(null, 'kg'), '');
});

test('number formatting', () => {
  assert.equal(L.fmtInt(122426.4), '122,426');
  assert.equal(L.fmtCompact(950), '950');
  assert.equal(L.fmtCompact(29921), '29.9k');
  assert.equal(L.fmtCompact(10000), '10k');
  assert.equal(L.fmtCompact(99960), '100k');
  assert.equal(L.fmtCompact(482300), '482k');
  assert.equal(L.fmtCompact(999600), '1M');
  assert.equal(L.fmtCompact(1234567), '1.23M');
  assert.equal(L.parseNum('82,5'), 82.5);
  assert.equal(L.parseNum(' '), null);
  assert.equal(L.parseNum('-4'), null);
  assert.equal(L.parseNum('abc'), null);
  assert.equal(L.pctChange(110, 100), 10);
  assert.equal(L.pctChange(5, 0), null);
});

test('only completed sets with reps count toward totals', () => {
  const w = workout('2026-10-01', [
    ex('Bench Press', 'Chest', [set(80, 8, true, 1000), set(80, 8, true, 61000), set(85, 5, false), set(90, null, true)]),
    ex('Pull-up', 'Back', [set(0, 10, true, 181000)]),
  ]);
  const st = L.workoutStats(w);
  assert.equal(st.sets, 3);
  assert.equal(st.reps, 26);
  assert.equal(st.volume, 1280);
  assert.equal(st.planned, 2);
  assert.equal(st.exercises, 2);
  assert.equal(L.durationMinutes(st), 3);
  const bench = L.exerciseStats(w.exercises[0]);
  assert.deepEqual(bench.top, { w: 80, r: 8 });
  assert.equal(bench.total, 4);
});

test('estimated one-rep max uses Epley', () => {
  assert.equal(L.e1rm(100, 1), 100);
  assert.ok(Math.abs(L.e1rm(100, 5) - 116.667) < 0.01);
  assert.equal(L.e1rm(0, 10), 0);
});

test('week-to-date compares the same days of last week', () => {
  const list = [
    workout('2026-09-21', [ex('Squat', 'Legs', [set(100, 5)])]), // last Monday
    workout('2026-09-24', [ex('Squat', 'Legs', [set(100, 5)])]), // last Thursday: outside the window on a Wednesday
    workout('2026-09-28', [ex('Squat', 'Legs', [set(100, 5), set(100, 5)])]),
  ];
  const wk = L.weekToDate(list, '2026-09-30');
  assert.equal(wk.start, '2026-09-28');
  assert.equal(wk.cur.sets, 2);
  assert.equal(wk.prev.sets, 1);
  assert.equal(wk.cur.volume, 1000);
});

test('summaries group volume by muscle, one value per workout', () => {
  const list = [
    workout('2026-09-28', [ex('Bench Press', 'Chest', [set(80, 8)]), ex('Chest Fly', 'Chest', [set(20, 12)]), ex('Squat', 'Legs', [set(100, 5)])]),
    workout('2026-09-30', [ex('Bench Press', 'Chest', [set(80, 8, false)])]),
  ];
  const sum = L.summarize(list);
  assert.equal(sum.workouts, 1);
  assert.deepEqual(sum.muscles.Chest.sessions, [880]);
  assert.equal(sum.muscles.Legs.volume, 500);
  const weeks = L.weeklyVolumes(list, '2026-10-04', 2);
  assert.deepEqual(weeks.map(w => w.start), ['2026-09-21', '2026-09-28']);
  assert.equal(weeks[1].volume, 1380);
});

test('exercise history is newest first and keeps the sets', () => {
  const list = [
    workout('2026-09-20', [ex('Bench Press', 'Chest', [set(75, 8), set(75, 8)])]),
    workout('2026-09-27', [ex('bench press', 'Chest', [set(77.5, 8), set(80, 5, false)])]),
    workout('2026-10-04', [ex('Bench Press', 'Chest', [set(80, 6)])]),
  ];
  const sessions = L.exerciseSessions(list, 'Bench Press', '2026-10-04');
  assert.deepEqual(sessions.map(s => s.date), ['2026-09-27', '2026-09-20']);
  assert.equal(sessions[0].count, 1);
  assert.equal(sessions[0].sets.length, 1);
  assert.equal(sessions[0].sets[0].w, 77.5);
  assert.equal(sessions[1].volume, 1200);
});

test('progress compares the top set with the previous session', () => {
  const list = [
    workout('2026-09-20', [ex('Bench Press', 'Chest', [set(80, 5)]), ex('Pull-up', 'Back', [set(0, 8)])]),
    workout('2026-09-27', [ex('Bench Press', 'Chest', [set(77.5, 8)]), ex('Pull-up', 'Back', [set(0, 9)])]),
  ];
  const today = workout('2026-10-04', [
    ex('Bench Press', 'Chest', [set(82.5, 5)]),
    ex('Pull-up', 'Back', [set(0, 11)]),
    ex('Face Pull', 'Back', [set(20, 15)]),
  ]);
  const [bench, pullup, face] = L.progressFor(list.concat(today), today);
  assert.equal(bench.delta, 5);
  assert.equal(bench.pr, true);
  assert.equal(pullup.kind, 'reps');
  assert.equal(pullup.delta, 2);
  assert.equal(face.first, true);
});

test('records track the best set and the gain since the first session', () => {
  const list = [
    workout('2026-09-20', [ex('Squat', 'Legs', [set(100, 5)])]),
    workout('2026-09-27', [ex('Squat', 'Legs', [set(110, 3)])]),
    workout('2026-10-01', [ex('Squat', 'Legs', [set(105, 5)])]),
  ];
  const [squat] = L.records(list);
  assert.deepEqual(squat.best, { w: 110, r: 3 });
  assert.equal(squat.bestDate, '2026-09-27');
  assert.equal(squat.gain, 10);
  assert.equal(squat.sessions, 3);
  assert.deepEqual(L.topExercises(list.concat([workout('2026-10-02', [ex('Bench Press', 'Chest', [set(60, 5)])])]), 2), ['Squat', 'Bench Press']);
});

test('workout names follow the muscles trained', () => {
  const name = muscles => L.autoName(workout('2026-10-01', muscles.map((m, i) => ex('E' + i, m, [set(10, 10)]))));
  assert.equal(name(['Chest', 'Shoulders', 'Arms']), 'Push day');
  assert.equal(name(['Back', 'Arms']), 'Pull day');
  assert.equal(name(['Legs', 'Core']), 'Leg day');
  assert.equal(name(['Chest', 'Back']), 'Upper body');
  assert.equal(name(['Chest', 'Back', 'Legs']), 'Full body');
  assert.equal(name(['Shoulders']), 'Shoulder day');
  assert.equal(L.autoName(workout('2026-10-01', [])), 'Workout');
});

test('tick allocation fills the ring and respects the minimum', () => {
  const counts = L.allocate([1000, 10, 0], 90, 2);
  assert.equal(counts.reduce((a, b) => a + b, 0), 90);
  assert.ok(counts.every(c => c >= 2));
  assert.deepEqual(L.allocate([0, 0], 10, 2), [5, 5]);
});

test('radial ticks cover the ring with valid lengths and colours', () => {
  const ticks = L.radialTicks([
    { color: '#6aa377', values: [640, 640, 600], dims: [false, false, true] },
    { color: '#f1f1ec', values: [150, 150] },
  ], 90);
  assert.equal(ticks.length, 90);
  for (const t of ticks) {
    assert.ok(t.len > 0 && t.len <= 1, 'length in range');
    assert.match(t.color, /^#[0-9a-f]{6}$/);
  }
  assert.ok(ticks.some(t => t.dim));
  assert.equal(L.radialTicks([], 40).length, 40);
});

test('example workouts stay before the anchor date and are marked', () => {
  const ex = L.sampleWorkouts('2026-10-04');
  const dates = Object.keys(ex);
  assert.ok(dates.length >= 14);
  assert.ok(dates.every(d => d < '2026-10-04' && d >= '2026-09-07'));
  assert.ok(Object.values(ex).every(w => w.example && w.exercises.every(e => e.sets.every(L.isDone))));
  const bench = L.exerciseSessions(Object.values(ex).sort((a, b) => (a.date < b.date ? -1 : 1)), 'Bench Press');
  assert.ok(bench[0].top.w > bench[bench.length - 1].top.w, 'examples show progress');
});

test('normalizeWorkout cleans stored or imported data', () => {
  assert.equal(L.normalizeWorkout(null), null);
  assert.equal(L.normalizeWorkout({ date: '04/10/2026' }), null);
  const w = L.normalizeWorkout({
    date: '2026-10-04',
    name: 42,
    exercises: [
      { name: '  Bench Press ', muscle: 'Pecs', sets: [{ w: '80', r: 8.4, done: 1 }, { w: -5, r: 'x' }] },
      { name: '' },
      'junk',
    ],
  });
  assert.equal(w.name, '');
  assert.equal(w.exercises.length, 1);
  assert.equal(w.exercises[0].name, 'Bench Press');
  assert.equal(w.exercises[0].muscle, 'Other');
  assert.deepEqual(w.exercises[0].sets[0], { w: 80, r: 8, done: true, t: null });
  assert.deepEqual(w.exercises[0].sets[1], { w: null, r: null, done: false, t: null });
});
