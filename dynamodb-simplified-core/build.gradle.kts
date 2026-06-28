import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.jreleaser.model.Active

plugins {
    `java-library`
    `maven-publish`
    signing
    jacoco
    pmd
    checkstyle
    id("net.ltgt.errorprone") // version from settings.gradle.kts pluginManagement
    id("org.jreleaser")
}

description = "Fluent wrapper for AWS DynamoDB Enhanced Client"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    api(platform("software.amazon.awssdk:bom:${project.findProperty("versionAwsSdkBom")}"))
    api("software.amazon.awssdk:dynamodb")
    api("software.amazon.awssdk:dynamodb-enhanced")

    api("org.jspecify:jspecify:${project.findProperty("versionJspecify")}")

    implementation("org.slf4j:slf4j-api:${project.findProperty("versionSlf4j")}")

    // Static analysis: Error Prone
    errorprone("com.google.errorprone:error_prone_core:${project.findProperty("versionErrorProneCore")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${project.findProperty("versionJunitJupiter")}")
    testImplementation("org.assertj:assertj-core:${project.findProperty("versionAssertj")}")
    testImplementation("org.mockito:mockito-core:${project.findProperty("versionMockito")}")
    testImplementation("org.mockito:mockito-junit-jupiter:${project.findProperty("versionMockito")}")
    testImplementation(platform("org.testcontainers:testcontainers-bom:${project.findProperty("versionTestcontainersBom")}"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:${project.findProperty("versionJunitPlatformLauncher")}")
    testRuntimeOnly("ch.qos.logback:logback-classic:${project.findProperty("versionLogback")}")
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }

}

// ---- Static Analysis: Error Prone ----
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        check("UnusedException", CheckSeverity.WARN)
        disable(
            "StringSplitter",
            "FutureReturnValueIgnored",
            "UnusedMethod",
            "UnusedVariable",
            "InvalidLink",
            "TypeParameterUnusedInFormals",
            "JavaTimeDefaultTimeZone"
        )
    }
}

// ---- Static Analysis: PMD ----
pmd {
    toolVersion = "${project.findProperty("versionPmd")}"
    ruleSets = emptyList()
    ruleSetFiles = files("${rootProject.projectDir}/config/pmd/pmd-rules.xml")
    isConsoleOutput = true
}


// ---- Static Analysis: Checkstyle ----
checkstyle {
    toolVersion = "${project.findProperty("versionCheckstyle")}"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    maxErrors = 0
    maxWarnings = 100
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (DynamoDB Local via Testcontainers)"
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter("test")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
}

tasks.named("check") {
    dependsOn(integrationTest)
}

jacoco {
    toolVersion = "${project.findProperty("versionJacoco")}"
}

tasks.jacocoTestReport {
    dependsOn("test", integrationTest)
    executionData.setFrom(fileTree(buildDir) { include("jacoco/*.exec") })
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn("test", integrationTest)
    executionData.setFrom(fileTree(buildDir) { include("jacoco/*.exec") })
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "dynamodb-simplified-core"
            from(components["java"])

            pom {
                name = "DynamoDB Simplified"
                description = project.description
                url = "https://github.com/hogwai/dynamodb-simplified"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                developers {
                    developer {
                        id = "hogwai"
                        name = "Hogwai"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/hogwai/dynamodb-simplified.git"
                    developerConnection = "scm:git:ssh://github.com/hogwai/dynamodb-simplified.git"
                    url = "https://github.com/hogwai/dynamodb-simplified"
                }
            }
        }
    }

    repositories {
        maven {
            name = "stagingDeploy"
            url = uri(layout.buildDirectory.dir("staging-deploy").get().asFile.toURI())
        }
    }
}

signing {
    useInMemoryPgpKeys(
        System.getenv("SIGNING_KEY") ?: "",
        System.getenv("SIGNING_PASSWORD") ?: ""
    )
    sign(publishing.publications["mavenJava"])
}

jreleaser {
    gitRootSearch = true
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active.set(Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepositories.add("build/staging-deploy")
                    // Credentials from env vars at deploy time in CI:
                    //   JRELEASER_MAVENCENTRAL_SONATYPE_USERNAME
                    //   JRELEASER_MAVENCENTRAL_SONATYPE_PASSWORD
                    // Artifacts already signed by Gradle signing plugin; JReleaser just deploys
                    sign.set(false)
                    checksums.set(true)
                    sourceJar.set(true)
                    javadocJar.set(true)
                    verifyPom.set(true)
                }
            }
        }
    }
}

tasks.named("jreleaserDeploy") {
    dependsOn("publish")
}
