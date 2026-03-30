# Design: Spring Error Handler Starter

## Use Cases

### UC-1: Zero-Config Error Handling
- **Actor**: Application Developer
- **Goal**: Get production-grade RFC 9457 error responses by adding a single dependency
- **Flow**:
  1. Developer adds `error-handler-spring-boot-starter` to Gradle/Maven
  2. Spring Boot auto-configuration discovers the starter on classpath
  3. Starter registers its `@ControllerAdvice`, error filter, and security handlers
  4. Any unhandled exception from any source produces an RFC 9457 response with convention-derived error code, trace ID (if Micrometer present), and user message
  5. Developer writes zero configuration and zero exception handling code
- **Failure modes**:
  - Starter conflicts with user's existing `@ControllerAdvice` → starter backs off (lowest precedence)
  - Starter's own processing fails → hardcoded minimal ProblemDetail returned
  - GraphQL or Gateway on classpath → starter auto-excludes

### UC-2: Custom Exception Mapping
- **Actor**: Application Developer
- **Goal**: Map domain exceptions to specific HTTP statuses, error codes, and log levels without writing `@ExceptionHandler` boilerplate
- **Flow**:
  1. Developer creates a custom exception class (e.g., `OrderNotFoundException`)
  2. Developer annotates it with `@ErrorCode(value = "ORDER_NOT_FOUND", status = 404, logLevel = INFO)` — OR — configures via properties: `error-handler.exceptions.com.example.OrderNotFoundException.status=404`
  3. When the exception is thrown, the starter resolves the mapping using the unified precedence table
  4. Response contains the configured status, code, and i18n-resolved messages
  5. Exception is logged at the configured level
- **Failure modes**:
  - No mapping exists → convention defaults apply (class name → UPPER_SNAKE_CASE, status 500)
  - Both annotation and properties exist → properties win
  - Exception class not on classpath in properties → ignored, WARN at startup

### UC-3: Error Context Enrichment
- **Actor**: Application Developer
- **Goal**: Attach business workflow context and runtime values to errors for debugging and observability
- **Flow**:
  1. Developer annotates a service or controller method with `@ErrorContext(tags = {"payment"}, process = "checkout", step = "authorize", capture = {"orderId", "request.customerName"})`
  2. Method executes normally — no overhead beyond AOP proxy dispatch
  3. When an exception is thrown, the AOP `@AfterThrowing` advice fires
  4. Capture expressions are resolved against method parameters via dot-notation (max depth 5, null-safe)
  5. Captured values are PII-sanitized
  6. Tags, process/step, and captured values are added to ProblemDetail extensions and written to MDC
  7. Exception propagates with enriched context to the starter's `@ControllerAdvice`
- **Failure modes**:
  - Capture references nonexistent parameter → skipped at runtime, WARN at startup
  - Capture resolves to null at any depth → value is `null`, no secondary exception
  - Internal method call (same class) → AOP not triggered, documented limitation
  - Nested `@ErrorContext` methods → contexts merge, inner wins on key conflicts

### UC-4: Cross-Service Error Propagation
- **Actor**: Application Developer (building microservices)
- **Goal**: Preserve upstream error context when Service A calls Service B and B returns an error
- **Flow**:
  1. Developer adds `error-handler-spring-boot-starter-restclient` module
  2. Starter auto-configures a `ResponseErrorHandler` on RestClient/RestTemplate beans
  3. Service A calls Service B; B returns a 404 with `application/problem+json`
  4. The handler deserializes the response into a `ProblemRelay` preserving all fields (type, status, detail, code, extensions)
  5. Developer re-throws the `ProblemRelay` (or lets it propagate)
  6. Starter's `@ControllerAdvice` catches it and produces an RFC 9457 response with the original upstream context preserved
  7. `propagation-depth` counter is incremented
- **Failure modes**:
  - Upstream response is not RFC 9457 → falls back to Spring's default `HttpClientErrorException`
  - Upstream response has `application/problem+json` but malformed body → falls back, logs WARN
  - Propagation depth exceeds max (default 3) → upstream error summarized instead of fully embedded
  - User provides their own `ResponseErrorHandler` → starter backs off

