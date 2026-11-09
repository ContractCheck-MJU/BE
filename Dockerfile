# 빌드와 실행 이미지를 분리한다. 실행 이미지에는 JDK와 Gradle이 없다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/contractcheck-*.jar app.jar
ENV LOG_PATH=/var/log/contractcheck
RUN mkdir -p /var/log/contractcheck
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
