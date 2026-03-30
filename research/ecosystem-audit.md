# Spring Error Handling Ecosystem Audit

**Research date:** 2026-03-30
**Purpose:** Competitive landscape analysis for the Spring Error Handler Starter project

---

## 1. Spring Framework Built-in Error Handling

Before examining third-party libraries, it is essential to understand what Spring itself provides, since this is the baseline every library competes with or extends.

### 1.1 Spring Boot's Default Mechanism: BasicErrorController

Spring Boot registers a `BasicErrorController` that handles all requests to `/error` by default. It serves as a fallback for exceptions raised outside Spring MVC (e.g., from servlet filters). It produces both HTML (for browsers) and JSON (for API clients) based on content negotiation.

**Limitations:**
- Response format is Spring-proprietary, not RFC-compliant
- Not easily customizable without subclassing
- Does not integrate with `@ControllerAdvice` patterns
- No structured error codes or i18n support out of the box

### 1.2 @ControllerAdvice / @ExceptionHandler

The primary Spring MVC mechanism for global exception handling. Developers annotate a class with `@ControllerAdvice` (or `@RestControllerAdvice`) and define `@ExceptionHandler` methods for each exception type.

**Limitations:**
- Requires substantial boilerplate per exception type
- Does not cover exceptions thrown in servlet filters or the security filter chain
- No built-in structured response format (developers must define their own)
- No standard error code strategy
- No i18n for error messages without manual plumbing

### 1.3 Spring Framework 6 / Spring Boot 3+: ProblemDetail and RFC 9457

Spring Framework 6.0 (shipped with Spring Boot 3.0, November 2022) added native RFC 9457 support. This is the most significant recent development and has made several third-party libraries obsolete.

**Key abstractions:**
- `ProblemDetail` — container for RFC 9457 fields (`type`, `title`, `status`, `detail`, `instance`) plus a `properties` map for extensions
- `ErrorResponse` — interface that all Spring MVC exceptions now implement
- `ErrorResponseException` — base class for custom exceptions that produce RFC 9457 responses
- `ResponseEntityExceptionHandler` — `@ControllerAdvice` base class that handles all built-in Spring MVC exceptions and any `ErrorResponseException`

**Enabling built-in RFC 9457 in Spring Boot:**
```properties
spring.mvc.problemdetails.enabled=true
```
This auto-configures a `ProblemDetailsExceptionHandler` (disabled by default).

**Built-in exception coverage** (via `ResponseEntityExceptionHandler`):
`MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`, `TypeMismatchException`, `MissingServletRequestParameterException`, `MissingPathVariableException`, `MissingRequestHeaderException`, `HandlerMethodValidationException`, `AsyncRequestTimeoutException`, and others.

**Documented gaps in Spring's built-in support:**
- `spring.mvc.problemdetails.enabled` is **off by default** — easy to miss
- No structured error **codes** (stable machine-readable identifiers beyond `type` URI)
- No automatic i18n of `detail` messages from `MessageSource`
- Filter-level exceptions (security filter chain) are **not handled** — GitHub issue #43172 filed November 2024 remains unresolved
- No trace ID / correlation ID injection into responses
- `properties` map unwrapping to top-level JSON fields is **Jackson-specific** (not portable)
- Consistent validation error formatting requires additional work beyond the defaults
- No configurable logging strategy per exception type or HTTP status
- Reactive (WebFlux) stack has parallel but separate API surface
- No database constraint violation handling

---

## 2. Third-Party Libraries and Starters

### 2.1 problem-spring-web (Zalando)

| Attribute | Value |
|---|---|
| **Maven coordinates** | `org.zalando:problem-spring-webmvc` / `org.zalando:problem-spring-webflux` |
| **GitHub** | https://github.com/zalando/problem-spring-web |
| **Stars** | ~1,100 |
| **Latest version** | 0.28.0 |
| **Last commit** | January 2025 |
| **License** | MIT |
| **Maintenance status** | **Maintenance mode — migration to Spring native recommended** |

**Description:**
The original and most widely adopted RFC 7807 library for Spring. Introduced "advice traits" — a composition pattern where exception handlers are defined as interface default methods. Approximately 20 built-in traits cover common Spring and Spring Security exceptions. Supports both MVC and WebFlux.

