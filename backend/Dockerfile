# 빌드와 실행을 나눈다. 실행 이미지에 Gradle과 소스가 남지 않게 하기 위해서다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# wrapper와 빌드 스크립트를 먼저 넣어 의존성 내려받기를 소스 변경과 분리한다.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src ./src
# 테스트는 CI에서 돌린다. 이미지 빌드는 컨테이너 실행에 필요한 것만 만든다.
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# 상태 확인에 쓸 curl만 더하고 목록 캐시는 남기지 않는다.
RUN apt-get update     && apt-get install -y --no-install-recommends curl     && rm -rf /var/lib/apt/lists/*

# 애플리케이션을 root로 돌리지 않는다.
RUN useradd --system --create-home --uid 10001 syncdoc
COPY --from=build /src/build/libs/*.jar /app/syncdoc.jar
USER syncdoc

EXPOSE 8080
# 상태 확인은 API-023이다. 컨테이너가 살아 있다는 것과 의존성이 준비된 것은 다른 값이다.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD ["sh", "-c", "curl -fsS http://127.0.0.1:8080/api/v1/health/ready || exit 1"]

ENTRYPOINT ["java", "-jar", "/app/syncdoc.jar"]
