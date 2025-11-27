import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("fabric-loom") version libs.versions.loom
    id("maven-publish")
    id("com.gradleup.shadow") version libs.versions.shadow
}

loom {
    accessWidenerPath.set(project(":common").file("src/main/resources/dreamdisplays.accesswidener"))
}

dependencies {
    minecraft(libs.fabricMinecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment(rootProject.property("neoForge.parchment.parchmentArtifact")!!)
    })
    modCompileOnly(libs.fabricLoader)
    modCompileOnly(libs.fabricApi)
    modCompileOnly(libs.fabricLanguageKotlin)

    shadow(project(":common"))
    compileOnly(kotlin("stdlib-jdk8"))
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
        exclude(dependency("org.jspecify:jspecify"))
    }
}