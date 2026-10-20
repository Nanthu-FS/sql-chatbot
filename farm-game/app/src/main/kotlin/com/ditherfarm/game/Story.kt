package com.ditherfarm.game

// ------------------------------------------------------------------ the bank loan story
/**
 * The story (Docs/DECISIONS.md, rounds 3 and 7): Grandpa Elias spent forty years asking Goldcrest
 * Bank for a loan to rebuild Willowmere, and old Mr. Sterling refused him every time. On his
 * deathbed he asks his grandchild to rebuild the village. The young Mr. Sterling refuses them too,
 * then comes back to assess the village after each rebuild; at the concrete town the loan is approved
 * in Elias's name and Willowmere is named the best farming village.
 */
enum class Who { PLAYER, OFFICER, NARRATOR, VILLAGER, RIVAL, OTHER }

/**
 * One line of a scene. [mood] 1 is the player's determined face or the officer's shocked one.
 * Round 7, for the cinematic opening: [left] / [right] put another face on that side (it stays until
 * changed), [name] labels an OTHER speaker on [side] (0 left, 1 right), [bg] switches the backdrop
 * (it stays; a fade goes through black), [cue] plays an effect and [slow] types the line slowly.
 */
class Line(
    val who: Who, val text: String, val mood: Int = 0,
    val left: String? = null, val right: String? = null, val name: String? = null, val side: Int = 1,
    val bg: String? = null, val cue: String? = null, val slow: Boolean = false,
)

/** A scene: [bank] scenes play in the loan office, the rest over the village. [tier] gates it. */
class Scene(val id: String, val tier: Int, val bank: Boolean, val lines: List<Line>, val coins: Int = 0, val villager: String? = null)

const val OFFICER_NAME = "Mr. Sterling"
const val GRANDPA_NAME = "Elias"
const val VILLAGE_NAME = "Willowmere"
const val FARMER_LOOKS = 4
const val NAME_MAX = 10

/** Cues a line can play (StoryPanel). */
object Cue {
    /** A title card: the text alone, large, over a dark screen. */
    const val CARD = "card"
    /** The bank's rubber stamp slams down: REFUSED, or APPROVED at the finale. */
    const val STAMP = "stamp"
    const val APPROVE = "approve"
    /** The farmer picks who they are (a new farm, or a replay) before the scene goes on. */
    const val PICK = "pick"
    /** The bedroom lamp gutters out and the screen fades to black. */
    const val LAMP_OUT = "lamp_out"
}

private fun p(text: String, mood: Int = 0, bg: String? = null, left: String? = null, right: String? = null) =
    Line(Who.PLAYER, text, mood, bg = bg, left = left, right = right)
private fun o(text: String, mood: Int = 0) = Line(Who.OFFICER, text, mood)
private fun n(text: String, bg: String? = null, left: String? = null, right: String? = null, cue: String? = null, slow: Boolean = false) =
    Line(Who.NARRATOR, text, bg = bg, left = left, right = right, cue = cue, slow = slow)
private fun card(text: String, bg: String? = null) = Line(Who.NARRATOR, text, bg = bg, cue = Cue.CARD)
private fun stamp() = Line(Who.NARRATOR, "*STAMP*  REFUSED.", cue = Cue.STAMP)

/** Grandpa Elias speaking from the left (the flashbacks) or the right (his bed). */
private fun el(text: String, side: Int = 0, slow: Boolean = false, face: String? = null) =
    Line(Who.OTHER, text, name = "ELIAS", side = side, slow = slow, left = if (side == 0) face else null, right = if (side == 1) face else null)
/** Old Mr. Sterling, behind the same desk. */
private fun st(text: String, face: String? = null) = Line(Who.OTHER, text, name = "MR. STERLING", side = 1, right = face)
private fun vl(id: String, name: String, text: String) = Line(Who.OTHER, text, name = name, side = 1, right = id)

