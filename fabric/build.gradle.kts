import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("fabric-loom") version libs.versions.loom
	id("maven-publish")
	id("com.gradleup.shadow") version libs.versions.shadow
}

dependencies {
	minecraft(libs.fabricMinecraft)
	mappings(loom.layered {
        officialMojangMappings()
        parchment(rootProject.property("neoForge.parchment.parchmentArtifact"))
    })
	modImplementation(libs.fabricLoader)
	modImplementation(libs.fabricApi)
    shadow(project(":mod-common"))
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
        include(project(":mod-common"))
        include(dependency("org.freedesktop.gstreamer:gst1-java-core"))
        include(dependency("com.github.felipeucelli:javatube"))
        include(dependency("org.json:json"))
        include(dependency("me.inotsleep:utils"))
    }
}
