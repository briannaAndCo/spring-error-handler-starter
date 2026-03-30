# Requirements: Spring Error Handler Starter

## Problem Statement

Spring Boot's error handling is fragmented across two incompatible error paths (controller advice and BasicErrorController), producing inconsistent response formats. Security filter exceptions, validation exceptions, and deserialization failures each require separate handling code. No well-adopted starter makes RFC 9457 the zero-configuration default while covering all error sources with a unified response format, automated logging, PII safety, and cross-service error propagation.

The Spring Error Handler Starter solves this by providing a production-grade error handling platform for Spring Boot applications. Adding the dependency is sufficient — every error, from every source, becomes a well-formed RFC 9457 response with structured codes, trace IDs, PII-safe content, and automated logging. The starter is extensible at every layer, from properties to strategy interfaces to full bean replacement.

**Target users:** Spring Boot application developers building REST APIs — from solo developers wanting sensible defaults to platform teams enforcing error response standards across microservices.

## Actors

| Actor | Type | Description |
|-------|------|-------------|
| Application Developer | user | Adds the starter dependency, optionally configures via properties and annotations, extends via strategy interfaces |
| Frontend / API Consumer | external | Receives RFC 9457 error responses, reads `userMessage` for display, parses `code` and `violations` for programmatic handling |
| Ops / Support Engineer | user | Uses trace IDs from error responses to correlate with logs and distributed traces, relies on structured logging and MDC enrichment |
| Upstream Service | external | Returns HTTP error responses (RFC 9457 or otherwise) that the starter's client modules deserialize into typed exceptions |
| Spring Security Filter Chain | system | Throws `AuthenticationException` and `AccessDeniedException` before DispatcherServlet, requiring dedicated interception |
| Servlet Container | system | Catches unhandled exceptions, forwards to `/error` path — the starter intercepts before this fallback |
| Spring Boot Auto-Configuration | system | Discovers and activates the starter's `@AutoConfiguration` classes based on classpath conditions |

## Constraints

- **Implementation language: Kotlin** — starter source code written in Kotlin
- **Build system: Gradle** — Gradle-based build
- **Spring Boot 4.x** is the primary target; **3.3+** is secondary
- **Servlet/MVC only** for v1.0; WebFlux deferred to v1.1+
- **Apache 2.0 license** — all code and transitive dependencies
- **Maven Central publishing** — release artifacts with sources, javadoc (dokka), GPG signatures
- **Spring Initializr compliance** — artifact named `{name}-spring-boot-starter`, `@AutoConfiguration`, configuration metadata, no reserved namespace usage
- **Multi-module architecture** — core starter + optional per-client modules
- **`@ConditionalOnMissingBean`** on every auto-configured bean — users can replace any component
- **Starter `@ControllerAdvice` at `LOWEST_PRECEDENCE`** — safety net, not first responder. Order overridable via property
- **Non-negotiable fail-safe** — the starter never lets its own exceptions escape; always falls back to minimal hardcoded ProblemDetail
- **Zero overhead on happy path** for unannotated methods
- **Performance budgets** (warm JVM, default features):
  - Error response generation: < 5ms (exception catch to response object, excludes I/O)
  - Starter auto-configuration: < 200ms
  - PII scan: < 2ms per field (max 10,000 chars)
- **All starter state immutable after context refresh** or thread-safe if mutable
- **Annotations are optional enhancements** — the starter is fully functional with zero code changes
- **Auto-exclude** when Spring Cloud Gateway or Spring for GraphQL detected on classpath
- **Secure by default** — production verbosity unless dev profile explicitly active

## Unified Precedence Table

All error response fields are resolved using a single precedence chain:

| Precedence | Source | Fields |
|-----------|--------|--------|
| 1 (highest) | Properties by exception class | status, code, logLevel, detail, title, userMessage |
| 2 | `@ErrorCode` annotation | status, code, logLevel |
| 3 | `@ResponseStatus` annotation | status |
| 4 | `@ErrorContext` annotation | tags, process, step, captures |
| 5 | MessageSource | title, detail, userMessage |
| 6 (lowest) | Convention defaults | code (from class name), status (500), logLevel (INFO/ERROR) |

## Module Structure

| Module | Scope | Description |
|--------|-------|-------------|
| `error-handler-spring-boot-starter` | v1.0 | Core — controller/filter/security error handling, annotations, logging, PII, i18n |
| `error-handler-spring-boot-starter-restclient` | v1.0 | RestClient + RestTemplate RFC 9457 error deserialization |
| `error-handler-spring-boot-starter-webclient` | v1.1 | WebClient RFC 9457 error deserialization |
| `error-handler-spring-boot-starter-feign` | v1.1 | OpenFeign RFC 9457 error deserialization |
| `error-handler-spring-boot-starter-retrofit` | v1.2 | Retrofit RFC 9457 error deserialization |
| `error-handler-spring-boot-starter-webflux` | v1.1+ | WebFlux reactive error handling parity |

---

## Capabilities

### CAP-1: Unified RFC 9457 Error Responses

**Behavior**: Every error — whether originating from a controller, servlet filter, or Spring Security filter chain — produces an RFC 9457 `ProblemDetail` response with a structured error code, regardless of content type requested (JSON, XML, HTML).

**Requirements**:
- The system shall produce RFC 9457 `ProblemDetail` responses for all unhandled exceptions. *(EARS: ubiquitous)*
- When an exception is thrown in a controller method, the system shall intercept it via `@ControllerAdvice` and return a `ProblemDetail` response. *(EARS: event-driven)*
- When an exception is thrown in a servlet filter or Spring Security filter, the system shall intercept it via a registered `Filter` and return a `ProblemDetail` response. *(EARS: event-driven)*
- When the client sends `Accept: application/json` or `Accept: application/problem+json`, the system shall return `application/problem+json`. *(EARS: event-driven)*
- When the client sends `Accept: text/html`, the system shall return a styled HTML error page containing the same information as the JSON response. *(EARS: event-driven)*
- When the client sends `Accept: application/xml`, the system shall return `application/problem+xml` using Spring's built-in XML serialization. *(EARS: event-driven)*
- When a template engine (Thymeleaf, FreeMarker) is on the classpath, the system shall use customizable templates for HTML error pages. When no template engine is present, the system shall produce a hardcoded HTML page. *(EARS: event-driven)*
- When the starter's own error handling code throws an exception, the system shall fall back to a minimal hardcoded ProblemDetail response and log the internal failure at ERROR. *(EARS: unwanted behavior)*
- Where Spring for GraphQL is detected on the classpath, the system shall not intercept GraphQL handler exceptions. *(EARS: optional feature)*
- Where Spring Cloud Gateway is detected on the classpath, the system shall not auto-configure. *(EARS: optional feature)*

**Acceptance Criteria**:
- Given a controller that throws `RuntimeException`, when a JSON client calls it, then the response is `application/problem+json` with `status`, `type`, `title`, `detail`, and `code` fields
- Given an exception thrown in a servlet filter, when a JSON client makes a request, then the response is the same RFC 9457 format as a controller exception
- Given a Spring Security `AccessDeniedException` from the filter chain, when a JSON client makes a request, then the response is RFC 9457 with status 403
- Given a browser request (`Accept: text/html`), when any exception occurs, then an HTML error page is returned
- Given the PII sanitizer throws during error handling, when any exception occurs, then a minimal hardcoded ProblemDetail is returned and the sanitizer failure is logged at ERROR
- Given Spring for GraphQL is on the classpath, when a `@QueryMapping` throws, then the starter does not intercept the exception

**Edge Cases**:
- Response already committed (streaming): detect `response.isCommitted()`, log at ERROR, do not attempt response modification
- Exception during content negotiation itself: fall back to `application/problem+json`
- Null exception message: `detail` field is set to a generic message based on HTTP status, not null
- `Accept: text/plain` or unsupported type: fall back to `application/problem+json`

