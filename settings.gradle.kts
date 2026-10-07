rootProject.name = "dynamodb-simplified"

pluginManagement {
    val props = java.util.Properties()
    props.load(java.io.FileInputStream("gradle.properties"))
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
    plugins {
        id("net.ltgt.errorprone") version props.getProperty("versionErrorpronePlugin")
        id("org.jreleaser") version props.getProperty("versionJreleaser")
    }
}
