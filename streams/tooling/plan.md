# Plan: tooling

## Objective
Add static analysis (Detekt with custom rules), code formatting (ktlint via kotlinter + detekt-formatting), and documentation generation (Dokka) to the build infrastructure. Update CI with a blocking lint job including SARIF upload. All tooling wired through build-logic convention plugins and version catalog.

## Approach
Three separate convention plugins following GP-9 (one plugin, one concern):
- `error-handler.detekt.gradle.kts` — Detekt with custom rules module in `build-logic/detekt-rules/`
- `error-handler.lint.gradle.kts` — kotlinter for auto-format
- `error-handler.docs.gradle.kts` — Dokka for `-javadoc.jar` and aggregated HTML

`detekt-formatting` wraps ktlint rules inside Detekt for unified CI checking. Kotlinter provides `./gradlew formatKotlin` for local DX. Both read the same `.editorconfig`.

Custom Detekt rules module (`build-logic/detekt-rules/`) provides:
- `NotNullAssertionOperatorRule` — blanket `!!` prohibition (SG-5)
- `NoDataClassInPublicApiRule` — flags `data class` in `*.api.*` packages (SG-6)

Dokka owns the `-javadoc.jar` artifact — `withJavadocJar()` removed from `kotlin-library` plugin. Root-level aggregated HTML docs via `dokkaGenerate`.

CI gets a `lint` job (Detekt + SARIF, ktlint, Dokka) that blocks the build matrix.

## Principle Compliance

| Principle | Approach | Risk Areas |
|-----------|----------|------------|
| GP-9: One config class, one domain | Separate convention plugins: `detekt`, `lint`, `docs` — one concern each | Resist bundling into `kotlin-library` |
| SG-2: `explicitApi()` | Already enforced in `kotlin-library` plugin — no changes | None |
| SG-5: No `!!` | Custom Detekt rule `NotNullAssertionOperator` — blanket prohibition | Must not flag `!!` in string templates |
| SG-6: No `data class` in public API | Custom Detekt rule `NoDataClassInPublicApi` — flags in `*.api.*` packages | Must not flag test or internal packages |
| SG-7: build-logic + version catalog | All versions in `libs.versions.toml`, plugins wired through `build-logic/` | None |
| SG-9: Dokka for javadoc | `error-handler.docs.gradle.kts` produces `-javadoc.jar` via Dokka | Must rewire correctly — publishing plugin must pick up Dokka output, not stub |

## Stack Guidelines Applied
- SG-5: `!!` prohibition — enforced via custom Detekt rule in `build-logic/detekt-rules/`
- SG-6: No `data class` in public API — enforced via custom Detekt rule in `build-logic/detekt-rules/`
- SG-7: build-logic + version catalog — all tool versions in `libs.versions.toml`, convention plugins in `build-logic/`
- SG-9: Dokka for documentation — `error-handler.docs.gradle.kts` produces Javadoc-format JAR for Maven Central

## Quality Checklist
- [ ] **Simplicity**: One convention plugin per concern, minimal custom rules module (3 files + SPI), no unnecessary abstractions
- [ ] **Conventions**: All versions in `libs.versions.toml`, all plugins through `build-logic/`, follows existing convention plugin patterns
- [ ] **Scope**: Only Detekt, ktlint, Dokka, CI update. No test coverage, no dependency scanning, no runtime code
- [ ] **Error handling**: N/A — no runtime code produced by this stream
- [ ] **Edge cases**: Custom rules handle string templates and test/internal packages correctly; no duplicate javadoc classifier; SARIF uploads on failure
- [ ] **Tests**: Custom Detekt rules have unit tests; plugin integration verified by clean Gradle task runs

## Acceptance Criteria
- [ ] `./gradlew detekt` passes on all library modules with zero violations
- [ ] Custom `NotNullAssertionOperator` rule flags `!!` usage; unit tested
- [ ] Custom `NoDataClassInPublicApi` rule flags `data class` in `*.api.*` packages; unit tested
- [ ] `./gradlew lintKotlin` passes on all library modules
- [ ] `./gradlew formatKotlin` auto-formats sources
- [ ] `.editorconfig`: 140 max line length, 4-space indent
- [ ] `detekt-formatting` runs ktlint rules inside Detekt
- [ ] `./gradlew dokkaGeneratePublicationJavadoc` produces `-javadoc.jar` from Dokka
- [ ] `withJavadocJar()` removed from library plugin — Dokka owns the artifact
- [ ] Root `./gradlew dokkaGenerate` produces aggregated HTML docs
- [ ] CI `lint` job runs Detekt, ktlint, Dokka; blocks build matrix
- [ ] SARIF uploaded to GitHub Security tab
- [ ] All versions in `libs.versions.toml`, all plugins through `build-logic/`
- [ ] `detekt-rules` inside `build-logic/`, not published
- [ ] Existing code clean — no baselined violations
- [ ] PR reviewed and approved

## PR Breakdown

### PR 1: Static analysis — Detekt with custom rules + ktlint (~14 files)
**Files created:**
- `build-logic/detekt-rules/build.gradle.kts`
- `build-logic/detekt-rules/src/main/kotlin/io/github/briannaandco/detekt/NotNullAssertionOperatorRule.kt`
- `build-logic/detekt-rules/src/main/kotlin/io/github/briannaandco/detekt/NoDataClassInPublicApiRule.kt`
- `build-logic/detekt-rules/src/main/kotlin/io/github/briannaandco/detekt/CustomRuleSetProvider.kt`
- `build-logic/detekt-rules/src/main/resources/META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`
- `build-logic/src/main/kotlin/error-handler.detekt.gradle.kts`
- `build-logic/src/main/kotlin/error-handler.lint.gradle.kts`
- `gradle/detekt/detekt.yml`
- `.editorconfig`

