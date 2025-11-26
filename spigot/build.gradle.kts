plugins {
    java
    id("com.gradleup.shadow") version libs.versions.shadow
    kotlin("jvm")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
    compileOnly(libs.jspecify)
    implementation("me.inotsleep:utils:1.3.4")
    implementation("com.github.zafarkhaja:java-semver:0.10.2")
    implementation("com.moandjiezana.toml:toml4j:0.7.2") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    compileOnly("com.google.code.gson:gson:2.13.2")
    implementation(kotlin("stdlib-jdk8:2.2.21"))
    implementation("org.bstats:bstats-bukkit:3.1.0")
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
    filteringCharset = Charsets.UTF_8.name()
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
    relocate("org.bstats", "com.dreamdisplays.bstats")
    manifest {
        attributes(
            "paperweight-mappings-namespace" to "mojang",
        )
    }
}
