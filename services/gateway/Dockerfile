FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

USER nobody:nogroup

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
