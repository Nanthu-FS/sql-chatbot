// Standalone JVM build that compile-checks the app and runs its unit + screenshot
// tests without the Android SDK (used where Google's Maven/SDK hosts are blocked).
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "jvm-check"
