package com.genshincalc.core.effects

import com.genshincalc.core.calc.Effect
import com.genshincalc.core.calc.EffectProvider
import com.genshincalc.core.calc.EffectSource

/** All modelled character kits, weapon passives, artifact sets and resonances. */
object GameEffects : EffectProvider {
    private val characters: Map<String, List<Effect>> by lazy {
        EffectTable("char", EffectSource.CHARACTER).apply {
            mondstadtKits()
            liyueKits()
            inazumaKits()
            sumeruKits()
            fontaineKits()
            natlanKits()
            nodKraiKits()
        }.build()
    }

    private val weapons: Map<String, List<Effect>> by lazy {
        EffectTable("weapon", EffectSource.WEAPON).apply {
            swordEffects()
            claymoreEffects()
            polearmEffects()
            bowEffects()
            catalystEffects()
        }.build()
    }

    private val sets: Map<String, List<Effect>> by lazy {
        EffectTable("set", EffectSource.ARTIFACT).apply { artifactSetEffects() }.build()
    }

    private val team: List<Effect> by lazy { resonanceEffects() + moonsignEffects() }

    override fun characterEffects(characterId: String): List<Effect> = characters[characterId].orEmpty()
    override fun weaponEffects(weaponId: String): List<Effect> = weapons[weaponId].orEmpty()
    override fun setEffects(setId: String): List<Effect> = sets[setId].orEmpty()
    override fun teamEffects(): List<Effect> = team

    /** Ids with modelled effects, for the "supported" badges in the UI. */
    val modelledCharacters: Set<String> get() = characters.keys
    val modelledWeapons: Set<String> get() = weapons.keys
    val modelledSets: Set<String> get() = sets.keys
}
