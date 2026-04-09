FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/subscription-service/pom.xml ./services/subscription-service/pom.xml
COPY services/subscription-service/src ./services/subscription-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/subscription-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/subscription-service/target/subscription-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8091

ENTRYPOINT ["java", "-jar", "app.jar"]