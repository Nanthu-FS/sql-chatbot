package com.ditherfarm.game

// ------------------------------------------------------------------ the bank loan story
/**
 * The story (Docs/DECISIONS.md, round 3): Goldcrest Bank refuses the farmer a loan under the
 * government's Rural Growth Scheme because the village "gives nothing back". The loan officer,
 * Mr. Sterling, comes back to assess the village after each rebuild, and at the concrete town the
 * loan is approved and the valley is named the best farming village.
 */
enum class Who { PLAYER, OFFICER, NARRATOR, VILLAGER, RIVAL }

/** One line of a scene; [mood] 1 is the player's determined face or the officer's shocked one. */
class Line(val who: Who, val text: String, val mood: Int = 0)

/** A scene: [bank] scenes play in the loan office, the rest over the village. [tier] gates it. */
class Scene(val id: String, val tier: Int, val bank: Boolean, val lines: List<Line>, val coins: Int = 0, val villager: String? = null)

const val OFFICER_NAME = "Mr. Sterling"
const val FARMER_LOOKS = 4
const val NAME_MAX = 10

private fun p(text: String, mood: Int = 0) = Line(Who.PLAYER, text, mood)
private fun o(text: String, mood: Int = 0) = Line(Who.OFFICER, text, mood)
private fun n(text: String) = Line(Who.NARRATOR, text)

val SCENES: List<Scene> = listOf(
    Scene("opening", 0, true, listOf(
        n("Goldcrest Bank. The rural loans desk."),
        o("Next! ...Name?"),
        p("{name}. I farm my grandpa's old land, out in the valley."),
        o("The valley. Let me guess: a loan under the new Rural Growth Scheme?"),
        p("Yes. With it I could fix up the farm, and help the whole village."),
        o("The Scheme backs villages that can give something back. Harvests. Trade. Taxes."),
        o("Your village has wooden huts, dirt roads and one rusty truck. It gives back nothing."),
        n("*STAMP*  REFUSED."),
        p("Nothing? We feed half the county with what little we have!", 1),
        o("Then come back when your little village is worth something. Stone houses, at the very least."),
        p("Fine. I'll make our valley the best farming village in the country. You'll see.", 1),
        o("Ha! I'll believe that when I see it. NEXT!"),
        n("Back home, Grandpa's old notebook waits on the farmhouse table..."),
    )),
    Scene("tier_1", 1, false, listOf(
        n("A shiny car stops in the village square."),
        o("So. Stone houses. How... quaint."),
        o("A seed shop and a blacksmith. One good season doesn't make a village, {name}."),
        o("Capacity rating: C. Still refused."),
        p("We're not done yet.", 1),
    )),
    Scene("tier_2", 2, false, listOf(
        o("Brick houses and cobbled streets. Hm. The numbers are... less embarrassing."),
        o("Rating: B. The Scheme only funds the best. Which you are not. Yet."),
        p("Then we'll build a town. Count on it.", 1),
    )),
    Scene("finale", 3, false, listOf(
        n("The shiny car is back. This time there is a trophy on the back seat."),
        o("Paved streets. A cafe. Trucks rolling out every week...", 1),
        o("The board looked at your numbers. Rating: A. Your loan is... approved."),
        o("And the Ministry has named the valley the Best Farming Village of the year."),
        p("Told you."),
        o("I may have been... a little hasty. Hmph.", 1),
        p("Drop by the cafe sometime, $OFFICER_NAME. The pie's on us."),
        n("Loan approved: 50,000 coins. The valley is the best farming village in the country!"),
    ), coins = 50_000),
)

fun sceneById(id: String) = SCENES.firstOrNull { it.id == id } ?: HEART_SCENES[id] ?: ACT2_SCENES[id]

/** The player's name as shown in the story ("Farmer" until one is picked). */
val World.playerName get() = state.playerName.ifBlank { "Farmer" }

/** Text with {name} filled in. */
fun World.storyText(line: Line) = line.text.replace("{name}", playerName)

/** The next scene to play: the opening on a new farm, then each village tier's visit. */
fun World.pendingScene(): Scene? = SCENES.firstOrNull { it.id !in state.storySeen && state.villageTier >= it.tier }

/** Marks a scene played and pays what it brings (the loan at the finale). */
fun World.finishScene(id: String) {
    val s = sceneById(id) ?: return
    if (!state.storySeen.add(id)) return
    // A heart event deepens the friendship a little more (round 6).
    s.villager?.let { befriend(it, 30); events += WorldEvent.HeartEventDone(it) }
    if (id in ACT2_SCENES) act2SceneDone(id)
    if (s.coins > 0) {
        state.coins += s.coins
        state.bump("coins_earned", s.coins.toLong())
        events += WorldEvent.LoanApproved(s.coins)
    }
}

/** Sets the farmer's look (0..3) and name (letters and spaces, trimmed, at most [NAME_MAX]). */
fun World.setPlayer(look: Int, name: String) {
    state.playerLook = look.coerceIn(0, FARMER_LOOKS - 1)
    state.playerName = name.filter { it.isLetter() || it == ' ' }.trim().take(NAME_MAX)
}