### UC-5: Frontend Error Consumption
- **Actor**: Frontend / API Consumer
- **Goal**: Display user-friendly error messages and programmatically handle specific error types
- **Flow**:
  1. Frontend makes an API request that results in an error
  2. Response arrives as RFC 9457 JSON with `status`, `code`, `userMessage`, `violations` (if validation), and `traceId`
  3. Frontend reads `userMessage` for display to the end user
  4. Frontend reads `code` to handle specific error types programmatically (e.g., show a login prompt for `UNAUTHORIZED`)
  5. For validation errors, frontend reads `violations` array and highlights specific form fields using `field` paths
  6. For support escalation, frontend surfaces `traceId` to the user ("Reference: abc123...")
- **Failure modes**:
  - `userMessage` is generic fallback (no MessageSource entry) → frontend shows HTTP status reason phrase
  - `traceId` absent (no Micrometer Tracing) → frontend omits reference ID
  - `violations` absent (not a validation error) → frontend shows top-level `userMessage` only
  - Response is not RFC 9457 (user's own `@ControllerAdvice` handled it) → frontend must handle both formats

### UC-6: Production Incident Debugging
- **Actor**: Ops / Support Engineer
- **Goal**: Correlate a user-reported error with backend logs and distributed traces
- **Flow**:
  1. User reports an error, providing the `traceId` from the error response (or support extracts it from frontend logs)
  2. Engineer searches log aggregation system (ELK, Datadog, Splunk) using the `traceId`
  3. Structured log entry contains `errorCode`, `errorType`, `errorStatus`, request `path` and `method`, plus any `@ErrorContext` tags/captures in MDC
  4. Engineer identifies the error source, business workflow (process/step), and relevant parameter values
  5. Engineer follows the `traceId` into the distributed tracing system (Jaeger, Zipkin) for cross-service context
  6. PII-sensitive values in logs appear redacted (`[REDACTED EMAIL]`) — safe for shared dashboards
- **Failure modes**:
  - No Micrometer Tracing → no traceId in response or logs; engineer falls back to timestamp + path correlation
  - MDC keys missing (no `@ErrorContext` on the method) → basic error metadata still present (errorCode, errorStatus)
  - Log aggregation pipeline doesn't parse structured JSON → engineer configures Boot's structured logging or the starter's fallback format

### UC-7: Extending the Starter
- **Actor**: Application Developer (platform team)
- **Goal**: Customize error handling behavior beyond what properties and annotations offer
- **Flow**:
  1. Developer identifies a customization need (e.g., add `"service"` and `"region"` to every error response)
  2. Developer implements `ProblemDetailCustomizer` and registers it as a `@Bean`
  3. Starter detects the customizer and invokes it on every ProblemDetail after its own processing
  4. For deeper customization, developer replaces any auto-configured bean by defining their own (e.g., custom `ErrorCodeResolver` for a company-wide naming convention)
  5. Starter backs off via `@ConditionalOnMissingBean`
  6. For full control, developer defines their own `@ControllerAdvice` at higher precedence — starter becomes safety net only
- **Failure modes**:
  - `ProblemDetailCustomizer` throws → starter catches, logs ERROR, returns ProblemDetail without customization
  - Customizer removes required RFC fields (`status`, `type`) → starter re-adds them after customizer runs
  - Multiple customizers registered → invoked in `@Order` sequence

---

## Architecture

### Overview

The starter is a layered interception system that catches exceptions at three levels of the Spring request lifecycle and funnels them through a single processing pipeline to produce RFC 9457 responses. It is packaged as a multi-module Kotlin/Gradle project with a core module and optional HTTP client modules.

```
Request → Security Filter → Servlet Filter → DispatcherServlet → Controller
              ↓ exception        ↓ exception                        ↓ exception
         ProblemWarden       ProblemBarrier                    ProblemSentinel
              ↓                    ↓                                ↓
              └────────────────────┴────────────────────────────────┘
                                   ↓
                             ProblemEngine
                    (resolve → classify → i18n → enrich → customize → sanitize → log)
                                   ↓
                          ProblemDetail response
```

### Components

#### ProblemSentinel
`@RestControllerAdvice` at `LOWEST_PRECEDENCE` — catches all controller-layer exceptions not handled by user-defined advice. Delegates to ProblemEngine, returns `ResponseEntity<ProblemDetail>`. Order overridable via `error-handler.advice-order` property. User-defined advice always takes priority by default.

#### ProblemBarrier
Servlet `Filter` registered early in the filter chain — catches exceptions thrown by other filters that would otherwise reach `BasicErrorController` via the container's `/error` forward. Wraps `filterChain.doFilter()` in a try-catch, delegates to ProblemEngine, writes ProblemDetail directly to `HttpServletResponse`. Checks `response.isCommitted()` before writing — if committed, logs ERROR only.

#### ProblemWarden
Auto-configured `AuthenticationEntryPoint` and `AccessDeniedHandler` — catches Spring Security filter chain exceptions. Produces 401 (`UNAUTHORIZED`) and 403 (`ACCESS_DENIED`) RFC 9457 responses. Delegates to ProblemEngine. Conditional on Spring Security classpath. Backs off when user provides their own beans.

#### ProblemEngine
The central processing pipeline. Receives a raw exception plus request context, returns a completed ProblemDetail. Has no knowledge of how the exception was intercepted or how the response is delivered. Processing stages:

1. **Resolve** — `ErrorCodeResolver` derives error code; unified precedence table resolves status, code, logLevel
2. **Classify** — `ExceptionClassifier` determines expected (4xx) vs unexpected (5xx)
3. **i18n** — `MessageSource` resolves title, detail, userMessage using error code and locale
4. **Enrich** — Inject traceId/spanId, merge `@ErrorContext` metadata, apply verbosity rules, add userMessage field
5. **Customize** — Invoke all `ProblemDetailCustomizer` beans in `@Order` sequence
6. **Sanitize** — `Redactor` scans all string fields and extensions for PII
7. **Log** — Write to MDC, log at resolved level, clean up MDC

Every stage is wrapped in a try-catch. If any stage fails, the pipeline short-circuits to a minimal hardcoded ProblemDetail and logs the internal failure at ERROR.

Interface: `fun process(exception: Throwable, request: HttpServletRequest): ProblemDetail`

#### ContextWeaver
AOP `@Aspect` that intercepts exceptions from methods annotated with `@ErrorContext`. Uses `@AfterThrowing` — capture expressions are only resolved when an exception is thrown. Resolves dot-notation expressions against method parameters (max depth 5, null-safe). Merges nested contexts (inner wins on conflict). Attaches metadata to a thread-local context that the ProblemEngine reads.

Zero overhead for unannotated beans. AOP proxy dispatch < 1ms on success path for annotated beans.

#### Redactor
PII detection and redaction. `SensitiveDataSanitizer` interface with a default pattern-based implementation. Detects email, SSN, credit card (Luhn-validated), phone, JWT. Type-specific placeholders (`[REDACTED EMAIL]`, etc.). Custom patterns via properties. < 2ms per field (10,000 char max). Timeout → `[SANITIZATION_FAILED]`. Recursive scanning of nested objects (max depth 5). Replaceable via `@ConditionalOnMissingBean`.

#### ProblemRelay
Exception type carrying a deserialized `ProblemDetail` from an upstream service response. Extends Spring's `ErrorResponseException`. Re-throwing produces the correct RFC 9457 response with upstream context preserved. The ProblemEngine detects this type and preserves upstream fields, incrementing `propagation-depth`.

#### ProblemDecoder
Auto-configured error handlers for HTTP client libraries. Deserializes `application/problem+json` responses into `ProblemRelay` instances. One implementation per client, each in its own module:

| Implementation | Client | Extension Point | Module |
|---------------|--------|----------------|--------|
| `RestClientProblemDecoder` | RestClient/RestTemplate | `ResponseErrorHandler` | restclient (v1.0) |
| `WebClientProblemDecoder` | WebClient | `ExchangeFilterFunction` | webclient (v1.1) |
| `FeignProblemDecoder` | OpenFeign | `ErrorDecoder` | feign (v1.1) |
| `RetrofitProblemDecoder` | Retrofit | `CallAdapter.Factory` | retrofit (v1.2) |

All share a common `ProblemDetailDeserializer` utility in the core module. Each backs off via `@ConditionalOnMissingBean`.

#### ProblemEngineProperties
`@ConfigurationProperties` under `error-handler.*`. Kotlin `data class` with `val` fields and defaults. Processed by `spring-boot-configuration-processor` for IDE completion. Key property groups:

- `error-handler.type-base-url` — base URI for problem type catalog
- `error-handler.user-message-field` — extension property name (default: `userMessage`)
- `error-handler.validation-status` — default validation status (default: 400)
- `error-handler.verbose-profiles` — profiles triggering full verbosity (default: `dev,local,development`)
- `error-handler.verbosity.*` — global and per-exception verbosity overrides
- `error-handler.exceptions.{fqcn}.*` — per-exception status/code/log-level mapping
- `error-handler.log-levels.status.{code}` — per-status log level
- `error-handler.log-max-message-length` — truncation limit (default: 10,000)
- `error-handler.max-propagation-depth` — max upstream error hops (default: 3)
- `error-handler.sanitizer.patterns.{LABEL}` — custom PII regex patterns
- `error-handler.trace-id-field` / `error-handler.span-id-field` — custom field names
- `error-handler.advice-order` — override for `@ControllerAdvice` order

### Data Model

The primary data object is Spring's `ProblemDetail` (RFC 9457). The starter does not define its own response model — it uses and extends Spring's built-in type via the `properties` map.

Custom types:
- **`ProblemRelay`** — extends `ErrorResponseException`, carries upstream `ProblemDetail`
- **`Violation`** — plain class with `field`, `message`, `rejectedValue`, `code`; used in extensions for validation and deserialization errors

### Data Flow

**Controller exception path:**
```
Exception thrown in controller
  → HandlerExceptionResolver chain
    → ProblemSentinel catches (lowest precedence)
      → ProblemEngine.process(exception, request)
        → ResponseEntity<ProblemDetail> returned to client
```

**Filter exception path:**
```
Exception thrown in filter
  → ProblemBarrier catches
    → ProblemEngine.process(exception, request)
      → ProblemDetail written directly to HttpServletResponse
```

**Security exception path:**
```
AuthenticationException / AccessDeniedException in security filter
  → ExceptionTranslationFilter catches
    → ProblemWarden handles
      → ProblemEngine.process(exception, request)
        → ProblemDetail written directly to HttpServletResponse
```

**Cross-service propagation path:**
```
Service A calls Service B via RestClient
  → B returns 404 application/problem+json
    → RestClientProblemDecoder deserializes → ProblemRelay
      → Developer re-throws ProblemRelay
        → ProblemSentinel catches
          → ProblemEngine detects ProblemRelay, preserves upstream context
            → ResponseEntity<ProblemDetail> with original upstream fields + incremented depth
```

### API Surface

**Public API (consumer-facing):**
- Annotations: `@ErrorCode`, `@ErrorContext`
- Exception: `ProblemRelay`
- Data: `Violation`
- Strategy interfaces: `ErrorCodeResolver`, `ProblemDetailCustomizer`, `SensitiveDataSanitizer`, `ExceptionClassifier`, `LoggingCustomizer`
- Properties: `error-handler.*` namespace

**Internal (not for consumer use):**
- `ProblemSentinel`, `ProblemBarrier`, `ProblemWarden`
- `ProblemEngine`
- `ContextWeaver`
- All auto-configuration classes

### Package Structure

```
io.github.briannaandco.errorhandler/
├── api/                          # Public API — consumers compile against this
│   ├── ErrorCode.kt              # Annotation for exception classes
│   ├── ErrorContext.kt           # Annotation for methods
│   ├── ProblemRelay.kt           # Upstream error carrier exception
│   ├── Violation.kt              # Field-level error data
│   ├── ErrorCodeResolver.kt      # fun interface
│   ├── ProblemDetailCustomizer.kt # fun interface
│   ├── SensitiveDataSanitizer.kt # fun interface
│   ├── ExceptionClassifier.kt   # fun interface
│   └── LoggingCustomizer.kt     # fun interface
└── internal/                     # Implementation — Kotlin internal visibility
    ├── ProblemEngine.kt          # Central pipeline
    ├── ProblemSentinel.kt        # Controller advice
    ├── ProblemBarrier.kt         # Servlet filter
    ├── ProblemWarden.kt          # Security handlers
    ├── ContextWeaver.kt          # AOP aspect
    ├── Redactor.kt               # Default PII sanitizer
    ├── ProblemEngineProperties.kt # Configuration properties
    └── autoconfigure/            # Auto-configuration classes
        ├── ProblemEngineAutoConfiguration.kt
        ├── ProblemSecurityAutoConfiguration.kt
        ├── ProblemTracingAutoConfiguration.kt
        └── ...
```

---

## Guiding Principles

### GP-1: Every exception produces a valid RFC 9457 response — no exception escapes to the servlet container
- **Rationale**: The starter's core promise is unified error format. If any exception reaches `BasicErrorController`, that promise is broken. The three interception layers (ProblemSentinel, ProblemBarrier, ProblemWarden) plus a hardcoded last-resort handler guarantee coverage.
- **Verification**: Test throws an unregistered exception type from every layer (controller, filter, security). Assert: content type is `application/problem+json`, body parses as valid `ProblemDetail`, no HTML in response. Where response is already committed, assert: error is logged at ERROR.
- **Caveat**: Does not apply when the HTTP response is already committed (streaming). In that case, the error is logged but the response cannot be modified.

### GP-2: The starter's own failure must never prevent an error response
- **Rationale**: An error handler that errors is the worst possible failure mode. Every processing stage in the ProblemEngine is wrapped in a try-catch. If any stage fails, the pipeline short-circuits to a dependency-free, configuration-free hardcoded ProblemDetail (`status: 500`, `type: about:blank`, generic detail). The internal failure is logged at ERROR.
- **Verification**: Test installs a `ProblemDetailCustomizer` that throws `RuntimeException`. Assert: response is still valid `application/problem+json` with status 500. Test corrupts `MessageSource` — same assertion. No stage failure propagates.

### GP-3: Every auto-configured bean backs off completely when the user provides their own
- **Rationale**: The starter extends, never replaces. `@ConditionalOnMissingBean` on every `@Bean` method. Back-off must be total — if a bean backs off, any infrastructure it would have registered (filters, interceptors, listeners) must also retract. Partial back-off where the bean is gone but its side effects remain is a violation.
- **Verification**: For each auto-configured bean, test defines a user replacement bean. Assert: starter's bean is absent, no starter-registered infrastructure for that bean is active. Removing the starter dependency entirely causes no application failure.
- **Caveat**: The hardcoded last-resort handler (GP-2) is unconditional — it does not back off. GP-3 applies to all beans except the last-resort handler.

### GP-4: Classification and resolution are separate class boundaries
- **Rationale**: The code that decides "what kind of error is this" (`ExceptionClassifier`, `ErrorCodeResolver`) must be in a different class from the code that decides "what response does this produce" (ProblemEngine). This separation makes each concern independently replaceable and testable.
- **Verification**: No class that implements a strategy interface (`ExceptionClassifier`, `ErrorCodeResolver`) references `ProblemDetail` or `HttpServletResponse`. No class that constructs `ProblemDetail` contains `instanceof` checks against domain exception types.

### GP-5: No implementation information leaks in responses under default configuration
- **Rationale**: Stack traces, fully qualified Java class names, SQL fragments, and server identifiers must never appear in any `ProblemDetail` field when no verbose profile is active. Convention-derived error codes (e.g., `ORDER_NOT_FOUND`) are permitted — they are stable, intentional identifiers, not accidental leakage.
- **Verification**: Start with default configuration (no verbose profiles). Throw every exception category. Assert: no response field contains a fully qualified class name, stack trace, SQL fragment, or server identifier. Error codes are present but are derived identifiers, not raw class names.

### GP-6: Machine-readable data goes in extension members, never parsed from `detail`
- **Rationale**: RFC 9457 explicitly states "consumers SHOULD NOT parse the 'detail' member for information." Error codes, violation field paths, rejected values, trace IDs, tags — all go in named extension properties. The `detail` field is human-readable narrative only.
- **Verification**: No test or client code extracts structured data from the `detail` string. All programmatically-consumed values (`code`, `violations`, `traceId`, `userMessage`, `tags`) are top-level extension properties.

### GP-7: Optional dependencies are classpath-detected and isolated in nested configuration classes
- **Rationale**: The starter has many optional integrations (Micrometer Tracing, Spring Security, Jackson, template engines, HTTP client libraries). Any `@Bean` method referencing an optional type must live inside a `static @Configuration(proxyBeanMethods = false)` inner class guarded by `@ConditionalOnClass`. This prevents `ClassNotFoundException` when the optional dependency is absent. Top-level `@AutoConfiguration` classes use only classpath or property conditions, never `@ConditionalOnBean`.
- **Verification**: Remove each optional dependency from the classpath one at a time. Assert: application starts without `ClassNotFoundException` or `NoClassDefFoundError`. No optional type appears in a top-level `@AutoConfiguration` class's method signatures.

### GP-8: Extension points are interfaces, not concrete classes
- **Rationale**: Every customization surface (`ErrorCodeResolver`, `ProblemDetailCustomizer`, `SensitiveDataSanitizer`, `ExceptionClassifier`, `LoggingCustomizer`) is a `fun interface`. Users implement interfaces, never subclass concrete classes. Adding a method to a concrete class users have subclassed is a binary-breaking change. Interfaces with default methods allow non-breaking evolution.
- **Verification**: All strategy types in the public API are `fun interface` declarations. Default implementations live in `internal` packages with Kotlin `internal` visibility.

### GP-9: One auto-configuration class, one functional domain
- **Rationale**: Each `@AutoConfiguration` class owns a single concern: exception interception, PII sanitization, trace ID injection, security integration, HTTP client integration, etc. No single class spans multiple unrelated domains. This keeps conditional activation clean — disabling security integration doesn't affect PII scanning.
- **Verification**: Each `@AutoConfiguration` class's beans all serve the same functional domain. Ordering annotations (`@AutoConfigureAfter`/`@AutoConfigureBefore`) explicitly declare inter-configuration dependencies.

### GP-10: Handlers hold no mutable shared state — thread safety through immutability, not synchronization
- **Rationale**: Error handlers fire under failure conditions, when shared state is most likely corrupted or contended. All pipeline components and strategy interface implementations must have no mutable instance fields and no `synchronized` blocks. All caches and configuration are populated at startup and read-only thereafter. Logging and `MessageSource` reads are permitted (they are thread-safe by contract).
- **Verification**: Static analysis flags mutable instance fields and `synchronized` usage in pipeline classes. `MessageSource` and SLF4J logger are the only external calls permitted during processing.

### GP-11: The original exception cause is never discarded
- **Rationale**: Every catch clause in the starter that does not rethrow must pass the caught exception to the new exception's constructor or to a logging call. Swallowing causes destroys the debugging trail. The ProblemEngine logs the full cause chain; the ProblemRelay preserves the upstream ProblemDetail as structured data and the original exception as the Java cause.
- **Verification**: Static analysis on all catch clauses in starter code. Every caught exception is either rethrown, passed as a cause to a new exception, or logged. No bare `throw ...("message")` without cause parameter.

### GP-12: MDC and thread-local state is scoped to a single error processing cycle and always cleaned up
- **Rationale**: Any thread-local state written during error processing (MDC entries, `@ErrorContext` captures) must be cleaned up in a `finally` block before the processing method returns. No starter-written MDC key may persist beyond the error response. Captured method parameter values are read once and copied — the starter never holds references to mutable user objects beyond the capture point.
- **Verification**: Test fires two concurrent requests that both error. Assert: MDC keys from request A never appear in request B's log entry. After error processing completes, assert: no starter-prefixed MDC keys remain on the thread.

---

## Stack Guidelines

### Stack: Kotlin 2.x / Spring Boot 4.x / Gradle (Kotlin DSL)

#### Conventions
- Source language: Kotlin (converting from Java scaffolding during core-design)
- Build: Gradle with Kotlin DSL, multi-module
- Group ID: `io.github.briannaandco`
- Package root: `io.github.briannaandco.errorhandler`
- Annotation processing: kapt (migrate to KSP when Spring processors support it)
- Testing: MockK + springmockk, JUnit 5 with backtick test names, ApplicationContextRunner for auto-config tests
- Documentation: Dokka with Javadoc-format output for Maven Central

#### Discrepancies Resolved
- **Java → Kotlin**: Existing scaffolding (2 Java files) will be converted to Kotlin when core-design implementation begins. No functional code exists yet — conversion is trivial.
- **Single module → Multi-module**: Current build is single-module. Will be restructured to multi-module with convention plugins during core-design.

#### Guidelines

| ID | Guideline | Implements Principle |
|----|-----------|---------------------|
| SG-1 | `kotlin-spring` + `all-open` plugins; `proxyBeanMethods = false` on all config classes | GP-7, GP-9 |
| SG-2 | `kotlin.explicitApi()` in all library modules | GP-8, GP-5 |
| SG-3 | `fun interface` for all strategy interfaces, not function types | GP-8 |
| SG-4 | Configuration properties are `data class` with `val` fields and defaults | GP-10 |
| SG-5 | Never use `!!` — use `requireNotNull()` or `?: error()` | GP-2, GP-11 |
| SG-6 | No `data class` in stable public API — plain classes with explicit equals/hashCode | GP-8 |
| SG-7 | `build-logic` composite build with convention plugins; `libs.versions.toml` | GP-9 |
| SG-8 | MockK + springmockk; backtick test names; `ApplicationContextRunner` for auto-config tests | GP-3, GP-7 |
| SG-9 | Dokka for documentation; Javadoc-format `-javadoc.jar` for Maven Central | GP-8 |

#### Tooling
- **Linter**: Detekt (Kotlin static analysis) with custom rules for `!!` prohibition and mutable field detection
- **Formatter**: ktlint (standard Kotlin style)
- **Test framework**: JUnit 5 + MockK + springmockk + AssertJ
- **Documentation**: Dokka (HTML + Javadoc format)
- **Build**: Gradle 8.12+ with Kotlin DSL, convention plugins in `build-logic/`

---

## Technical Decisions

### 1. Package Structure — Public API vs Internals
- **Context**: The starter needs a clear boundary between public API and implementation
- **Decision**: Both package separation (`api/` and `internal/`) and Kotlin `internal` visibility modifier
- **Alternatives considered**: Package-only (no compile-time enforcement), visibility-only (no filesystem clarity)
- **Rationale**: Package naming makes the boundary visible in file explorers and documentation. Kotlin's `internal` modifier enforces it at compile time. Best of both.

### 2. Group ID
- **Context**: Maven Central requires group ID matching a verified identity
- **Decision**: `io.github.briannaandco` (matches GitHub org)
- **Alternatives considered**: `io.github.landonharter` (personal), custom domain
- **Rationale**: Matches the GitHub organization where the repo lives

### 3. Annotation Processing — kapt vs KSP
- **Context**: `spring-boot-configuration-processor` and `spring-boot-autoconfigure-processor` need annotation processing
- **Decision**: Start with kapt, migrate to KSP when Spring processors officially support it
- **Alternatives considered**: KSP now (risk of incompatibility)
- **Rationale**: kapt is guaranteed to work. KSP is faster but Spring's processor support is not yet confirmed. Migration is a build-only change, no code impact.

### 4. Component Naming Theme
- **Context**: Components need memorable, cohesive names
- **Decision**: Sentinel theme — ProblemSentinel, ProblemBarrier, ProblemWarden, ProblemEngine, ContextWeaver, Redactor, ProblemRelay, ProblemDecoder
- **Alternatives considered**: Forge theme (ProblemForge, ProblemGate), generic names (ErrorHandlerAdvice, ErrorHandlerFilter)
- **Rationale**: Cohesive, memorable, and each name evokes the component's role

---

## Risks

- **Spring Boot 4 API changes**: Spring Boot 4 may change auto-configuration or ProblemDetail APIs. Mitigation: CI matrix tests against Boot 3.3+ and 4.x; conditional code paths where needed.
- **AOP proxy limitations**: `@ErrorContext` on internal method calls (same class) won't trigger. Mitigation: Document clearly; consider compile-time weaving guidance for power users.
- **PII false positives**: Regex-based PII detection will flag legitimate values (e.g., `user@queue` matched as email). Mitigation: False positive is safer than false negative; custom patterns allow tuning; Redactor is replaceable.
- **Community adoption for Initializr**: Spring Initializr requires community evidence (stars, downloads, SO). Mitigation: Build the best starter first; target submission at 6-12 months post-launch with 300+ stars.
- **Kotlin adoption barrier**: Some Spring Boot users are Java-only. A Kotlin starter is unusual. Mitigation: The starter is consumed as a dependency — users never write Kotlin. Public API is Java-friendly (`fun interface`, `@JvmStatic`, explicit return types).
- **Multi-module complexity**: More modules = more release coordination. Mitigation: Convention plugins standardize builds; all modules share the same version.
