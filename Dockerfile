# Multi-stage Dockerfile for MeiStudio
# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
# Copy pom.xml and dependencies first for better caching
COPY pom.xml .
RUN mvn dependency:go-offline
# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copy the built jar
COPY --from=build /app/target/meistudio-backend-1.0.0-SNAPSHOT.jar app.jar

# JVM Optimizations for 2GB Server
# MaxRAMPercentage=50 means 1GB if container limit is 2GB
# Using explicit Xmx for safety
ENV JAVA_OPTS="-Xmx1024m -Xms512m -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