**Key features:**
- Advice trait composition model (mix in only the handlers you need)
- Spring Security integration traits
- Content negotiation aware (`application/problem+json`)
- Post-processing hooks for custom response manipulation
- Built on the separate `zalando/problem` library (the Java model for Problem)

**Spring Boot compatibility:**
| Library version | Spring Boot |
|---|---|
| 0.28.0+ | 3.x / Spring 6.x |
| 0.27.0 | 2.x / Spring 5.x |
| 0.23.0 | 1.5.x / Spring 4.x |

**Status note:**
Zalando has officially placed this project in maintenance mode. The README recommends migrating to Spring Framework's native Error Responses feature. No new features will be added; only critical bug fixes. This is a significant signal for the ecosystem.

**Gaps / weaknesses:**
- Maintenance mode creates adoption risk
- Depends on the separate `org.zalando:problem` Java model (added dependency)
- Not compatible with Spring Boot 4.x (likely unmaintained past Boot 3 EOL)
- No error code strategy beyond the RFC `type` URI
- No i18n of messages from `MessageSource`

---

### 2.2 error-handling-spring-boot-starter (wimdeblauwe)

| Attribute | Value |
|---|---|
| **Maven coordinates** | `io.github.wimdeblauwe:error-handling-spring-boot-starter` |
| **GitHub** | https://github.com/wimdeblauwe/error-handling-spring-boot-starter |
| **Stars** | ~502 |
| **Latest version** | 5.1.0 |
| **Release date** | January 26, 2026 |
| **License** | Apache 2.0 |
| **Maintenance status** | **Active** |
| **Maven Central usage** | 32 dependent components |

**Description:**
The most actively maintained dedicated error handling starter currently available. Registers a `@ControllerAdvice` that intercepts exceptions from `@RestController` methods. Highly configurable via `application.properties` without writing code.

**Key features:**
- Configurable error codes (strategies: `ALL_CAPS` or `FULL_QUALIFIED_NAME`)
- `@ResponseErrorCode` and `@ResponseErrorProperty` annotations for per-exception customization
- Detailed validation error formatting (field-level, with property name, rejected value, constraint code)
- Spring Security exception handling (AccessDenied, BadCredentials, Locked, Disabled, etc.)
- Optional RFC 9457 `ProblemDetail` format via `error.handling.use-problem-detail-format=true`
- Configurable logging strategy per exception (`NO_LOGGING`, `MESSAGE_ONLY`, `WITH_STACKTRACE`)
- Per-status-code log level configuration
- Filter exception handling (opt-in via property)
- Spring Security `UnauthorizedEntryPoint` and `AccessDeniedHandler` integration
- OpenAPI / Swagger annotation support (`@ApiErrorResponse`)
- i18n support via Spring `MessageSource` with `{}` interpolation syntax

**Spring Boot compatibility:**
| Library version | Spring Boot | Java |
|---|---|---|
| 5.1.0 | 4.0.x | 17+ |
| 4.7.0 | 3.5.x | 17+ |
| 4.6.0 | 3.3.x–3.5.x | 17+ |
| 3.4.1 | 2.7.x | 11+ |

**Limitations:**
- Handles only `@RestController` exceptions (not `@Controller`)
- Filter exceptions disabled by default (must opt in)
- RFC 9457 format is an opt-in mode, not the primary design
- No distributed tracing / trace ID injection in responses
- No database constraint violation handling

---

### 2.3 errors-spring-boot-starter (alimate)

| Attribute | Value |
|---|---|
| **Maven coordinates** | `me.alidg:errors-spring-boot-starter` |
| **GitHub** | https://github.com/alimate/errors-spring-boot-starter |
| **Stars** | ~268 |
| **Latest version** | 1.4.0 |
| **Last commit** | April 2023 |
| **License** | Apache 2.0 |
| **Maintenance status** | **Archived / Abandoned** |

**Description:**
Aimed to provide a consistent approach to handle all exceptions through unified `WebErrorHandler` implementations. Supported both servlet and reactive stacks and included application-specific error codes with HTTP status mapping via Spring `MessageSource`.

**Key features:**
- Application-specific error codes with HTTP status mapping
- Error message interpolation via `MessageSource`
- HTTP error representation with fingerprinting support
- Argument exposure in error messages
- Built-in handlers for Spring MVC, Spring Security, validation
- Reactive stack support

