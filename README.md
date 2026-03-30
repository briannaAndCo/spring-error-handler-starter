# Spring Error Handler Starter

Production-grade error responses for Spring Boot — RFC 9457, zero configuration.

## The Problem

Spring Boot's error handling is fragmented. Controller exceptions, filter exceptions, and security exceptions each take different paths and produce different response formats. Even with `spring.mvc.problemdetails.enabled=true`, the `BasicErrorController` still returns legacy format for filter-level errors. Developers end up writing boilerplate across `@ControllerAdvice`, `AuthenticationEntryPoint`, `AccessDeniedHandler`, and `ErrorAttributes` — all producing inconsistent responses.

## What This Starter Does

Add the dependency. Every error becomes a well-formed [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) response — from controllers, filters, and Spring Security — with structured error codes, trace IDs, PII-safe content, and automated logging. No configuration required.

```json
{
  "type": "https://api.example.com/errors/ORDER_NOT_FOUND",
  "title": "Not Found",
  "status": 404,
  "detail": "Order 12345 could not be located",
  "code": "ORDER_NOT_FOUND",
  "userMessage": "We couldn't find that order",
  "traceId": "6d2a4f0e1b3c..."
}
```

## Why Not the Alternatives?

| | This Starter | wimdeblauwe (502★) | Zalando (1,100★) | Spring Built-in |
|---|---|---|---|---|
| RFC 9457 by default | Yes | Opt-in flag | Maintenance mode | Opt-in, incomplete |
| All error sources unified | Controller + filter + security | Controller default, filter/security opt-in | Controller only | Two incompatible paths |
| Trace ID injection | Auto-detects Micrometer | No | No | No |
| PII sanitization | Built-in, pluggable | No | No | No |
| HTTP client error propagation | Round-trip conversion | No | No | No |
| Spring Boot 4 native | Yes | Retrofitted | No Boot 4 support | Yes |

## Key Features

**Zero-Config Defaults**
- RFC 9457 `ProblemDetail` for every error, every source
- Structured error codes derived from exception class names (`OrderNotFoundException` → `ORDER_NOT_FOUND`)
- Micrometer Tracing `traceId`/`spanId` auto-injected when available
- Security exceptions produce matching RFC 9457 responses automatically
- All three validation exception types normalized into a consistent `violations` format

**Optional Annotations**
- `@ErrorCode` on exception classes — map status, code, and log level
- `@ErrorContext` on methods — attach tags, workflow process/step, and captured parameter values to errors

**Safety**
- PII detection and redaction with type-specific placeholders (`[REDACTED EMAIL]`, `[REDACTED CREDIT_CARD]`)
- Environment-aware verbosity — full detail in dev, minimal in production
- The starter never leaks its own exceptions — always falls back to a safe minimal response

**Automated Logging**
- Expected exceptions (4xx) at INFO, unexpected (5xx) at ERROR with stack trace
- MDC enrichment with `errorCode`, `errorType`, `errorStatus`
- Configurable log level per exception class or HTTP status
- Integrates with Spring Boot structured logging (3.4+)

**Cross-Service Error Propagation**
- Upstream RFC 9457 responses deserialized into typed `ProblemDetailException`
- Re-throwing preserves original error context (type, status, detail, code)
- Propagation depth tracking prevents unbounded growth
- Modules for RestClient, RestTemplate (v1.0), WebClient, OpenFeign (v1.1), Retrofit (v1.2)

**Extensible at Every Layer**
- Properties for common config (codes, log levels, verbosity, PII patterns)
- `@ConditionalOnMissingBean` on every auto-configured bean
- Strategy interfaces: `ErrorCodeResolver`, `ProblemDetailCustomizer`, `SensitiveDataSanitizer`, `ExceptionClassifier`, `LoggingCustomizer`

## Modules

| Module | Version | Description |
|--------|---------|-------------|
| `error-handler-spring-boot-starter` | v1.0 | Core — error handling, annotations, logging, PII, i18n |
| `error-handler-spring-boot-starter-restclient` | v1.0 | RestClient + RestTemplate error propagation |
| `error-handler-spring-boot-starter-webclient` | v1.1 | WebClient error propagation |
| `error-handler-spring-boot-starter-feign` | v1.1 | OpenFeign error propagation |
| `error-handler-spring-boot-starter-retrofit` | v1.2 | Retrofit error propagation |

## Requirements

- Spring Boot 4.x (primary), 3.3+ (secondary)
- Java 17+
- Servlet/MVC (WebFlux support planned for v1.1+)

## License

[Apache License 2.0](LICENSE)