val SCENES: List<Scene> = listOf(
    Scene("opening", 0, true, listOf(
        // Forty years ago.
        card("WILLOWMERE\nFORTY YEARS AGO", bg = "bank_old"),
        n("Goldcrest Bank. The rural loans desk.", left = "elias_young", right = "sterling_sr_young"),
        st("Next. ...Name?"),
        el("Elias, sir. From $VILLAGE_NAME, out in the valley."),
        el("The spring floods took our bridge, and the mill wheel's cracked. We need a loan to fix them."),
        st("$VILLAGE_NAME. Thirty farms and a duck pond."),
        st("This bank lends to towns with a future, young man."),
        stamp(),
        el("Then I'll come back. Every year, if I have to."),
        // Twenty years ago.
        card("TWENTY YEARS AGO", bg = "bank_old"),
        n("The same desk. The same stamp.", left = "elias_mid", right = "sterling_sr_mid"),
        el("Half the young folk have gone to the city. The school closed this winter."),
        el("With a loan we could rebuild. Give them a reason to stay."),
        st("Elias. This is your twentieth application."),
        st("A dying village is a poor investment."),
        stamp(),
        el("It's not dying. It's waiting."),
        // Five years ago.
        card("FIVE YEARS AGO", bg = "bank_old"),
        n("Elias comes in slowly now, with a cane.", left = "elias_old", right = "sterling_sr_old"),
        el("Morning, Sterling. You look older."),
        st("So do you, Elias. And the answer is the same."),
        el("The Pruitts sold their fields to Vexmoor last month. The Abernathys too. Land I helped them plough."),
        el("Please. Before there's nothing left to save."),
        st("My son takes over this desk next year. Perhaps he'll see it differently."),
        st("...I doubt it."),
        stamp(),
        n("Forty years. Forty applications. Forty REFUSED stamps."),
        // Who are you?
        Line(Who.NARRATOR, "", cue = Cue.PICK),
        // The deathbed.
        card("THE FARMHOUSE\nTHIS AUTUMN", bg = "bedroom"),
        n("The last light of the day comes in through Grandpa's window.", left = "player", right = "elias_bed"),
        el("{name}... you came. Good. Good.", side = 1, slow = true),
        p("Of course I came, Grandpa. I'm right here."),
        el("Look at that sky. Your grandma always said the valley blushes at sunset.", side = 1, slow = true),
        el("I kept thinking... one more year. One more trip to that bank, and they'd say yes.", side = 1, slow = true),
        el("Forty years, {name}. They never did.", side = 1, slow = true),
        p("Don't talk like that. You'll ask them again in the spring."),
        el("No. Not this time.", side = 1, slow = true),
        n("His hand finds yours. It is lighter than you remember.", slow = true),
        el("My notebook, on the table. Everything I know about this land is in there.", side = 1, slow = true),
        el("$VILLAGE_NAME was beautiful once. Bread in the morning. Music in the square on Sundays.", side = 1, slow = true),
        el("Promise me something.", side = 1, slow = true),
        p("Anything."),
        el("Rebuild the village. Not for the bank. For the people who stayed.", side = 1, slow = true),
        p("I promise, Grandpa. I'll rebuild it. All of it.", 1),
        el("...There it is. That stubborn look. You got that from me.", side = 1, slow = true),
        el("I'm so proud of you, {kid}.", side = 1, slow = true),
        n("The lamp burns low. Outside, the old windmill creaks once more, and is still.", cue = Cue.LAMP_OUT, slow = true),
        card("Elias passed away that night,\nwith the window open to his fields."),
        // The porch.
        n("Later, on the porch. The village is dark on the hill.", bg = "porch", right = "mara"),
        vl("mara", "MARA", "Oh, {name}. Come here, love."),
        vl("mara", "MARA", "He talked about you every single day. His {grandchild}, the one who'd come home."),
        vl("tobias", "TOBIAS", "Forty years he fought for this village. Never once gave up."),
        vl("tobias", "TOBIAS", "If you need a hand... well. These old hands can still swing a hammer."),
        p("Then we start tomorrow. And I'm going to that bank myself.", 1),
        vl("mara", "MARA", "Ha! That's Elias's {grandchild}, all right."),
        // The bank, today.
        card("GOLDCREST BANK\nONE WEEK LATER", bg = "bank"),
        n("The same desk. A younger face behind it.", right = "officer"),
        o("Next! ...Name?"),
        p("{name}. Elias's {grandchild}, from $VILLAGE_NAME."),
        o("$VILLAGE_NAME. My father used to talk about an old farmer from there. Came in every single year."),
        o("Let me guess: a loan under the new Rural Growth Scheme?"),
        p("To rebuild the village. The way my grandpa wanted."),
        o("The Scheme backs villages that can give something back. Harvests. Trade. Taxes."),
        o("Your village has wooden huts, dirt roads and one rusty truck. It gives back nothing."),
        stamp(),
        p("My grandpa heard that word for forty years. I won't.", 1),
        o("Then come back when your little village is worth something. Stone houses, at the very least."),
        p("I'll make $VILLAGE_NAME the best farming village in the country. You'll see.", 1),
        o("Ha! Like father, like son. Or... like grandfather, like {grandchild}. NEXT!"),
        n("Back home, Grandpa's notebook waits on the farmhouse table..."),
    )),
    Scene("tier_1", 1, false, listOf(
        n("A shiny car stops in the village square."),
        o("So. Stone houses. How... quaint."),
        o("A seed shop and a blacksmith. My father would have laughed. One good season doesn't make a village, {name}."),
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
        o("The board looked at your numbers. Rating: A. Your loan is..."),
        Line(Who.NARRATOR, "*STAMP*  APPROVED.", cue = Cue.APPROVE),
        o("I had it made out in your grandfather's name. Elias. It seemed... right."),
        p("He would have loved to see this."),
        o("My father kept all forty of his applications, you know. In a drawer. He never threw one away."),
        o("He was wrong about $VILLAGE_NAME. So was I.", 1),
        o("And the Ministry has named $VILLAGE_NAME the Best Farming Village of the year."),
        p("Drop by the cafe sometime, $OFFICER_NAME. The pie's on us."),
        n("Loan approved: 50,000 coins. $VILLAGE_NAME is the best farming village in the country!"),
    ), coins = 50_000),
)

