plugins {
    id("org.jreleaser") version "1.24.0" apply false
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
