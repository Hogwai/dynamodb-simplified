rootProject.name = "dynamodb-simplified"

include(
    "dynamodb-simplified-core",
    "dynamodb-simplified-demo"
)

pluginManagement {
    val props = java.util.Properties()
    props.load(java.io.FileInputStream("gradle.properties"))
    plugins {
        id("net.ltgt.errorprone") version props.getProperty("versionErrorpronePlugin")
        id("io.micronaut.application") version props.getProperty("versionMicronautPlugins")
        id("io.micronaut.minimal.application") version props.getProperty("versionMicronautPlugins")
        id("com.gradleup.shadow") version props.getProperty("versionShadow")
        id("io.micronaut.aot") version props.getProperty("versionMicronautPlugins")
    }
}
