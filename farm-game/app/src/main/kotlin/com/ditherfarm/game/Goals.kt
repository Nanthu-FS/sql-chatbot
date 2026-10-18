package com.ditherfarm.game

import org.json.JSONObject

// ------------------------------------------------------------------ definitions
class AchievementTier(val target: Long, val coins: Int, val xp: Int, val items: Map<String, Int>)
class AchievementDef(val id: String, val name: String, val desc: String, val metric: String, val tiers: List<AchievementTier>)
class PageDef(val id: String, val name: String, val entries: List<String>, val coins: Int, val bonus: String, val bonusText: String)

class GoalsData(val achievements: List<AchievementDef>, val pages: List<PageDef>) {
    companion object {
        val EMPTY = GoalsData(emptyList(), emptyList())

        fun parse(text: String): GoalsData {
            val o = JSONObject(text)
            val ach = o.getJSONArray("achievements").objects().map { a ->
                AchievementDef(
                    a.getString("id"), a.getString("name"), a.getString("desc"), a.getString("metric"),
                    a.getJSONArray("tiers").objects().map { t ->
                        AchievementTier(t.getLong("target"), t.optInt("coins"), t.optInt("xp"), t.optJSONObject("items")?.toIntMap() ?: emptyMap())
                    },
                )
            }
            val pages = o.getJSONArray("pages").objects().map { p ->
                PageDef(p.getString("id"), p.getString("name"), p.getJSONArray("entries").strings(), p.optInt("coins"), p.getString("bonus"), p.getString("bonusText"))
            }
            return GoalsData(ach, pages)
        }
    }
}

// ------------------------------------------------------------------ achievements
private val GEMS = listOf("amethyst", "emerald", "ruby", "diamond")

/** Machines that count for Industrialist (everything the player builds). */
private fun World.buildable() = data.buildings.values.filter { !it.prebuilt }

/** Current value of an achievement metric. */
fun World.metric(name: String): Long = when (name) {
    "crop_kinds" -> data.crops.keys.count { state.stat("harvest_$it") > 0 }.toLong()
    "gold_crops" -> state.stat("harvest_gold")
    "year" -> time.year.toLong()
    "out_season_sales" -> state.stat("out_season_sales")
    "rocks" -> state.stat("rocks_broken")
    "deepest" -> state.mine.deepestFloor.toLong()
    "gem_kinds" -> GEMS.count { it in state.discovered }.toLong()
    "plots" -> ownedPlots.toLong()
    "machines" -> buildable().count { isBuilt(it.id) }.toLong()
    "wizard" -> state.stat("wizard_sales")
    "hearts5" -> data.villagers.count { hearts(it.id) >= 5 }.toLong()
    "min_tool" -> Tool.entries.minOf { toolTier(it) }.toLong()
    "pages" -> state.pagesClaimed.size.toLong()
    "ancient_oaks" -> state.stat("fell_ancient_oak")
    else -> state.stat(name)
}

/** A tier's target, resolving 0 ("all of them"). */
fun World.target(a: AchievementDef, tier: Int): Long {
    val t = a.tiers[tier].target
    if (t > 0) return t
    return when (a.metric) {
        "machines" -> buildable().size.toLong()
        "hearts5" -> data.villagers.size.toLong()
        else -> 1
    }
}

/** Tiers already claimed for an achievement. */
fun World.claimedTiers(id: String) = state.achievements[id] ?: 0

/** True when the next unclaimed tier of [a] has been reached. */
fun World.claimable(a: AchievementDef): Boolean {
    val tier = claimedTiers(a.id)
    return tier < a.tiers.size && metric(a.metric) >= target(a, tier)
}

fun World.claimableCount(): Int =
    data.goals.achievements.count { claimable(it) } + data.goals.pages.count { pageClaimable(it) }

fun World.claimAchievement(id: String): Boolean {
    val a = data.goals.achievements.firstOrNull { it.id == id } ?: return false
    if (!claimable(a)) return false
    val tier = claimedTiers(id)
    val t = a.tiers[tier]
    state.achievements[id] = tier + 1
    state.coins += t.coins
    t.items.forEach { (k, v) -> add(k, v) }
    events += WorldEvent.AchievementClaimed(id, tier, t.coins)
    gainXp(t.xp)
    return true
}

/** Fires an event once for every achievement tier that has just been reached. */
internal fun World.checkAchievements() {
    for (a in data.goals.achievements) {
        if (!claimable(a)) continue
        val key = "${a.id}#${claimedTiers(a.id)}"
        if (state.achievementsSeen.add(key)) events += WorldEvent.AchievementReady(a.id)
    }
}

// ------------------------------------------------------------------ collection book
/** Item ids on a page, expanding #crops, #minerals and #villagers ("villager:<id>"). */
fun World.pageEntries(p: PageDef): List<String> = p.entries.flatMap { e ->
    when (e) {
        "#crops" -> data.crops.keys.toList()
        "#minerals" -> data.items.keys.filter { it.startsWith("mineral_") }.sorted()
        "#villagers" -> data.villagers.map { "villager:${it.id}" }
        else -> listOf(e)
    }
}

fun World.entryFound(entry: String): Boolean =
    if (entry.startsWith("villager:")) (state.friendship[entry.removePrefix("villager:")] ?: 0) > 0 else entry in state.discovered

fun World.pageProgress(p: PageDef): Pair<Int, Int> {
    val all = pageEntries(p)
    return all.count { entryFound(it) } to all.size
}

fun World.pageClaimable(p: PageDef): Boolean {
    if (p.id in state.pagesClaimed) return false
    val (have, all) = pageProgress(p)
    return have == all
}

fun World.claimPage(id: String): Boolean {
    val p = data.goals.pages.firstOrNull { it.id == id } ?: return false
    if (!pageClaimable(p)) return false
    state.pagesClaimed += id
    state.coins += p.coins
    events += WorldEvent.PageClaimed(id, p.coins)
    return true
}

/** Permanent bonus from a claimed collection page. */
fun World.hasBonus(bonus: String) = data.goals.pages.any { it.bonus == bonus && it.id in state.pagesClaimed }
