plugins {
    id("net.neoforged.moddev") version libs.versions.moddev
}

dependencies {
    val javacppVersion = libs.versions.javacv.get()
    val ffmpegVersion = "${libs.versions.ffmpeg.get()}-$javacppVersion"

    api(libs.javacv)
    api(libs.javacpp)
    api(libs.ffmpeg)
    api(libs.utils)
    api(libs.javatube)
    api(libs.jspecify)

    // Platform-specific FFmpeg natives
    api("org.bytedeco:ffmpeg:$ffmpegVersion:macosx-arm64")
    api("org.bytedeco:ffmpeg:$ffmpegVersion:macosx-x86_64")
    api("org.bytedeco:ffmpeg:$ffmpegVersion:windows-x86_64")
    api("org.bytedeco:ffmpeg:$ffmpegVersion:linux-x86_64")

    compileOnly(kotlin("stdlib-jdk8"))
}

neoForge {
    enable {
        version = libs.versions.neoforge.get()
    }
}
