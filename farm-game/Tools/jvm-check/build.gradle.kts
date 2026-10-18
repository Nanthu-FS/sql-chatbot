plugins { kotlin("jvm") version "2.1.0" }

val app = rootDir.resolve("../../app/src")
val androidAll = "org.robolectric:android-all:15-robolectric-12650502"

sourceSets {
    main {
        kotlin.srcDir(app.resolve("main/kotlin"))
        kotlin.srcDir("stubs/kotlin") // stand-in for the aapt-generated R class
    }
    test {
        kotlin.srcDir(app.resolve("test/kotlin"))
        java.srcDir("stubs/java") // minimal androidx.test:monitor (Google Maven is unreachable)
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    compileOnly(androidAll)
    testCompileOnly(androidAll)
    testRuntimeOnly(androidAll)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1") {
        exclude(group = "androidx.test")
        exclude(group = "androidx.test.espresso")
    }
}

tasks.test {
    maxHeapSize = "2g"
    inputs.dir(app.resolve("main/assets"))
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
    // -Passets=<dir> renders the shots with another asset folder (art previews before they land).
    systemProperty("assets.dir", (project.findProperty("assets") as String?) ?: app.resolve("main/assets").absolutePath)
    // gradle test -Pshots writes 3x PNGs of each rendered screen to build/shots.
    if (project.hasProperty("shots")) systemProperty("shots.out", (project.findProperty("shotsOut") as String?) ?: layout.buildDirectory.dir("shots").get().asFile.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