**Status note:**
The repository was archived by the owner on April 21, 2023 and is now read-only. It supports Spring Boot 2.x only. **Not viable for new projects.**

---

### 2.4 spring-boot-problem-handler (officiallysingh)

| Attribute | Value |
|---|---|
| **Maven coordinates** | `io.github.officiallysingh:spring-boot-problem-handler` |
| **GitHub** | https://github.com/officiallysingh/spring-boot-problem-handler |
| **Stars** | ~39 |
| **Latest version** | 1.10.6 |
| **License** | Not specified in README |
| **Maintenance status** | **Active** |

**Description:**
A comprehensive RFC 7807-compliant library that aims for zero-boilerplate setup — exceptions can be configured entirely via properties files without writing custom `@ExceptionHandler` methods. Auto-configures `ControllerAdvice` beans conditionally based on classpath presence of relevant dependencies.

**Key features:**
- Zero-code exception handling via properties files
- Pre-built advice for Spring Web, Spring Security, Spring Data, OpenAPI validation
- Database constraint violation parsing (PostgreSQL, SQL Server, MongoDB) with dynamic error key derivation
- Micrometer tracing integration
- i18n via Spring `MessageSource`
- Stack trace and cause chain embedding in responses
- Conditional autoconfiguration (advices activate only when relevant jar is detected)
- Dual stack: Spring Web (Servlet) and Spring Webflux (Reactive)

**Spring Boot compatibility:**
| Library version | Spring Boot |
|---|---|
| 1.10.6 | 4.x (Java 17+, Jakarta EE 10) |
| 1.9.3 | 3.x |

**Gaps / weaknesses:**
- Low community adoption (39 stars)
- License not prominently stated
- Small contributor base (single maintainer risk)
- Limited documentation compared to wimdeblauwe starter

---

### 2.5 problem4j-spring (problem4j)

| Attribute | Value |
|---|---|
| **Maven coordinates** | `io.github.problem4j:problem4j-spring-webmvc` / `io.github.problem4j:problem4j-spring-webflux` |
| **GitHub** | https://github.com/problem4j/problem4j-spring |
| **Stars** | ~8 |
| **Latest version** | 2.2.4 |
| **Release date** | March 29, 2026 |
| **License** | MIT |
| **Maintenance status** | **Feature complete (bug fixes only)** |

**Description:**
A Spring integration for RFC 7807/9457 that wraps an immutable, fluent `Problem` model. Emphasizes a clean problem-first API: developers declare problems via `@ProblemMapping` annotation, throw `ProblemException`, or implement custom resolvers.

**Key features:**
- Immutable, fluent `Problem` model with extension support
- `@ProblemMapping` declarative exception-to-problem mapping
- `ProblemException` for programmatic throwing
- Custom resolver strategy
- Context interpolation (trace IDs, metadata)
- Consistent responses across WebMVC and WebFlux
- Spring Boot autoconfiguration

**Spring Boot compatibility:**
| Branch | Library version | Spring Boot |
|---|---|---|
| main | 2.x | 4.x |
| release-v1.*.x | 1.x | 3.x |

**Gaps / weaknesses:**
- Extremely low adoption (8 stars, 0 forks, 1 watcher)
- Feature-complete status means no community growth roadmap
- Single maintainer
- No track record for commercial/production use

---

### 2.6 ekino-spring-boot-starter-errors (ekino)

| Attribute | Value |
|---|---|
| **Maven coordinates** | `com.ekino.oss.spring:ekino-spring-boot-starter-errors` |
| **GitHub** | https://github.com/ekino/spring-boot-starter-errors |
| **Stars** | ~9 |
| **Latest version** | 9.0.0 (March 2023) |
| **License** | MIT |
| **Maintenance status** | **Effectively abandoned** |

**Description:**
An opinionated starter from Ekino (a French digital agency) that handles common Spring and AWS exceptions in standardized JSON format. Field-level validation errors, Spring Security handling, and configurable stack trace display.

**Key features:**
- Standardized JSON error format
- AWS exception handling
- Field-level error details with custom codes
- Stack trace toggle

**Status note:**
No commits since March 2023. Tied to Spring Boot 3.0 (version 9.x). Not actively maintained. **Not viable for new projects.**

