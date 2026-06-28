plugins {
    id("io.micronaut.application") version "4.6.2" apply false
    id("com.gradleup.shadow") version "8.3.11" apply false
    id("io.micronaut.aot") version "4.6.1" apply false
}

val libraryVersion = "0.1.0"

group = "dev.hogwai"
version = libraryVersion

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}