**Not Included**:
- WebSocket error handling
- `@Async` method exceptions
- Mid-stream SSE errors
- Reformatting responses from user-defined `@ControllerAdvice` (user advice takes priority)
- WebFlux (deferred to v1.1+)

---

### CAP-2: Structured Error Codes and Problem Type Catalog

**Behavior**: Every error response includes a machine-readable error code derived from the exception class name by convention, overridable via annotation or properties. The RFC 9457 `type` field is a URI built from a configurable base URL and the error code.

**Requirements**:
- The system shall generate an error code for every exception by stripping the `Exception` suffix and converting to `UPPER_SNAKE_CASE`. *(EARS: ubiquitous)*
- When `error-handler.type-base-url` is configured, the system shall set the `type` field to `{base-url}/errors/{error-code}`. *(EARS: event-driven)*
- When `error-handler.type-base-url` is not configured, the system shall set the `type` field to `about:blank`. *(EARS: event-driven)*
- When `error-handler.type-base-url` is not configured, the system shall log a single INFO-level message at startup suggesting configuration. *(EARS: event-driven)*
- Where an exception class is annotated with `@ErrorCode("CUSTOM_CODE")`, the system shall use the annotated code instead of the convention-derived code. *(EARS: optional feature)*
- Where a properties mapping exists for an exception class (`error-handler.codes.{fqcn}=CUSTOM_CODE`), the system shall use the properties-defined code. *(EARS: optional feature)*
- The system shall include the error code as an extension property (`code`) in every ProblemDetail response. *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given `OrderNotFoundException` is thrown, when no overrides exist, then the response contains `"code": "ORDER_NOT_FOUND"` and `"type": "about:blank"`
- Given `error-handler.type-base-url=https://api.example.com` is configured, when `OrderNotFoundException` is thrown, then `"type": "https://api.example.com/errors/ORDER_NOT_FOUND"`
- Given `@ErrorCode("PAYMENT_DECLINED")` on an exception class, when that exception is thrown, then `"code": "PAYMENT_DECLINED"` regardless of class name
- Given a properties mapping `error-handler.codes.com.example.PaymentException=PAY_FAIL`, when `PaymentException` is thrown, then `"code": "PAY_FAIL"`
- Given both an annotation and a properties mapping exist for the same exception, when it is thrown, then properties take precedence (externalized config wins)

**Edge Cases**:
- Exception class name without `Exception` suffix (e.g., `AccessDenied`): convert as-is to `ACCESS_DENIED`
- Nested/inner exception classes: use the simple class name, not the enclosing class
- Anonymous exception classes: fall back to superclass name for code derivation

**Not Included**:
- Serving problem type documentation at the `type` URI (actuator endpoint deferred to v1.1)
- Auto-generating a catalog from all registered codes

---

### CAP-3: Validation Exception Normalization

**Behavior**: All three Spring validation exception types are normalized into a single consistent `violations` array format in the ProblemDetail response. Default status is 400, configurable via `error-handler.validation-status`.

**Requirements**:
- When a `MethodArgumentNotValidException` is thrown, the system shall return a response with a `violations` array extracted from the `BindingResult`. *(EARS: event-driven)*
- When a `ConstraintViolationException` is thrown, the system shall return a response with a `violations` array extracted from the `ConstraintViolation` set. *(EARS: event-driven)*
- When a `HandlerMethodValidationException` is thrown, the system shall return a response with a `violations` array extracted from the `MethodValidationResult`. *(EARS: event-driven)*
- The system shall normalize all three exception types into a consistent violation format: `field`, `message`, `rejectedValue`, `code`. *(EARS: ubiquitous)*
- The system shall use full property path notation for nested objects (e.g., `address.zipCode`, `items[0].quantity`). *(EARS: ubiquitous)*
- The system shall normalize `ConstraintViolationException`'s `PropertyPath` to match Spring's dot-notation for `FieldError`. *(EARS: ubiquitous)*
- The system shall default to HTTP 400 for all validation exceptions, configurable via `error-handler.validation-status`. *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given a `@RequestBody` with `@Valid` that fails on field `email`, when the request is made, then the response contains `"violations": [{"field": "email", "message": "must not be blank", "rejectedValue": "", "code": "NOT_BLANK"}]`
- Given `@Validated` on a controller with `@RequestParam @Min(1) int page` receiving `page=0`, when the request is made, then the response is 400 (not 500) with a violation for field `page`
- Given a nested object `address.zipCode` fails validation, when the request is made, then `"field": "address.zipCode"` (full path)
- Given a collection element `items[2].quantity` fails validation, when the request is made, then `"field": "items[2].quantity"` with index notation
- Given all three exception types thrown in separate requests, when comparing responses, then the `violations` array format is identical across all three
- Given `error-handler.validation-status=422`, when a validation failure occurs, then the response status is 422

**Edge Cases**:
- Class-level constraint (no specific field): `field` is `null`, violation still included
- Multiple violations on the same field: each appears as a separate entry in the array
- Custom constraint annotation: `code` derived from annotation simple name (e.g., `@ValidEmail` → `VALID_EMAIL`)
- Violation message contains PII (e.g., rejected value is a credit card number): `rejectedValue` is sanitized by PII scanner

**Not Included**:
- Custom violation response formats — the `violations` array structure is fixed (same structure shared with CAP-12)
- Jakarta Validation group ordering information — which group failed is not exposed

---

### CAP-4: Round-Trip HTTP/Exception Conversion

**Behavior**: When calling upstream services, RFC 9457 error responses are automatically deserialized into typed `ProblemDetailException` instances that preserve the full ProblemDetail. Re-throwing these exceptions in a controller produces the correct RFC 9457 response with original upstream context preserved.

**v1.0 scope**: Core `ProblemDetailException` type, server-side re-throw behavior, RestClient module, RestTemplate module.
**v1.1**: WebClient module, OpenFeign module.
**v1.2**: Retrofit module.

**Requirements**:
- When an HTTP client receives a response with status 4xx/5xx and content type `application/problem+json`, the system shall deserialize it into a `ProblemDetailException` that carries the full `ProblemDetail` object. *(EARS: event-driven)*
- When an HTTP client receives a 4xx/5xx response that is NOT `application/problem+json`, the system shall fall back to Spring's default error handling for that client. *(EARS: event-driven)*
- When a `ProblemDetailException` is thrown in a controller, the system shall produce an RFC 9457 response preserving the original `type`, `status`, `detail`, `code`, and extensions from the upstream error. *(EARS: event-driven)*
- Where the `RestClient` is on the classpath, the system shall auto-configure a `ResponseErrorHandler` that performs RFC 9457 deserialization. *(EARS: optional feature)*
- Where the `RestTemplate` is on the classpath, the system shall auto-configure a `ResponseErrorHandler` that performs RFC 9457 deserialization. *(EARS: optional feature)*
- The system shall include a `propagation-depth` counter as a ProblemDetail extension, incrementing on each propagation hop. *(EARS: ubiquitous)*
- When `propagation-depth` exceeds `error-handler.max-propagation-depth` (default: 3), the system shall summarize the upstream error rather than fully embedding it. *(EARS: unwanted behavior)*
- Where a user provides their own `ResponseErrorHandler` bean, the system shall back off via `@ConditionalOnMissingBean`. *(EARS: optional feature)*

**Acceptance Criteria**:
- Given Service B returns a 404 ProblemDetail with `code: "USER_NOT_FOUND"`, when Service A calls B via RestClient and re-throws, then Service A's response contains `code: "USER_NOT_FOUND"` and `status: 404`
- Given Service B returns a 500 with plain text body (not RFC 9457), when Service A calls B via RestClient, then Spring's default `HttpClientErrorException` is thrown (no deserialization attempted)
- Given a 3-hop propagation chain A→B→C→D, when D returns an error with `propagation-depth: 0`, then A receives `propagation-depth: 3` and the response includes summarized upstream context
- Given the user registers a custom `ResponseErrorHandler` bean, when an upstream error occurs, then the starter's handler is not active

