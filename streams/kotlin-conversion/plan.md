# Plan: kotlin-conversion

## Objective
Convert the project from Java to Kotlin with package rename from `io.github.landonharter` to `io.github.briannaandco`. Establishes Kotlin build infrastructure (plugins, kapt, compiler options) as the foundation for all subsequent streams.

## Approach
Single-PR atomic conversion: update build.gradle.kts for Kotlin (plugins, kapt, compiler options, explicitApi(), MockK), convert 2 Java files to Kotlin under the new `io.github.briannaandco.errorhandler` package, update all references (AutoConfiguration.imports, POM metadata), verify CI compatibility across Spring Boot 3.3.8 and 3.4.4.

## Principle Compliance

| Principle | Approach | Risk Areas |
|-----------|----------|------------|
| GP-3: Every auto-configured bean backs off | `@AutoConfiguration` + `@ConditionalOnWebApplication` annotations preserved in conversion | `.imports` file FQCN mismatch — test catches it |
| GP-7: Optional deps classpath-detected, isolated | `@ConditionalOnWebApplication` preserved on top-level class | None — no new optional deps introduced |
| GP-9: One auto-config class, one domain | Single class boundary maintained — no merging of concerns | None |

## Stack Guidelines Applied
- SG-1: `kotlin("plugin.spring")` added; `@AutoConfiguration(proxyBeanMethods = false)` explicit — applied in `build.gradle.kts` and `ErrorHandlerAutoConfiguration.kt`
- SG-2: `kotlin.explicitApi()` enabled — applied in `build.gradle.kts`; `ErrorHandlerAutoConfiguration` gets explicit `public` visibility
- SG-5: No `!!` usage — trivially satisfied (empty class), standard set for future code
- SG-8: MockK + springmockk added, Mockito excluded, backtick test name, `WebApplicationContextRunner` pattern — applied in `build.gradle.kts` and test file

## Quality Checklist
- [ ] **Simplicity**: 1:1 language conversion, no new abstractions, no unnecessary plugins
- [ ] **Conventions**: SG-1 (kotlin-spring, proxyBeanMethods=false), SG-2 (explicitApi), SG-5 (no !!), SG-8 (MockK, backtick tests)
- [ ] **Scope**: Only conversion + rename; no new functionality, no Detekt/ktlint/Dokka (stream 3), no libs.versions.toml (stream 2)
- [ ] **Error handling**: `.imports` FQCN mismatch caught by existing `hasSingleBean` test
- [ ] **Edge cases**: Spring Boot 3.3.8 compat with Kotlin 2.0.21 verified via `./gradlew build -PspringBootVersion=3.3.8`
- [ ] **Tests**: Existing test converted to Kotlin with backtick name; no new tests needed (no new functionality)

## Acceptance Criteria
- [ ] `build.gradle.kts` applies `kotlin("jvm")`, `kotlin("plugin.spring")`, `kotlin("kapt")` at version 2.0.21
- [ ] `kotlin.explicitApi()` is enabled
- [ ] Kotlin compiler options include `-Xjsr305=strict` and target JVM 17
- [ ] `kapt { correctErrorTypes = true }` is configured
- [ ] `annotationProcessor` dependencies replaced with `kapt`
- [ ] MockK + springmockk added as test dependencies, Mockito excluded from `spring-boot-starter-test`
- [ ] Group ID is `io.github.briannaandco`; POM metadata references `briannaandco`
- [ ] `ErrorHandlerAutoConfiguration.kt` exists at `io.github.briannaandco.errorhandler` with `@AutoConfiguration(proxyBeanMethods = false)` and explicit `public` visibility
- [ ] `ErrorHandlerAutoConfigurationTest.kt` uses backtick test name and `WebApplicationContextRunner`
- [ ] `AutoConfiguration.imports` references `io.github.briannaandco.errorhandler.ErrorHandlerAutoConfiguration`
- [ ] Old Java files under `src/main/java` and `src/test/java` are deleted
- [ ] `./gradlew build` passes with Spring Boot 3.4.4
- [ ] `./gradlew build -PspringBootVersion=3.3.8` passes
- [ ] No `!!` usage anywhere
- [ ] PR reviewed and approved

