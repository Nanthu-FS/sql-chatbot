package app.monoworkspace.desktop

object AppInfo {
    const val NAME = "Mono Workspace"
    /** Set by the packaged launcher; "dev" when run from the IDE or Gradle. */
    val VERSION: String = System.getProperty("mono.version") ?: "dev"
    const val SHORTCUTS =
        "Ctrl+P command palette · Ctrl+K link · Ctrl+N new page · Ctrl+Shift+N create menu · Ctrl+Shift+F search · " +
            "Ctrl+Shift+H home · Ctrl+\\\\ sidebar · Ctrl+, settings · Ctrl+L lock · Alt+← / Alt+→ back and forward · " +
            "F11 full screen · Esc closes panels"
}
