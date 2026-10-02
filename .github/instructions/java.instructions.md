---
applyTo: "**/*.java"
---
# Java Development — MediaServer.Service.Series.Loader

You are a Java 25 and Spring Boot expert for the MediaServer.Service.Series.Loader microservice.

## Language Version: Java 25

```java
// Records for DTOs
public record ApiResponse(String status, String message) {}

// Pattern matching
if (obj instanceof ErrorResponse err) {
    log.error("API error: {}", err.message());
}
```

## Lombok Patterns

```java
// Service
@Service @RequiredArgsConstructor @Slf4j
public class LoaderService {
    private final LoaderRepository repository;
}

// Config properties
@ConfigurationProperties(prefix = "loader")
@Validated @Data
public class LoaderConfig {
    @NotBlank private String apiUrl;
    @Positive private int batchSize = 50;
}
```

## Package Structure

```
org.mundell.mediaservice.service.series.loader/
  config/      — @Configuration, @ConfigurationProperties
  services/    — Business logic
  clients/     — External API clients
  models/      — DTOs and request/response records
  scheduler/   — @Scheduled tasks (if applicable)
```

## What NOT To Do

- Do not use raw types
- Do not use `System.out.println`
- Do not hardcode credentials or URLs
- Do not use `Optional.get()` without checking — use `orElseThrow`, `orElse`
