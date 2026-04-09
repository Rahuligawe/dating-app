FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/notification-service/pom.xml ./services/notification-service/pom.xml
COPY services/notification-service/src ./services/notification-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/notification-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/notification-service/target/notification-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8092

ENTRYPOINT ["java", "-jar", "app.jar"]