fun sceneById(id: String) = SCENES.firstOrNull { it.id == id } ?: HEART_SCENES[id] ?: ACT2_SCENES[id]

/** The player's name as shown in the story ("Farmer" until one is picked). */
val World.playerName get() = state.playerName.ifBlank { "Farmer" }

/** Text with {name} (and, round 7, {grandchild} and {kid}) filled in for the player. */
fun World.storyText(line: Line) = storyText(line.text)

fun World.storyText(text: String) = text.replace("{name}", playerName)
    .replace("{grandchild}", if (state.playerGender == Gender.FEMALE) "granddaughter" else "grandson")
    .replace("{kid}", if (state.playerGender == Gender.FEMALE) "my girl" else "my boy")

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

/** Male or female (round 7): romance follows it, and two of the four looks belong to each. */
enum class Gender { MALE, FEMALE;
    /** The two looks (portraits/farmer_<i>) for this gender. */
    val looks get() = if (this == MALE) listOf(0, 3) else listOf(2, 1)
}

fun lookGender(look: Int) = if (look in Gender.FEMALE.looks) Gender.FEMALE else Gender.MALE

/** Sets the farmer's look (0..3), their gender (from the look), and name (letters and spaces, trimmed, at most [NAME_MAX]). */
fun World.setPlayer(look: Int, name: String) {
    state.playerLook = look.coerceIn(0, FARMER_LOOKS - 1)
    state.playerGender = lookGender(state.playerLook)
    state.playerName = name.filter { it.isLetter() || it == ' ' }.trim().take(NAME_MAX)
}
