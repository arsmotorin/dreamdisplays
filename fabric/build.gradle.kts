import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("fabric-loom") version libs.versions.loom
    id("maven-publish")
    id("com.gradleup.shadow") version libs.versions.shadow
    kotlin("jvm")
}

loom {
    accessWidenerPath.set(project(":common").file("src/main/resources/dreamdisplays.accesswidener"))
}

dependencies {
    minecraft(libs.fabricMinecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment(rootProject.property("neoForge.parchment.parchmentArtifact"))
    })
    modImplementation(libs.fabricLoader)
    modImplementation(libs.fabricApi)
    modImplementation(libs.fabricLanguageKotlin)
    include(libs.fabricLanguageKotlin)
    shadow(project(":common"))

    implementation(kotlin("stdlib-jdk8"))
}

tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("version", projectVersion)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to projectVersion))
    }
    filesMatching("quilt.mod.json") {
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

tasks.withType<RemapJarTask>().configureEach {
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })

    archiveClassifier = ""
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))

    archiveBaseName = "dreamdisplays-fabric"
    archiveVersion.set(rootProject.version.toString())
}

tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadow"))
    dependencies {
        include(project(":common"))

        // Kotlin stdlib is provided by Fabric Language Kotlin mod, don't include it here

        // JavaCV and FFmpeg
        include(dependency("org.bytedeco:javacv"))
        include(dependency("org.bytedeco:javacpp"))
        include(dependency("org.bytedeco:ffmpeg"))

        // Platform-specific natives
        include(dependency("org.bytedeco:javacpp:.*:macosx-arm64"))
        include(dependency("org.bytedeco:javacpp:.*:macosx-x86_64"))
        include(dependency("org.bytedeco:javacpp:.*:windows-x86_64"))
        include(dependency("org.bytedeco:javacpp:.*:linux-x86_64"))

        include(dependency("org.bytedeco:ffmpeg:.*:macosx-arm64"))
        include(dependency("org.bytedeco:ffmpeg:.*:macosx-x86_64"))
        include(dependency("org.bytedeco:ffmpeg:.*:windows-x86_64"))
        include(dependency("org.bytedeco:ffmpeg:.*:linux-x86_64"))

        include(dependency("com.github.felipeucelli:javatube"))
        include(dependency("org.json:json"))
        include(dependency("me.inotsleep:utils"))
    }
}
repositories {
    mavenCentral()
}