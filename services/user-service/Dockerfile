FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/user-service-3.3.2.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]