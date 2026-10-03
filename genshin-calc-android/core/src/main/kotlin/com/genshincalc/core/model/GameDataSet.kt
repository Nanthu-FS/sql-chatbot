package com.genshincalc.core.model

import kotlinx.serialization.json.Json
import java.io.InputStream

/** All bundled game data. Load once with [GameDataSet.load] (a few hundred ms) and share it. */
class GameDataSet(
    val gameVersion: String,
    val characters: List<CharacterData>,
    val weapons: List<WeaponData>,
    val artifactSets: List<ArtifactSetData>,
    val enemies: List<EnemyData>,
    val curves: CurvesFile,
) {
    private val charactersById = characters.associateBy { it.id }
    private val weaponsById = weapons.associateBy { it.id }
    private val setsById = artifactSets.associateBy { it.id }
    private val enemiesById = enemies.associateBy { it.id }

    fun character(id: String): CharacterData =
        charactersById[id] ?: throw IllegalArgumentException("Unknown character '$id'")

    fun characterOrNull(id: String?): CharacterData? = id?.let { charactersById[it] }

    fun weapon(id: String): WeaponData = weaponsById[id] ?: throw IllegalArgumentException("Unknown weapon '$id'")

    fun weaponOrNull(id: String?): WeaponData? = id?.let { weaponsById[it] }

    fun artifactSet(id: String): ArtifactSetData =
        setsById[id] ?: throw IllegalArgumentException("Unknown artifact set '$id'")

    fun artifactSetOrNull(id: String?): ArtifactSetData? = id?.let { setsById[it] }

    fun enemyOrNull(id: String?): EnemyData? = id?.let { enemiesById[it] }

    fun weaponsOfType(type: WeaponType): List<WeaponData> = weapons.filter { it.type == type }

    fun characterCurve(name: String): List<Double> =
        curves.character[name] ?: throw IllegalArgumentException("Unknown character curve '$name'")

    fun weaponCurve(name: String): List<Double> =
        curves.weapon[name] ?: throw IllegalArgumentException("Unknown weapon curve '$name'")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var cached: GameDataSet? = null

        /** Loads the data bundled as Java resources in this module (works on Android and the JVM). */
        fun load(): GameDataSet = cached ?: synchronized(this) {
            cached ?: loadFrom { name ->
                GameDataSet::class.java.getResourceAsStream("/gamedata/$name")
                    ?: error("Missing resource gamedata/$name")
            }.also { cached = it }
        }

        fun loadFrom(open: (String) -> InputStream): GameDataSet {
            fun read(name: String) = open(name).use { it.readBytes().decodeToString() }
            val chars = json.decodeFromString<CharactersFile>(read("characters.json"))
            val weapons = json.decodeFromString<WeaponsFile>(read("weapons.json"))
            val artifacts = json.decodeFromString<ArtifactsFile>(read("artifacts.json"))
            val enemies = json.decodeFromString<EnemiesFile>(read("enemies.json"))
            val curves = json.decodeFromString<CurvesFile>(read("curves.json"))
            return GameDataSet(
                gameVersion = chars.gameVersion,
                characters = chars.characters.sortedBy { it.name },
                weapons = weapons.weapons.sortedWith(compareByDescending<WeaponData> { it.rarity }.thenBy { it.name }),
                artifactSets = artifacts.artifacts.sortedWith(
                    compareByDescending<ArtifactSetData> { it.maxRarity }.thenBy { it.name },
                ),
                enemies = enemies.enemies.sortedBy { it.name },
                curves = curves,
            )
        }
    }
}