## PR Breakdown

### PR 1: Convert project from Java to Kotlin with package rename (6 files)
**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/io/github/briannaandco/errorhandler/ErrorHandlerAutoConfiguration.kt`
- Create: `src/test/kotlin/io/github/briannaandco/errorhandler/ErrorHandlerAutoConfigurationTest.kt`
- Modify: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Delete: `src/main/java/io/github/landonharter/errorhandler/ErrorHandlerAutoConfiguration.java`
- Delete: `src/test/java/io/github/landonharter/errorhandler/ErrorHandlerAutoConfigurationTest.java`

**Depends on:** none
**Potential improvements:** libs.versions.toml (deferred → stream 2), Detekt/ktlint (deferred → stream 3), Dokka (deferred → stream 3)

#### Tasks
1. [ ] Update `build.gradle.kts`: add Kotlin plugins (2.0.21), kapt, compiler options, `explicitApi()`, MockK deps, group/POM rename — **Sonnet** (SG-1, SG-2, SG-8)
2. [ ] Create `ErrorHandlerAutoConfiguration.kt` at new package with explicit `public` visibility and `@AutoConfiguration(proxyBeanMethods = false)` — **Haiku** (SG-1, SG-2, GP-9)
3. [ ] Create `ErrorHandlerAutoConfigurationTest.kt` with backtick test name — **Haiku** (SG-8)
4. [ ] Update `AutoConfiguration.imports` to new FQCN — **Haiku** (GP-3)
5. [ ] Delete old Java source files — **Haiku**
6. [ ] Verify build passes with Spring Boot 3.4.4 and 3.3.8 — **Haiku** (AC verification)

## All Files
- **Create**: `src/main/kotlin/io/github/briannaandco/errorhandler/ErrorHandlerAutoConfiguration.kt` (Kotlin conversion of auto-config class), `src/test/kotlin/io/github/briannaandco/errorhandler/ErrorHandlerAutoConfigurationTest.kt` (Kotlin conversion of test)
- **Modify**: `build.gradle.kts` (Kotlin plugins, deps, group rename, POM), `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (new FQCN)
- **Delete**: `src/main/java/io/github/landonharter/errorhandler/ErrorHandlerAutoConfiguration.java`, `src/test/java/io/github/landonharter/errorhandler/ErrorHandlerAutoConfigurationTest.java`

## Dependencies
- Blocked by: none
- Blocks: multi-module (stream 2), tooling (stream 3), all subsequent streams

## Decisions
- Package rename from `io.github.landonharter` to `io.github.briannaandco` included in this stream (natural time since files are moving anyway)
- `kotlin.explicitApi()` pulled forward from stream 3 (zero-cost now, expensive to retrofit later)
- MockK/springmockk added now to prevent Mockito adoption by habit in subsequent streams
- Kotlin 2.0.21 chosen (version Spring Boot 3.4.4 was built against — maximum compatibility)
- Kotlin version declared inline in `build.gradle.kts` (version catalog deferred to stream 2)
- `@AutoConfiguration(proxyBeanMethods = false)` explicit per SG-1 even though it's the annotation default
- `-Xjsr305=strict` added for Spring null-safety interop
- `kapt { correctErrorTypes = true }` enabled for Spring annotation processors

## Notes
- 8 of 12 guiding principles are dormant for this stream (no runtime logic exists yet)
- Spring Boot 3.3.8 was built against Kotlin 1.9.x but Kotlin 2.0.21 is backward compatible — CI matrix verifies
- `kotlin-spring` plugin is required even with `proxyBeanMethods = false` for future AOP, `@Transactional`, and test classes
- The existing `hasSingleBean` test is the primary correctness gate — it catches `.imports` mismatches and plugin failures
