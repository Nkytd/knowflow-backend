ARG REGISTRY_PREFIX=

FROM ${REGISTRY_PREFIX}maven:3.9.8-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests package

FROM ${REGISTRY_PREFIX}eclipse-temurin:17-jre
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=dev

COPY --from=build /app/target/knowflow-backend-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
