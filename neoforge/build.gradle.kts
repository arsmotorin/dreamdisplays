plugins {
    id("net.neoforged.moddev")
    id("maven-publish")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":common"))
    shadow(project(":common"))
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
    dependencies {
        include(project(":common"))
        include(dependency("org.freedesktop.gstreamer:gst1-java-core"))
        include(dependency("com.github.felipeucelli:javatube"))
        include(dependency("org.json:json"))
        include(dependency("me.inotsleep:utils"))
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7"))
        include(dependency("org.jetbrains:annotations"))
    }
    val prefix = "com.dreamdisplays.libs"
    listOf(
        "com.github.felipeucelli.javatube",
        "me.inotsleep.utils",
        "org.freedesktop.gstreamer",
        "org.json",
        "kotlin",
        "org.jetbrains.annotations"
    ).forEach { pack ->
        relocate(pack, "$prefix.$pack")
    }
}
