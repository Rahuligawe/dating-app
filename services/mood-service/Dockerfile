FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/mood-service/pom.xml ./services/mood-service/pom.xml
COPY services/mood-service/src ./services/mood-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/mood-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/mood-service/target/mood-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]