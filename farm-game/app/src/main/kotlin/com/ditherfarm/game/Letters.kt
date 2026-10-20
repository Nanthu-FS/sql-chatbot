package com.ditherfarm.game

/**
 * Grandpa Elias's letters (round 7): tucked into his notebook before he died, each one opens when the
 * farm reaches a milestone he knew it would. One waits at a time, as a sealed envelope on the farm;
 * opened letters can be read again from the menu. More come with the wedding and the kid.
 */
class LetterDef(val id: String, val title: String, val text: String, val ready: World.() -> Boolean)

val LETTERS = listOf(
    LetterDef("harvest", "THE FIRST HARVEST",
        "{name},\n\nIf you're reading this, you've pulled your first crop out of my old fields. I tucked this into the notebook to find you right about now.\n\n" +
            "That soil is stubborn. So are we. Water it every day and it will forgive almost anything.\n\nI'm proud of you already.",
        { state.stat("harvested") >= 1 }),
    LetterDef("stone", "STONE HOUSES",
        "{name},\n\nStone houses! I can almost hear old Sterling choking on his tea.\n\n" +
            "When the village was young, Tobias's father laid the first stones by the square. Ask Tobias about it sometime. He pretends not to remember, but he does.\n\nKeep going.",
        { state.villageTier >= 1 }),
    LetterDef("mine", "THE OLD MINE",
        "{name},\n\nSo you got the old mine open. Be careful down there.\n\n" +
            "The year we married, your grandma and I found a crystal the size of a fist on the tenth floor. I sold it to pay for the bridge the bank wouldn't fix.\n\n" +
            "Deeper is darker. Take a lantern. Take a friend.",
        { state.mine.repaired }),
    LetterDef("animal", "THE FIRST ANIMAL",
        "{name},\n\nAn animal on the farm again! Give it a name, and talk to it in the mornings. Ours always gave more when we did.\n\n" +
            "Your grandma named every hen we ever had after a film star. Don't tell anyone.",
        { ranch.animals.isNotEmpty() }),
    LetterDef("brick", "BRICK AND COBBLES",
        "{name},\n\nBrick and cobbles. Willowmere hasn't looked this proud since I was a boy.\n\n" +
            "The young folk will start coming back now. Make room for them at the table. That's how a village grows: one more chair at a time.",
        { state.villageTier >= 2 }),
    LetterDef("loan", "THE ANSWER WAS YES",
        "{name},\n\nIf you're reading this, the bank finally said yes.\n\n" +
            "Forty years I sat across that desk from a Sterling. I never stopped believing someone in this family would hear the right answer.\n\n" +
            "It was never about the money. It was about the people who stayed. Thank you for staying.\n\nAll my love, always.",
        { "finale" in state.storySeen }),
)

fun letterById(id: String) = LETTERS.firstOrNull { it.id == id }

/** The next sealed letter, once the farm has reached it (and the opening has played). */
fun World.pendingLetter(): LetterDef? =
    if ("opening" !in state.storySeen) null else LETTERS.firstOrNull { it.id !in state.letters && it.ready(this) }

fun World.openLetter(id: String) {
    if (letterById(id) != null && state.letters.add(id)) state.bump("letters_read")
}
