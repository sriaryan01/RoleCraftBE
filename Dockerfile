# Build the Spring Boot application with Java 17.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# Run only the packaged application in a smaller runtime image.
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/backend-1.0.0.jar app.jar

# Render provides PORT at runtime. Spring Boot reads it from application.properties.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
