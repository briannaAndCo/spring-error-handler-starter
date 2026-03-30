# Research Summary & Positioning Recommendation

**Stream:** research
**Date:** 2026-03-30
**Status:** Complete — unblocks core-design

---

## Executive Summary

The Spring error handling ecosystem has a clear gap: **no well-adopted starter makes RFC 9457 the zero-configuration default while covering all error sources (controllers, filters, security) with a unified response format.** The market leader (wimdeblauwe, 502 stars) was designed pre-RFC 9457 and treats it as an opt-in mode. Zalando's historically dominant library is in maintenance mode. Spring's built-in support is fragmented across two incompatible error paths.

A Spring Boot 4-native starter that is RFC-9457-first, covers all error sources, and auto-injects trace IDs has no direct well-adopted competition.

---

## Research Deliverables

| Document | Description |
|----------|-------------|
| [spring-initializr-third-party-starter-guidelines.md](./spring-initializr-third-party-starter-guidelines.md) | Complete acceptance criteria for start.spring.io listing, with go/no-go checklist |
| [ecosystem-audit.md](./ecosystem-audit.md) | Competitive analysis of 8 libraries/starters with feature matrix |
| [spring-boot-error-handling-analysis.md](./spring-boot-error-handling-analysis.md) | Deep-dive into Spring Boot's 10 built-in error handling mechanisms |
| [gap-analysis.md](./gap-analysis.md) | 12 identified gaps with severity/coverage scoring and priority matrix |

---

## Key Findings

### 1. Spring Initializr Acceptance — What It Takes

**Hard requirements:**
- Artifact named `{name}-spring-boot-starter` (NOT `spring-boot-*`)
- Apache 2.0-compatible license (all transitive deps too)
- Published to Maven Central with sources, javadoc, GPG signatures
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Configuration metadata for all `@ConfigurationProperties`
- Both Maven and Gradle supported
- Active community with evidence (stars, downloads, Stack Overflow)

**Soft requirements (team discretion):**
- Strategic alignment with Spring ecosystem direction
- Stable dependencies (alpha-status transitive deps caused rejection)
- Sufficient community size (no hard threshold — accepted range: 360 stars to 30M downloads/month)

**Process:** Issue first (New Entry Proposal template), PR only after discussion. DCO sign-off required. Ongoing obligation to submit version update PRs.

**Implication for us:** We cannot apply until we have meaningful community adoption. Build the best starter first, grow community, then apply. Target: 6-12 months post-launch before submission.

### 2. Competitive Landscape — Who's Out There

| Library | Status | Threat Level | Key Advantage |
|---------|--------|-------------|---------------|
| **wimdeblauwe** (502★) | Active, Boot 4 | **Primary competitor** | Most features, most adoption, Apache 2.0 |
| **officiallysingh** (39★) | Active, Boot 4 | Low | DB constraint handling, zero-code config |
| **Zalando problem-spring-web** (1,100★) | Maintenance mode | Declining | Historical adoption, but migration recommended |
| **problem4j** (8★) | Feature complete | Negligible | Clean RFC 9457 model |
| **alimate, ekino, mkopylec** | Abandoned | None | Historical reference only |

**Key insight:** wimdeblauwe is the only real competitor. Its weaknesses are our opportunities:
- RFC 9457 is opt-in, not default
- Filter exceptions are opt-in
- No trace ID injection
- No DB constraint handling
- No microservice error propagation
- MVC only (no WebFlux)

### 3. Spring's Built-in Support — What's Missing

Spring Boot 3.x/4.x provides solid primitives but leaves significant integration gaps:

1. **Two incompatible error paths** — `@ControllerAdvice` and `BasicErrorController` produce different formats; `BasicErrorController` doesn't use ProblemDetail (spring-boot#48392)
2. **Security exceptions orphaned** — require separate `AuthenticationEntryPoint`/`AccessDeniedHandler` implementations; can't use `@ControllerAdvice` (spring-boot#43172)
3. **Validation fragmented** — three exception types, `ConstraintViolationException` defaults to 500
4. **`type` field useless by default** — `about:blank` everywhere
5. **No trace ID injection** — must manually extract from Micrometer Tracing MDC
6. **No per-exception verbosity** — global include/exclude only

---

## Positioning Recommendation

### Identity: The RFC 9457 Platform for Spring Boot

**Tagline:** *Production-grade error responses for Spring Boot — RFC 9457, zero configuration.*

**Core positioning:** We are not another error handling library. We are the **RFC 9457 platform** — the starter that makes Spring Boot's error responses production-ready by default. Add the dependency and every error, from every source, is a well-formed RFC 9457 response with trace IDs, structured codes, and i18n.

