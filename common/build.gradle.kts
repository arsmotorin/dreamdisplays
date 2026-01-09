plugins {
    id("net.neoforged.moddev")
    kotlin("jvm")
}

dependencies {
    api(libs.gst1)
    implementation(libs.javacpp)
    implementation(libs.ffmpeg)
    implementation(libs.javacv)
    api(libs.utils)
    api(libs.newpipeExtractor)
    api(libs.jspecify)
    implementation(kotlin("stdlib-jdk8"))
}

neoForge {
    enable {
        version = libs.versions.neoforge.get()
    }
}
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
}

tasks.jar {
    from(rootProject.file("LICENSE"))
}

repositories {
    mavenCentral()
}
