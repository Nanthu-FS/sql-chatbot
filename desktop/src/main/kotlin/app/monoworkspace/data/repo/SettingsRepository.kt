package app.monoworkspace.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
data class AppSettings(
    val workspaceName: String = DEFAULT_WORKSPACE_NAME,
    val appLock: Boolean = false,
    val pinSalt: String? = null,
    val pinHash: String? = null,
    val lastOpenedPageId: String? = null,
    val sidebarWidthDp: Int = 280,
    val sidebarCollapsed: Boolean = false,
    val seeded: Boolean = false,
    val windowWidth: Int = 1360,
    val windowHeight: Int = 860,
    val windowMaximized: Boolean = false,
    val reduceMotion: Boolean = false,
    /** "system" follows Windows' app mode; "light" or "dark" pins one. */
    val themeMode: String = "system",
    val lightTheme: String = "mono",
    val darkTheme: String = "observatory",
    val ambientEffects: Boolean = true,
) {
    companion object {
        const val DEFAULT_WORKSPACE_NAME = "My Workspace"
    }
}

/** Settings live next to the workspace in settings.json; same API as the Android DataStore version. */
class SettingsRepository(private val dir: File) {
    private val file = File(dir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(load())

    val settings: Flow<AppSettings> = state.asStateFlow()

    private fun load(): AppSettings = runCatching {
        if (file.exists()) json.decodeFromString(AppSettings.serializer(), file.readText()) else AppSettings()
    }.getOrDefault(AppSettings()).let { if (it.workspaceName.isBlank()) it.copy(workspaceName = AppSettings.DEFAULT_WORKSPACE_NAME) else it }

    fun now(): AppSettings = state.value
    suspend fun current(): AppSettings = state.value

    private suspend fun edit(f: (AppSettings) -> AppSettings) {
        state.update(f)
        withContext(Dispatchers.IO) { persist() }
    }

    @Synchronized
    private fun persist() {
        dir.mkdirs()
        val tmp = File(dir, "settings.json.tmp")
        tmp.writeText(json.encodeToString(AppSettings.serializer(), state.value))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    suspend fun setWorkspaceName(name: String) = edit { it.copy(workspaceName = name.trim().take(80).ifBlank { AppSettings.DEFAULT_WORKSPACE_NAME }) }
    suspend fun setSidebarWidth(dp: Int) = edit { it.copy(sidebarWidthDp = dp.coerceIn(220, 420)) }
    suspend fun setSidebarCollapsed(collapsed: Boolean) = edit { it.copy(sidebarCollapsed = collapsed) }
    suspend fun setSeeded(seeded: Boolean) = edit { it.copy(seeded = seeded) }
    suspend fun setReduceMotion(on: Boolean) = edit { it.copy(reduceMotion = on) }
    suspend fun setThemeMode(mode: String) = edit { it.copy(themeMode = mode) }
    suspend fun setAmbientEffects(on: Boolean) = edit { it.copy(ambientEffects = on) }

    /** Picks a theme for its kind; pinning the matching mode unless following Windows. */
    suspend fun chooseTheme(id: String, dark: Boolean) = edit {
        val pinned = when {
            it.themeMode == "system" -> "system"
            dark -> "dark"
            else -> "light"
        }
        if (dark) it.copy(darkTheme = id, themeMode = pinned) else it.copy(lightTheme = id, themeMode = pinned)
    }
    suspend fun setLastOpened(pageId: String?) = edit { it.copy(lastOpenedPageId = pageId) }
    suspend fun setWindow(width: Int, height: Int, maximized: Boolean) = edit {
        it.copy(windowWidth = width.coerceAtLeast(720), windowHeight = height.coerceAtLeast(520), windowMaximized = maximized)
    }

    /** App lock on desktop is a PIN (4–12 digits), stored as a salted SHA-256 hash. */
    suspend fun enableLock(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        edit { it.copy(appLock = true, pinSalt = salt, pinHash = hash(salt, pin)) }
    }

    suspend fun disableLock() = edit { it.copy(appLock = false, pinSalt = null, pinHash = null) }

    fun checkPin(pin: String): Boolean {
        val s = state.value
        val salt = s.pinSalt ?: return false
        return MessageDigest.isEqual(hash(salt, pin).toByteArray(), (s.pinHash ?: "").toByteArray())
    }

    private fun hash(salt: String, pin: String): String {
        var bytes = (salt + ":" + pin).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        repeat(20_000) { bytes = md.digest(bytes) }
        return bytes.toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
