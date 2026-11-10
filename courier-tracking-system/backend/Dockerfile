# ========================================================
# Stage 1: Build JAR using Maven and Temurin JDK 21
# ========================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# ========================================================
# Stage 2: Minimal Production Runtime with Temurin JRE 21
# ========================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root system group and user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy compiled jar from builder stage
COPY --from=builder /app/target/courier-tracking-backend-*.jar app.jar

# Enforce non-root ownership
RUN chown -R appuser:appgroup /app
USER appuser:appgroup

# Default Spring Boot port
EXPOSE 8080

# Production environment variables
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

# Start application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
