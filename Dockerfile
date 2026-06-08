# syntax=docker/dockerfile:1.7
# Build multi-stage du BFF caisse (Spring Boot 4, Gradle).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Couche dépendances (cache) : wrapper + scripts de build d'abord
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Code source + build du jar (sans tests)
COPY . .
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --create-home app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app
EXPOSE 8081
USER app
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
