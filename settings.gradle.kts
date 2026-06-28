rootProject.name = "dynamodb-simplified"

pluginManagement {
    val props = java.util.Properties()
    props.load(java.io.FileInputStream("gradle.properties"))
    plugins {
        id("net.ltgt.errorprone") version props.getProperty("versionErrorpronePlugin")
        id("org.jreleaser") version props.getProperty("versionJreleaser")
    }
}
