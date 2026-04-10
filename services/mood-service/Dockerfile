FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

# 🔥 FULL PROJECT COPY
COPY . .

RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/mood-service/target/mood-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]