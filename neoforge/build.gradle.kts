plugins {
    id("net.neoforged.moddev")
    id("maven-publish")
    id("com.gradleup.shadow")
    kotlin("jvm")
}

dependencies {
    implementation(project(":common"))
    shadow(project(":common"))
    shadow(libs.newpipeExtractor)
}

neoForge {
    enable {
        version = libs.versions.neoforge.get()
    }
    accessTransformers.from(file("src/main/resources/META-INF/accesstransformer.cfg"))
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

    val prefix = "com.dreamdisplays.libs"
    listOf(
        "me.inotsleep.utils",
        "org.freedesktop.gstreamer",
        "org.json",
        "org.jsoup",
        "org.mozilla",
        "kotlin",
        "org.jetbrains.annotations",
        "org.schabi.newpipe"
    ).forEach { pack ->
        relocate(pack, "$prefix.$pack")
    }
}
