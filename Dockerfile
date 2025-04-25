FROM openjdk:21-jdk-slim AS build
WORKDIR /build

COPY . .

RUN ./mvnw dependency:go-offline
RUN ./mvnw clean install -DskipTests -pl app -am

FROM openjdk:21-jdk-slim
WORKDIR /app

COPY --from=build /build/app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
