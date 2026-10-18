package com.ditherfarm.game

/** Marks every feat and blueprint as done, for tests about what comes after them. */
fun World.knowEverything() {
    for (f in FEATS.values + BLUEPRINTS.values) if (state.stat(f.stat) < f.n) state.stats[f.stat] = f.n
    checkFeats()
    events.clear()
}
