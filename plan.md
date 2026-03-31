# Plan: Spring Error Handler Starter

## Objective
A Spring Boot starter that provides a comprehensive, opinionated error handling platform for Spring applications — targeting inclusion as an officially recommended community starter on start.spring.io. The starter will offer auto-configured exception handling, structured error responses, and extensible error resolution strategies, meeting all Spring Initializr third-party starter guidelines for licensing, quality, and community standards.

## Completed Streams

| Stream | Type | Notes |
|--------|------|-------|
| research | research | Initializr guidelines, ecosystem audit (8 libraries), gap analysis (12 gaps), positioning strategy |
| compliance | ops | Licensing (Apache 2.0), naming conventions, Maven Central publishing, Spring Boot compatibility |
| kotlin-conversion | ops | Java→Kotlin (2.0.21), package rename to io.github.briannaandco, explicitApi(), MockK, kapt |

## Active Streams

### Build & Infrastructure

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 2 | multi-module | in-progress | — | Restructure to multi-module Gradle, build-logic composite build, convention plugins, libs.versions.toml |
| 3 | tooling | ops | 2 | Detekt config, ktlint config, explicitApi(), Dokka setup, CI matrix update |

### Public API Surface

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 4 | api-interfaces | feature | 2 | fun interface declarations: ErrorCodeResolver, ProblemDetailCustomizer, SensitiveDataSanitizer, ExceptionClassifier, LoggingCustomizer |
| 5 | api-annotations | feature | 2 | @ErrorCode and @ErrorContext annotation definitions (declarations only, no processing) |
| 6 | api-types | feature | 2 | ProblemRelay (extends ErrorResponseException), Violation (plain class), package structure |

### Core Pipeline

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 7 | error-code-resolution | feature | 4 | Default ErrorCodeResolver: strip Exception suffix → UPPER_SNAKE_CASE. Superclass chain lookup. Annotation + properties override. |
| 8 | exception-classifier | feature | 4 | Default ExceptionClassifier: 4xx = expected, 5xx = unexpected. Status resolution from @ErrorCode, @ResponseStatus, properties, convention. |
| 9 | properties | feature | 2 | ProblemEngineProperties data class. All property groups. Configuration metadata generation via kapt. |
| 10 | problem-engine | feature | 7, 8, 9 | ProblemEngine pipeline skeleton — resolve + classify stages. Fail-safe try-catch wrapping. Hardcoded last-resort fallback. |
| 11 | problem-sentinel | feature | 10 | @RestControllerAdvice at LOWEST_PRECEDENCE. Delegates to ProblemEngine. Order overridable via property. Back-off tests. |
| 12 | problem-barrier | feature | 10 | Servlet Filter. Catches filter-chain exceptions. response.isCommitted() check. Writes ProblemDetail directly. |

### Security

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 13 | problem-warden-entrypoint | feature | 10 | AuthenticationEntryPoint producing 401 RFC 9457. Conditional on Spring Security. ConditionalOnMissingBean. |
| 14 | problem-warden-access-denied | feature | 10 | AccessDeniedHandler producing 403 RFC 9457. @PreAuthorize AccessDeniedException via ControllerAdvice. |

### Validation

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 15 | violation-model | feature | 6 | Violation data class finalization. Shared structure for validation + deserialization errors. |
| 16 | validation-method-argument | feature | 10, 15 | MethodArgumentNotValidException → 400 with violations array. BindingResult → Violation mapping. Full property paths. |
| 17 | validation-constraint | feature | 10, 15 | ConstraintViolationException → 400 (not 500). PropertyPath → dot-notation normalization. |
| 18 | validation-handler-method | feature | 10, 15 | HandlerMethodValidationException → 400. MethodValidationResult → Violation mapping. |
| 19 | deserialization-errors | feature | 10, 15 | HttpMessageNotReadableException handling. Jackson exception hierarchy parsing. Plain-language type names. |

### Sanitization

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 20 | redactor-interface | feature | 4 | SensitiveDataSanitizer fun interface finalization. Timeout contract. Input/output contract. |
| 21 | redactor-patterns | feature | 20 | Default implementation: email, SSN, credit card (Luhn), phone, JWT regex patterns. Type-specific placeholders. |
| 22 | redactor-config | feature | 21, 9 | Custom patterns via properties. Configurable labels. Max recursion depth. Timeout → [SANITIZATION_FAILED]. |
| 23 | redactor-integration | feature | 22, 10 | Wire Redactor into ProblemEngine sanitize stage. Recursive nested object scanning. Performance tests (< 2ms). |

