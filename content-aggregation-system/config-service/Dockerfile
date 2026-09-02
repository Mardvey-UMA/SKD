# --- Stage 1: Build ---
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

RUN apk add --no-cache bash

COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src/main/proto/ src/main/proto/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# --- Stage 2: Create minimal JRE via jlink ---
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.ec,jdk.management,jdk.net,jdk.unsupported,jdk.zipfs \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress zip-6 \
    --output /custom-jre

# --- Stage 3: Minimal runtime ---
FROM alpine:3.21
WORKDIR /app

RUN apk add --no-cache gcompat libstdc++ wget

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /custom-jre /opt/jre
COPY --from=builder /build/build/libs/*.jar app.jar

ENV JAVA_HOME=/opt/jre
ENV PATH="$JAVA_HOME/bin:$PATH"

RUN chown -R spring:spring /app
USER spring:spring

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
