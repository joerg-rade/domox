#FROM registry.access.redhat.com/ubi9/openjdk-17
FROM maven:3.9.10-eclipse-temurin-21-alpine

WORKDIR /app
COPY . .

# root rights are required for mvn clean
USER root
RUN mvn clean install -DskipTests

# Package Stage
FROM maven:3.9.10-eclipse-temurin-21-alpine
WORKDIR /app
COPY --from=0 /app/domox-webapp/target/domox-webapp*.jar /app.jar
ENTRYPOINT ["java", "-jar" ,"/app.jar"]
EXPOSE 8080:8080
