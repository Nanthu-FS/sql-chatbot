package com.ditherfarm.game

/**
 * Romance scenes (round 7, Part 3): for each candidate a bouquet scene (dating begins), a date on the
 * hill at sunset at ten hearts, and the proposal; then the wedding in the village square, where
 * Juniper marries you and remembers Elias. Nothing explicit: flirting, hands, a kiss at the wedding.
 */
private fun v(text: String, face: String? = null) = Line(Who.VILLAGER, text, right = face)
private fun p(text: String, mood: Int = 0) = Line(Who.PLAYER, text, mood)
private fun n(text: String, bg: String? = null, cue: String? = null) = Line(Who.NARRATOR, text, bg = bg, cue = cue)
private fun card(text: String, bg: String) = Line(Who.NARRATOR, text, bg = bg, cue = Cue.CARD)
private fun other(name: String, face: String, text: String) = Line(Who.OTHER, text, name = name, side = 1, right = face)

fun bouquetScene(id: String) = "romance_${id}_bouquet"
fun dateScene(id: String) = "romance_${id}_date"
fun proposalScene(id: String) = "romance_${id}_propose"
fun weddingScene(id: String) = "wedding_$id"

/** What each candidate says: when the bouquet comes, on the date, at the proposal, and their vow. */
private class Voice(val bouquet: List<String>, val date: List<String>, val yes: List<String>, val vow: String)

private val VOICES = mapOf(
    "ivy" to Voice(
        listOf("For me? You do know I'm a florist, right?", "...Nobody has ever given ME flowers. Not once.", "I'm keeping every petal. Don't laugh."),
        listOf("I planted the first flowers on this hill when I was a girl. Everyone said nothing would grow.", "Look at it now. Look at all of it.", "I think I've been waiting for someone to look at it with."),
        listOf("Yes! Yes, yes, yes! Oh, I'm going to cry on the ring.", "We're having flowers at the wedding. ALL the flowers."),
        "I'll plant something new for every year we have. Starting with today."),
    "sela" to Voice(
        listOf("Sunflowers. A bold choice.", "...I like bold.", "Careful, {name}. I'm told I'm worth the wait."),
        listOf("My grandmother said a good vintage tastes like the year it was made.", "If this year were a wine, it would taste like you.", "That's the closest I'll ever get to poetry. Don't tell anyone."),
        listOf("I've never been in a hurry about anything.", "But I've never been surer. Yes."),
        "Some things are worth every season of waiting. You were."),
    "willa" to Voice(
        listOf("You tied these yourself? The knot's clean. Good hands.", "Ha. I'm blushing, aren't I? I never blush.", "Fine. Yes. Let's see where this goes."),
        listOf("I always build things for other people to live in. Never thought about my own.", "Lately I keep drawing plans with two chairs on the porch.", "Guess who I picture in the second one."),
        listOf("Is that... measure twice, {name}. Ask me again.", "...Yes. Measured. Yes."),
        "I'll build us a life the way I build a house: straight, strong, and with a lot of windows."),
    "coco" to Voice(
        listOf("Flowers! I'm putting them on every table in the cafe.", "No. No, wait. These ones are mine.", "Come by for coffee tomorrow? It's on the house. Every day is, now."),
        listOf("You know what I love about this valley? Everyone comes back for seconds.", "I think I'd like to keep coming back to you.", "Oh, that was cheesy. Pretend I said it better."),
        listOf("Yes! Oh, this calls for cake. A big cake. A huge cake.", "I'm baking it myself and nobody can stop me."),
        "I promise you warm bread, hot coffee and the last slice of pie. Always the last slice."),
    "rook" to Voice(
        listOf("These are for... me?", "Nobody's ever... I'll put them in water. In a bucket. I don't own a vase.", "Thanks, {name}. Really."),
        listOf("Bram said something to me last week. That I'm the best smith he's trained.", "I wanted you to be the first person I told.", "I think that means something. About you. About us."),
        listOf("I made something too, you know. I was going to wait.", "Doesn't matter. Yes. Of course yes."),
        "Iron remembers every hit. I'll remember every day with you."),
    "finn" to Voice(
        listOf("...Oh.", "Thanks. I'll, uh. I'll put them by the window. Where the light's good.", "You're not like anyone I've met, {name}."),
        listOf("Quiet up here. I like it.", "I'm not much for talking. You've noticed.", "But I'd sit quiet next to you for the rest of my life. If you'd let me."),
        listOf("...", "Yes. Sorry. Yes. I was just trying to remember this. All of it."),
        "Wherever the river goes, I'll come home to you."),
    "jasper" to Voice(
        listOf("Now there's a song. A farmer brings a fiddler flowers.", "I've had applause in forty towns, {name}. This is better.", "I may have to stay longer than I planned."),
        listOf("I wrote the last verse of the Willowmere song last night.", "It's about someone who stopped a wanderer from wandering.", "You don't have to guess who."),
        listOf("I've played a hundred weddings. I always wondered how the groom felt.", "Now I know. Yes. Yes, a thousand times."),
        "I've walked a lot of roads. This is the one I stay on."),
    "marco" to Voice(
        listOf("Flowers. For me.", "I'm marking this on the map. Right here. The day {name} brought me flowers.", "I'm going to need a bigger map."),
        listOf("I measured this valley for a company that only saw numbers.", "Now, when I look at the map, I don't see acres. I see you, everywhere on it.", "Is that a strange thing to say? It's true."),
        listOf("I think I knew the first day. When you told me the fields had names.", "Yes. Let's put our name on the map together."),
        "Wherever we go, I'll know exactly where home is. It's next to you."),
)


