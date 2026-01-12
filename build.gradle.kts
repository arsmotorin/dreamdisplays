plugins {
    java
    kotlin("jvm") apply false
    // See: https://github.com/neoforged/NeoGradle/issues/19
    id("net.neoforged.moddev") version "2.0.137" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.15.0-alpha.25" apply false
    id("com.gradleup.shadow") version "9.3.0" apply false
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.3" apply false
}

subprojects {
    apply(plugin = "java")
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = Charsets.UTF_8.name()
    }
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.quiltmc.org/repository/release/")
        maven("https://maven.quiltmc.org/repository/snapshot/")
        maven("https://repo.l0sty.ru/releases")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://jitpack.io")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}

repositories {
    mavenCentral()
}
