plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
}

group = "com.contentplatform"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.toVersion(property("javaVersion").toString())
    targetCompatibility = JavaVersion.toVersion(property("javaVersion").toString())
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot WebFlux (reactive)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Spring Security OAuth2 Resource Server (JWT validation)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Spring Data Redis Reactive
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Jackson Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Actuator + Prometheus metrics
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Structured JSON logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
        exclude(module = "mockito-junit-jupiter")
    }
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("io.mockk:mockk:${property("mockkVersion")}")
    testImplementation("com.ninja-squad:springmockk:${property("springmockkVersion")}")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework.security:spring-security-test")

    // Testcontainers
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")

    // WireMock
    testImplementation("org.wiremock:wiremock-standalone:${property("wiremockVersion")}")

    // JWT test utilities
    testImplementation("com.nimbusds:nimbus-jose-jwt:${property("nimbusJoseJwtVersion")}")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(property("javaVersion").toString()))
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=warn"))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("api.version", "1.43")
}
