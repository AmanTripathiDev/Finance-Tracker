FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

COPY backend/.mvn .mvn
COPY backend/mvnw mvnw
COPY backend/pom.xml pom.xml
RUN chmod +x mvnw

COPY backend/src src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /app/target/finance-tracker-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
