plugins {
    id("dreamdisplays.kotlin-conventions")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    api(project(":api"))
    api(project(":media:runtime"))
    api(project(":util"))
    api(libs.caffeine)
    api(project(":media:player"))
    api(libs.newpipeExtractor)
    api(libs.commonsCompress)
    api(libs.tukaaniXz)
    api(libs.kotlinxCoroutinesCore)
    compileOnly(libs.gson)
    compileOnly(libs.slf4jApi)
    compileOnly(libs.kotlinStdlib)
    testImplementation(libs.kotlinStdlib)
    testImplementation(libs.gson)
    testImplementation(libs.slf4jApi)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
