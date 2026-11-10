FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8085

USER nobody:nogroup

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
