# Utilise une image Java 21
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Crée un dossier de travail
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Étape 2 : Image finale avec le jar
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Copier le jar compilé depuis l'étape précédente
COPY --from=builder /app/target/*.jar app.jar

# Charger les variables d'environnement depuis Docker
ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS

# L'application lit les variables comme SPRING_DATASOURCE_URL, etc.
ENTRYPOINT ["java", "-jar", "app.jar"]