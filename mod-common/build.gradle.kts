plugins {
    id("net.neoforged.moddev") version libs.versions.moddev
    kotlin("jvm")
}

dependencies {
    val javacppVersion = "1.5.12"
    val ffmpegVersion = "7.1.1-$javacppVersion"

    api(libs.javacv)
    api(libs.javacpp)
    api(libs.ffmpeg)

    // Platform-specific FFmpeg natives
    api("org.bytedeco:ffmpeg:$ffmpegVersion:macosx-arm64")
    api("org.bytedeco:ffmpeg:$ffmpegVersion:macosx-x86_64")
    api("org.bytedeco:ffmpeg:$ffmpegVersion:windows-x86_64")
    api("org.bytedeco:ffmpeg:$ffmpegVersion:linux-x86_64")

    api(libs.utils)
    api(libs.javatube)
    api(libs.jspecify)
    implementation(libs.jna)
    implementation(libs.jnaPlatform)
    implementation(libs.lwjgl)
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
