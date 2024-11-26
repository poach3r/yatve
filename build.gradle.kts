plugins {
    kotlin("jvm") version "2.0.20"
    id("com.github.johnrengelman.shadow") version "8.0.0"
    id("org.graalvm.buildtools.native") version "0.10.3"
}

group = "org.poach3r"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencies {
    implementation("io.github.jwharm.javagi:gtk:0.11.1-SNAPSHOT")
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(22)
}

tasks {
    shadowJar {
        archiveBaseName.set("yatve")
        archiveVersion.set("1.0.0")
        manifest {
            attributes["Main-Class"] = "org.poach3r.MainKt"
        }
    }

    graalvmNative {
        //toolchainDetection.set(true)

        binaries {
            named("main") {
                imageName.set("yatve")
                mainClass.set("org.poach3r.MainKt")
                debug.set(true)
                verbose.set(true)
                buildArgs.add("-O4")
            }
        }
    }
}
