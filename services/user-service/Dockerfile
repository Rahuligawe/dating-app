FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# 🔥 FULL PROJECT COPY
COPY . .

RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/user-service/target/user-service-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]