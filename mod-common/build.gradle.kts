plugins {
    id("net.neoforged.moddev") version libs.versions.moddev
}

dependencies {
    api(libs.gst1)
	api(libs.utils)
	api(libs.javatube)
    api(libs.jspecify)
    implementation(libs.jna)
    implementation(libs.jnaPlatform)
	implementation(libs.lwjgl)
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