**Edge Cases**:
- Upstream response has `application/problem+json` content type but malformed body: fall back to default error handling, log parse failure at WARN
- Upstream ProblemDetail has unknown extension properties: preserve them as-is in the `ProblemDetailException`
- Circular service calls (A→B→A): `propagation-depth` limit prevents infinite growth
- Upstream `type` URI references a domain the current service doesn't control: preserve it, do not rewrite
- `ProblemDetailException` re-thrown inside a method with `@ErrorContext` annotations: annotation metadata is merged with upstream context (annotation values take precedence on conflict)

**Not Included**:
- Retry logic or circuit-breaking on upstream errors
- Mapping upstream error codes to local error codes (users can do this in their own `@ControllerAdvice`)
- WebClient support (v1.1), OpenFeign support (v1.1), Retrofit support (v1.2)
- Raw OkHttp or Apache HttpClient support

---

### CAP-5: Method Annotations for Error Context

**Behavior**: Developers annotate any Spring-managed method with `@ErrorContext` to attach tags, process/workflow information, and captured runtime values to any exception thrown within. Captured values are resolved from method parameters using dot-notation, written to MDC for logging, and included as ProblemDetail extensions after PII sanitization. This is an opt-in enhancement; future versions may expand annotation capabilities to cover non-error paths.

**Requirements**:
- Where a method is annotated with `@ErrorContext`, the system shall intercept exceptions thrown within via AOP (`@AfterThrowing`) and enrich them with the annotation's metadata before they propagate. *(EARS: optional feature)*
- When an exception is thrown in an `@ErrorContext`-annotated method, the system shall resolve `capture` expressions against method parameters using dot-notation (e.g., `"request.customerName"`), with a maximum traversal depth of 5 levels. *(EARS: event-driven)*
- When an exception is thrown in an `@ErrorContext`-annotated method, the system shall add `tags` to the ProblemDetail extensions. *(EARS: event-driven)*
- When an exception is thrown in an `@ErrorContext`-annotated method, the system shall add `process` and `step` to the ProblemDetail extensions. *(EARS: event-driven)*
- When an exception is thrown in an `@ErrorContext`-annotated method, the system shall write captured values and tags to MDC before logging. *(EARS: event-driven)*
- The system shall run PII sanitization on all captured values before including them in responses or logs. *(EARS: ubiquitous)*
- When no exception is thrown in an `@ErrorContext`-annotated method, the system shall add no overhead beyond AOP proxy dispatch (< 1ms). Capture expressions are only resolved on exception. *(EARS: ubiquitous)*
- Where `@ErrorContext` annotations exist on nested calls (method A calls method B, both annotated), the system shall merge context — inner annotations add to, not replace, outer annotations. On key conflicts, inner wins. *(EARS: optional feature)*
- The system shall work on any Spring-managed bean method (controllers, services, repositories, components). *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given `@ErrorContext(tags = {"payment"}, process = "checkout", step = "authorize", capture = {"orderId", "request.email"})` on a method with params `Long orderId, CheckoutRequest request`, when an exception is thrown, then the ProblemDetail contains `"tags": ["payment"], "process": "checkout", "step": "authorize", "orderId": 123, "email": "[REDACTED EMAIL]"` (email sanitized)
- Given an annotated method that succeeds, when called 1000 times, then overhead is < 1ms per call
- Given nested `@ErrorContext` — outer has `tags: ["checkout"]`, inner has `tags: ["payment"]` — when inner throws, then tags are `["checkout", "payment"]`
- Given `capture = {"request.address.zipCode"}` and `request.address` is null, when an exception is thrown, then the captured value is `null` (no secondary NPE)
- Given a captured value matches a PII pattern, when the exception is thrown, then the value is redacted in both the response and the MDC/logs

**Edge Cases**:
- Capture expression references a nonexistent parameter name: log WARN at startup (annotation processing), skip at runtime
- Capture expression resolves to a complex object (not a primitive/String): call `toString()` and sanitize
- Method has no parameters but `capture` is specified: ignore capture, log WARN
- AOP proxy not created (e.g., internal method call within the same class): annotation has no effect — document this Spring AOP limitation
- Dot-notation path exceeds max depth of 5: stop traversal, use value at depth 5

**Not Included**:
- Compile-time validation of capture expressions
- AspectJ compile-time weaving (Spring AOP proxy-based only)
- Capture of return values (only method parameters are capturable)
- Non-error-path annotation use (future versions)

---

### CAP-6: Automated Logging with MDC Enrichment

**Behavior**: Every exception handled by the starter is automatically logged with configurable log levels, MDC enrichment with error metadata, and PII sanitization. Expected exceptions log at INFO with no stack trace by default; unexpected exceptions log at ERROR with full stack trace. Integrates with Spring Boot's structured logging (3.4+), contributing error-specific fields; provides standalone JSON format when Boot's structured logging is not active.

**Requirements**:
- When an exception is handled by the starter, the system shall log it automatically. *(EARS: ubiquitous)*
- The system shall default to INFO level for expected exceptions (4xx status) and ERROR level for unexpected exceptions (5xx status). *(EARS: ubiquitous)*
- The system shall not include stack traces for expected exceptions by default. *(EARS: ubiquitous)*
- The system shall include full stack traces for unexpected exceptions by default. *(EARS: ubiquitous)*
- When an exception is logged, the system shall write `errorCode`, `errorType`, `errorStatus` to MDC before the log statement and clean them up after. *(EARS: event-driven)*
- Where `@ErrorContext` captured values and tags exist, the system shall also write those to MDC before logging. *(EARS: optional feature)*
- Where a log level is specified via properties for an exception class (`error-handler.log-levels.{fqcn}=WARN`), the system shall use the properties-defined level. *(EARS: optional feature)*
- Where a log level is specified via annotation on the exception class (`@ErrorCode(logLevel = WARN)`), the system shall use the annotated level. *(EARS: optional feature)*
- Where a log level is specified via properties for an HTTP status code (`error-handler.log-levels.status.404=DEBUG`), the system shall use the status-level mapping. *(EARS: optional feature)*
- The system shall apply log level precedence: (1) properties by exception class, (2) annotation on exception class, (3) properties by HTTP status code, (4) default (INFO for 4xx, ERROR for 5xx). *(EARS: ubiquitous)*
- The system shall sanitize exception messages via the PII sanitizer before logging. *(EARS: ubiquitous)*
- When an exception message exceeds `error-handler.log-max-message-length` (default: 10,000 characters), the system shall truncate it. *(EARS: unwanted behavior)*
- Where Spring Boot's structured logging (3.4+) is active, the system shall contribute error-specific fields to it. Where it is not active, the system shall provide a default structured JSON log format. *(EARS: optional feature)*
- Where the user has custom log format configuration (Logback/Log4j2), the system shall back off from providing its own format. *(EARS: optional feature)*
- The default structured log format shall include: `timestamp`, `level`, `errorCode`, `errorStatus`, `errorType`, `message`, `traceId`, `path`, `method`, and `stackTrace` (when applicable). *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given a `UserNotFoundException` (404), when thrown, then it is logged at INFO with message only, no stack trace, and MDC contains `errorCode=USER_NOT_FOUND`, `errorStatus=404`
- Given a `NullPointerException` (500), when thrown, then it is logged at ERROR with full stack trace
- Given `error-handler.log-levels.com.example.RateLimitException=WARN`, when `RateLimitException` is thrown, then it is logged at WARN
- Given `error-handler.log-levels.status.404=DEBUG`, when any 404 exception is thrown without a class-level override, then it is logged at DEBUG
- Given an exception message containing an email address, when logged, then the email is redacted in the log output
- Given an exception message of 50,000 characters, when logged, then it is truncated to 10,000 characters
- Given MDC already contains user-set keys, when the starter logs an error, then user-set MDC keys are preserved after cleanup (only starter-added keys removed)
- Given no custom log configuration exists and Spring Boot structured logging is not active, when an exception is logged, then the log entry is structured JSON with all standard fields
- Given the user has a custom `logback-spring.xml`, when an exception is logged, then the starter's default format is not applied