---

### 2.7 errorest-spring-boot-starter (mkopylec)

| Attribute | Value |
|---|---|
| **Maven coordinates** | `com.github.mkopylec:errorest-spring-boot-starter` |
| **GitHub** | https://github.com/mkopylec/errorest-spring-boot-starter |
| **Stars** | ~12 |
| **Latest version** | 2.0.0 (April 2018) |
| **License** | Apache 2.0 |
| **Maintenance status** | **Abandoned** |

**Description:**
A historical starter offering unified HTTP response format, custom `RestTemplate` (`ErrorestTemplate`), Bean Validation integration, and Spring Security exception support. Written in Java and Groovy.

**Status note:**
Last release was April 2018. Spring Boot 2.x era. No recent activity. **Historical reference only.**

---

### 2.8 Spring HATEOAS (Problem Details support)

| Attribute | Value |
|---|---|
| **Maven coordinates** | `org.springframework.hateoas:spring-hateoas` |
| **GitHub** | https://github.com/spring-projects/spring-hateoas |
| **Maintenance status** | **Active (Spring team)** |

Spring HATEOAS version 1.1 added support for RFC 7807 `application/problem+json` media type. This is a first-party Spring library, not a dedicated error handling starter. It integrates Problem Details support into the HATEOAS model for applications that already use HAL or similar hypermedia types. Not a standalone error handling solution.

---

## 3. Ecosystem Summary Table

| Library | Stars | Latest Version | Spring Boot 4 | Spring Boot 3 | RFC 9457 | i18n | Filter Exceptions | DB Constraints | Status |
|---|---|---|---|---|---|---|---|---|---|
| Spring Built-in (ProblemDetail) | — | 6.x / Boot 4 | Yes | Yes (Boot 3+) | Yes | Partial | No | No | Active (Spring team) |
| problem-spring-web (Zalando) | ~1,100 | 0.28.0 | No | Yes | Yes | No | No | No | Maintenance mode |
| error-handling-spring-boot-starter (wimdeblauwe) | ~502 | 5.1.0 | Yes | Yes | Opt-in | Yes | Opt-in | No | **Active** |
| errors-spring-boot-starter (alimate) | ~268 | 1.4.0 | No | No | No | Yes | No | No | Archived |
| spring-boot-problem-handler (officiallysingh) | ~39 | 1.10.6 | Yes | Yes | Yes | Yes | Unknown | Yes | Active (low adoption) |
| problem4j-spring | ~8 | 2.2.4 | Yes | Yes | Yes | No | No | No | Feature complete |
| ekino-spring-boot-starter-errors | ~9 | 9.0.0 | No | No | No | No | No | No | Abandoned |
| errorest-spring-boot-starter (mkopylec) | ~12 | 2.0.0 | No | No | No | No | No | No | Abandoned |

---

## 4. Ecosystem Gaps and Opportunities

The following gaps exist in the current Spring error handling landscape:

### 4.1 No turnkey RFC 9457 starter with strong adoption
Spring's built-in `ProblemDetail` support is opt-in and not production-ready without significant boilerplate. Zalando's library is in maintenance mode. The wimdeblauwe starter requires opting into RFC 9457 format — it is not the primary design. No well-adopted library makes RFC 9457 the default, zero-configuration experience.

