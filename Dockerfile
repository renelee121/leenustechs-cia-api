FROM eclipse-temurin:25-jre-noble
WORKDIR /app
RUN groupadd --system --gid 10001 appuser \
    && useradd --system --uid 10001 --gid 10001 --no-create-home appuser
# Only JAR tested by Maven and approved by SonarQube is provided to BuildKit.
COPY --chown=10001:10001 target/cia-api.jar /app/cia-api.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/cia-api.jar"]