**Edge Cases**:
- Exception has a null message: log the exception class name and error code, no message
- Cyclic exception cause chain (A caused by B caused by A): detect cycle, log without infinite recursion
- Multiple exceptions handled in rapid succession on the same thread: MDC is cleaned between each
- User has partial log configuration (e.g., custom appender but no format): starter's format applies only when no encoder/layout is detected for the relevant appender

**Not Included**:
- Log destination configuration — the starter logs via SLF4J, destination is the user's concern
- Log rate limiting / deduplication
- WebFlux reactive MDC context propagation (deferred to v1.1+)

---

### CAP-7: PII and Sensitive Data Sanitization

**Behavior**: All error responses and log output are scanned for sensitive data patterns before being emitted. A pluggable `SensitiveDataSanitizer` interface provides the detection and redaction mechanism, backed by a default pattern-based implementation covering common PII types. Redaction placeholders indicate the type of data detected.

**Requirements**:
- The system shall sanitize the `detail`, `userMessage`, and all extension properties of every ProblemDetail response before serialization. *(EARS: ubiquitous)*
- The system shall sanitize exception messages before logging. *(EARS: ubiquitous)*
- The system shall sanitize captured `@ErrorContext` values before inclusion in responses or logs. *(EARS: ubiquitous)*
- The system shall provide a default `SensitiveDataSanitizer` implementation that detects and redacts: email addresses, Social Security numbers, credit card numbers (Luhn-validated), phone numbers, and JWT tokens. *(EARS: ubiquitous)*
- The system shall replace detected PII with type-specific redaction placeholders: `[REDACTED EMAIL]`, `[REDACTED SSN]`, `[REDACTED CREDIT_CARD]`, `[REDACTED PHONE]`, `[REDACTED JWT]`. *(EARS: ubiquitous)*
- Where the user provides their own `SensitiveDataSanitizer` bean, the system shall use it instead of the default. *(EARS: optional feature)*
- Where additional patterns are configured via `error-handler.sanitizer.patterns.{LABEL}={regex}` properties, the system shall add them to the default scanner's pattern list, producing `[REDACTED {LABEL}]` placeholders. *(EARS: optional feature)*
- The system shall complete PII scanning within 2ms per field (max 10,000 chars). On timeout, the entire field value is replaced with `[SANITIZATION_FAILED]`. *(EARS: ubiquitous)*
- If the sanitizer itself throws an exception, the system shall fall back to replacing the entire field value with `[SANITIZATION_FAILED]` and log the sanitizer failure at ERROR. *(EARS: unwanted behavior)*
- The system shall recursively scan string values in nested objects (maps, lists) in ProblemDetail extensions, with a maximum recursion depth of 5 levels (configurable). Circular references shall be detected and skipped. *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given a `detail` message containing `"Contact user at jane@example.com"`, when the response is generated, then the response contains `"Contact user at [REDACTED EMAIL]"`
- Given a captured value `"4111-1111-1111-1111"`, when included in ProblemDetail extensions, then it appears as `[REDACTED CREDIT_CARD]`
- Given a custom `SensitiveDataSanitizer` bean that redacts account numbers, when an exception contains an account number, then the custom scanner is used
- Given `error-handler.sanitizer.patterns.ACCOUNT_NUMBER=ACCT-\\d{8}` in properties, when `"ACCT-12345678"` appears in an error message, then it is redacted as `[REDACTED ACCOUNT_NUMBER]`
- Given the default sanitizer encounters a regex catastrophic backtracking scenario, when scanning takes > 2ms, then the scan is interrupted and the field is replaced with `[SANITIZATION_FAILED]`
- Given a value that looks like an email but is a valid technical identifier (e.g., `user@queue`), when scanned, then it is redacted (false positive is safer than false negative for PII)

**Edge Cases**:
- Null or empty strings: return as-is, no scanning
- Extremely long strings (> 10,000 chars): truncate before scanning to bound performance
- PII spanning a field boundary (e.g., credit card split across `detail` and an extension): each field is scanned independently
- Binary data or non-UTF-8 strings in exception messages: skip scanning, log WARN
- Nested objects in extensions (maps, lists): recursively scan string values up to max depth

**Not Included**:
- Context-aware PII detection (e.g., recognizing that "123 Main St" is an address based on surrounding context)
- PII detection in stack traces beyond what appears in exception messages
- Data masking (partial reveal like `****1234`) — full redaction only
- GDPR/CCPA compliance certification — the sanitizer is a defense-in-depth tool, not a compliance solution

---

### CAP-8: Trace ID and Correlation ID Injection

**Behavior**: When Micrometer Tracing is on the classpath, the starter automatically injects `traceId` and `spanId` into every ProblemDetail response as extension properties. Works across all error paths (controller, filter, security). Trace fields are best-effort extensions — clients must not depend on their presence.

**Requirements**:
- Where Micrometer Tracing is on the classpath, the system shall extract `traceId` and `spanId` from the current observation context and include them as ProblemDetail extension properties. *(EARS: optional feature)*
- Where Micrometer Tracing is not on the classpath, the system shall not attempt trace ID injection and shall not fail. *(EARS: optional feature)*
- The system shall inject trace IDs consistently across all error paths — controller exceptions, filter exceptions, and security exceptions. *(EARS: ubiquitous)*
- Where the user configures custom field names via `error-handler.trace-id-field` and `error-handler.span-id-field`, the system shall use those names instead of the defaults. *(EARS: optional feature)*
- When the trace context is unavailable (no active span), the system shall omit the trace fields rather than including null values. *(EARS: unwanted behavior)*

**Acceptance Criteria**:
- Given Micrometer Tracing is on the classpath and a span is active, when an exception occurs, then the response contains `"traceId": "abc123..."` and `"spanId": "def456..."`
- Given Micrometer Tracing is not on the classpath, when an exception occurs, then the response has no trace fields and no `ClassNotFoundException` is thrown
- Given a filter-chain exception with an active span, when the error is handled, then `traceId` is present in the response (same as controller path)
- Given `error-handler.trace-id-field=correlationId`, when an exception occurs, then the response contains `"correlationId": "abc123..."` instead of `"traceId"`
- Given no active span exists (e.g., exception during application startup), when the error is handled, then no trace fields are included

**Edge Cases**:
- Filter exception occurs before Micrometer's tracing filter runs: no span exists, omit trace fields
- Multiple tracing systems on the classpath (e.g., Brave + OpenTelemetry): use Micrometer's abstraction layer, not vendor-specific APIs

**Not Included**:
- Creating new spans for error handling — the starter reads existing spans, does not create them
- Baggage propagation into error responses
- Integration with non-Micrometer tracing systems (Jaeger client directly, etc.)
- WebFlux reactive context trace extraction (deferred to v1.1+)

---

### CAP-9: User-Facing Message Field

**Behavior**: Every ProblemDetail response includes a configurable extension property (default: `userMessage`) containing a frontend-friendly error message, separate from the technical `detail` field. Resolved via `MessageSource` for i18n support.