**Files modified:**
- `gradle/libs.versions.toml` (Detekt + kotlinter versions)
- `build-logic/build.gradle.kts` (Detekt + kotlinter plugin deps)
- `build-logic/settings.gradle.kts` (include detekt-rules)
- `error-handler-spring-boot-autoconfigure/build.gradle.kts` (apply detekt + lint)
- `error-handler-spring-boot-starter/build.gradle.kts` (apply detekt + lint)

**Depends on:** none
**Model:** Sonnet

#### Tasks
1. [ ] Add Detekt, kotlinter, `detekt-formatting` to version catalog
2. [ ] Add plugin dependencies to `build-logic/build.gradle.kts`
3. [ ] Create `detekt-rules` subproject in `build-logic/`
4. [ ] Update `build-logic/settings.gradle.kts` to include `detekt-rules`
5. [ ] Implement `NotNullAssertionOperatorRule`
6. [ ] Implement `NoDataClassInPublicApiRule`
7. [ ] Create `CustomRuleSetProvider` + SPI registration
8. [ ] Unit tests for both custom rules
9. [ ] Create `gradle/detekt/detekt.yml`
10. [ ] Create `error-handler.detekt.gradle.kts` convention plugin
11. [ ] Create `.editorconfig`
12. [ ] Create `error-handler.lint.gradle.kts` convention plugin
13. [ ] Apply plugins in both module build files
14. [ ] Fix existing Detekt/ktlint violations
15. [ ] Verify `./gradlew detekt lintKotlin` passes

### PR 2: Dokka setup + CI lint job (~7 files)
**Files created:**
- `build-logic/src/main/kotlin/error-handler.docs.gradle.kts`

**Files modified:**
- `gradle/libs.versions.toml` (Dokka version)
- `build-logic/build.gradle.kts` (Dokka plugin dep)
- `build-logic/src/main/kotlin/error-handler.kotlin-library.gradle.kts` (remove `withJavadocJar()`)
- `error-handler-spring-boot-autoconfigure/build.gradle.kts` (apply docs)
- `error-handler-spring-boot-starter/build.gradle.kts` (apply docs)
- `build.gradle.kts` (aggregated HTML docs)
- `.github/workflows/ci.yml` (lint job + SARIF + Dokka)

**Depends on:** PR 1 merged, rebase from origin
**Model:** Sonnet

#### Tasks
1. [ ] Add Dokka to version catalog
2. [ ] Add Dokka plugin dependency to `build-logic/build.gradle.kts`
3. [ ] Create `error-handler.docs.gradle.kts` convention plugin
4. [ ] Remove `withJavadocJar()` from library plugin
5. [ ] Apply docs plugin in both module build files
6. [ ] Configure aggregated HTML docs in root `build.gradle.kts`
7. [ ] Update CI workflow — lint job, SARIF, Dokka, `needs: lint`
8. [ ] Verify Dokka tasks pass

## All Files
**Create:**
- `build-logic/detekt-rules/build.gradle.kts`
- `build-logic/detekt-rules/src/main/kotlin/io/github/briannaandco/detekt/NotNullAssertionOperatorRule.kt`
- `build-logic/detekt-rules/src/main/kotlin/io/github/briannaandco/detekt/NoDataClassInPublicApiRule.kt`
- `build-logic/detekt-rules/src/main/kotlin/io/github/briannaandco/detekt/CustomRuleSetProvider.kt`
- `build-logic/detekt-rules/src/main/resources/META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`
- `build-logic/src/main/kotlin/error-handler.detekt.gradle.kts`
- `build-logic/src/main/kotlin/error-handler.lint.gradle.kts`
- `build-logic/src/main/kotlin/error-handler.docs.gradle.kts`
- `gradle/detekt/detekt.yml`
- `.editorconfig`

**Modify:**
- `gradle/libs.versions.toml`
- `build-logic/build.gradle.kts`
- `build-logic/settings.gradle.kts`
- `build-logic/src/main/kotlin/error-handler.kotlin-library.gradle.kts`
- `error-handler-spring-boot-autoconfigure/build.gradle.kts`
- `error-handler-spring-boot-starter/build.gradle.kts`
- `build.gradle.kts`
- `.github/workflows/ci.yml`

## Dependencies
- Blocked by: none
- Blocks: all feature streams (they inherit Detekt/ktlint enforcement)

## Decisions
- Custom Detekt rule module for blanket `!!` prohibition — built-in rules insufficient for SG-5
- Custom Detekt rule for `data class` in public API — no stock rule exists for SG-6
- `detekt-rules` module inside `build-logic/` — keeps build tooling together, naturally unpublished
- Both kotlinter and `detekt-formatting` — kotlinter for local auto-format, detekt-formatting for unified CI checks
- Separate `error-handler.docs.gradle.kts` plugin — GP-9 separation, Dokka owns javadoc JAR
- Max line length 140 — Kotlin/Spring ecosystem standard
- Lint blocks build matrix in CI — fast failure, no wasted compute
- SARIF upload for PR annotations
- Dokka in CI — catches doc build failures early
- Fix existing violations directly — no baseline files
- Aggregated Dokka HTML docs from root project

## Notes
- `explicitApi()` (SG-2) already configured — no changes needed
- Detekt 1.23.8 is the correct version for Kotlin 2.0.21 (not 2.0.0-alpha which requires Kotlin 2.3+)
- Kotlinter 5.4.2 uses Gradle Worker API process isolation, stable with Kotlin 2.0+
- Dokka 2.2.0 supports K2 analysis and Gradle configuration cache
- `detekt-formatting` and kotlinter both read `.editorconfig` — no conflicting config
- PR 2 depends on PR 1 being merged; rebase from origin before starting PR 2
