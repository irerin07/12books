# 빌드
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 래퍼와 빌드 스크립트를 먼저 복사해 의존성 해석 결과를 레이어 캐시에 남긴다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon --quiet || true

COPY src src
RUN ./gradlew bootJar --no-daemon

# 실행
FROM eclipse-temurin:21-jre
WORKDIR /app

# 루트로 돌리지 않는다
RUN useradd --system --create-home --shell /usr/sbin/nologin app
USER app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
