FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

COPY pom.xml .
COPY services/eureka-server/pom.xml ./services/eureka-server/pom.xml
COPY services/eureka-server/src ./services/eureka-server/src

RUN apt-get update && apt-get install -y maven && \
    mvn -pl services/eureka-server -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/services/eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]