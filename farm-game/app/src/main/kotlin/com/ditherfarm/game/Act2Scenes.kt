package com.ditherfarm.game

/**
 * Story Act 2 scenes (round 6): Ms. Kessler of Vexmoor Agri-Corp against the farm, one scene to
 * open each chapter and the Valley Festival to close the act. Mood 1 is Kessler caught off guard.
 */
private fun k(text: String, mood: Int = 0) = Line(Who.RIVAL, text, mood)
private fun p(text: String) = Line(Who.PLAYER, text)
private fun n(text: String) = Line(Who.NARRATOR, text)

private fun act2(id: String, vararg lines: Line) = Scene(id, 0, false, lines.toList())

val ACT2_SCENES: Map<String, Scene> = listOf(
    act2("act2_1",
        n("A sleek green car rolls into the village. Nobody in the valley owns a car that clean."),
        k("{name}, is it? Imelda Kessler, Vexmoor Agri-Corp. I'll be brief."),
        k("Vexmoor wants this valley. Mega-farms from the river to the forest. We'll pay you well for your land."),
        k("We already own a good part of it. When the bank kept turning your grandfather down, his neighbours sold to us. Cheaply."),
        p("Grandpa told me. The Pruitts. The Abernathys. Fields he helped them plough."),
        p("My land isn't for sale. And I want theirs back."),
        k("Everything is for sale. But fine. Let's do this the valley's way."),
        k("Every week, a contest: your little farm against Vexmoor. When you lose, the village will see who really feeds it."),
        n("Chapter 1: The Vexmoor Cup. Tap the trophy to see this week's contest, and beat Vexmoor's score before the week ends."),
    ),
    act2("act2_2",
        k("You... beat us? Our yields are engineered!", 1),
        p("Our soil is just better loved."),
        k("Luck. Vexmoor has bought the old mill by the river. And I'll be talking to your villagers."),
        n("Chapter 2: Friends in the Valley. Reach 5 hearts with a villager, and win another week."),
    ),
    act2("act2_3",
        k("I offered your neighbours twice the pay to work our fields. They laughed at me.", 1),
        p("People here look after each other."),
        k("Sentiment doesn't scale, {name}. Vexmoor is planting a thousand fruit trees on the hill."),
        k("And my best surveyor has quit to stay in your valley. Marco. Traitor.", 1),
        n("Marco the surveyor has moved to Willowmere."),
        n("Chapter 3: Roots. Grow 4 trees in your orchard, and win another week."),
    ),
    act2("act2_4",
        k("A late frost took half our trees. Yours are fine?", 1),
        p("Grandpa planted windbreaks. Old tricks."),
        k("Then Vexmoor will do livestock. Ten thousand hens in one shed."),
        n("Chapter 4: Happy Herd. Keep 4 animals in your pasture, and win another week."),
    ),
    act2("act2_5",
        k("The inspectors closed our sheds. Something about the hens being miserable.", 1),
        p("Ours get petted every day."),
        k("Ridiculous. Fine. Vexmoor will dam the river for our water. Your fish will be gone by autumn."),
        n("Chapter 5: The River. Catch 15 fish to show the village what the river feeds, and win another week."),
    ),
    act2("act2_6",
        n("The whole village signs a petition, with your fish count on top. The dam plans are dropped."),
        k("You've turned the whole valley against us."),
        p("It was never for you."),
        k("One last contest, {name}. Win, and Vexmoor leaves. Lose, and you sell."),
        n("Chapter 6: The Festival. Make the farm beautiful (50 beauty) for the Valley Festival, and win the final week."),
    ),
    act2("act2_finale",
        n("The Valley Festival! Lanterns, music, and every stall full of the valley's food."),
        k("I've been ordered home. Vexmoor is pulling out of the valley.", 1),
        k("For what it's worth, {name}... this is the best food I've eaten in years."),
        p("There's always a seat at the festival for you, Ms. Kessler."),
        n("The village unveils a golden statue on the village road: a farmer holding a wheat sheaf."),
        n("Vexmoor sells its fields back to the valley for a song. The Pruitts' old farm will be planted again in spring. Grandpa would have danced."),
        n("The valley is saved! +25000 coins."),
    ),
).associateBy { it.id }
