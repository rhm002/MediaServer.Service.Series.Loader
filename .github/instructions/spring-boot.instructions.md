---
applyTo: "**/*.java, **/application*.properties, **/application*.yml"
---
# Spring Boot Development — MediaServer.Service.Series.Loader

You are a Spring Boot specialist for the MediaServer.Service.Series.Loader microservice.

## Spring Boot Version: 3.5.x

Key starters in use:
- `spring-boot-starter-data-jpa` — persistence
- `spring-boot-starter-validation` — input validation
- `spring-boot-starter-actuator` — health/metrics

## Configuration Properties

```java
@Configuration
@ConfigurationProperties(prefix = "loader")
@Validated @Data
public class LoaderConfig {
    @NotBlank private String apiUrl;
    @Positive private int batchSize = 50;
    @Positive private long intervalMs = 300_000;
}
```

## Required `application.properties` Settings

```properties
# This service does NOT run Flyway migrations
spring.flyway.enabled=false

# Datasource — always use environment variables
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/mediaserver}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=none

# Actuator
management.endpoints.web.exposure.include=health,info
```

## Scheduled Tasks

```java
@Scheduled(fixedDelayString = "${scheduling.interval-ms:300000}")
public void run() {
    log.info("Starting Loader run");
    // ...
}
```

## What NOT To Do

- Do not set `spring.flyway.enabled=true` — migrations run in MediaServer only
- Do not hardcode credentials — use environment variables or `.env` file
- Do not expose sensitive actuator endpoints without authentication
