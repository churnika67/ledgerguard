FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY ledger-service/pom.xml ledger-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
RUN mvn -q -pl common,ledger-service -am dependency:go-offline

COPY common/src common/src
COPY ledger-service/src ledger-service/src
RUN mvn -q -pl common,ledger-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/ledger-service/target/ledger-service.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1
# JAVA_TOOL_OPTIONS (e.g. -Xmx384m on a 1GB host) is read directly by the JVM at startup from
# the process environment -- no extra plumbing needed here, exec-form ENTRYPOINT included.
ENTRYPOINT ["java", "-jar", "app.jar"]
