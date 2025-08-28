// build.gradle.kts (Project-level)
plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("com.android.application") version "8.7.0" apply false
    id("com.android.library") version "8.7.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}