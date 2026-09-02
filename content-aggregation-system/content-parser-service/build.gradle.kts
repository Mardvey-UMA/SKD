import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val javaVersion: String by project
val postgresqlVersion: String by project
val apacheHttpClient5Version: String by project
val protobufVersion: String by project
val caffeineVersion: String by project
val testcontainersVersion = "1.21.4"

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
        mavenBom("org.testcontainers:testcontainers-bom:${testcontainersVersion}")
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
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

sourceSets {
    main {
        kotlin { srcDirs("src/main/kotlin") }
        java { srcDirs("build/generated/source/proto/main/java") }
        proto { srcDir("src/main/proto") }
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://mvn.mchv.eu/repository/mchv/") }
}

configurations.all {
    exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.liquibase:liquibase-core:4.25.1")

    // Observability (Spring Boot 3.x)
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Resilience4j for circuit breaker, rate limiting, retry
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")

    // AWS SDK for S3 (S3-compatible storage: SeaweedFS, AWS, etc.)
    implementation(platform("software.amazon.awssdk:bom:2.31.1"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:apache-client")

    // HTTP Clients
    implementation("org.apache.httpcomponents.client5:httpclient5:${apacheHttpClient5Version}")

    // Protobuf
    implementation("com.google.protobuf:protobuf-java:${protobufVersion}")
    implementation("io.confluent:kafka-protobuf-serializer:7.5.1") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "com.google.protobuf", module = "protobuf-java-util")
    }

    // Database
    implementation("org.postgresql:postgresql:${postgresqlVersion}")

    // Utilities
    implementation("com.github.ben-manes.caffeine:caffeine:${caffeineVersion}")

    // TDLight (Telegram)
    implementation(platform("it.tdlight:tdlight-java-bom:3.4.4+td.1.8.52"))
    implementation("it.tdlight:tdlight-java")
    runtimeOnly("it.tdlight:tdlight-natives") {
        artifact {
            classifier = "linux_amd64_gnu_ssl3"
        }
    }

    // HTML sanitization
    implementation("org.jsoup:jsoup:1.18.1")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.13")
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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Copy> {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
}

tasks.withType<Test> {
    useJUnitPlatform()
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
                    // Full proto, not lite, needed for Kafka serialization (Message interface)
                }
            }
        }
    }
}
