# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Cache dependencies first (layer caching optimization)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Run with lightweight Alpine JRE (~90MB vs ~250MB for the full JRE image)
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/target/crowdlens-backend-*.jar app.jar

# Create directory for SQLite DB volume
RUN mkdir -p /app/data

EXPOSE 8080

ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-XX:+UseSerialGC", "-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]