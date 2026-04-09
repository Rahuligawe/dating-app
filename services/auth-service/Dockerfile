FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/auth-service/pom.xml ./services/auth-service/pom.xml
COPY services/auth-service/src ./services/auth-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/auth-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/auth-service/target/auth-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]