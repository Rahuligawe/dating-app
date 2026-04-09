FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/event-service/pom.xml ./services/event-service/pom.xml
COPY services/event-service/src ./services/event-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/event-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/event-service/target/event-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]