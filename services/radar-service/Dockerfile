FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/radar-service/pom.xml ./services/radar-service/pom.xml
COPY services/radar-service/src ./services/radar-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/radar-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/radar-service/target/radar-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8089

ENTRYPOINT ["java", "-jar", "app.jar"]