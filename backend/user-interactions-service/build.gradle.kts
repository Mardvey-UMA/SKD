plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
}

group = "com.skd"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.toVersion(property("javaVersion").toString())
    targetCompatibility = JavaVersion.toVersion(property("javaVersion").toString())
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Quartz
    implementation("org.springframework.boot:spring-boot-starter-quartz")

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Structured JSON logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // PostgreSQL
    implementation("org.postgresql:postgresql")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    // Prometheus metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Liquibase (runtime for Spring Liquibase startup + test DB migrations)
    runtimeOnly("org.liquibase:liquibase-core")
    testImplementation("org.liquibase:liquibase-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // Docker Java (override for Docker 29+ API compatibility)
    testImplementation("com.github.docker-java:docker-java-api:${property("dockerJavaVersion")}")
    testImplementation("com.github.docker-java:docker-java-transport-zerodep:${property("dockerJavaVersion")}")

    // Testcontainers
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:kafka:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")

    // MockK
    testImplementation("io.mockk:mockk:${property("mockkVersion")}")
    testImplementation("com.ninja-squad:springmockk:${property("springmockkVersion")}")

    // AssertJ
    testImplementation("org.assertj:assertj-core")

    // WireMock
    testImplementation("org.wiremock:wiremock-standalone:${property("wiremockVersion")}")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(property("javaVersion").toString()))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("api.version", "1.43")
}
