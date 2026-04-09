FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/api-gateway/pom.xml ./services/api-gateway/pom.xml
COPY services/api-gateway/src ./services/api-gateway/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/api-gateway -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]