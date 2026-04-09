FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/chat-service/pom.xml ./services/chat-service/pom.xml
COPY services/chat-service/src ./services/chat-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/chat-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/chat-service/target/chat-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]