# Gap Analysis: Spring Error Handling Ecosystem

**Date:** 2026-03-30
**Purpose:** Identify unmet needs in the Spring error handling landscape to guide the Spring Error Handler Starter's feature set and differentiation strategy.

---

## Methodology

This analysis cross-references three inputs:
1. Spring Boot's built-in error handling mechanisms and their documented limitations
2. The competitive landscape of third-party error handling starters
3. Common developer pain points surfaced in GitHub issues, Stack Overflow, and community forums

Each gap is scored on two dimensions:
- **Severity** — how painful is this for developers today? (Low / Medium / High / Critical)
- **Coverage** — how well do existing solutions address it? (None / Partial / Adequate)

---

## Gap Summary Table

| # | Gap | Severity | Coverage | Best Current Solution |
|---|-----|----------|----------|-----------------------|
| 1 | Unified error format across both error paths | Critical | None | Manual plumbing |
| 2 | RFC 9457 as zero-config default | High | Partial | wimdeblauwe (opt-in flag) |
| 3 | Security filter exception handling | High | Partial | wimdeblauwe (opt-in), manual delegation pattern |
| 4 | Validation exception normalization | High | Partial | wimdeblauwe (partial), officiallysingh |
| 5 | Trace ID / correlation ID injection | High | None | Manual code |
| 6 | Problem type catalog infrastructure | Medium | None | Manual URI management |
| 7 | Complete i18n for all exception sources | Medium | Partial | officiallysingh, wimdeblauwe |
| 8 | Environment-aware verbosity | Medium | None | Manual profile properties |
| 9 | Database constraint violation handling | Medium | Partial | officiallysingh (PostgreSQL, MongoDB, SQL Server) |
| 10 | Microservice error propagation | High | None | No solution exists |
| 11 | Declarative exception-to-response mapping | Medium | Partial | wimdeblauwe (properties), officiallysingh (properties) |
| 12 | MVC + WebFlux parity from one artifact | Medium | Partial | officiallysingh (both stacks) |

---

## Detailed Gap Analysis

### Gap 1: No Unified Error Response Format (Critical / None)

**The problem:** Spring Boot has two independent, incompatible error paths:
- **Path A:** `@ControllerAdvice` → `HandlerExceptionResolver` chain → direct response
- **Path B:** Exception escapes to servlet container → forward to `/error` → `BasicErrorController` → `DefaultErrorAttributes`

