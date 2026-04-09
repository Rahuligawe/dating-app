FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/user-service/pom.xml ./services/user-service/pom.xml
COPY services/user-service/src ./services/user-service/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/user-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /app/services/user-service/target/user-service-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]