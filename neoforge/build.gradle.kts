plugins {
    id("net.neoforged.moddev") version libs.versions.moddev
    id("maven-publish")
    id("com.gradleup.shadow") version libs.versions.shadow
}

dependencies {
    shadow(project(":mod-common"))
}

neoForge {
    enable {
        version = libs.versions.neoforge.get()
    }
    runs {
        register("neoClient") {
            client()
        }
    }
}

tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("version", projectVersion)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(mapOf("version" to projectVersion))
    }
    filesMatching("version") {
        expand(mapOf("version" to projectVersion))
    }
}

java {
    withSourcesJar()
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
}

tasks.jar {
    from(rootProject.file("LICENSE"))
}

tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadow"))
    archiveBaseName = "dreamdisplays-neoforge"
    archiveClassifier = ""
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
    dependencies {
        include(project(":mod-common"))
        include(dependency("org.freedesktop.gstreamer:gst1-java-core"))
        include(dependency("com.github.felipeucelli:javatube"))
        include(dependency("org.json:json"))
        include(dependency("me.inotsleep:utils"))
    }
}