**Requirements**:
- The system shall include a `userMessage` extension property in every ProblemDetail response. *(EARS: ubiquitous)*
- Where a `MessageSource` entry exists for the error code (`error.{error-code}.user-message`), the system shall resolve the `userMessage` from it. *(EARS: optional feature)*
- When no `MessageSource` entry exists, the system shall fall back to a `MessageSource` lookup by HTTP status (`error.status.{code}.user-message`), then to the HTTP status reason phrase. *(EARS: event-driven)*
- Where the user configures `error-handler.user-message-field`, the system shall use that name instead of `userMessage`. *(EARS: optional feature)*
- The system shall sanitize the `userMessage` via the PII sanitizer before inclusion. *(EARS: ubiquitous)*
- The system shall never include stack traces, class names, or internal implementation details in `userMessage`. *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given `messages.properties` contains `error.ORDER_NOT_FOUND.user-message=We couldn't find that order`, when `OrderNotFoundException` is thrown, then the response contains `"userMessage": "We couldn't find that order"`
- Given no message entry for `NULL_POINTER`, when `NullPointerException` is thrown, then `"userMessage": "Internal Server Error"`
- Given `error-handler.user-message-field=displayError`, when any exception is thrown, then the field name is `displayError` instead of `userMessage`
- Given a `MessageSource` entry containing `"Contact support at admin@internal.com"`, when resolved, then the email is redacted: `"Contact support at [REDACTED EMAIL]"`
- Given a locale-specific `messages_fr.properties` with `error.ORDER_NOT_FOUND.user-message=Commande introuvable`, when a French-locale request triggers the error, then `"userMessage": "Commande introuvable"`
- Given `messages.properties` contains `error.status.404.user-message=The requested resource was not found`, when any unmapped 404 is thrown, then `"userMessage": "The requested resource was not found"`

**Edge Cases**:
- `MessageSource` lookup throws: fall back to HTTP status reason phrase, log WARN
- `userMessage` resolved to empty string: use HTTP status reason phrase instead
- Exception message contains useful business context but also PII: `userMessage` comes from `MessageSource` (safe), not from the exception message

**Not Included**:
- Auto-generating user messages from exception messages (too risky for PII)
- Per-field user messages for validation violations (violations have their own `message` field)

---

### CAP-10: Environment-Aware Verbosity

**Behavior**: The starter automatically adjusts error response detail levels based on the active Spring profile. Development profiles include rich debugging information; production profiles minimize information exposure. Per-exception-class verbosity overrides are supported.

**Requirements**:
- While a profile listed in `error-handler.verbose-profiles` (default: `dev,local,development`) is active, the system shall include `detail`, exception class name, and stack trace in error responses by default. *(EARS: state-driven)*
- While no verbose profile is active (assumed production), the system shall exclude stack traces and exception class names from responses by default. *(EARS: state-driven)*
- While in production mode, the system shall include `detail` only for expected exceptions (4xx) and use a generic message for unexpected exceptions (5xx). *(EARS: state-driven)*
- Stack traces shall always be excluded from responses for expected exceptions, regardless of verbosity level. *(EARS: ubiquitous)*
- Where per-exception verbosity is configured via `error-handler.verbosity.{fqcn}=full|minimal`, the system shall use that setting regardless of profile. *(EARS: optional feature)*
- Where `error-handler.verbosity.default=full|minimal` is configured, the system shall use it instead of profile-based detection. *(EARS: optional feature)*
- The system shall always include `status`, `type`, `title`, `code`, `userMessage`, and `traceId` (if available) regardless of verbosity level. *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given the `dev` profile is active, when a `NullPointerException` is thrown, then the response includes `detail`, `exception` class name, and `stackTrace`
- Given no verbose profile is active, when a `NullPointerException` is thrown, then the response includes only `status`, `type`, `title`, `code`, `userMessage`, and `traceId` — no stack trace, no class name, generic detail
- Given no verbose profile is active, when `UserNotFoundException` (404) is thrown, then `detail` is included (expected exception) but no stack trace
- Given `error-handler.verbosity.com.example.DebugException=full` in production, when `DebugException` is thrown, then full detail is included despite production mode
- Given `error-handler.verbosity.default=minimal` in dev profile, when any exception is thrown, then minimal detail is returned (explicit config overrides profile detection)
- Given `error-handler.verbose-profiles=dev,staging,qa`, when the `staging` profile is active, then verbose output is used

**Edge Cases**:
- Multiple profiles active (e.g., `dev,cloud`): if any verbose profile is active, default to verbose
- Custom profile names not in verbose list: default to minimal (secure by default)
- Stack trace in verbose mode still sanitized by PII scanner

**Not Included**:
- Auto-detecting cloud environments (AWS, GCP, Azure) as production
- Per-request verbosity toggling (e.g., via header or query parameter — security risk)

---

### CAP-11: Declarative Exception-to-Response Mapping

**Behavior**: Developers map exception classes to HTTP status codes, error codes, log levels, and messages via annotations on exception classes or via properties for exceptions they don't control. No `@ExceptionHandler` boilerplate needed.

