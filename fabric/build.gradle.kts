import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("net.fabricmc.fabric-loom-remap")
    id("maven-publish")
    id("com.gradleup.shadow")
    kotlin("jvm")
}

loom {
    accessWidenerPath.set(project(":common").file("src/main/resources/dreamdisplays.classtweaker"))
}

dependencies {
    minecraft(libs.fabricMinecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment(rootProject.property("neoForge.parchment.parchmentArtifact")!!)
    })
    modImplementation(libs.fabricLoader)
    modImplementation(libs.fabricApi)
    shadow(project(":common"))
    shadow(libs.newpipeExtractor)
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
    configurations = listOf(project.configurations.shadow.get())
    val prefix = "com.dreamdisplays.libs"
    listOf(
        "me.inotsleep.utils",
        "org.freedesktop.gstreamer",
        "org.json",
        "kotlin",
        "org.jetbrains.annotations",
        "org.schabi.newpipe"
    ).forEach { pack ->
        relocate(pack, "$prefix.$pack")
    }
    exclude("**/*.so")
    exclude("**/*.dll")
    exclude("**/*.dylib")
    exclude("**/*.a")
    exclude("**/*.lib")
    exclude("org/bytedeco/**/*.properties")
    exclude("org/bytedeco/**/linux/**")
    exclude("org/bytedeco/**/windows/**")
    exclude("org/bytedeco/**/macosx/**")
    exclude("org/bytedeco/**/android/**")
}
