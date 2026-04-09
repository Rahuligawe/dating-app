FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/location-service/pom.xml ./services/location-service/pom.xml
COPY services/location-service/src ./services/location-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/location-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/location-service/target/location-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "app.jar"]