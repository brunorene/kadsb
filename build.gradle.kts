import org.jetbrains.kotlin.gradle.dsl.Coroutines.ENABLE

plugins {
    application
    kotlin("jvm") version "1.2.21"
}

application {
    mainClassName = "MainKt"
}

kotlin {
    experimental.coroutines = ENABLE
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    compile(kotlin("stdlib"))
    compile("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "0.22.1")
    compile("org.mapdb", "mapdb", "3.0.5")
    compile("org.opensky-network", "libadsb", "2.1")
}