/** The married scenes: at 12 hearts the spouse asks about a family; at 14, a quiet anniversary. */
private val MARRIED = mapOf(
    "ivy" to (listOf("I planted a row of baby sunflowers by the porch today.", "And I caught myself thinking... how nice it would be to watch someone small run between them.") to listOf("Ten years from now, this porch will be covered in roses. I'm sure of it.", "And I'll still be bringing you flowers. Even though you're the farmer.")),
    "sela" to (listOf("My grandmother had a saying: a vineyard is planted for the grandchildren.", "I'd like to plant one with you. A family, I mean. If you'd like that too.") to listOf("A year married. I opened a bottle I laid down the week we met.", "It's perfect. Of course it is. I'm never wrong about vintages. Or about you.")),
    "willa" to (listOf("I measured the spare room today. Twice.", "It'd fit a crib. Just saying. A little one running round the farm... I'd like that. Would you?") to listOf("I carved our initials into the porch beam. Don't tell Tobias, he'd say I ruined good oak.", "It's the best thing I ever built. Us, I mean. Not the beam.")),
    "coco" to (listOf("I baked tiny cupcakes today. The baby-sized kind.", "...Okay, I'll just say it. I want a little one to share them with. Our little one.") to listOf("You know what I love about mornings? You, stealing the first slice before I've even cut it.", "Never stop doing that. Ever.")),
    "rook" to (listOf("Bram's teaching his nephew the anvil. Kid's got good arms.", "I keep thinking I'd like to teach someone too. Our kid. If you want that.") to listOf("I made you something. It's just a little iron heart.", "Took me three nights. The first two were terrible. Like me, before you.")),
    "finn" to (listOf("...I made a little fishing rod today. Small. Really small.", "Thought maybe someday someone would need it. Someone of ours.") to listOf("Quiet up on the hill again.", "Remember our first date? I still don't talk much. But I'm still here. I'm staying.")),
    "jasper" to (listOf("I wrote a lullaby last night. I didn't even mean to.", "Then I realised who I wanted to sing it to. A little one. Ours.") to listOf("A wandering fiddler, settled on a farm. Who'd have guessed?", "I've played in forty towns, {name}. None of them sounded like home until you.")),
    "marco" to (listOf("I drew a new map of the farm today. I added a little room by the window.", "For someone small. A family. I'd like that, if you would.") to listOf("I found my old company map of the valley. All numbers and acres.", "I burned it. We make our own maps now.")),
)

private fun scenesFor(id: String): List<Scene> {
    val voice = VOICES.getValue(id)
    val blush = "$id:blush"
    val bouquet = Scene(bouquetScene(id), 0, false, listOf(n("You hold out the bouquet.")) +
        voice.bouquet.mapIndexed { i, t -> v(t, if (i == 0) blush else null) } +
        n("You're dating! Hearts 9 and 10 are open now."), villager = id)
    val date = Scene(dateScene(id), 0, false, listOf(n("A blanket on the hill above Willowmere. The sun goes down over the fields.", bg = "hill")) +
        voice.date.mapIndexed { i, t -> v(t, if (i == voice.date.size - 1) blush else null) } +
        p("I'm glad it's you.") +
        n("A hand finds yours on the blanket. Neither of you lets go."), villager = id)
    val propose = Scene(proposalScene(id), 0, false, listOf(
        n("The hill again, at sunset. Your hand shakes a little as you take out the ring.", bg = "hill"),
        p("Will you marry me?", 1),
    ) + voice.yes.mapIndexed { i, t -> v(t, blush) } +
        n("The wedding is in $WEDDING_DAYS days, in the village square."), villager = id)
    val wedding = Scene(weddingScene(id), 0, false, listOf(
        card("THE VILLAGE SQUARE\nTHREE DAYS LATER", "square"),
        n("All of Willowmere has come. Ribbons on the lamp posts, petals on the cobbles, Jasper's fiddle tuning up."),
        other("JUNIPER", "juniper", "Friends. Neighbours. We're here for the happiest day this square has seen in forty years."),
        other("JUNIPER", "juniper", "Elias used to sit right over there on that bench and say, one day this village will dance again."),
        other("JUNIPER", "juniper", "Well, old friend. Look at us now."),
        other("JUNIPER", "juniper", "{name}, would you say your promise?"),
        p("I came to Willowmere to keep a promise to my grandpa. I'm staying because of you."),
        v(voice.vow, blush),
        other("JUNIPER", "juniper", "Then by the power of this whole stubborn village... you're married! Go on, then."),
        Line(Who.NARRATOR, "The kiss.", right = blush, cue = Cue.KISS),
        other("MARA", "mara", "Oh, I'm not crying. It's the flour."),
        other("TOBIAS", "tobias", "Forty years I waited to see this square full again. Worth every one."),
        other("PIP", "pip", "Does this mean there's cake? There's CAKE!"),
        n("You're married! Your spouse moves to the farm."),
    ), villager = id)
    val (ask, anniv) = MARRIED.getValue(id)
    val family = Scene(heartScene(id, KID_ASK_HEARTS), 0, false, listOf(n("An evening on the porch. The fireflies are out.", bg = "porch")) +
        ask.mapIndexed { i, t -> v(t, if (i == ask.size - 1) blush else null) } +
        p("I'd love that.") +
        n("In a few days, a little one will join the family."), villager = id)
    val year = Scene(heartScene(id, 14), 0, false, listOf(n("A blanket on the hill above Willowmere, a year on.", bg = "hill")) +
        anniv.mapIndexed { i, t -> v(t, if (i == anniv.size - 1) blush else null) } +
        p("Happy anniversary.") +
        n("Fourteen hearts. Some things only get better."), villager = id)
    return listOf(bouquet, date, propose, wedding, family, year)
}

val ROMANCE_SCENES: Map<String, Scene> = VOICES.keys.flatMap { scenesFor(it) }.associateBy { it.id }
