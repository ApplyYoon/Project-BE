# 도커 이미지를 받아 실행해 주는 서비스(Render 등) 배포용입니다. 서비스가 PORT 환경변수로 포트를 알려 줍니다.
# 지금은 Render 로 배포합니다. (render.yaml, docs/DEPLOY.md)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
