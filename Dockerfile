# syntax=docker/dockerfile:1

# --- Stage 1: build the jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dipendenze prima del codice: il layer si ricostruisce solo se cambia il pom
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# --- Stage 2: runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/rag-chatbot-*.jar app.jar

# Render assegna la porta via $PORT; Spring la legge da server.port in application.yml.
EXPOSE 8080

# MaxRAMPercentage: il free tier di Render ha 512 MB, lasciamo margine alla JVM
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
