import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    kotlin("jvm") version "1.7.0" apply false
    id("com.gradle.plugin-publish") version "1.0.0"
    id("com.github.ben-manes.versions") version "0.42.0"
}

allprojects {
    group = "com.mohistmc.mohistkite"
    version = "0.1"
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}

fun isNonStable(version: String) = "^[0-9,.v-]+(-r)?$".toRegex().matches(version).not()
