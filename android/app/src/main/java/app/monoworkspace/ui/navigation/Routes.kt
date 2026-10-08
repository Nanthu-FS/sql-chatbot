package app.monoworkspace.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class PageRoute(val pageId: String, val blockId: String? = null)

@Serializable
data class DatabaseRoute(val pageId: String)

@Serializable
data object SearchRoute

@Serializable
data object TrashRoute

@Serializable
data object SettingsRoute
