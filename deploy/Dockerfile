FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system swordol && useradd --system --gid swordol --home-dir /app swordol
COPY server/target/swordol-server.jar /app/server.jar
USER swordol
EXPOSE 7777
ENTRYPOINT ["java", "-jar", "/app/server.jar", "--host", "0.0.0.0", "--port", "7777"]
