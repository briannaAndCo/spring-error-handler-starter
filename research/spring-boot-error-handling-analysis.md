# Spring Boot Built-in Error Handling: Deep-Dive Analysis

> Research document for the Spring Error Handler Starter project.
> Covers Spring Boot error handling mechanisms in depth, focusing on what is provided, how it works, its limitations, and what changed in Spring Boot 3.x.

---

## Table of Contents

1. [The Overall Architecture](#1-the-overall-architecture)
2. [BasicErrorController](#2-basicerrorcontroller)
3. [@ControllerAdvice / @RestControllerAdvice](#3-controlleradvice--restcontrolleradvice)
4. [HandlerExceptionResolver Chain](#4-handlerexceptionresolver-chain)
5. [ResponseStatusException](#5-responsestatusexception)
6. [ProblemDetail — RFC 9457 Support (Spring 6 / Spring Boot 3)](#6-problemdetail--rfc-9457-support-spring-6--spring-boot-3)
7. [ErrorAttributes — DefaultErrorAttributes](#7-errorattributes--defaulterrorattributes)
8. [server.error.* Configuration Properties](#8-servererror-configuration-properties)
9. [Spring WebFlux Error Handling](#9-spring-webflux-error-handling)
10. [Spring Security Error Handling](#10-spring-security-error-handling)
11. [Validation Error Handling](#11-validation-error-handling)
12. [Version History — Key Changes by Release](#12-version-history--key-changes-by-release)
13. [Summary of Gaps and Pain Points](#13-summary-of-gaps-and-pain-points)

---

## 1. The Overall Architecture

Spring Boot's error handling is a layered system with multiple interception points. Understanding the request lifecycle is essential before examining individual components.

### Request Lifecycle (Spring MVC)

```
Incoming HTTP Request
       |
       v
Servlet Container (Tomcat/Jetty/Undertow)
       |
       v
Spring Security Filter Chain
  [ExceptionTranslationFilter catches AuthenticationException / AccessDeniedException]
       |
       v
DispatcherServlet
       |
       v
Handler Mapping → Handler (Controller method)
       |
  Exception thrown?
       |
       v
HandlerExceptionResolver Chain
  1. ExceptionHandlerExceptionResolver   (@ExceptionHandler / @ControllerAdvice)
  2. ResponseStatusExceptionResolver     (@ResponseStatus / ResponseStatusException)
  3. DefaultHandlerExceptionResolver     (Spring MVC built-in exceptions)
       |
  Still unresolved?
       |
       v
Exception propagates to Servlet Container
       |
       v
Container forwards request to /error (ErrorPage dispatch)
       |
       v
BasicErrorController
  - JSON response (non-browser clients)
  - HTML "Whitelabel" page (browser clients)
```

### Key Insight: Two Separate Error Paths

There are fundamentally two error paths in Spring Boot:

- **Path 1 (HandlerExceptionResolver chain):** Exceptions thrown inside controllers/handlers, resolved by `@ControllerAdvice` and related resolvers. This path has full access to the handler context.
- **Path 2 (BasicErrorController):** Everything that escapes Path 1 — filter exceptions, unresolved handler exceptions, servlet container-level errors. This path operates via an internal servlet `forward()` and has no access to the original request body.

These two paths are independent and largely incompatible, which is a primary source of developer pain.

---

## 2. BasicErrorController

### What It Is

`BasicErrorController` is the auto-configured "catch-all" error handler registered at the `/error` path. It is the last line of defense for errors that were not handled by `@ControllerAdvice` or any `HandlerExceptionResolver`.

**Introduced:** Spring Boot 1.0 (auto-configuration of an `/error` endpoint)

**Package:** `org.springframework.boot.autoconfigure.web.servlet.error`

**Auto-configured by:** `ErrorMvcAutoConfiguration`

### How It Works

When an unhandled exception escapes the `DispatcherServlet`, the Servlet container (Tomcat, Jetty, etc.) catches it and performs an internal `RequestDispatcher.forward()` to the `/error` path. The container injects request attributes before forwarding:

| Request Attribute | Description |
|---|---|
| `jakarta.servlet.error.status_code` | HTTP status code |
| `jakarta.servlet.error.exception` | The exception object |
| `jakarta.servlet.error.message` | Exception message |
| `jakarta.servlet.error.request_uri` | Original request URI |
| `jakarta.servlet.error.servlet_name` | Servlet that caused the error |

`BasicErrorController` reads these attributes (via `DefaultErrorAttributes`) and renders a response.

### Two Rendering Methods

`BasicErrorController` has two `@RequestMapping` methods, selected via content negotiation:

```java
// Returns HTML page for browser clients (text/html)
@RequestMapping(produces = MediaType.TEXT_HTML_VALUE)
public ModelAndView errorHtml(HttpServletRequest request, HttpServletResponse response) { ... }

// Returns JSON for all other clients (REST consumers, Postman, etc.)
@RequestMapping
public ResponseEntity<Map<String, Object>> error(HttpServletRequest request) { ... }
```

### Default JSON Response Structure

```json
{
  "timestamp": "2025-01-15T10:30:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/users/123"
}
```

Since Spring Boot 2.3, the `message` field is **not included by default** (see Section 8 for configuration).

### Default HTML Response (Whitelabel)

A minimal, branded error page showing status, error text, and (optionally) the message. Controlled by `server.error.whitelabel.enabled`.

### Customization Points

| Approach | Description |
|---|---|
| Extend `BasicErrorController` | Add handlers for new content types (e.g., XML); register as a `@Controller` bean |
| Implement `ErrorController` | Complete replacement of the `/error` endpoint |
| Replace `ErrorAttributes` bean | Customize what information is collected/exposed |
| Custom error pages | Static HTML in `src/main/resources/public/error/404.html` or templates in `src/main/resources/templates/error/5xx.ftlh` |
| `ErrorViewResolver` | Programmatic control over error view selection |
| `ErrorPageRegistrar` | Register custom error pages for specific status codes or exception types |

### Limitations / Pain Points

- **No access to the original request body.** The servlet `forward()` mechanism discards the body. This makes it impossible to include request body context in error responses.
- **Double dispatch overhead.** Every unhandled error triggers an extra round-trip through the servlet pipeline (`forward()` to `/error`).
- **Inconsistency with `@ControllerAdvice`.** Errors handled by `@ControllerAdvice` never reach `BasicErrorController`. Errors from filters always do. This split makes unified error response formatting extremely difficult.
- **No exception type routing.** `BasicErrorController` cannot route different error responses based on exception type — it only knows the status code and message from request attributes. The original exception object is present as an attribute but is awkward to use directly.
- **Format mismatch with `@ControllerAdvice`.** If you customize error response format in `@ControllerAdvice`, `BasicErrorController` will still return the old format for filter-level and unhandled exceptions.
- **Whitelabel page is unhelpful.** The default HTML error page provides no useful information in production and must always be customized.

---

## 3. @ControllerAdvice / @RestControllerAdvice

### What It Is

`@ControllerAdvice` is a specialization of `@Component` that declares cross-cutting concerns (exception handling, model attributes, data binding) applicable across multiple controllers. `@RestControllerAdvice` combines `@ControllerAdvice` with `@ResponseBody`, making all methods implicitly return response bodies.

**Introduced:** Spring Framework 3.2

**Powered by:** `ExceptionHandlerExceptionResolver` (first in the `HandlerExceptionResolver` chain)

### How It Works

`@ExceptionHandler` methods inside a `@ControllerAdvice` class are invoked by `ExceptionHandlerExceptionResolver` when a matching exception is thrown during handler (controller) execution. The resolver scans all registered `@ControllerAdvice` beans and finds the best match.

### Exception Matching Rules

1. **Local `@Controller` handlers take priority** over `@ControllerAdvice` handlers.
2. Within a single `@ControllerAdvice`, the most specific exception type wins.
3. As of Spring 5.3, matching searches at arbitrary cause levels in the exception chain, not just the top-level exception. `ExceptionDepthComparator` ranks matches by depth.
4. **Root exception match vs. cause match:** A cause-match on a higher-priority `@ControllerAdvice` is preferred over a root-match on a lower-priority one.

### Multiple @ControllerAdvice Ordering

Multiple `@ControllerAdvice` beans are ordered by:
- `@Order` annotation (lower value = higher priority, evaluated first)
- `@Priority` annotation
- Implementing `Ordered` interface

Best practice: declare a primary global handler with a low `@Order` value; Spring's own `ResponseEntityExceptionHandler` has `@Order(0)` when auto-configured via `spring.mvc.problemdetails.enabled`.

### Scoping

`@ControllerAdvice` can be scoped to only apply to certain controllers:

```java
// Applies only to controllers in specific packages
@ControllerAdvice(basePackages = "com.example.api")

// Applies only to controllers annotated with @RestController
@ControllerAdvice(annotations = RestController.class)

// Applies only to controllers in these specific classes
@ControllerAdvice(assignableTypes = {SomeController.class, AnotherController.class})
```

### Supported @ExceptionHandler Return Types

| Return Type | Behavior |
|---|---|
| `ResponseEntity<T>` | Full control over status + headers + body |
| `ProblemDetail` / `ErrorResponse` | RFC 9457 format (Spring 6+) |
| `String` | View name for HTML response |
| `ModelAndView` | View + model for HTML response |
| `void` | Fully handled inline (requires writing to response directly) |
| `@ResponseBody` on method | Body is serialized as response |

### Media Type Routing (Spring 6+)

As of Spring 6, `@ExceptionHandler` methods can declare `produces` to route based on the `Accept` header:

```java
@ExceptionHandler(produces = "application/json")
public ResponseEntity<ErrorBody> handleAsJson(MyException ex) { ... }

@ExceptionHandler(produces = "text/html")
public String handleAsHtml(MyException ex, Model model) { ... }
```

### Limitations / Pain Points

- **Does not catch Security filter exceptions.** `AuthenticationException` and `AccessDeniedException` thrown by Spring Security filters (which run before `DispatcherServlet`) cannot be caught by `@ControllerAdvice`. Separate `AuthenticationEntryPoint` and `AccessDeniedHandler` are required.
- **Does not catch exceptions from other filters.** Any `Throwable` thrown in a `javax.servlet.Filter` or `jakarta.servlet.Filter` escapes the `@ControllerAdvice` boundary.
- **Inconsistency with `BasicErrorController` fallback.** Exceptions not handled by any `@ControllerAdvice` fall through to `BasicErrorController` with a different response format.
- **No `@ControllerAdvice` for WebSocket / SSE errors.** These require separate handling mechanisms.
- **Global `@ControllerAdvice` applies to all handlers including `BasicErrorController`.** This can cause subtle double-handling issues or unexpected matching.
- **Order dependency creates fragility.** Applications with multiple `@ControllerAdvice` beans (e.g., from different libraries or teams) can produce non-obvious ordering conflicts.

---

## 4. HandlerExceptionResolver Chain

### The Default Chain

Spring MVC registers three built-in `HandlerExceptionResolver` implementations, processed in this order:

| Order | Resolver | What It Handles |
|---|---|---|
| 1 (highest) | `ExceptionHandlerExceptionResolver` | `@ExceptionHandler` methods in `@Controller` and `@ControllerAdvice` |
| 2 | `ResponseStatusExceptionResolver` | `@ResponseStatus`-annotated exceptions; `ResponseStatusException` subclasses |
| 3 | `DefaultHandlerExceptionResolver` | Spring MVC's own built-in exceptions (see list below) |

### ExceptionHandlerExceptionResolver (Order 1)

- Invoked first.
- Scans `@Controller` methods first, then all `@ControllerAdvice` beans (ordered).
- Returns `null` if no matching `@ExceptionHandler` is found, passing control to the next resolver.

### ResponseStatusExceptionResolver (Order 2)

Handles two cases:

1. **`@ResponseStatus` on an exception class:**
   ```java
   @ResponseStatus(HttpStatus.NOT_FOUND)
   public class ResourceNotFoundException extends RuntimeException { ... }
   ```
2. **`ResponseStatusException` (and subclasses) thrown programmatically:**
   ```java
   throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
   ```

Writes the HTTP status and reason phrase directly to the response.

### DefaultHandlerExceptionResolver (Order 3)

Handles Spring MVC's own built-in exceptions, mapping them to appropriate HTTP status codes:

| Exception | HTTP Status |
|---|---|
| `HttpRequestMethodNotSupportedException` | 405 Method Not Allowed |
| `HttpMediaTypeNotSupportedException` | 415 Unsupported Media Type |
| `HttpMediaTypeNotAcceptableException` | 406 Not Acceptable |
| `MissingPathVariableException` | 500 Internal Server Error |
| `MissingServletRequestParameterException` | 400 Bad Request |
| `MissingServletRequestPartException` | 400 Bad Request |
| `ServletRequestBindingException` | 400 Bad Request |
| `MethodArgumentNotValidException` | 400 Bad Request |
| `HandlerMethodValidationException` | 400 Bad Request (Spring 6.1+) |
| `NoHandlerFoundException` | 404 Not Found |
| `AsyncRequestTimeoutException` | 503 Service Unavailable |
| `HttpMessageNotReadableException` | 400 Bad Request |
| `HttpMessageNotWritableException` | 500 Internal Server Error |
| `TypeMismatchException` | 400 Bad Request |

**Note:** `DefaultHandlerExceptionResolver` writes the status code but produces **no response body**. This is a significant limitation — clients receive an empty body for many common errors. `ResponseEntityExceptionHandler` (Spring 5.3+) is the higher-level alternative that does produce a body.

### SimpleMappingExceptionResolver

An optional resolver (not registered by default) that maps exception class names to error view names. Primarily used for server-side rendered applications. Not auto-configured by Spring Boot.

### Resolution Flow and Fallback

If all three resolvers return `null` (cannot handle the exception), the exception propagates to the Servlet container, which forwards it to `/error` (handled by `BasicErrorController`).

### Limitations / Pain Points

- **`DefaultHandlerExceptionResolver` produces no response body.** Clients receive a bare status code — this surprises many developers who expect a JSON error body.
- **The chain cannot be easily extended.** Adding a custom `HandlerExceptionResolver` bean requires careful ordering to avoid interfering with the built-in chain.
- **Error body format is inconsistent across resolvers.** `ExceptionHandlerExceptionResolver` returns whatever `@ExceptionHandler` returns; `ResponseStatusExceptionResolver` writes directly to the response; `DefaultHandlerExceptionResolver` writes no body.

---

## 5. ResponseStatusException

### What It Is

`ResponseStatusException` is a `RuntimeException` subclass that carries an HTTP status code and optional reason/cause. It was designed to provide a convenient, annotation-free way to signal HTTP errors from controller code.

**Introduced:** Spring Framework 5.0 (Spring Boot 2.0)

**Package:** `org.springframework.web.server`

### How It Works

```java
// Simple usage — throw anywhere in a controller
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

// With cause exception
throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Upstream failed", ex);
```

Handled by `ResponseStatusExceptionResolver` (Order 2 in the chain), which reads the status and reason and sets them on the response.

### In Spring 6 / Spring Boot 3: ErrorResponse Integration

In Spring 6, `ResponseStatusException` implements the `ErrorResponse` interface, meaning it produces a `ProblemDetail`-formatted body when `spring.mvc.problemdetails.enabled=true` or when using `ResponseEntityExceptionHandler`.

### Sub-types (Spring 6+)

Spring MVC exceptions are now subtypes of `ResponseStatusException` via the `ErrorResponseException` base class:

```
ErrorResponseException (implements ErrorResponse)
    → All Spring MVC built-in exceptions (e.g., MethodArgumentNotValidException)
    → ResponseStatusException (for programmatic use)
```

### Advantages

- No need to create a custom exception class per error type.
- Can produce different status codes for the same logical error (e.g., `NOT_FOUND` vs `GONE`).
- Works from within the controller without `@ControllerAdvice` setup.
- Carries the original cause exception for logging.

### Limitations / Pain Points

- **No centralized handling.** Thrown ad-hoc across controllers, making consistent error response structure harder to enforce.
- **Code duplication.** The same status/message combination may be repeated across multiple controllers.
- **Reason phrase is locale-insensitive and not i18n-friendly.** The `reason` string is written directly to the response — no `MessageSource` lookup.
- **No custom response headers.** No built-in mechanism to set custom headers alongside the error (unlike returning `ResponseEntity`).
- **Clutters controller logic.** Mixing business logic with HTTP concerns violates separation of concerns.
- **Not suitable as a base for structured error bodies.** Without ProblemDetail support, the response body content is minimal.

---

## 6. ProblemDetail — RFC 9457 Support (Spring 6 / Spring Boot 3)

### What It Is

RFC 9457 ("Problem Details for HTTP APIs") defines a standard JSON/XML format for error responses. Spring Framework 6 and Spring Boot 3 introduced first-class support for this standard.

**Introduced:** Spring Framework 6.0 / Spring Boot 3.0 (November 2022)

**Replaces:** RFC 7807 (same concept, updated spec)

### Standard ProblemDetail Fields

```json
{
  "type": "https://example.com/errors/not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "User with ID 123 was not found in the system",
  "instance": "/api/users/123"
}
```

| Field | Type | Description | Default |
|---|---|---|---|
| `type` | URI | Identifies the problem type | `about:blank` |
| `title` | String | Short human-readable summary | Derived from HTTP status |
| `status` | Integer | HTTP status code | Set by exception |
| `detail` | String | Specific explanation of this occurrence | None |
| `instance` | URI | URI identifying the specific occurrence | Current request path |

Non-standard fields can be added via `setProperty(key, value)` or by extending `ProblemDetail`.

### Core Abstractions

#### ProblemDetail

The data object representing an RFC 9457 response body.

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "User not found");
problem.setType(URI.create("https://api.example.com/errors/user-not-found"));
problem.setTitle("User Not Found");
problem.setProperty("userId", 123);  // non-standard extension field
```

#### ErrorResponse Interface

Contract implemented by all Spring MVC exceptions (as of Spring 6). Exposes:
- HTTP status
- Response headers
- `ProblemDetail` body

All Spring MVC exceptions (`MethodArgumentNotValidException`, `HttpRequestMethodNotSupportedException`, etc.) implement `ErrorResponse`.

#### ErrorResponseException

Base class for creating custom RFC 9457-compliant exceptions:

```java
public class UserNotFoundException extends ErrorResponseException {
    public UserNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND,
              ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                  "User " + userId + " not found"),
              null);
    }
}
```

#### ResponseEntityExceptionHandler

A `@ControllerAdvice` base class that handles all Spring MVC exceptions and renders them as RFC 9457 responses. Provides protected handler methods for each exception type that can be overridden:

```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // custom override
    }
}
```

### Enabling ProblemDetail in Spring Boot

#### Spring Boot 3.0 — 3.2 (opt-in)
```properties
spring.mvc.problemdetails.enabled=true
spring.webflux.problemdetails.enabled=true
```

When enabled, Spring Boot auto-configures a `ResponseEntityExceptionHandler` at `@Order(0)`.

#### Spring Boot 3.3+ (default enabled)
ProblemDetail is enabled by default. The `spring.mvc.problemdetails.enabled` property still exists for opt-out.

### Internationalization (i18n)

`ResponseEntityExceptionHandler` resolves message codes via `MessageSource`. The message code conventions are:

| Field | Message Code Pattern |
|---|---|
| `type` | `problemDetail.type.{fully.qualified.ExceptionClass}` |
| `title` | `problemDetail.title.{fully.qualified.ExceptionClass}` |
| `detail` | `problemDetail.{fully.qualified.ExceptionClass}[.suffix]` |

```properties
# messages.properties
problemDetail.type.org.springframework.web.servlet.NoHandlerFoundException=https://errors.example.com/not-found
problemDetail.title.org.springframework.web.servlet.NoHandlerFoundException=Resource Not Found
problemDetail.org.springframework.web.servlet.NoHandlerFoundException=No handler found for {0} {1}
```

### Content Negotiation

When ProblemDetail is enabled, the Jackson codec uses `application/problem+json` and `application/problem+xml` as the media types, favored during content negotiation over `application/json`.

### Issue: BasicErrorController Does Not Use ProblemDetail

As of Spring Boot 3.3/3.4, `BasicErrorController` does **not** align with `spring.mvc.problemdetails.enabled`. Exceptions that fall through to `BasicErrorController` (e.g., filter exceptions) still return the legacy `DefaultErrorAttributes` format, not RFC 9457. This is a known issue tracked at [spring-boot#48392](https://github.com/spring-projects/spring-boot/issues/48392) and represents a significant gap.

### Limitations / Pain Points

- **`BasicErrorController` does not participate.** There is no unified ProblemDetail format for all error paths.
- **`type` defaults to `about:blank`.** Developers must explicitly set a meaningful URI for the `type` field, or all errors look identical to RFC-aware clients.
- **No built-in error catalog.** The spec encourages maintaining a catalog of problem types at stable URIs, but Spring provides no infrastructure for this.
- **Non-standard fields must be added per-exception.** There is no global interceptor to add standard fields (e.g., `traceId`, `timestamp`) to every `ProblemDetail` response.
- **Spring Boot auto-configuration nuance.** When `spring.mvc.problemdetails.enabled=true` auto-configures a `ResponseEntityExceptionHandler` at `@Order(0)`, a custom `@ControllerAdvice extends ResponseEntityExceptionHandler` must use a negative `@Order` to take precedence.

---

## 7. ErrorAttributes — DefaultErrorAttributes

### What It Is

`ErrorAttributes` is the interface that collects error information from the current request context. It is used by both `BasicErrorController` (servlet path) and the `/error` endpoint rendering pipeline.

**Package:** `org.springframework.boot.web.servlet.error` (MVC) / `org.springframework.boot.web.reactive.error` (WebFlux)

### DefaultErrorAttributes

The default implementation. It reads error information from request attributes (set by the servlet container on error dispatch) and from the current exception in the `WebRequest`.

**Default attributes collected:**

| Attribute | Always Included | Condition |
|---|---|---|
| `timestamp` | Yes | Always |
| `status` | Yes | Always |
| `error` | Yes | HTTP status reason phrase |
| `path` | Yes | Always |
| `exception` | No | `server.error.include-exception=true` |
| `message` | No | `server.error.include-message=always` (was hidden in 2.3+) |
| `errors` | No | `server.error.include-binding-errors=always` |
| `trace` | No | `server.error.include-stacktrace=always` |

### Key Change in Spring Boot 2.3

Before Spring Boot 2.3, `message` and `errors` (binding result errors) were included in the default error response. In 2.3, they were **hidden by default** for security reasons — exception messages could reveal implementation details, database schema information, or sensitive configuration. This was a breaking change that confused many developers.

To restore the pre-2.3 behavior:
```properties
server.error.include-message=always
server.error.include-binding-errors=always
```

### Customization

**Approach 1: Extend `DefaultErrorAttributes`**
```java
@Component
public class MyErrorAttributes extends DefaultErrorAttributes {
    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
        attributes.put("service", "user-service");
        attributes.remove("trace");  // always remove stack trace
        return attributes;
    }
}
```

**Approach 2: Implement `ErrorAttributes` directly**

For complete control, implement the interface and register as a `@Component` bean.

### Limitations / Pain Points

- **Only affects the `BasicErrorController` path.** `ErrorAttributes` is not consulted when `@ControllerAdvice` handles an exception — those return their own response body directly.
- **Schema is fixed and opinionated.** The key names (`timestamp`, `status`, `error`, `path`) are Spring Boot-specific. Changing them (e.g., to match an RFC 9457 schema) requires overriding the entire collection logic.
- **`ErrorAttributeOptions` is not well-known.** The `options` parameter controls which optional fields are included, but its configuration is driven by `server.error.*` properties — there is no programmatic per-exception control.
- **Cannot add per-exception custom fields.** `DefaultErrorAttributes` does not know which exception type occurred at a detailed level — all it has is the exception object from the request attribute.

---

## 8. server.error.* Configuration Properties

All `server.error.*` properties affect the `BasicErrorController` / `DefaultErrorAttributes` path only. They have no effect on `@ControllerAdvice` responses.

### Complete Property Reference

| Property | Default | Values | Description |
|---|---|---|---|
| `server.error.path` | `/error` | Any path | URL mapping for the error controller |
| `server.error.whitelabel.enabled` | `true` | `true`, `false` | Enable/disable the default HTML error page |
| `server.error.include-exception` | `false` | `true`, `false` | Include the `exception` class name in the response |
| `server.error.include-message` | `never` | `never`, `on-param`, `always` | Include the `message` field |
| `server.error.include-binding-errors` | `never` | `never`, `on-param`, `always` | Include the `errors` field (validation binding results) |
| `server.error.include-stacktrace` | `never` | `never`, `on-param`, `always` | Include the `trace` field (full stack trace) |

### Property Value Semantics

- `never`: Field is never included in the response.
- `always`: Field is always included.
- `on-param`: Field is included only when a `trace=true` (or `errors=true`) query parameter is present in the request. Useful for development debugging without permanently exposing data.

### Custom Error Pages

Static error pages can be placed at:
- `src/main/resources/public/error/404.html` (specific status code)
- `src/main/resources/public/error/5xx.html` (wildcard series)
- `src/main/resources/templates/error/404.html` (template engine)
- `src/main/resources/templates/error/5xx.ftlh` (FreeMarker)

Spring Boot searches for exact status codes first, then series masks, then falls back to a generic `error` template.

### Limitations / Pain Points

- **Properties only affect the `BasicErrorController` path.** If all errors are handled by `@ControllerAdvice`, these properties are irrelevant.
- **`on-param` is a security anti-pattern in production.** Query-parameter-controlled stack trace exposure is convenient for development but must be disabled in production.
- **No environment-based switching.** Spring Boot does not auto-configure different `server.error.*` defaults for `dev` vs `prod` profiles — developers must manage this manually.

---

## 9. Spring WebFlux Error Handling

### Architecture Differences from MVC

Spring WebFlux has a fundamentally different error handling architecture because it is non-blocking and reactive. There is no Servlet container, no `RequestDispatcher.forward()`, and no `DispatcherServlet`.

| Aspect | Spring MVC | Spring WebFlux |
|---|---|---|
| Core handler | `BasicErrorController` | `DefaultErrorWebExceptionHandler` |
| Resolver chain | `HandlerExceptionResolver` | Not applicable (different mechanism) |
| Error routing | Servlet container `forward()` | `WebExceptionHandler` filter |
| Auto-configuration | `ErrorMvcAutoConfiguration` | `ErrorWebFluxAutoConfiguration` |
| Reactive return types | Not applicable | `Mono<T>`, `Flux<T>` supported |
| Thread model | Thread-per-request (blocking) | Event loop (non-blocking) |

### WebExceptionHandler

The core contract in WebFlux for handling exceptions at the filter/router level:

```java
public interface WebExceptionHandler {
    Mono<Void> handle(ServerWebExchange exchange, Throwable ex);
}
```

### DefaultErrorWebExceptionHandler

Spring Boot's auto-configured WebFlux error handler. Extends `AbstractErrorWebExceptionHandler` and:
- Serves JSON error responses for REST clients.
- Serves HTML error pages for browser clients.
- Is registered at a very low order (before all application `WebExceptionHandler` beans).

### @ExceptionHandler in WebFlux

Works the same as in MVC, but methods can return reactive types:

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UserNotFoundException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleNotFound(UserNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        return Mono.just(ResponseEntity.status(404).body(problem));
    }
}
```

### RFC 9457 in WebFlux

Enabled separately from MVC:

```properties
spring.webflux.problemdetails.enabled=true
```

### Custom Error Handling in WebFlux

Three extension points, in increasing complexity:

1. **Customize `ErrorAttributes`:** Implement `org.springframework.boot.web.reactive.error.ErrorAttributes`.
2. **Extend `DefaultErrorWebExceptionHandler`:** Override specific routing/rendering methods.
3. **Implement `AbstractErrorWebExceptionHandler`:** Full control using functional routing style:

```java
@Component
@Order(-2)  // Must be higher priority than DefaultErrorWebExceptionHandler (-1)
public class GlobalWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Map<String, Object> errorProperties = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        return ServerResponse.status(HttpStatus.valueOf((Integer) errorProperties.get("status")))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(errorProperties);
    }
}
```

### WebFlux-Specific Pain Points

- **Order of `WebExceptionHandler` beans is critical.** `DefaultErrorWebExceptionHandler` is at `@Order(-1)`. Custom handlers must be declared at a more negative order value.
- **Exception handling via `onErrorResume` in reactive chains.** WebFlux reactive pipelines require handling errors inline with `Mono.onErrorResume()` / `Flux.onErrorResume()`. Using only `@ExceptionHandler` is insufficient for errors that occur inside reactive pipelines.
- **Bridging reactor errors to HTTP responses is non-trivial.** Errors wrapped in `reactor.core.Exceptions` may not be unwrapped correctly by `@ExceptionHandler`.
- **No direct equivalent to `BasicErrorController` for WebFlux.** While `DefaultErrorWebExceptionHandler` serves a similar purpose, the customization model is different and more complex.

---

## 10. Spring Security Error Handling

### Why @ControllerAdvice Cannot Handle Security Errors

Spring Security filters run **before** the `DispatcherServlet`. When `ExceptionTranslationFilter` catches an `AuthenticationException` or `AccessDeniedException`, the exception never reaches the Spring MVC layer. Therefore, `@ControllerAdvice` and `@ExceptionHandler` **cannot** intercept these exceptions.

This is one of the most commonly misunderstood aspects of Spring Boot error handling.

### ExceptionTranslationFilter

The central component for translating security exceptions into HTTP responses:

```
Downstream Filter throws AuthenticationException
          ↓
ExceptionTranslationFilter catches it
          ↓
Is user anonymous?
  → Yes: Invoke AuthenticationEntryPoint (prompt for credentials → 401)
  → No: Invoke AccessDeniedHandler (→ 403)
```

Additionally, before invoking `AuthenticationEntryPoint`, the filter saves the original request via `RequestCache` so it can be replayed after successful authentication.

### AuthenticationEntryPoint

Interface for handling `AuthenticationException` — triggered for unauthenticated requests:

```java
public interface AuthenticationEntryPoint {
    void commence(HttpServletRequest request, HttpServletResponse response,
                  AuthenticationException authException) throws IOException, ServletException;
}
```

Default behavior: redirects to login page (for form login) or sends a `WWW-Authenticate` header with 401 (for HTTP Basic).

**Custom REST API implementation:**
```java
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getOutputStream().println(new ObjectMapper().writeValueAsString(
            Map.of("error", "Unauthorized", "message", ex.getMessage())
        ));
    }
}
```

### AccessDeniedHandler

Interface for handling `AccessDeniedException` — triggered for authenticated users without permission:

```java
public interface AccessDeniedHandler {
    void handle(HttpServletRequest request, HttpServletResponse response,
                AccessDeniedException accessDeniedException) throws IOException, ServletException;
}
```

Default behavior: returns a 403 response (can redirect to an access denied page).

### Configuration

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(customEntryPoint)
            .accessDeniedHandler(customDeniedHandler)
        )
        .build();
}
```

### Workaround: Delegating to HandlerExceptionResolver

A common pattern to make security exceptions "look like" they were handled by `@ControllerAdvice` is to delegate from `AuthenticationEntryPoint` / `AccessDeniedHandler` to the `HandlerExceptionResolver` composite:

```java
@Component
public class DelegatingAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final HandlerExceptionResolver resolver;

    public DelegatingAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res,
                         AuthenticationException ex) {
        resolver.resolveException(req, res, null, ex);
    }
}
```

This allows `@ExceptionHandler(AuthenticationException.class)` methods in `@ControllerAdvice` to handle the exception — but requires the explicit delegation setup.

### Limitations / Pain Points

- **Two completely separate error handling systems.** Spring Security uses `AuthenticationEntryPoint` / `AccessDeniedHandler`; Spring MVC uses `@ControllerAdvice`. These must be separately configured and kept in sync for consistent error response format.
- **Method security exceptions are partially catchable.** `AccessDeniedException` thrown by `@PreAuthorize` / `@Secured` inside a controller method (after `DispatcherServlet` processing begins) can be caught by `@ControllerAdvice`. But the same exception thrown during filter processing cannot — same exception class, different behavior.
- **ProblemDetail does not automatically cover security errors.** Even with `spring.mvc.problemdetails.enabled=true`, security errors need separate ProblemDetail-aware `AuthenticationEntryPoint` / `AccessDeniedHandler` implementations.

---

## 11. Validation Error Handling

### Two Distinct Exception Types

Spring Boot validation produces two fundamentally different exceptions depending on where and how validation is applied. They require **separate** exception handlers.

### MethodArgumentNotValidException

**Triggered by:** `@Valid` or `@Validated` on `@RequestBody` or `@ModelAttribute` parameters in controllers.

**HTTP Status:** 400 Bad Request (automatically by `DefaultHandlerExceptionResolver` and `ResponseEntityExceptionHandler`).

**Error structure:** Contains a `BindingResult` with `FieldError` and `ObjectError` objects.

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    List<String> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .collect(Collectors.toList());
    return ResponseEntity.badRequest().body(Map.of("errors", errors));
}
```

### ConstraintViolationException

**Triggered by:** `@Validated` on method parameters that are `@PathVariable`, `@RequestParam`, or service-layer method parameters (when `MethodValidationPostProcessor` is configured).

**HTTP Status:** 500 Internal Server Error by default — **Spring Boot does not automatically handle this with a 400 response.** This is a very common pain point.

**Error structure:** Contains `ConstraintViolation` objects with property paths and invalid values.

```java
@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
    List<String> errors = ex.getConstraintViolations().stream()
        .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
        .collect(Collectors.toList());
    return ResponseEntity.badRequest().body(Map.of("errors", errors));
}
```

### HandlerMethodValidationException (Spring 6.1 / Spring Boot 3.2)

**Introduced:** Spring Framework 6.1 / Spring Boot 3.2 (November 2023)

**Triggered by:** Method-level constraint validation on controller method parameters (path variables, request params, headers) when not using `@Valid` on a request body.

**HTTP Status:** 400 for input validation, 500 for return value validation.

This exception was introduced to unify method parameter validation under a single Spring-managed exception (instead of leaking `ConstraintViolationException` from Hibernate Validator into the web layer).

`ResponseEntityExceptionHandler` handles it via `handleHandlerMethodValidationException()`.

**Key nuance:** Applications may need to handle all three exceptions depending on usage patterns:
- `MethodArgumentNotValidException` for request body validation
- `HandlerMethodValidationException` for parameter/header validation (Spring 6.1+)
- `ConstraintViolationException` for service-layer and pre-6.1 parameter validation

### Key Comparison Table

| Aspect | `MethodArgumentNotValidException` | `ConstraintViolationException` | `HandlerMethodValidationException` |
|---|---|---|---|
| Spring type | Spring Web | Bean Validation API (JSR 380) | Spring Web (6.1+) |
| Layer | Controller only | Controller + Service | Controller (method params) |
| Default HTTP status | 400 | **500** | 400 |
| Handled by default | Yes (DefaultHandlerExceptionResolver) | **No** | Yes (Spring 6.1+, ResponseEntityExceptionHandler) |
| Introduced | Spring 3.x | Not Spring-specific | Spring 6.1 |
| Error structure | `BindingResult` / `FieldError` | `ConstraintViolation` set | `MethodValidationResult` |

### Validation Error Response Format

By default, validation errors (binding result fields) are **not** included in the `BasicErrorController` response. They must be enabled:

```properties
server.error.include-binding-errors=always
```

Or extracted and formatted manually in `@ExceptionHandler`.

### Limitations / Pain Points

- **`ConstraintViolationException` returns 500 by default.** Developers must explicitly handle it or clients receive an Internal Server Error for what is logically a bad request.
- **Three different exception types for "validation failed".** No single handler catches all validation errors — applications need to handle at least two (often three) exception classes.
- **Inconsistent error field structure.** `BindingResult` errors have `field`, `rejectedValue`, and `defaultMessage`. `ConstraintViolation` errors have `propertyPath`, `invalidValue`, and `messageTemplate`. Normalizing these into a consistent response schema requires custom code.
- **Nested validation errors.** Validating nested objects produces errors with dotted property paths (e.g., `address.zipCode`). Rendering these clearly in a response requires custom handling.
- **Group-ordered validation.** Jakarta Validation groups allow staged validation (group A before group B), but Spring's default handling does not expose which group failed — all violations are returned together.
- **Custom constraint messages require `MessageSource` integration.** By default, constraint messages come from the Bean Validation `messages.properties`, not Spring's `MessageSource`. Making them consistent requires configuring a Spring-aware `MessageInterpolator`.

---

## 12. Version History — Key Changes by Release

| Version | Key Changes |
|---|---|
| **Spring Boot 1.0** | `BasicErrorController` and `DefaultErrorAttributes` introduced. Whitelabel error page. |
| **Spring Boot 2.0** | `ResponseStatusException` available (Spring 5.0). `DefaultHandlerExceptionResolver` updated. |
| **Spring Boot 2.3** | `message` and `errors` fields **removed from default error response** (security hardening). `server.error.include-message` and `server.error.include-binding-errors` properties added. |
| **Spring Boot 2.5** | `ErrorAttributeOptions` introduced for programmatic control of included attributes. |
| **Spring Framework 5.3** | `@ExceptionHandler` now searches at arbitrary cause levels (not just top-level). `ExceptionDepthComparator` improvements. |
| **Spring Boot 3.0 / Spring 6.0** | `ProblemDetail`, `ErrorResponse`, `ErrorResponseException`, `ResponseEntityExceptionHandler` updated for RFC 9457. All Spring MVC exceptions implement `ErrorResponse`. `spring.mvc.problemdetails.enabled` property added (default `false`). Jakarta EE namespace migration. |
| **Spring Boot 3.2 / Spring 6.1** | `HandlerMethodValidationException` introduced. `ResponseEntityExceptionHandler.handleHandlerMethodValidationException()` added. Method validation unified under a Spring-managed exception. |
| **Spring Boot 3.3** | `spring.mvc.problemdetails.enabled` defaults to `true`. ProblemDetail support effectively on by default. |
| **Spring Boot 3.4+** | Ongoing alignment between `BasicErrorController` and ProblemDetail (tracked in [spring-boot#48392](https://github.com/spring-projects/spring-boot/issues/48392)). |

---

## 13. Summary of Gaps and Pain Points

This section synthesizes the key limitations of Spring Boot's built-in error handling that represent opportunities for a higher-level starter.

### Gap 1: No Unified Error Response Format

The single biggest problem. Spring Boot has two independent error pipelines:
- `@ControllerAdvice` / `HandlerExceptionResolver` (for controller-level exceptions)
- `BasicErrorController` / `DefaultErrorAttributes` (for everything else)

These two paths produce responses in incompatible formats. Even with ProblemDetail enabled, `BasicErrorController` still returns the legacy `DefaultErrorAttributes` format for filter-level exceptions and uncaught errors. A starter must bridge this gap.

### Gap 2: Security Exceptions Are Orphaned

`AuthenticationException` and `AccessDeniedException` (when thrown by security filters) require a completely separate configuration (`AuthenticationEntryPoint`, `AccessDeniedHandler`). They cannot participate in `@ControllerAdvice` or ProblemDetail handling without explicit plumbing code. Most teams duplicate error formatting logic in three places: MVC advice, security entry point, and security access denied handler.

### Gap 3: Validation Exception Fragmentation

Three different exception types for validation failures (`MethodArgumentNotValidException`, `ConstraintViolationException`, `HandlerMethodValidationException`), with different structures and inconsistent default behaviors (especially `ConstraintViolationException` defaulting to 500). No built-in way to normalize them into a single consistent response schema.

### Gap 4: ProblemDetail `type` Field is Always `about:blank`

Spring produces technically valid but semantically useless ProblemDetail responses when `type` is not explicitly set per exception. No infrastructure exists for maintaining a catalog of problem types and automatically applying them.

### Gap 5: No Global Response Field Injection

Common fields like `traceId`, `requestId`, `timestamp`, or `service` cannot be injected into all error responses without either extending `DefaultErrorAttributes` (for the `BasicErrorController` path) and `ResponseEntityExceptionHandler` (for the `@ControllerAdvice` path) separately. There is no single interception point.

### Gap 6: No Environment-Aware Error Detail Configuration

Spring Boot does not auto-configure different verbosity levels for development vs. production. The `server.error.include-message=never` default is production-safe but frustrating in development. Developers must manually manage profile-specific properties.

### Gap 7: Exception-to-Status Mapping Is Scattered

`@ResponseStatus`, `ResponseStatusException`, `@ControllerAdvice` handlers, and `DefaultHandlerExceptionResolver` all handle exception-to-status mapping in different ways. There is no single registry or DSL for defining "this exception class → this HTTP status + message + type URI" mappings.

### Gap 8: No i18n for Security and Filter Errors

The `ResponseEntityExceptionHandler` i18n support (via `MessageSource`) applies only to exceptions it handles. Security errors and filter-level errors receive no i18n treatment.

### Gap 9: Stack Trace and Sensitive Data Exposure Requires Manual Discipline

The `server.error.include-stacktrace` and `server.error.include-message` properties are global. There is no mechanism to always expose `message` for some exception types (e.g., business validation errors) while hiding it for others (e.g., database errors). This forces teams to choose between developer experience and security.

### Gap 10: WebFlux Parity

WebFlux has an entirely separate error handling model (`WebExceptionHandler`, `AbstractErrorWebExceptionHandler`). A starter targeting both MVC and WebFlux must implement two parallel systems with no shared abstractions from the framework.

---

*Document generated: 2026-03-30*
*Primary sources: Spring Framework 6.x documentation, Spring Boot 3.x reference, GitHub issues spring-projects/spring-boot and spring-projects/spring-framework*
