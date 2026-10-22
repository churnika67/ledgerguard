FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY ledger-service/pom.xml ledger-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn -q -pl common,notification-service -am dependency:go-offline

COPY common/src common/src
COPY notification-service/src notification-service/src
RUN mvn -q -pl common,notification-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/notification-service/target/notification-service.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
