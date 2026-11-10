# syntax=docker/dockerfile:1
# El pom.xml requiere JDK 21 para compilar.
FROM maven:3.10.0-eclipse-temurin-21-noble AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -Dmaven.test.skip=true

# El bytecode Java 21 puede ejecutarse sobre Temurin 25.
FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app
RUN groupadd --system --gid 10001 appuser \
    && useradd --system --uid 10001 --gid 10001 --no-create-home appuser
COPY --from=build --chown=10001:10001 /workspace/target/cia-api.jar /app/cia-api.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/cia-api.jar"]
