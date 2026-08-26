FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q package -Dmaven.test.skip=true

FROM eclipse-temurin:21.0.6_7-jre-alpine
WORKDIR /app
RUN addgroup -S stores && adduser -S stores -G stores
COPY --from=build /app/target/*.jar app.jar
USER stores
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
