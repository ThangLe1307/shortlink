FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .

COPY link-api/ link-api/
COPY contracts/ contracts/
COPY redirect-svc/ redirect-svc/

RUN mvn clean package -pl link-api -am -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY . .

RUN addgroup -S short-link && adduser -S link-api -G short-link
USER link-api

COPY --from=builder /build/link-api/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
