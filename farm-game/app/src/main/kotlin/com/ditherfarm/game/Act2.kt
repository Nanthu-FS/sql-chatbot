package com.ditherfarm.game

import org.json.JSONObject

/**
 * Story Act 2 (round 6, owner picks: an agri-corp rival, weekly goal races, six chapters, a valley
 * festival at the end). After the bank loan, Vexmoor Agri-Corp's Ms. Kessler wants the valley for
 * mega-farms and challenges the farm to the Vexmoor Cup: each game week a contest with a score to
 * beat by its last day. A chapter moves on after a contest win plus that chapter's goal; the sixth
 * ends with the Valley Festival, a statue in the village and [ACT2_REWARD] coins.
 */
const val ACT2_CHAPTERS = 6
const val ACT2_DONE = ACT2_CHAPTERS + 1
const val ACT2_REWARD = 25_000
const val CONTEST_DAYS = 7

class Act2State {
    /** 0 before it begins, 1..6 while playing, [ACT2_DONE] after the festival. */
    var chapter = 0
    var week = -1
    var contest = ""
    var base = 0L
    var target = 0
    var weekWon = false
    var wonThisChapter = false
    var wins = 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("ch", chapter); put("week", week); put("contest", contest); put("base", base); put("target", target)
        put("weekWon", weekWon); put("chWon", wonThisChapter); put("wins", wins)
    }

    companion object {
        fun fromJson(o: JSONObject) = Act2State().apply {
            chapter = o.optInt("ch"); week = o.optInt("week", -1); contest = o.optString("contest"); base = o.optLong("base")
            target = o.optInt("target"); weekWon = o.optBoolean("weekWon"); wonThisChapter = o.optBoolean("chWon"); wins = o.optInt("wins")
        }
    }
}

/** One kind of weekly contest: what is counted, how much Vexmoor scores, and when it can run. */
class ContestDef(
    val id: String, val name: String, val desc: String, val base: Int, val perChapter: Int,
    val metric: World.() -> Long, val available: World.() -> Boolean = { true },
)

val CONTESTS = listOf(
    ContestDef("harvest", "Harvest Week", "Harvest the most crops", 60, 20, { state.stat("harvested") }),
    ContestDef("gold", "Golden Harvest", "Harvest gold-quality crops", 8, 3, { state.stat("harvest_gold") }),
    ContestDef("orders", "Order Rush", "Deliver village orders", 6, 2, { state.stat("orders") }),
    ContestDef("timber", "Timber Week", "Fell trees in the forest", 12, 4, { state.stat("fells") }),
    ContestDef("eggs", "Egg-stravaganza", "Collect eggs, milk and wool", 6, 2, { state.stat("animal_products") }, { ranch.animals.isNotEmpty() }),
    ContestDef("fruit", "Fruit Basket", "Pick orchard fruit", 18, 6, { state.stat("fruit_picked") }, { orchard.trees.any { it != null } }),
    ContestDef("fish", "Big Catch", "Catch fish in the river", 5, 2, { state.stat("fish_caught") }, { hasRod }),
    ContestDef("jam", "Jam Session", "Make jams and juices", 4, 2,
        { state.stats.entries.filter { (k, _) -> k.startsWith("made_") && (k.endsWith("_jam") || k.endsWith("_juice")) }.sumOf { it.value } },
        { isBuilt("preserves_jar") || isBuilt("fruit_press") }),
    ContestDef("mine", "Rock and Roll", "Break rocks in the mine", 50, 15, { state.stat("rocks_broken") }, { quarryOpen }),
).associateBy { it.id }

/** The goal each chapter needs (besides a contest win) before the next one begins. */
class ChapterGoal(val text: String, val progress: World.() -> Pair<Int, Int>)

val CHAPTER_GOALS = mapOf(
    1 to ChapterGoal("Win a Vexmoor Cup week") { 0 to 0 },
    2 to ChapterGoal("Reach 5 hearts with a villager") { (data.villagers.maxOfOrNull { hearts(it.id) } ?: 0) to 5 },
    3 to ChapterGoal("Grow 4 trees in the orchard") { orchard.trees.count { it != null } to 4 },
    4 to ChapterGoal("Keep 4 animals in the pasture") { ranch.animals.size to 4 },
    5 to ChapterGoal("Catch 15 fish") { state.stat("fish_caught").toInt() to 15 },
    6 to ChapterGoal("Reach 50 beauty for the festival") { beauty to 50 },
)

val World.act2: Act2State get() = state.act2
val World.contest: ContestDef? get() = CONTESTS[act2.contest]
fun World.contestScore(): Long = contest?.let { (it.metric)(this) - act2.base } ?: 0L
fun World.contestDaysLeft(): Int = CONTEST_DAYS - time.dayIndex % CONTEST_DAYS
fun World.chapterGoalMet(c: Int = act2.chapter): Boolean = CHAPTER_GOALS[c]?.progress?.invoke(this)?.let { (h, n) -> h >= n } ?: true

/** Starts this week's contest (the same kind never runs twice in a row). */
private fun World.startContest(week: Int) {
    val a = act2
    val pool = CONTESTS.values.filter { it.id != a.contest && (it.available)(this) }
    val c = pool[Rng(state.seed * 131 + week).nextInt(pool.size)]
    a.week = week; a.contest = c.id; a.base = (c.metric)(this); a.target = c.base + c.perChapter * (a.chapter - 1); a.weekWon = false
    events += WorldEvent.ContestStarted(c.id, a.target)
}

/** Each dawn: a week that has ended is settled and the next contest begins. */
internal fun World.act2Dawn(day: Int) {
    val a = act2
    if (a.chapter !in 1..ACT2_CHAPTERS) return
    val week = day / CONTEST_DAYS
    if (week == a.week) return
    if (a.week >= 0 && !a.weekWon) events += WorldEvent.ContestLost(a.contest)
    startContest(week)
}

/** Checks the running contest: reaching Vexmoor's score wins the week at once. */
fun World.checkContest() {
    val a = act2
    if (a.chapter !in 1..ACT2_CHAPTERS || a.weekWon || contest == null) return
    if (contestScore() < a.target) return
    a.weekWon = true
    a.wonThisChapter = true
    a.wins++
    val coins = 800 + 400 * a.chapter
    state.coins += coins
    state.bump("contests_won")
    events += WorldEvent.ContestWon(a.contest, coins)
}

/** The Act 2 scene ready to play on the farm, if any. */
fun World.pendingAct2Scene(): Scene? {
    val a = act2
    return when {
        a.chapter == 0 && "finale" in state.storySeen -> ACT2_SCENES["act2_1"]
        a.chapter in 1 until ACT2_CHAPTERS && a.wonThisChapter && chapterGoalMet() -> ACT2_SCENES["act2_${a.chapter + 1}"]
        a.chapter == ACT2_CHAPTERS && a.wonThisChapter && chapterGoalMet() -> ACT2_SCENES["act2_finale"]
        else -> null
    }?.takeIf { it.id !in state.storySeen }
}

/** A chapter scene has played: the story moves on. */
internal fun World.act2SceneDone(id: String) {
    val a = act2
    when (id) {
        "act2_finale" -> {
            a.chapter = ACT2_DONE
            state.coins += ACT2_REWARD
            events += WorldEvent.Act2Complete(ACT2_REWARD)
        }
        else -> {
            a.chapter = id.removePrefix("act2_").toInt()
            a.wonThisChapter = false
            // The first chapter starts the Cup; later ones carry on with this week's contest.
            if (a.week < 0) startContest(time.dayIndex / CONTEST_DAYS)
        }
    }
}
