plugins {
    kotlin("jvm") version "2.0.20"
    id("com.github.johnrengelman.shadow") version "8.0.0"
}

group = "org.poach3r"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.jwharm.javagi:gtk:0.11.0")
    implementation("io.github.jwharm.javagi:gtksourceview:0.11.0")
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(22)
}

tasks.shadowJar {
    archiveBaseName.set("yatve")
    archiveVersion.set("1.0.0")
    manifest {
        attributes["Main-Class"] = "org.poach3r.MainKt"
    }
}

tasks.build {
    dependsOn(shadow)
}