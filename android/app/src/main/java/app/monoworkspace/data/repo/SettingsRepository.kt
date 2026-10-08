package app.monoworkspace.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val workspaceName: String = DEFAULT_WORKSPACE_NAME,
    val appLock: Boolean = false,
    val lastOpenedPageId: String? = null,
    val sidebarWidthDp: Int = 280,
    val sidebarCollapsed: Boolean = false,
    val seeded: Boolean = false,
) {
    companion object {
        const val DEFAULT_WORKSPACE_NAME = "My Workspace"
    }
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val workspaceName = stringPreferencesKey("workspace_name")
        val appLock = booleanPreferencesKey("app_lock")
        val lastOpened = stringPreferencesKey("last_opened_page")
        val sidebarWidth = intPreferencesKey("sidebar_width")
        val sidebarCollapsed = booleanPreferencesKey("sidebar_collapsed")
        val seeded = booleanPreferencesKey("seeded")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            workspaceName = p[Keys.workspaceName]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_WORKSPACE_NAME,
            appLock = p[Keys.appLock] ?: false,
            lastOpenedPageId = p[Keys.lastOpened],
            sidebarWidthDp = p[Keys.sidebarWidth] ?: 280,
            sidebarCollapsed = p[Keys.sidebarCollapsed] ?: false,
            seeded = p[Keys.seeded] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setWorkspaceName(name: String) = store.edit { it[Keys.workspaceName] = name.trim().take(80) }
    suspend fun setAppLock(enabled: Boolean) = store.edit { it[Keys.appLock] = enabled }
    suspend fun setSidebarWidth(dp: Int) = store.edit { it[Keys.sidebarWidth] = dp.coerceIn(220, 420) }
    suspend fun setSidebarCollapsed(collapsed: Boolean) = store.edit { it[Keys.sidebarCollapsed] = collapsed }
    suspend fun setSeeded(seeded: Boolean) = store.edit { it[Keys.seeded] = seeded }

    suspend fun setLastOpened(pageId: String?) = store.edit {
        if (pageId == null) it.remove(Keys.lastOpened) else it[Keys.lastOpened] = pageId
    }
}
