val javaVersion: String by project
val postgresqlVersion: String by project
val caffeineVersion: String by project
val springmockkVersion: String by project
val protobufVersion: String by project
val testcontainersVersion = "1.20.4"

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.4"
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.5")
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

group = "com.contentagg"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
    maven { url = uri("https://jitpack.io") }
}

configurations.all {
    exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.liquibase:liquibase-core:4.25.1")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Observability (Spring Boot 3.x)
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Protobuf
    implementation("com.google.protobuf:protobuf-java:${protobufVersion}")
    implementation("io.confluent:kafka-protobuf-serializer:7.5.1") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "com.google.protobuf", module = "protobuf-java-util")
    }

    // Utilities
    implementation("com.github.ben-manes.caffeine:caffeine:${caffeineVersion}")

    // Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Database
    implementation("org.postgresql:postgresql:${postgresqlVersion}")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    // Testcontainers
    testImplementation("org.testcontainers:junit-jupiter:${testcontainersVersion}")
    testImplementation("org.testcontainers:postgresql:${testcontainersVersion}")
    testImplementation("org.testcontainers:mockserver:${testcontainersVersion}")
    testImplementation("org.testcontainers:kafka:${testcontainersVersion}")
    testImplementation("org.testcontainers:localstack:${testcontainersVersion}")

    testImplementation("org.mock-server:mockserver-netty:5.15.0") {
        exclude(group = "org.slf4j", module = "slf4j-ext")
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "ch.qos.logback")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
}

tasks.named<Jar>("jar") {
    enabled = false
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${protobufVersion}"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                }
            }
        }
    }
}
