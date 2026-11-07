# Stage 1: Build the Spring Boot application
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy Maven configuration.
COPY pom.xml .

# Copy source code.
COPY src ./src

# Compile and package the application.
RUN mvn -B -DskipTests package


# Stage 2: Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create a non-root user.
RUN useradd --system --uid 10001 appuser

# Copy the packaged application from the build stage.
COPY --from=build /workspace/target/*.jar app.jar

# Run as a non-root user.
USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]