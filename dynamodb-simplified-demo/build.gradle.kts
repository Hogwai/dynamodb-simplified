plugins {
    id("io.micronaut.minimal.application") // version from settings.gradle.kts pluginManagement
    id("com.gradleup.shadow") // version from settings.gradle.kts pluginManagement
    id("io.micronaut.aot") // version from settings.gradle.kts pluginManagement
}

description = "Demo application for DynamoDB Simplified"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":dynamodb-simplified-core"))
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.aws:micronaut-aws-sdk-v2")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("software.amazon.awssdk:dynamodb-enhanced")
    implementation("software.amazon.awssdk:dynamodb")
    implementation("net.datafaker:datafaker:${project.findProperty("versionDatafaker")}")

    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")

    compileOnly("org.projectlombok:lombok")
    runtimeOnly("ch.qos.logback:logback-classic")
}

application {
    mainClass = "dev.hogwai.Application"
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("dev.hogwai.*")
    }
    aot {
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}

