import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    idea
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    kotlin.compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
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
        maven("https://prmaven.neoforged.net/NeoForge/pr2815") // TODO: remove me when 1.21.11 releases
        maven("https://maven.fabricmc.net/")
        maven("https://repo.l0sty.ru/releases")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://jitpack.io")
    }
}
