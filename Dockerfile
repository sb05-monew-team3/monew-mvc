FROM gradle:8.4-jdk17-jammy AS build
WORKDIR /build

COPY build.gradle settings.gradle /build/
COPY gradlew /build/
COPY gradle /build/gradle

RUN ./gradlew dependencies

COPY src /build/src

RUN ./gradlew clean build -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /build/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]