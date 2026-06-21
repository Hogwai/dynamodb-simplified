plugins {
    id("io.micronaut.application") version "4.6.1" apply false
    id("com.gradleup.shadow") version "8.3.9" apply false
    id("io.micronaut.aot") version "4.6.1" apply false
}

group = "com.hogwai"
version = "0.1"

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}
