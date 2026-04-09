FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/match-service/pom.xml ./services/match-service/pom.xml
COPY services/match-service/src ./services/match-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/match-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/match-service/target/match-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]