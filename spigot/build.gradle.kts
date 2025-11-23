plugins {
    java
    id("com.gradleup.shadow") version libs.versions.shadow
}

group = "dreamdisplays"

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:26.0.2")

    implementation("me.inotsleep:utils:1.3.4")
    implementation("com.github.zafarkhaja:java-semver:0.10.2")
    implementation("com.moandjiezana.toml:toml4j:0.7.2") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    compileOnly("com.google.code.gson:gson:2.13.2")
}

val targetJavaVersion = 21
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(targetJavaVersion)
}

tasks.processResources {
    val projectVersion = version.toString()
    val props = mapOf("version" to projectVersion)
    inputs.properties(props)
    filteringCharset = "UTF-8"

    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    archiveClassifier.set("")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
    archiveBaseName.set("dreamdisplays-spigot")
    archiveVersion.set(rootProject.version.toString())
    manifest {
        attributes(
            "paperweight-mappings-namespace" to "mojang",
        )
    }
}
