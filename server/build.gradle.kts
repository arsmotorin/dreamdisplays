plugins {
    java
    id("com.gradleup.shadow") version libs.versions.shadow
    kotlin("jvm")
    id("io.papermc.paperweight.userdev") version libs.versions.paperweight
}

dependencies {
    compileOnly(libs.ofratAnnotations)
    "kotlinCompilerPluginClasspath"(libs.ofratPlugin)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll(
        "-P", "plugin:io.github.arsmotorin.ofrat:platform=paper"
    )
}

dependencies {
    paperweight.devBundle("io.papermc.paper", libs.versions.paperApi.get())
    compileOnly(libs.jspecify)
    compileOnly(project(":common"))
    compileOnly(libs.fabricLoader)
    compileOnly(libs.fabricApi)

    implementation(libs.utils)
    implementation(libs.semver)
    implementation(libs.tomlj)
    implementation(libs.kotlinStdlib)
    implementation(libs.bstats)
}

val javaVersion = providers.gradleProperty("java.version").get().toInt()
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(javaVersion)
}

tasks.processResources {
    val projectVersion = version.toString()
    val props = mapOf("version" to projectVersion)
    inputs.properties(props)
    filteringCharset = Charsets.UTF_8.name()
    filesMatching("paper-plugin.yml") {
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
    archiveBaseName.set("dreamdisplays-paper")
    archiveVersion.set(rootProject.version.toString())
    manifest {
        attributes(
            "paperweight-mappings-namespace" to "mojang",
        )
    }
    val prefix = "com.dreamdisplays.libs"
    listOf(
        "me.inotsleep.utils",
        "org.bstats",
        "org.tomlj",
        "com.github.zafarkhaja.semver",
    ).forEach { pack ->
        relocate(pack, "$prefix.$pack")
    }
}
