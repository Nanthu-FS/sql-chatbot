// Plugins are put on the root classpath here (instead of `plugins { ... apply false }`) so that the
// Android Gradle Plugin is only resolved when the Android SDK - and therefore the :app module - is
// present. Subprojects apply the plugins by id.
buildscript {
    val versions = file("gradle/libs.versions.toml").readText()
    fun version(name: String) = Regex("""(?m)^$name\s*=\s*"([^"]+)"""").find(versions)!!.groupValues[1]
    val kotlinVersion = version("kotlin")
    val androidSdkAvailable = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        if (androidSdkAvailable) {
            classpath("com.android.tools.build:gradle:${version("agp")}")
        }
    }
}