### Logging

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 24 | logging-core | feature | 10 | Automated exception logging via SLF4J. Expected = INFO no trace, unexpected = ERROR with trace. Message truncation. |
| 25 | logging-mdc | feature | 24 | MDC enrichment: errorCode, errorType, errorStatus. Cleanup in finally. Concurrent request isolation tests. |
| 26 | logging-levels | feature | 24, 9 | Per-exception-class log level (properties + annotation). Per-status log level. Precedence resolution. |
| 27 | logging-structured | feature | 24 | Spring Boot 3.4+ structured logging integration. Standalone JSON fallback format. Back-off detection. |

### i18n & User Message

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 28 | i18n-resolution | feature | 10 | MessageSource resolution for title, detail. Convention message codes. Fallback chains. Parameter interpolation. |
| 29 | user-message | feature | 28, 9 | userMessage extension property. Configurable field name. Status-based fallback. Locale from request. |
| 30 | verbosity | feature | 8, 9 | Environment-aware verbosity. Configurable verbose-profiles. full vs minimal. Per-exception override. |

### Tracing

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 31 | tracing-injection | feature | 10 | Micrometer Tracing conditional detection. traceId/spanId extraction. ProblemDetail extension injection. |
| 32 | tracing-absence | feature | 31 | Behavior when Micrometer not on classpath. No ClassNotFoundException. Omit fields when no active span. |

### Annotations Processing

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 33 | context-weaver-core | feature | 5, 10 | ContextWeaver @Aspect. @AfterThrowing advice. Thread-local context storage. ProblemEngine reads context. |
| 34 | context-weaver-capture | feature | 33 | Dot-notation expression resolution against method parameters. Max depth 5. Null-safe traversal. |
| 35 | context-weaver-merge | feature | 33 | Nested @ErrorContext merging. Inner wins on conflict. Tags concatenation. |
| 36 | context-weaver-sanitize | feature | 34, 23 | PII sanitization on captured values before response and MDC. Integration with Redactor. |

### Propagation

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 37 | problem-relay | feature | 6, 10 | ProblemRelay handling in ProblemEngine. Upstream field preservation. propagation-depth increment. Max depth summarization. |
| 38 | problem-deserializer | feature | 6 | ProblemDetailDeserializer utility. Parses application/problem+json into ProblemRelay. Malformed body fallback. |
| 39 | restclient-decoder | feature | 38, 2 | RestClient/RestTemplate ResponseErrorHandler. Separate module. ConditionalOnClass + ConditionalOnMissingBean. |

### Content Negotiation

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 40 | content-json | feature | 11 | application/problem+json and application/json handling. Default content type behavior. |
| 41 | content-xml | feature | 11 | application/problem+xml via Spring's built-in serialization. Conditional on XML support. |
| 42 | content-html | feature | 11 | HTML error pages. Template engine detection. Styled templates. Hardcoded HTML fallback. User-overridable. |

### Exclusions

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 43 | exclude-graphql | feature | 2 | ConditionalOnMissingClass for Spring GraphQL. Property to force-enable. Tests. |
| 44 | exclude-gateway | feature | 2 | ConditionalOnMissingClass for Spring Cloud Gateway. Property to force-enable. Tests. |

### Documentation

| # | Stream | Type | Blocked By | Deliverables |
|---|--------|------|------------|-------------|
| 45 | docs-config-reference | docs | 9, all features | Configuration reference: all error-handler.* properties with descriptions and defaults |
| 46 | docs-usage-guide | docs | all features | Getting started, common patterns, extension examples, migration guide |
| 47 | docs-sample-project | docs | all features | Working sample application demonstrating key features |
| 48 | docs-readme-update | docs | all features | Final README with accurate feature list, badges, quickstart |

## Dependency Graph

```
1 → 2 → 3 (build infrastructure)
2 → 4, 5, 6, 9 (API surface + config)
4 → 7, 8, 20 (strategy defaults)
7, 8, 9 → 10 (ProblemEngine)
10 → 11, 12 (interception)
10 → 13, 14 (security)
6, 10, 15 → 16, 17, 18, 19 (validation)
20 → 21 → 22 → 23 (sanitization chain)
10 → 24 → 25, 26, 27 (logging chain)
10 → 28 → 29 (i18n chain)
8, 9 → 30 (verbosity)
10 → 31 → 32 (tracing)
5, 10 → 33 → 34, 35 (context weaver)
34, 23 → 36 (capture + sanitize)
6, 10 → 37, 38 → 39 (propagation)
11 → 40, 41, 42 (content negotiation)
2 → 43, 44 (exclusions)
```

## Parallelism

Once stream 10 (problem-engine) is complete, these groups can run in parallel:
- Security (13-14)
- Validation (16-19)
- Logging (24-27)
- i18n (28-29, 30)
- Tracing (31-32)
- Annotations (33-36)
- Content negotiation (40-42)
- Propagation (37-39)

## Notes
- 48 streams total: 3 ops, 41 feature, 4 docs
- Streams 1-12 are the critical path to a functional MVP
- All streams produce tests alongside implementation
- Each stream maps to one or more capabilities from requirements.md