These paths produce different response schemas. Even with `spring.mvc.problemdetails.enabled=true`, `BasicErrorController` still returns legacy format (tracked: spring-boot#48392). Filter exceptions, security exceptions, and unresolved handler exceptions all go through Path B.

**What exists:** No library fully bridges this. wimdeblauwe's filter exception opt-in is the closest, but it doesn't unify the response format — it intercepts specific filter exceptions and handles them via its own `@ControllerAdvice`.

**What's needed:** A single, guaranteed response format for every error regardless of origin. The starter must intercept at the filter level, the security level, and the controller level, producing identical RFC 9457 responses from all three.

---

### Gap 2: RFC 9457 as Zero-Config Default (High / Partial)

**The problem:** Spring's native ProblemDetail support requires:
- `spring.mvc.problemdetails.enabled=true` (default `true` only since Boot 3.3)
- Custom exception classes extending `ErrorResponseException`
- Manual `type` URI assignment (defaults to useless `about:blank`)
- Manual `ResponseEntityExceptionHandler` subclassing for customization

**What exists:**
- wimdeblauwe: RFC 9457 is opt-in via `error.handling.use-problem-detail-format=true` — not the primary design
- problem4j: RFC 9457 native but has ~8 stars / zero adoption
- officiallysingh: RFC 7807 native but low adoption (39 stars)

**What's needed:** A starter where adding the dependency is sufficient. RFC 9457 format for every error response, meaningful `type` URIs auto-generated, structured error codes included by convention. Zero properties needed for the default experience.

---

### Gap 3: Security Filter Exception Handling (High / Partial)

**The problem:** `@ControllerAdvice` cannot intercept `AuthenticationException` or `AccessDeniedException` when thrown from the Spring Security filter chain (before `DispatcherServlet`). Developers must separately implement `AuthenticationEntryPoint` and `AccessDeniedHandler`, duplicating error formatting logic.

This is documented in spring-boot#43172 (November 2024, unresolved as of March 2026).

**What exists:**
- wimdeblauwe: Provides `UnauthorizedEntryPoint` and `AccessDeniedHandler` implementations, opt-in via properties
- Manual delegation pattern: Wire `HandlerExceptionResolver` into security handlers (well-known workaround, but boilerplate)

**What's needed:** Auto-configured `AuthenticationEntryPoint` and `AccessDeniedHandler` that produce RFC 9457 responses matching the controller-level format. Zero configuration. Back off when user provides their own beans (`@ConditionalOnMissingBean`).

---

### Gap 4: Validation Exception Normalization (High / Partial)

**The problem:** Three distinct exception types for validation failures:
- `MethodArgumentNotValidException` → 400 (handled)
- `ConstraintViolationException` → **500** (NOT handled — major pain point)
- `HandlerMethodValidationException` → 400 (handled, Spring 6.1+)

Each has a different error structure (`BindingResult` vs `ConstraintViolation` vs `MethodValidationResult`). No built-in normalization produces a consistent field-error format across all three.

**What exists:**
- wimdeblauwe: Handles validation with detailed field-level output, but format differs from RFC 9457 extensions
- officiallysingh: Handles all three types
- Spring's `ResponseEntityExceptionHandler`: Handles `MethodArgumentNotValidException` and `HandlerMethodValidationException` but NOT `ConstraintViolationException`

**What's needed:** A single, consistent validation error extension to ProblemDetail that normalizes all three exception types into a standard `violations` array with `field`, `message`, `rejectedValue`, and `code` for each violation.

---

### Gap 5: Trace ID / Correlation ID Injection (High / None)

**The problem:** Micrometer Tracing is the standard observability layer in Spring Boot 3/4. When an error occurs, the trace ID is available in the MDC — but no library automatically injects it into error responses. Developers must manually extract `traceId` and add it to every error response in every handler.

This is critical for production debugging: support teams need to correlate a user-reported error response with backend traces.

**What exists:** No library automates this. officiallysingh mentions Micrometer tracing integration in its README but the implementation is unclear.

**What's needed:** Auto-detect Micrometer Tracing on the classpath. When present, automatically inject `traceId` (and optionally `spanId`) into every RFC 9457 response as extension properties. Configurable field names. Works across all error paths (controller, filter, security).

---

### Gap 6: Problem Type Catalog Infrastructure (Medium / None)

**The problem:** RFC 9457's `type` field should be a dereferenceable URI pointing to documentation about the problem type. Spring defaults it to `about:blank`. No library provides infrastructure for:
- Defining a catalog of problem types
- Auto-generating `type` URIs from exception classes
- Serving the problem type documentation at those URIs

**What exists:** wimdeblauwe has error code strategies (`ALL_CAPS`, `FULL_QUALIFIED_NAME`) but these produce codes, not URI-based type references.

**What's needed:** A convention-based `type` URI generator (e.g., `https://{configurable-base}/errors/{error-code}`) with an optional actuator endpoint or static resource serving the problem type catalog. Error codes derived from exception class names by default, overridable via annotation or properties.

---

### Gap 7: Complete i18n for All Exception Sources (Medium / Partial)

**The problem:** Spring's `ResponseEntityExceptionHandler` resolves i18n message codes for framework exceptions only. Custom domain exceptions, security exceptions, and filter exceptions receive no i18n treatment. Developers must manually wire `MessageSource` lookups for each exception type.

**What exists:**
- wimdeblauwe: i18n via `MessageSource` with `{}` interpolation for custom exceptions
- officiallysingh: i18n via `MessageSource`
- Spring built-in: i18n for framework exceptions only (via `problemDetail.*` message codes)

**What's needed:** Convention-based `MessageSource` resolution for ALL exception types — framework, security, validation, and custom domain exceptions. Message code pattern: `error.{error-code}.title`, `error.{error-code}.detail`. Fallback to exception message when no `MessageSource` entry exists.

---

### Gap 8: Environment-Aware Verbosity (Medium / None)

**The problem:** Spring Boot does not auto-configure different error detail levels for development vs. production. `server.error.include-message=never` is production-safe but frustrating in development. Teams must manually create `application-dev.properties` overrides.

More importantly, verbosity should be exception-aware: business validation errors should always include `detail`, while unexpected exceptions should hide implementation details in production.

**What exists:** No library provides this.

**What's needed:**
- Profile-aware defaults: verbose in `dev`/`local`, minimal in `prod`
- Exception-class-level verbosity configuration: "always show detail for `BusinessException` subclasses, never for `DataAccessException`"
- Configurable stack trace inclusion per exception type (not just global on/off)

---

### Gap 9: Database Constraint Violation Handling (Medium / Partial)

**The problem:** JPA/JDBC constraint violations (`DataIntegrityViolationException` wrapping database-specific exceptions) surface as opaque 500 errors. The underlying constraint name (e.g., `uk_users_email`) contains semantic information that could produce a meaningful 409 Conflict response.

**What exists:** officiallysingh parses constraint names for PostgreSQL, SQL Server, and MongoDB. No other library addresses this.

**What's needed:** Auto-detect `DataIntegrityViolationException`, extract constraint name, map to a configurable error code and HTTP status. Default: 409 Conflict. Support for common databases (PostgreSQL, MySQL, H2, Oracle, SQL Server). Constraint-name-to-error-code mapping via properties.

---

### Gap 10: Microservice Error Propagation (High / None)

**The problem:** In a microservice architecture, Service A calls Service B via RestClient/WebClient/Feign. When B returns an RFC 9457 error, A typically wraps it in a generic `HttpClientErrorException` and returns its own generic 500. The original error context (type, detail, violations) is lost.

**What exists:** No Spring library addresses this.

**What's needed:**
- RFC 9457-aware `ResponseErrorHandler` for `RestClient`/`RestTemplate`
- RFC 9457-aware `ExchangeFilterFunction` for `WebClient`
- RFC 9457-aware `ErrorDecoder` for OpenFeign
- Configurable behavior: propagate upstream error as-is, wrap with context, or map to local error codes

This is a differentiator with no competition.

---

### Gap 11: Declarative Exception-to-Response Mapping (Medium / Partial)

**The problem:** Mapping a custom exception to an HTTP status + error code + message currently requires either:
- An `@ExceptionHandler` method (boilerplate)
- `@ResponseStatus` annotation (limited — no custom body)
- `ErrorResponseException` subclass (verbose)

**What exists:**
- wimdeblauwe: Properties-based mapping (`error.handling.codes.*`, `error.handling.messages.*`)
- officiallysingh: Properties-based mapping

**What's needed:** Support both annotation-based (`@ProblemResponse(status = 404, code = "USER_NOT_FOUND")`) and properties-based mapping. The annotation approach is more discoverable; the properties approach works for exceptions you don't control.

---

### Gap 12: MVC + WebFlux Parity (Medium / Partial)

**The problem:** Spring MVC and WebFlux have entirely separate error handling architectures. A starter must implement parallel systems with no shared framework abstractions.

**What exists:**
- officiallysingh: Supports both stacks
- problem4j: Separate `webmvc` and `webflux` modules
- wimdeblauwe: MVC only

**What's needed:** A single starter artifact with conditional auto-configuration for both stacks. Identical configuration properties, identical response format, identical feature set. Auto-detect classpath (`DispatcherServlet` vs `DispatcherHandler`) and configure accordingly.

---

## Priority Matrix

### Must-Have (v1.0 — required for differentiation)
1. **Unified error format** — RFC 9457 for every error path
2. **Zero-config RFC 9457** — just add the dependency
3. **Security exception handling** — auto-configured entry point + access denied handler
4. **Validation normalization** — consistent format for all three exception types
5. **Trace ID injection** — auto-detect Micrometer Tracing

### Should-Have (v1.0 or fast-follow)
6. Problem type catalog with convention-based URIs
7. Complete i18n across all exception sources
8. Environment-aware verbosity
9. Declarative exception-to-response mapping (annotations + properties)

### Nice-to-Have (v1.x roadmap)
10. Database constraint violation handling
11. Microservice error propagation (RestClient, WebClient, Feign)
12. WebFlux parity

---

*This gap analysis directly informs the core-design stream's architecture decisions.*
