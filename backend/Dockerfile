# Backend container image for ECS Fargate
# NOTE: This builds the JAR inside the image for simplicity.
# If you prefer faster builds, we can switch to a CI-produced JAR + slimmer runtime image.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom first for better layer caching
COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# Spring Boot listens on 9000 per application.yml
EXPOSE 9000

# Copy the built jar (artifactId is 'backend')
COPY --from=build /app/target/backend-*.jar /app/app.jar

# Make sure JVM behaves well in containers.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

ENTRYPOINT ["java","-jar","/app/app.jar"]