### Differentiation Pillars

| Pillar | What We Do | What wimdeblauwe Does |
|--------|-----------|----------------------|
| **RFC 9457 by default** | Every response is ProblemDetail, no opt-in needed | Custom format by default, RFC 9457 opt-in |
| **All error sources** | Controller + filter + security unified automatically | Controller by default, filter opt-in, security opt-in |
| **Trace ID injection** | Auto-detects Micrometer Tracing, injects traceId | Not available |
| **Validation unification** | All 3 exception types → single `violations` format | Handles validation but not all 3 types uniformly |
| **Spring Boot 4 native** | Designed for Boot 4 from day one | Retrofitted for Boot 4 |

### What We Explicitly Don't Do (Scope Boundaries)

- **Don't replace `@ControllerAdvice`** — we extend it. Users who define their own handlers keep full control.
- **Don't invent a new error format** — we implement the RFC. Extensions use the RFC's own `properties` map.
- **Don't require code changes** — the starter works with zero configuration. Every feature backs off via `@ConditionalOnMissingBean`.
- **Don't break Spring's contracts** — we layer on top. Removing the starter dependency restores default Spring behavior.

### Naming

Per Spring Boot naming conventions:
- **Starter artifact:** `error-handler-spring-boot-starter`
- **Autoconfigure module:** `error-handler-spring-boot` (if two-module split)
- **Property namespace:** `error-handler.*`
- **Group ID:** TBD (must be a domain you control for Maven Central)

### Feature Roadmap

**v1.0 — MVP (must-have for launch)**
- RFC 9457 responses for all error sources (controller, filter, security)
- Structured error codes with convention-based `type` URIs
- Validation exception normalization (all 3 types → unified format)
- Micrometer Tracing auto-detection and trace ID injection
- `@ConditionalOnMissingBean` for every auto-configured bean
- Spring Boot 4.x compatibility (primary), 3.3+ compatibility (secondary)
- Configuration metadata with full Javadoc
- `ApplicationContextRunner`-based test suite

**v1.1 — Fast follow**
- Complete i18n via `MessageSource` (all exception sources)
- Environment-aware verbosity (dev vs prod profiles)
- Problem type catalog with optional actuator endpoint
- Declarative exception mapping (annotation + properties)
- Database constraint violation handling

**v1.x — Roadmap**
- WebFlux parity (conditional auto-configuration)
- Microservice error propagation (RestClient, WebClient, Feign decoders)
- OpenAPI integration (`@ApiErrorResponse` generation)

**Initializr submission — target: v1.2+ with community evidence**

---

## Spring Initializr Acceptance Checklist (Actionable)

Status tracking for eventual start.spring.io submission:

### Technical Requirements
- [ ] Artifact named `error-handler-spring-boot-starter`
- [ ] Apache 2.0 license (all transitive deps verified)
- [ ] Published to Maven Central (release, not SNAPSHOT)
- [ ] `sources.jar` + `javadoc.jar` + GPG signatures
- [ ] POM has `<name>`, `<description>`, `<url>`, `<licenses>`, `<developers>`, `<scm>`
- [ ] `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [ ] `spring-boot-autoconfigure-processor` in build
- [ ] `spring-boot-configuration-processor` in build
- [ ] All `@ConfigurationProperties` fields have Javadoc
- [ ] `@ConditionalOnMissingBean` on all auto-configured beans
- [ ] `@AutoConfiguration` (not `@Configuration`) on auto-config classes
- [ ] No component scanning from auto-configuration classes
- [ ] Uses Spring Boot's dependency management without overriding
- [ ] CI tests against supported Spring Boot versions

### Community Requirements
- [ ] GitHub stars (target: 300+ before submission)
- [ ] Stack Overflow presence (answer questions, create tag)
- [ ] Active issue tracker
- [ ] Code of Conduct (Contributor Covenant)
- [ ] Reference documentation URL
- [ ] Sample/demo project
- [ ] Monthly release cadence (shows active maintenance)

### Submission Process
- [ ] Open issue on spring-io/start.spring.io using New Entry Proposal template
- [ ] All template sections completed
- [ ] Version mapping table (Boot version → starter version)
- [ ] CI link provided
- [ ] Commit to ongoing version update PRs

---

## Next Steps

This research stream is complete. Findings flow into:

1. **core-design** (blocked → unblocked) — Use the gap analysis and feature roadmap to design the starter's architecture
2. **compliance** (in progress) — Use the Initializr checklist and Maven Central requirements to set up the project skeleton

---

*Research complete. All documents in this workspace are the deliverables.*