**Requirements**:
- Where an exception class is annotated with `@ErrorCode(value = "CUSTOM_CODE", status = 409, logLevel = WARN)`, the system shall use those values when handling that exception. *(EARS: optional feature)*
- Where a properties mapping exists (`error-handler.exceptions.{fqcn}.status=409`), the system shall use the properties-defined mapping. *(EARS: optional feature)*
- Where a properties mapping exists (`error-handler.exceptions.{fqcn}.code=CUSTOM_CODE`), the system shall use the properties-defined code. *(EARS: optional feature)*
- Where a properties mapping exists (`error-handler.exceptions.{fqcn}.log-level=WARN`), the system shall use the properties-defined log level. *(EARS: optional feature)*
- The system shall apply the unified precedence table (see above) for all field resolution. *(EARS: ubiquitous)*
- When no mapping exists for an exception class, the system shall check the exception's superclass chain until a mapping is found or the default is used. *(EARS: event-driven)*
- The system shall default unmapped exceptions to 500 Internal Server Error. *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given `@ErrorCode(value = "DUPLICATE_ORDER", status = 409)` on `DuplicateOrderException`, when thrown, then the response is 409 with `"code": "DUPLICATE_ORDER"`
- Given `error-handler.exceptions.java.lang.IllegalArgumentException.status=400` in properties, when `IllegalArgumentException` is thrown, then the response is 400 (not 500)
- Given both `@ErrorCode(status = 409)` and `error-handler.exceptions.{fqcn}.status=422` for the same class, when thrown, then 422 is used (properties win)
- Given `CustomException extends BusinessException`, and `@ErrorCode(status = 422)` on `BusinessException` only, when `CustomException` is thrown, then 422 is used (superclass mapping inherited)
- Given `@ResponseStatus(HttpStatus.NOT_FOUND)` on an exception and no `@ErrorCode`, when thrown, then 404 is used (Spring's annotation respected)
- Given no mapping at all for `SomeObscureException`, when thrown, then 500 with convention-derived code `SOME_OBSCURE`

**Edge Cases**:
- Exception class has both `@ResponseStatus` and `@ErrorCode` with different statuses: `@ErrorCode` wins (more specific)
- Properties mapping references a class not on the classpath: ignored, log WARN at startup
- Exception with multiple levels of inheritance each having `@ErrorCode`: nearest ancestor wins

**Not Included**:
- Mapping by exception message content (fragile, not recommended)
- Mapping by package name (too coarse-grained)
- Runtime registration of new mappings (mappings are static after startup)

---

### CAP-12: Response Deserialization Error Improvement

**Behavior**: When an incoming request body fails deserialization (malformed JSON, type mismatches), the starter produces structured, field-level error information by parsing Jackson's exception hierarchy, without leaking Java class names or raw input that may contain PII. Uses the same `Violation` structure as CAP-3.

**Requirements**:
- When a `HttpMessageNotReadableException` wrapping a `MismatchedInputException` is thrown, the system shall extract the JSON path, expected type, and rejected value and return them in a `violations` array. *(EARS: event-driven)*
- When a `HttpMessageNotReadableException` wrapping a `JsonParseException` is thrown, the system shall return the line and column number of the parse failure without including a raw input snippet. *(EARS: event-driven)*
- The system shall suppress Java class names (e.g., `java.lang.Integer`, `com.example.Dto`) from the `detail` field, using plain-language type names instead (e.g., "number", "text", "true/false"). *(EARS: ubiquitous)*
- The system shall sanitize rejected values via the PII sanitizer before inclusion. *(EARS: ubiquitous)*
- The system shall return 400 Bad Request for all deserialization failures. *(EARS: ubiquitous)*
- The system shall use error code `REQUEST_BODY_INVALID` for deserialization failures. *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given a request body with `{"age": "abc"}` where `age` is an integer field, when the request is made, then the response contains `"violations": [{"field": "age", "message": "Expected a number but received a string", "rejectedValue": "abc"}]`
- Given a completely malformed JSON body (`{not json`), when the request is made, then the response contains `"detail": "Request body could not be parsed"` with line/column info, no raw snippet
- Given a missing required field, when Jackson throws `MismatchedInputException`, then the violation includes the field path
- Given a deeply nested field fails (`order.items[0].price`), when the request is made, then `"field": "order.items[0].price"`
- Given the rejected value is a credit card number, when included in violations, then it appears as `[REDACTED CREDIT_CARD]`
- Given the error detail would normally say `Cannot deserialize value of type java.lang.Integer`, then the response says `Expected a number` with no Java class names

**Edge Cases**:
- Multiple deserialization errors in one request (Jackson stops at first by default): report the first error found
- Request body is empty: `"detail": "Request body is missing"`
- Request content type is not JSON: delegate to Spring's default handling
- Extremely large rejected value string: truncate before sanitization

**Not Included**:
- XML deserialization error improvement
- Form data (`application/x-www-form-urlencoded`) deserialization errors
- Multipart request parsing errors

---

### CAP-13: i18n via MessageSource

**Behavior**: All error response text fields — `title`, `detail`, and `userMessage` — are resolvable via Spring's `MessageSource` for any exception type (framework, security, validation, custom domain). Convention-based message codes with fallback chains.

**Requirements**:
- The system shall resolve message codes for every exception using the pattern `error.{error-code}.title`, `error.{error-code}.detail`, `error.{error-code}.user-message`. *(EARS: ubiquitous)*
- When a `MessageSource` entry exists for a message code, the system shall use the resolved message. *(EARS: event-driven)*
- When no `MessageSource` entry exists for `detail`, the system shall fall back to the exception message (for expected exceptions) or a generic status-based message (for unexpected exceptions in production). *(EARS: event-driven)*
- When no `MessageSource` entry exists for `title`, the system shall fall back to the HTTP status reason phrase. *(EARS: event-driven)*
- When no `MessageSource` entry exists for `user-message`, the system shall fall back to `MessageSource` lookup by HTTP status (`error.status.{code}.user-message`), then to the HTTP status reason phrase. *(EARS: event-driven)*
- The system shall support message parameter interpolation using the exception's context (e.g., `error.ORDER_NOT_FOUND.detail=Order {0} was not found`). *(EARS: ubiquitous)*
- The system shall resolve the locale from the current request via Spring's `LocaleResolver`. *(EARS: ubiquitous)*
- The system shall apply i18n to all exception sources — controller, filter, security, and validation exceptions. *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given `messages.properties` contains `error.ORDER_NOT_FOUND.title=Order Not Found` and `error.ORDER_NOT_FOUND.detail=Order {0} could not be located`, when `OrderNotFoundException("12345")` is thrown, then `"title": "Order Not Found"` and `"detail": "Order 12345 could not be located"`
- Given `messages_de.properties` contains `error.ACCESS_DENIED.user-message=Zugriff verweigert`, when a German-locale request gets a 403, then `"userMessage": "Zugriff verweigert"`
- Given no message entries for `NULL_POINTER`, when `NullPointerException` is thrown, then `title` falls back to `"Internal Server Error"` and `userMessage` falls back to `"Internal Server Error"`
- Given a Spring Security `AccessDeniedException`, when i18n is configured, then the message codes `error.ACCESS_DENIED.title` etc. are resolved
- Given a `ConstraintViolationException`, when i18n is configured, then `error.CONSTRAINT_VIOLATION.detail` is resolved

**Edge Cases**:
- `MessageSource` lookup throws: fall back to defaults, log WARN
- Message contains PII after interpolation: sanitized before inclusion in response
- No `LocaleResolver` configured: use `Locale.getDefault()`
- Message code collision between user-defined and framework exception codes: user-defined wins (their `messages.properties` is loaded first)

**Not Included**:
- Providing bundled translations for common exceptions (users supply their own `messages.properties`)
- RTL or bidirectional text handling
- Pluralization rules beyond what `MessageSource` supports natively

---

### CAP-14: Framework Extensibility

**Behavior**: The starter provides a tiered extensibility model so users can customize behavior at increasing levels of depth — from properties configuration to full bean replacement — without forking or abandoning the starter.

**Requirements**:
- The system shall provide a properties-based configuration tier for all common customizations (error codes, log levels, verbosity, PII patterns, field names). *(EARS: ubiquitous)*
- The system shall back off every auto-configured bean via `@ConditionalOnMissingBean`, allowing users to replace any component by defining their own bean. *(EARS: ubiquitous)*
- The system shall provide the following strategy interfaces for fine-grained extension:
  - `ErrorCodeResolver` — custom error code derivation logic
  - `ProblemDetailCustomizer` — enrich or modify any ProblemDetail before it is serialized
  - `SensitiveDataSanitizer` — custom PII detection and redaction
  - `ExceptionClassifier` — determine whether an exception is expected or unexpected
  - `LoggingCustomizer` — custom log format or destination for error entries
  *(EARS: ubiquitous)*
- Where a user registers a `ProblemDetailCustomizer` bean, the system shall invoke it on every ProblemDetail response after the starter's own processing is complete. *(EARS: optional feature)*
- Where multiple `ProblemDetailCustomizer` beans exist, the system shall invoke them in `@Order` sequence. *(EARS: optional feature)*
- The system shall support annotation-based customization on exception classes (`@ErrorCode`) as a tier between properties and strategy interfaces. *(EARS: ubiquitous)*
- The system shall document all extension points with Javadoc and usage examples. *(EARS: ubiquitous)*

**Acceptance Criteria**:
- Given a user defines a `ProblemDetailCustomizer` bean that adds `"service": "order-api"` to every response, when any exception is thrown, then the response contains `"service": "order-api"`
- Given a user defines their own `ErrorCodeResolver` bean, when an exception is thrown, then the user's resolver is used instead of the default convention
- Given a user defines their own `SensitiveDataSanitizer` bean, when PII scanning occurs, then the user's scanner is used
- Given a user defines their own `@ControllerAdvice` that handles `IllegalArgumentException`, when that exception is thrown, then the user's advice handles it (starter backs off)
- Given a user registers two `ProblemDetailCustomizer` beans at `@Order(1)` and `@Order(2)`, when an exception occurs, then both are invoked in order
- Given a user replaces the auto-configured filter bean with their own, when a filter exception occurs, then the user's filter handles it

**Edge Cases**:
- `ProblemDetailCustomizer` throws an exception: starter catches it, logs at ERROR, returns ProblemDetail without customization (fail-safe rule)
- User registers a `ProblemDetailCustomizer` that removes required RFC 9457 fields (`status`, `type`): starter re-adds them after customizer runs
- User replaces `ExceptionClassifier` with one that classifies all exceptions as expected: all exceptions log at INFO with no stack trace (user's choice, starter respects it)

**Not Included**:
- Plugin architecture with dynamic loading (OSGi, SPI discovery at runtime)
- Extension points for HTML error page rendering (v1.x consideration)
- Public event bus for error events (e.g., Spring `ApplicationEvent` for each error)

---

### CAP-15: Security Exception Handling

**Behavior**: Auto-configured `AuthenticationEntryPoint` and `AccessDeniedHandler` that produce RFC 9457 responses matching the controller-level format, ensuring security exceptions are not orphaned from the unified error format.

**Requirements**:
- The system shall auto-configure an `AuthenticationEntryPoint` that returns a 401 RFC 9457 response with error code `UNAUTHORIZED`. *(EARS: ubiquitous)*
- The system shall auto-configure an `AccessDeniedHandler` that returns a 403 RFC 9457 response with error code `ACCESS_DENIED`. *(EARS: ubiquitous)*
- The system shall apply the same processing pipeline to security exceptions as controller exceptions: error code resolution, i18n, trace ID injection, MDC enrichment, PII sanitization, user message, and logging. *(EARS: ubiquitous)*
- Where the user provides their own `AuthenticationEntryPoint` bean, the system shall back off via `@ConditionalOnMissingBean`. *(EARS: optional feature)*
- Where the user provides their own `AccessDeniedHandler` bean, the system shall back off via `@ConditionalOnMissingBean`. *(EARS: optional feature)*
- Where Spring Security is not on the classpath, the system shall not configure security exception handling. *(EARS: optional feature)*
- When `AccessDeniedException` is thrown from within a controller method (via `@PreAuthorize`), the system shall handle it via `@ControllerAdvice` with the same format as the filter-chain path. *(EARS: event-driven)*

**Acceptance Criteria**:
- Given an unauthenticated request to a secured endpoint, when Spring Security rejects it, then the response is RFC 9457 JSON with `status: 401`, `code: "UNAUTHORIZED"`, `traceId`, and `userMessage`
- Given an authenticated user without permission, when Spring Security rejects the request, then the response is RFC 9457 JSON with `status: 403`, `code: "ACCESS_DENIED"`
- Given `messages.properties` contains `error.UNAUTHORIZED.user-message=Please log in to continue`, when a 401 occurs, then `"userMessage": "Please log in to continue"`
- Given the user provides their own `AuthenticationEntryPoint` bean, when an auth failure occurs, then the user's entry point is used
- Given Spring Security is not on the classpath, when the application starts, then no security-related beans are configured and no `ClassNotFoundException` occurs
- Given `@PreAuthorize("hasRole('ADMIN')")` on a controller method and a non-admin user calls it, then the response matches the filter-chain 403 format exactly

**Edge Cases**:
- OAuth2 resource server with custom `BearerTokenAuthenticationEntryPoint`: starter backs off if user has configured it
- Multiple `SecurityFilterChain` beans with different entry points: starter's entry point applies only to chains that don't specify their own
- Security exception during CORS preflight: response must include CORS headers alongside the ProblemDetail body
- Anonymous authentication vs. no authentication: both produce 401 with `UNAUTHORIZED`

**Not Included**:
- Custom security error codes per authentication mechanism (e.g., separate codes for expired token, invalid token, missing token)
- CSRF exception handling (uses Spring Security's default)
- Session expiration handling

---

### CAP-16: Database Constraint Violation Handling (v1.1)

**Behavior**: Auto-detect `DataIntegrityViolationException`, extract the database constraint name, and map it to a configurable error code and HTTP status. Produces meaningful error responses instead of opaque 500 errors.

**Planned scope**: v1.1. Not in scope for v1.0.

**Requirements** (preliminary):
- When a `DataIntegrityViolationException` is thrown, the system shall extract the constraint name from the underlying database exception. *(EARS: event-driven)*
- The system shall map constraint names to error codes and HTTP status via properties (`error-handler.constraints.{constraint-name}.code`, `error-handler.constraints.{constraint-name}.status`). *(EARS: optional feature)*
- The system shall default unmapped constraint violations to 409 Conflict. *(EARS: ubiquitous)*
- The system shall support PostgreSQL, MySQL, H2, Oracle, and SQL Server constraint name extraction. *(EARS: ubiquitous)*

**Not Included in v1.1**:
- MongoDB constraint handling
- Auto-deriving error codes from constraint names without explicit mapping

---

## Non-Requirements

The following are explicitly out of scope for the entire project:

- **Replacing `@ControllerAdvice`** — the starter extends it; users keep full control
- **Inventing a new error format** — the starter implements RFC 9457; extensions use the RFC's `properties` map
- **WebSocket error handling** — different protocol, not HTTP
- **`@Async` method exception handling** — exceptions from `@Async` methods are swallowed by Spring's `SimpleAsyncUncaughtExceptionHandler`
- **Mid-stream SSE error handling** — response is already committed
- **GraphQL error handling** — GraphQL has its own error format per spec
- **Spring Cloud Gateway error handling** — Gateway has its own error handling; starter auto-excludes
- **Rate limiting / abuse prevention** — responsibility of upstream infrastructure (API gateway, Spring Security)
- **GDPR/CCPA compliance certification** — the PII sanitizer is defense-in-depth, not a compliance solution
- **Log destination configuration** — the starter logs via SLF4J; destination is the user's concern
- **Retry logic or circuit-breaking** — responsibility of resilience libraries (Resilience4j, Spring Retry)
- **Input validation** — the starter handles validation *errors*, not validation itself

---

## Terminology

| Term | Definition | Aliases | Not to be confused with |
|------|-----------|---------|------------------------|
| ProblemDetail | The RFC 9457 standard data object representing an HTTP API error response. Contains `type`, `title`, `status`, `detail`, `instance`, and extensible `properties`. Single output format for all errors. | Problem, RFC 9457 response, error response | `ErrorAttributes` (Spring Boot legacy), generic "error object" |
| Error Code | A stable, machine-readable identifier for an error type (e.g., `ORDER_NOT_FOUND`). Derived by convention from exception class name (strip `Exception`, UPPER_SNAKE_CASE), overridable via annotation or properties. | Problem code, error type code | HTTP status code (numeric), RFC 9457 `type` field (a URI) |
| Error Context | Metadata attached to an exception via `@ErrorContext` annotation on a method. Includes static tags, process/step workflow identifiers, and captured runtime values resolved from method parameters using dot-notation. | Exception context, error metadata | Error code (single identifier), MDC entries (error context writes to MDC, but MDC is broader) |
| Expected Exception | An exception representing a normal, anticipated failure condition (e.g., not found, validation failure). Maps to 4xx status codes. Logged at INFO, no stack trace. Includes `detail` in production. Stack trace excluded from response. | Business exception, client error | Unexpected exception (5xx) |
| Unexpected Exception | An exception representing an unanticipated failure (e.g., NPE, DB connection failure). Maps to 5xx status codes. Logged at ERROR with full stack trace. In production, `detail` replaced with generic message and stack trace excluded from response. | System error, internal error | Expected exception (4xx) |
| Sanitizer | The `SensitiveDataSanitizer` component responsible for detecting and redacting PII from error responses and log output. Uses pattern-based detection by default. Replaces matches with type-specific placeholders (e.g., `[REDACTED EMAIL]`). Pluggable. | PII scanner, data redactor | Input validation (sanitizer operates on output), verbosity (controls field presence; sanitizer redacts values within fields) |
| Propagation | The round-trip conversion of RFC 9457 error responses across service boundaries. An upstream error response is deserialized into a `ProblemDetailException`, re-throwable to produce an RFC 9457 response preserving original context. `propagation-depth` counter tracks hops. | Error forwarding, upstream error pass-through | Exception chaining (Java `cause` chain within one service), trace context propagation (Micrometer headers) |
| Violation | A single field-level error within a validation or deserialization failure. Object with `field` (full property path), `message`, `rejectedValue` (PII-sanitized), and `code`. Collected into a `violations` array in ProblemDetail extensions. Shared structure for CAP-3 and CAP-12. | Field error, validation error, constraint violation | ProblemDetail (top-level response containing zero or many violations), error code (top-level identifier) |
| User Message | A frontend-friendly, end-user-safe message in a configurable extension property (default: `userMessage`). Resolved via `MessageSource` for i18n, falling back to HTTP status reason phrase. Never contains stack traces, class names, or implementation details. | Display message, frontend message | `detail` (technical/developer-facing), `message` (Java exception message, may contain PII) |
| Verbosity Level | Controls how much information is included in error responses. Two levels: `full` (detail, class name, stack trace for unexpected — default in dev) and `minimal` (no stack trace/class name, generic detail for 5xx — default in production). Configurable globally, per-profile, or per-exception. | Detail level, error detail mode | Log level (controls logging, not response), sanitization (redacts values; verbosity controls field presence) |
| ProblemDetailException | Exception type that carries a deserialized `ProblemDetail` from an upstream service response. Extends `ErrorResponseException`. Re-throwing it produces an RFC 9457 response preserving the upstream error context. Central to the propagation model (CAP-4). | Propagated exception, upstream error exception | `ErrorResponseException` (Spring's base class — `ProblemDetailException` extends it), `HttpClientErrorException` (Spring's default, does not carry ProblemDetail) |
| ExceptionClassifier | Strategy interface that determines whether an exception is expected (4xx) or unexpected (5xx). Affects log level, stack trace inclusion, and verbosity behavior. Default implementation classifies by HTTP status code. Replaceable via `@ConditionalOnMissingBean`. | — | Error code resolver (determines the code string, not the classification) |
| ProblemDetailCustomizer | Strategy interface invoked on every ProblemDetail response after the starter's processing. Allows users to enrich or modify responses (e.g., add `"service"` field). Multiple customizers invoked in `@Order` sequence. Fail-safe: exceptions caught and logged. | Response enricher, response customizer | `ErrorCodeResolver` (determines codes), `SensitiveDataSanitizer` (redacts values) |

---

## Decision Log

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | Zero-config vs. annotations — contradictory? | Annotations are optional enhancements; starter is fully functional with zero code changes | Preserves zero-config promise while allowing power users finer control |
| 2 | Bidirectional HTTP/exception conversion scope | Full round-trip: response → exception → response with context preservation | Strongest differentiator; no competitor does this |
| 3 | Configurable error message field vs. RFC compliance | Extension property approach — `userMessage` alongside `detail`, not replacing it | Preserves RFC 9457 compliance; frontends read the extension property |
| 4 | PII scanning scope | Both response and log sanitization, pluggable interface with default patterns | Defense in depth; PII can leak through either channel |
| 5 | MDC optional tagging meaning | Starter writes error metadata into MDC before logging (errorCode, errorType, errorStatus) | Enables log aggregation tools to index on error fields |
| 6 | Method annotation target | All Spring-managed bean methods via AOP, using `@AfterThrowing` (capture only on exception) | Maximizes utility; `@AfterThrowing` eliminates success-path overhead |
| 7 | Method annotation value resolution | Dot-notation against method parameters, max depth 5, null-safe, PII-sanitized | Balances expressiveness with safety; depth limit prevents graph walking |
| 8 | Process information meaning | Action flow context — `process` and `step` fields on `@ErrorContext` | Enables tracing errors to specific business workflow stages |
| 9 | REST client scope for v1.0 | RestClient + RestTemplate in v1.0; WebClient + OpenFeign in v1.1; Retrofit in v1.2 | Balances launch scope with user demand; RestClient/RestTemplate are most common |
| 10 | Safe logging definition | PII redaction + expected at INFO (no stack trace) + unexpected at ERROR (full trace) + truncation | Covers the common unsafe logging scenarios without overengineering |
| 11 | Starter failure behavior | Non-negotiable: always fall back to minimal hardcoded ProblemDetail; never let internal exceptions escape | An error handler that errors is the worst possible failure mode |
| 12 | @ControllerAdvice ordering | LOWEST_PRECEDENCE (safety net); user advice always wins; order overridable via property | Respects "don't replace, extend" principle |
| 13 | Error code convention | Strip `Exception` suffix → UPPER_SNAKE_CASE; overridable via annotation or properties | Sensible default for 90% of cases; properties override allows external config |
| 14 | Module structure | Multi-module: core + per-client optional modules | Clean dependencies; Initializr lists the core module |
| 15 | Content negotiation | Starter handles JSON, XML, HTML; HTML uses template engine if available, hardcoded fallback otherwise | Full content type coverage without hard dependency on template engine |
| 16 | Async/streaming scope | Out of scope for v1.0 (@Async, SSE mid-stream, WebSocket) | These error sources cannot produce ProblemDetail responses reliably |
| 17 | GraphQL/Gateway exclusion | Auto-exclude via classpath detection; document; property to force-enable | Prevents interference with incompatible error handling models |
| 18 | PII redaction placeholders | Type-specific: `[REDACTED EMAIL]`, `[REDACTED SSN]`, etc. | Aids debugging by indicating what was redacted without revealing the data |
| 19 | Validation default status | 400 by default, configurable via `error-handler.validation-status` | Respects teams that prefer 422 for semantic validation failures |
| 20 | User message fallback chain | MessageSource by error code → MessageSource by status → HTTP status reason phrase | i18n-aware fallback prevents English-only user messages |
| 21 | WebFlux scope | Out of scope for v1.0; deferred to v1.1+ as separate module | Focuses v1.0 on servlet/MVC; prevents scope creep |
| 22 | Structured logging approach | Integrate with Spring Boot 3.4+ structured logging; standalone JSON format as fallback | Avoids conflicting with Boot's own structured logging |
| 23 | Profile detection for verbosity | Configurable verbose profiles via `error-handler.verbose-profiles`; secure by default | Avoids fragile profile name detection; users add their own profiles |
| 24 | Performance targets | Warm JVM conditions; error response < 5ms; startup < 200ms; PII scan < 2ms per field (10K chars max) | Verifiable under stated conditions |
| 25 | PII scanner recursion | Max depth 5 (configurable), circular reference detection | Prevents DoS via deeply nested or circular ProblemDetail extensions |
| 26 | HTML error pages | Template engine when available, hardcoded HTML fallback otherwise, user-overridable | No hard dependency on template engine |
| 27 | CAP-2/CAP-11 precedence overlap | Single unified precedence table referenced from both capabilities | Eliminates confusion about resolution order |
| 28 | CAP-3/CAP-12 violation format overlap | Same `Violation` structure shared by both capabilities | Consistent field-error format regardless of error source |
| 29 | Database constraint handling | Deferred to v1.1 as CAP-16 | Important differentiator but not blocking for v1.0 launch |
| 30 | Implementation language | Kotlin with Gradle build | User preference |
| 31 | PII timeout behavior | Entire field replaced with `[SANITIZATION_FAILED]`; never return partially scanned content | Security: partial scan could miss PII in the unscanned portion |

---

## Source Material

- [spring-initializr-third-party-starter-guidelines.md](./spring-initializr-third-party-starter-guidelines.md) — Spring Initializr acceptance criteria
- [ecosystem-audit.md](./ecosystem-audit.md) — Competitive analysis of 8 libraries/starters
- [spring-boot-error-handling-analysis.md](./spring-boot-error-handling-analysis.md) — Spring Boot built-in error handling deep-dive
- [gap-analysis.md](./gap-analysis.md) — 12 ecosystem gaps with severity scoring
- [research-summary.md](./research-summary.md) — Positioning recommendation and feature roadmap
- User-provided context: additional specs (MDC tagging, annotations, HTTP client variations, PII checks, safe logging, extensibility, frontend message field, deserialization error improvement)

---

## Open Questions

- **Actuator integration**: Should the starter provide a health indicator validating its configuration at startup and/or an actuator endpoint for the error code catalog? (Identified in adversarial review, not yet scoped)
- **OpenAPI integration**: Schema-only in v1.1, annotation-driven in v1.x — exact capability definition deferred
- **`@ErrorContext` expansion**: Future versions may expand annotation capabilities beyond error paths — scope TBD
- **Spring Boot 3.3 vs 4.x API differences**: May require conditional compilation or separate source sets — to be determined during core-design
