FROM eclipse-temurin:25.0.5_8-jre-noble

WORKDIR /app/finance_tracker

COPY target/finance-tracker-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]