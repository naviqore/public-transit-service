FROM openjdk:21-jdk-slim AS build
WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./

COPY public-transit-service/pom.xml public-transit-service/
COPY public-transit-service/src public-transit-service/src

COPY public-transit-service-app/pom.xml public-transit-service-app/
COPY public-transit-service-app/src public-transit-service-app/src

COPY public-transit-service-benchmark/pom.xml public-transit-service-benchmark/

RUN ./mvnw dependency:go-offline -pl '!public-transit-service-benchmark'
RUN ./mvnw clean install -DskipTests -pl '!public-transit-service-benchmark'

FROM openjdk:21-jdk-slim
WORKDIR /app

COPY --from=build /app/public-transit-service-app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