### 4.2 Filter-chain exception handling is a persistent pain point
`@ControllerAdvice` does not handle exceptions thrown from servlet filters or the Spring Security filter chain. This is a known limitation (GitHub issue #43172, filed November 2024, unresolved). No popular starter fully automates this in a zero-boilerplate way. The wimdeblauwe starter offers an opt-in property but it is not widely known.

### 4.3 No automatic trace ID / correlation ID injection into error responses
Developers using Micrometer Tracing (the standard in Spring Boot 3/4) must manually extract trace IDs and inject them into error responses. No library automates this integration.

### 4.4 Structured error codes beyond the RFC type URI
RFC 9457 defines a `type` field as a URI. In practice, teams want stable machine-readable codes (like `ACCOUNT_NOT_FOUND`) for client-side logic and i18n. The wimdeblauwe starter provides this but it is not part of the RFC model. An opinionated extension model with strong defaults is missing from the top libraries.

### 4.5 i18n gap in Spring's built-in support
Spring's `ResponseEntityExceptionHandler` resolves message codes via `MessageSource` for framework exceptions only. For custom domain exceptions, developers must wire i18n manually. No library provides a complete, zero-code i18n story for all exception types out of the box.

### 4.6 Database constraint violation handling
Most libraries ignore database-level constraint violations (unique keys, foreign keys). The `spring-boot-problem-handler` library addresses this but has low adoption. This is a real production gap — constraint violations commonly surface as opaque 500 errors.

### 4.7 Microservice error propagation
No starter addresses the cross-service error propagation problem: when Service A calls Service B and B returns a problem detail, how does A surface that to its callers? Libraries like OpenFeign can propagate errors, but there is no standard starter that wires RFC 9457 error decoding into Feign or RestClient automatically.

### 4.8 Spring Boot 4 compatibility vacuum
Many libraries have not yet published Spring Boot 4.x compatible versions (as of March 2026). The wimdeblauwe starter (5.1.0, January 2026) and spring-boot-problem-handler (1.10.6) have addressed this. A new starter targeting Boot 4 natively has a clear market window.

---

## 5. Competitive Positioning Recommendations

Given the landscape above, the Spring Error Handler Starter has clear differentiation opportunities:

1. **RFC 9457 by default, zero configuration** — make the standard the default, not an opt-in flag
2. **Filter chain coverage** — handle security and servlet filter exceptions automatically
3. **Trace ID injection** — first-class Micrometer Tracing integration in error responses
4. **Structured error codes** — opinionated but overridable code strategy beyond the RFC type URI
5. **Complete i18n story** — all exceptions, including custom domain exceptions, resolved via MessageSource
6. **Spring Boot 4 native** — target the newest supported versions to capture the upgrade wave
7. **Database constraint violations** — built-in handling for common JPA/JDBC constraint scenarios
8. **Single starter, both stacks** — consistent experience for MVC and WebFlux from one artifact

The wimdeblauwe starter is the closest to a complete solution but was designed before Spring's native RFC 9457 support existed, and its RFC 9457 mode is an afterthought. A starter designed RFC-9457-first with the above features would have no direct well-adopted competition.

---

## 6. Sources

- [zalando/problem-spring-web — GitHub](https://github.com/zalando/problem-spring-web)
- [wimdeblauwe/error-handling-spring-boot-starter — GitHub](https://github.com/wimdeblauwe/error-handling-spring-boot-starter)
- [Error Handling Spring Boot Starter — Documentation](https://wimdeblauwe.github.io/error-handling-spring-boot-starter/current/)
- [alimate/errors-spring-boot-starter — GitHub](https://github.com/alimate/errors-spring-boot-starter)
- [ekino/spring-boot-starter-errors — GitHub](https://github.com/ekino/spring-boot-starter-errors)
- [officiallysingh/spring-boot-problem-handler — GitHub](https://github.com/officiallysingh/spring-boot-problem-handler)
- [problem4j/problem4j-spring — GitHub](https://github.com/problem4j/problem4j-spring)
- [mkopylec/errorest-spring-boot-starter — GitHub](https://github.com/mkopylec/errorest-spring-boot-starter)
- [Error Responses :: Spring Framework — Official Docs](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [Spring Boot RFC 7807 GitHub Issue #19525](https://github.com/spring-projects/spring-boot/issues/19525)
- [Add Global Exception Handling for Filters — GitHub Issue #43172](https://github.com/spring-projects/spring-boot/issues/43172)
- [Guidelines for 3rd Party Starters — initializr Wiki](https://github.com/spring-io/initializr/wiki/Guidelines-for-3rd-Party-Starters)
- [Maven Central: io.github.wimdeblauwe:error-handling-spring-boot-starter](https://central.sonatype.com/artifact/io.github.wimdeblauwe/error-handling-spring-boot-starter)
- [Maven Central: io.github.officiallysingh:spring-boot-problem-handler](https://central.sonatype.com/artifact/io.github.officiallysingh/spring-boot-problem-handler)
- [Returning Errors Using ProblemDetail in Spring Boot — Baeldung](https://www.baeldung.com/spring-boot-return-errors-problemdetail)
- [A Guide to the Problem Spring Web Library — Baeldung](https://www.baeldung.com/problem-spring-web)
