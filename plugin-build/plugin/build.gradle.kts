plugins {
    kotlin("jvm") version "1.7.0" apply false
    `java-gradle-plugin`
    id("com.github.ben-manes.versions") version "0.42.0"
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib-jdk8"))
    implementation("net.fabricmc:tiny-remapper:0.8.1")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("me.lucko:jar-relocator:1.5") {
        exclude("org.ow2.asm")
    }

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

gradlePlugin {
    plugins {
        create("com.mohistmc.mohistkite") {
            id = "com.mohistmc.mohistkite"
            implementationClass = "com.mohistmc.mohistkite.gradle.Plugin"
            version = "0.1"
        }
    }
}

tasks.create("setupPluginUploadFromEnvironment") {
    doLast {
        val key = System.getenv("GRADLE_PUBLISH_KEY")
        val secret = System.getenv("GRADLE_PUBLISH_SECRET")

        if (key == null || secret == null) {
            throw GradleException("gradlePublishKey and/or gradlePublishSecret are not defined environment variables")
        }

        System.setProperty("gradle.publish.key", key)
        System.setProperty("gradle.publish.secret", secret)
    }
}
