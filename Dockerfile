# syntax=docker/dockerfile:1

# ===== Etapa 1: build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copia primeiro so o pom.xml para aproveitar cache de dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o restante do codigo e builda
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== Etapa 2: runtime =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario nao-root (boa pratica de seguranca)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# SPRING_PROFILES_ACTIVE definido via variavel de ambiente no deploy (US-005)
# nunca hardcoded aqui
ENTRYPOINT ["java", "-jar", "/app/app.jar"]