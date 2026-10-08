import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.compose") version "1.8.2"
}

group = "app.monoworkspace"

// Every installer needs its own version: Windows Installer derives the product
// code from it, so reinstalling the same version only opens an (empty)
// maintenance mode instead of upgrading. CI passes 1.1.<run number>.
val appVersion: String = System.getenv("MONO_VERSION")?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+""")) } ?: "1.0.0"
version = appVersion

// The engine, models and repositories are shared with the Android app: one
// source of truth for rich text, formulas, filters, export and data logic.
val androidSrc = file("../android/app/src/main/java/app/monoworkspace")
val syncShared by tasks.registering(Sync::class) {
    from(androidSrc) {
        include(
            "model/**",
            "core/**",
            "engine/**",
            "data/Mappers.kt",
            "data/repo/PageRepository.kt",
            "data/repo/BlockRepository.kt",
            "data/repo/DatabaseRepository.kt",
            "data/repo/DatabaseQuery.kt",
            "data/repo/SearchRepository.kt",
            "data/repo/ExportRepository.kt",
            "Seeder.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/shared/app/monoworkspace"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(syncShared)
        }
        test {
            kotlin.srcDir("../android/app/src/test/java")
        }
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
        optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.coroutines.FlowPreview")
    }
}

sourceSets {
    main {
        resources.srcDirs(
            "../android/app/src/main/assets",
            "../android/app/src/main/res/font",
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.animation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test {
    systemProperty("assetsDir", file("../android/app/src/main/assets").absolutePath)
}

compose.desktop {
    application {
        mainClass = "app.monoworkspace.desktop.MainKt"
        jvmArgs("-Dmono.version=$appVersion")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Mono Workspace"
            packageVersion = appVersion
            description = "Local-only blocks, pages and databases"
            vendor = "Mono Workspace"
            copyright = "Mono Workspace"
            modules("java.desktop", "java.prefs", "jdk.unsupported", "java.naming")
            windows {
                menu = true
                menuGroup = "Mono Workspace"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6d3f6a52-3b8e-4d4b-9a51-3c3f6d9c1e21"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

// Offscreen tour of the real UI written to build/screenshots (used by CI).
tasks.register<JavaExec>("renderScreenshots") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.monoworkspace.desktop.ScreenshotsKt")
    args(layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
}
