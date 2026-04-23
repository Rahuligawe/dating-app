FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw

COPY pom.xml .
COPY services/eureka-server/pom.xml services/eureka-server/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/user-service/pom.xml services/user-service/pom.xml
COPY services/swipe-service/pom.xml services/swipe-service/pom.xml
COPY services/match-service/pom.xml services/match-service/pom.xml
COPY services/chat-service/pom.xml services/chat-service/pom.xml
COPY services/event-service/pom.xml services/event-service/pom.xml
COPY services/location-service/pom.xml services/location-service/pom.xml
COPY services/mood-service/pom.xml services/mood-service/pom.xml
COPY services/radar-service/pom.xml services/radar-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
COPY services/subscription-service/pom.xml services/subscription-service/pom.xml
COPY services/admin-service/pom.xml       services/admin-service/pom.xml
RUN ./mvnw dependency:resolve -pl services/mood-service -q 2>/dev/null || true

COPY services/mood-service/src services/mood-service/src
RUN ./mvnw clean package -DskipTests -pl services/mood-service

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/services/mood-service/target/mood-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
