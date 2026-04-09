FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/swipe-service/pom.xml ./services/swipe-service/pom.xml
COPY services/swipe-service/src ./services/swipe-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/swipe-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/swipe-service/target/swipe-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]