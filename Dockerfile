FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

COPY . .

RUN ./mvnw dependency:go-offline
RUN ./mvnw clean install -DskipTests -pl app -am

FROM gcr.io/distroless/java25-debian13
WORKDIR /app

COPY --from=build --chown=nonroot:nonroot /build/app/target/*.jar app.jar

ENV APP_HOME_PAGE_ENABLED=false
ENV SPRINGDOC_API_DOCS_ENABLED=false
ENV SPRINGDOC_SWAGGER_UI_ENABLED=false
ENV MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics

USER nonroot
EXPOSE 8080

ENTRYPOINT ["/usr/bin/java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=80.0", "-jar", "app.jar"]