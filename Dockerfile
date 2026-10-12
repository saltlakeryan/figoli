FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 --create-home spring
COPY --from=build --chown=10001:10001 /workspace/target/hello-world-0.1.0-SNAPSHOT.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
