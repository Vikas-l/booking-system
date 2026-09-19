FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common ./common
COPY inventory-service ./inventory-service
COPY booking-service ./booking-service
COPY payment-service ./payment-service
COPY notification-service ./notification-service
RUN mvn -pl payment-service -am -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
