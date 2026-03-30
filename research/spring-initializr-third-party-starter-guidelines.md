# Spring Initializr Third-Party Starter Guidelines
## Comprehensive Acceptance Criteria for start.spring.io Listing

**Prepared:** 2026-03-30
**Sources:** Spring Initializr Wiki, start.spring.io GitHub repo (issue templates, actual proposals), Spring Boot reference documentation, Maven Central publishing requirements

---

## Overview

[start.spring.io](https://start.spring.io) lists both official Spring Boot starters and a curated set of third-party community starters. Getting listed is not automatic — it requires meeting a formal set of requirements and passing review by the Spring Boot team. The guidelines balance four goals:

1. An opinionated, high-quality getting-started experience
2. Alignment with the Spring ecosystem's overall direction
3. Community contributions
4. Long-term confidence in viability and support

**Important:** Meeting all requirements does not guarantee listing. The Spring Boot team retains final authority based on strategic fit. Conversely, starters may be removed if usage drops below meaningful thresholds.

**Primary sources:**
- [Guidelines for 3rd Party Starters (Spring Initializr Wiki)](https://github.com/spring-io/initializr/wiki/Guidelines-for-3rd-Party-Starters)
- [start.spring.io New Entry Proposal issue template](https://github.com/spring-io/start.spring.io/blob/main/.github/ISSUE_TEMPLATE/add-entry-proposal.md)
- [start.spring.io CONTRIBUTING.adoc](https://github.com/spring-io/start.spring.io/blob/main/CONTRIBUTING.adoc)
- [Spring Boot Reference: Creating Your Own Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
- [Spring Boot Reference: Build Systems](https://docs.spring.io/spring-boot/reference/using/build-systems.html)
- [Maven Central Publishing Requirements](https://central.sonatype.org/publish/requirements/)

---

## 1. Naming Conventions

### 1.1 Artifact Naming

| Requirement | Status | Detail |
|---|---|---|
| Starter artifact must NOT start with `spring-boot` | **MANDATORY** | The `spring-boot-*` prefix is reserved exclusively for official Spring Boot artifacts |
| Third-party starter must follow `{name}-spring-boot-starter` pattern | **MANDATORY** | e.g., `acme-spring-boot-starter`, `timefold-solver-spring-boot-starter` |
| Autoconfigure-only module follows `{name}-spring-boot` pattern | **RECOMMENDED** | When splitting into two modules |
| Test-scoped starter follows `{name}-spring-boot-starter-test` pattern | **RECOMMENDED** | For test-time-only starters |

**Official starters** use: `spring-boot-starter-{type}` (e.g., `spring-boot-starter-web`)

**Third-party starters** use: `{project-name}-spring-boot-starter` (e.g., `acme-spring-boot-starter`)

Source: [Spring Boot Reference: Build Systems](https://docs.spring.io/spring-boot/reference/using/build-systems.html), [Spring Boot Reference: Creating Your Own Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html#features.developing-auto-configuration.custom-starter)

### 1.2 Configuration Property Namespace

| Requirement | Status | Detail |
|---|---|---|
| Use a unique property namespace (prefix) for all configuration keys | **MANDATORY** | Must not collide with any other starter's namespace |
| Do NOT use Spring Boot's reserved namespaces | **MANDATORY** | Reserved: `server`, `management`, `spring`, and all Boot-owned prefixes |
| Prefix example | **RECOMMENDED** | Use your project name, e.g., `acme.property.name` |

Source: [Spring Boot Reference: Developing Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)

### 1.3 Module Structure: One vs. Two Modules

A compliant starter may use either layout:

**Single-module (simple):**
```
acme-spring-boot-starter/
  └── Contains both auto-configuration code and starter POM
```

**Two-module (recommended for complex starters):**
```
acme-spring-boot/          (autoconfigure module — the library)
acme-spring-boot-starter/  (starter module — empty POM pulling in the autoconfigure module)
```

The starter module is typically an empty JAR whose sole purpose is to declare dependencies opinionatedly. It must directly or indirectly reference `spring-boot-starter`.

---

## 2. Licensing Requirements

| Requirement | Status | Detail |
|---|---|---|
| Project must be open source | **MANDATORY** | Closed-source/proprietary starters are not eligible |
| License must be compatible with Apache 2.0 | **MANDATORY** | Apache 2.0 itself, MIT, BSD, and EPL are generally acceptable; GPL is generally not |
| All transitive dependencies must also be Apache-2.0-compatible | **MANDATORY** | No dependency may carry a license incompatible with Apache 2.0 |
| All dependencies must be available on Maven Central | **MANDATORY** | No private/internal repositories allowed |

**Acceptable licenses observed in accepted starters:** Apache 2.0 (most common), MIT (Sentry)

**Note:** Each proposed starter's license and its dependencies' licenses are explicitly reviewed during the entry proposal process.

Source: [Spring Initializr Wiki](https://github.com/spring-io/initializr/wiki/Guidelines-for-3rd-Party-Starters)

---

## 3. Maven Central Publishing Requirements

All starter artifacts and their dependencies must be available on Maven Central. This requires meeting Sonatype's publishing requirements.

| Requirement | Status | Detail |
|---|---|---|
| Published to Maven Central (not just Sonatype snapshots) | **MANDATORY** | Release versions only; no `-SNAPSHOT` versions for listing |
| `sources.jar` artifact published | **MANDATORY** | Alongside the main JAR |
| `javadoc.jar` artifact published | **MANDATORY** | Alongside the main JAR; placeholder JARs with README are acceptable if full Javadoc cannot be generated |
| GPG/PGP signatures on all artifacts (`.asc` files) | **MANDATORY** | Every deployed file needs a corresponding `.asc` signature |
| Checksums on all artifacts (`.md5`, `.sha1`) | **MANDATORY** | Both formats required; `.sha256`/`.sha512` are optional |
| POM `<name>` field | **MANDATORY** | Human-readable project identifier |
| POM `<description>` field | **MANDATORY** | Overview of project purpose |
| POM `<url>` field | **MANDATORY** | Project website or repository URL |
| POM `<licenses>` section | **MANDATORY** | At least one license with `<name>` and `<url>` |
| POM `<developers>` section | **MANDATORY** | Developer name, email, organization, organizational URL |
| POM `<scm>` section | **MANDATORY** | Source control connection details (read-only and read-write) plus web frontend URL |
| Semantic versioning | **STRONGLY RECOMMENDED** | e.g., `1.2.3`, not custom schemes |
| Version strings must NOT end in `-SNAPSHOT` | **MANDATORY** | Only release versions published to Maven Central |

**Publication process (as of February 2024):** Sonatype migrated to the Central Publisher Portal. The old Jira-ticket registration process is retired. Use the `central-publishing-maven-plugin` or Gradle equivalent.

Source: [Maven Central Publishing Requirements](https://central.sonatype.org/publish/requirements/), [GPG Requirements](https://central.sonatype.org/publish/requirements/gpg/)

---

## 4. Spring Boot Version Compatibility Requirements

| Requirement | Status | Detail |
|---|---|---|
| Must actively maintain compatibility with current Spring Boot releases | **MANDATORY** | Compatibility must be achieved using Spring Boot's own dependency management — no overriding of Boot-managed dependency versions |
| Must provide version mappings for the proposal | **MANDATORY** | Specify which starter version pairs with which Spring Boot version(s) |
| Must provide CI evidence of Spring Boot compatibility | **MANDATORY** | Link to CI jobs (e.g., GitHub Actions) that verify compatibility |
| Must submit a PR when a new release is made | **MANDATORY (operational)** | When a new starter version is released, maintainers must open a PR to start.spring.io to update the version |
| Strategic compatibility note | **ADVISORY** | The Spring Boot team may remove starters that fall significantly behind on Boot version support |

**Observed version mapping format from accepted proposals:**

```
Spring Boot 3.x  →  MyStarter 2.x
Spring Boot 4.x  →  MyStarter 3.x
```

**Critical constraint:** "Compatibility should be achieved using Spring Boot's dependency management without overriding." This means your starter's POM must import `spring-boot-dependencies` (or inherit from `spring-boot-starter-parent`) and not pin different versions of Boot-managed artifacts.

Source: [start.spring.io issue template](https://github.com/spring-io/start.spring.io/blob/main/.github/ISSUE_TEMPLATE/add-entry-proposal.md), observed from accepted/declined proposals

---

## 5. Auto-Configuration Metadata Requirements

### 5.1 Auto-Configuration Discovery File

| Requirement | Status | Detail |
|---|---|---|
| `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **MANDATORY (Spring Boot 2.7+/3.x)** | One fully-qualified class name per line; comments with `#` are supported |
| `META-INF/spring.factories` with `EnableAutoConfiguration` key | **DEPRECATED / LEGACY** | Was the mechanism before Spring Boot 2.7; removed from support in Spring Boot 3.0 |
| Auto-configuration classes must NOT be component-scanned | **MANDATORY** | They must only be discovered via the imports file |
| Auto-configuration classes must NOT enable component scanning themselves | **MANDATORY** | No `@ComponentScan` on auto-configuration classes |

**File format:**
```
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.example.acme.autoconfigure.AcmeAutoConfiguration
com.example.acme.autoconfigure.AcmeWebAutoConfiguration
```

For nested classes, use `$`:
```
com.example.Outer$NestedAutoConfiguration
```

### 5.2 Auto-Configuration Metadata (Performance Optimization)

| Requirement | Status | Detail |
|---|---|---|
| Include `spring-boot-autoconfigure-processor` annotation processor | **STRONGLY RECOMMENDED** | Generates `META-INF/spring-autoconfigure-metadata.properties` at compile time |
| `META-INF/spring-autoconfigure-metadata.properties` in published JAR | **RECOMMENDED** | Enables eager filtering of non-applicable auto-configurations at startup — improves startup time |

**Maven configuration:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-autoconfigure-processor</artifactId>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

**Gradle configuration:**
```gradle
dependencies {
    annotationProcessor "org.springframework.boot:spring-boot-autoconfigure-processor"
}
```

### 5.3 Configuration Properties Metadata

| Requirement | Status | Detail |
|---|---|---|
| If providing `@ConfigurationProperties`, must include configuration metadata | **MANDATORY** | The entry proposal checklist explicitly asks: "Starter provides metadata, including documentation, for all its configuration properties" |
| Include `spring-boot-configuration-processor` in build | **MANDATORY if using `@ConfigurationProperties`** | Generates `META-INF/spring-configuration-metadata.json` automatically from Javadoc |
| All `@ConfigurationProperties` fields must have Javadoc | **MANDATORY** | This Javadoc becomes the IDE completion hint; plain text only (no HTML) |
| May provide `META-INF/additional-spring-configuration-metadata.json` | **RECOMMENDED** | For properties not bound to a `@ConfigurationProperties` bean, or to add hints to existing keys |

**Javadoc style rules for configuration properties:**
- Do NOT start descriptions with "The" or "A"
- `boolean` fields: start with "Whether" or "Enable"
- Collection fields: start with "Comma-separated list of..."
- Duration fields: use `Duration` type (not `long`); note the unit if not milliseconds
- Do NOT include default values in Javadoc if they are hardcoded (they are auto-generated from field initializers)

Source: [Spring Boot Reference: Developing Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html), [Spring Boot Configuration Metadata](https://docs.spring.io/spring-boot/specification/configuration-metadata/index.html)

### 5.4 Auto-Configuration Class Requirements

| Requirement | Status | Detail |
|---|---|---|
| Annotate auto-configuration class with `@AutoConfiguration` | **MANDATORY** | Not `@Configuration` directly; `@AutoConfiguration` is meta-annotated with `@Configuration` |
| Use `@ConditionalOnClass` to guard class-sensitive beans | **MANDATORY** | Prevents `ClassNotFoundException` when the configured library is absent |
| Use `@ConditionalOnMissingBean` to allow user override | **STRONGLY RECOMMENDED** | Lets users define their own bean to opt out of auto-configuration |
| Declare ordering if needed | **RECOMMENDED** | Use `@AutoConfiguration(before=..., after=...)` or `@AutoConfigureBefore`/`@AutoConfigureAfter` |
| Wrap `@ConditionalOnClass` on `@Bean` methods in a nested `@Configuration` class | **BEST PRACTICE** | Prevents class loading errors; the outer `@AutoConfiguration` class can be loaded without the conditional class being present |

---

## 6. Documentation Requirements

| Requirement | Status | Detail |
|---|---|---|
| Reference documentation (URL required in proposal) | **MANDATORY** | Must link to comprehensive documentation covering Spring Boot integration |
| Sample/demo project (URL required in proposal) | **MANDATORY** | At least one working example application using the starter with Spring Boot |
| Spring Boot integration documentation as a dedicated section | **STRONGLY RECOMMENDED** | From review of declined proposals, reviewers expect a clear, dedicated Spring Boot quickstart section |
| Javadoc on all public APIs | **RECOMMENDED** | Published via `javadoc.jar` to Maven Central |

**From the issue template:**
> "Each entry can be associated with one or several links. Please provide at least a link to a sample and the reference documentation."

---

## 7. Quality and Testing Requirements

| Requirement | Status | Detail |
|---|---|---|
| Proper test coverage validating Spring Boot integration | **MANDATORY** | The wiki explicitly states this |
| Test combinations of optional dependencies using standalone sample projects | **MANDATORY** | Especially important if your starter has optional dependencies |
| CI must verify Spring Boot compatibility | **MANDATORY** | Must provide a link to CI (GitHub Actions, etc.) that runs tests against supported Spring Boot versions |
| Use `ApplicationContextRunner` for auto-configuration tests | **STRONGLY RECOMMENDED** | The Spring Boot testing idiom for auto-configuration unit tests |
| Compatibility must be tested WITHOUT overriding Spring Boot's dependency management | **MANDATORY** | CI tests must import `spring-boot-dependencies` as-is |

**Testing tools to use:**
```java
// Unit test auto-configuration
private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(MyAutoConfiguration.class));

// Test with missing class
contextRunner.withClassLoader(new FilteredClassLoader(MyService.class))
    .run(context -> assertThat(context).doesNotHaveBean("myService"));
```

Source: [Spring Boot Reference: Testing Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html), [Spring Initializr Wiki](https://github.com/spring-io/initializr/wiki/Guidelines-for-3rd-Party-Starters)

---

## 8. Community and Maintenance Requirements

| Requirement | Status | Detail |
|---|---|---|
| Sizeable, active community using and maintaining the library | **MANDATORY** | The Spring team wants evidence, not promises |
| Evidence: GitHub stars, contributors, commit activity | **REQUIRED IN PROPOSAL** | Show the project is alive and growing |
| Evidence: Stack Overflow presence | **REQUIRED IN PROPOSAL** | Shows real user adoption |
| Active issue tracker (public URL required) | **MANDATORY** | GitHub Issues, Jira, etc. |
| Code of Conduct in place | **MANDATORY** | Must adopt a code of conduct (Contributor Covenant recommended) |
| Maintainers must agree to keep starter updated | **MANDATORY** | When a new Boot release ships, maintainers must submit a PR to update start.spring.io |
| Only one starter POM per library | **MANDATORY** | start.spring.io will list at most one starter entry per third-party library |

**Evidence thresholds observed from accepted proposals:**
- SpringDoc OpenAPI: 30M+ downloads/month, 185K+ dependent GitHub repos, 3.7K GitHub stars
- Timefold Solver: 360 GitHub stars (5 months old), 68 Stack Overflow questions, monthly releases

These are illustrative, not exact thresholds. The team exercises judgment. The Grafana OpenTelemetry starter was declined despite meeting many criteria because the integration was not yet consistent with other observability entries and had alpha-stability dependencies.

Source: [Spring Initializr Wiki](https://github.com/spring-io/initializr/wiki/Guidelines-for-3rd-Party-Starters), [start.spring.io issue template](https://github.com/spring-io/start.spring.io/blob/main/.github/ISSUE_TEMPLATE/add-entry-proposal.md)

---

## 9. Build System Integration Requirements

| Requirement | Status | Detail |
|---|---|---|
| If providing custom build plugins, BOTH Maven AND Gradle must be supported | **MANDATORY** | start.spring.io generates both Maven and Gradle projects |
| Custom build configuration details must be provided in the proposal | **REQUIRED IN PROPOSAL** | Describe any generated build snippets beyond the dependency declaration |
| Most starters need no custom build plugins | **NOTE** | The checklist item is marked "Not applicable" when no build plugins are needed |

Source: [start.spring.io issue template](https://github.com/spring-io/start.spring.io/blob/main/.github/ISSUE_TEMPLATE/add-entry-proposal.md)

---

## 10. Review Process and Approval Workflow

### 10.1 How to Submit a Proposal

The official process (from `CONTRIBUTING.adoc`):

1. **Do NOT open a Pull Request first.** PRs without prior issue discussion will be rejected.
2. Open a GitHub issue on the [spring-io/start.spring.io](https://github.com/spring-io/start.spring.io/issues) repository.
3. Choose the **"New Entry Proposal"** issue template.
4. Fill out all sections of the template completely.
5. The Spring team will review and discuss in the issue.
6. If accepted, the maintainer (or a team contributor) opens a PR to add the entry.
7. Ongoing: maintainers must submit PRs for version upgrades via the "Entry Upgrade" issue/PR flow.

**Commit requirements:**
- All commits must include a `Signed-off-by:` trailer (DCO — Developer Certificate of Origin). Spring migrated from CLA to DCO in January 2025.

### 10.2 Entry Proposal Template Checklist

This is the **exact checklist** from the official [New Entry Proposal issue template](https://github.com/spring-io/start.spring.io/blob/main/.github/ISSUE_TEMPLATE/add-entry-proposal.md):

| Section | What to Provide |
|---|---|
| Code of Conduct | Confirm adherence; link to project's own CoC |
| Well-established community | GitHub stars, contributors, Stack Overflow questions, download counts, dependent repos |
| Ongoing maintenance | Release history, commitment to keep up with Boot releases |
| Licence | Project license + all dependency licenses |
| Issue Tracker URL | Public issue tracker link |
| Continuous Integration | CI link; must test with Boot's dependency management without overrides |
| Maven Central | Checkbox: "Starter and all of its dependencies are available on Maven Central" |
| Configuration metadata | Checkbox: "Starter provides metadata, including documentation, for all its configuration properties" |
| Build system integration | Details of any Maven/Gradle plugin configuration generated |
| Version mappings | Table of Spring Boot version → starter version |
| Links to additional resources | Reference documentation URL + sample project URL (minimum) |

### 10.3 Ongoing Maintenance Obligations

Once listed, the maintainer takes on obligations:

- Submit PRs to start.spring.io for every new release (version updates)
- Keep compatibility with each new Spring Boot minor/major release
- The Spring Boot team periodically reviews usage statistics
- Starters with insufficient usage may be removed to keep the list opinionated

### 10.4 Reasons for Declining (Observed from Real Issues)

From reviewing declined proposals:

| Reason | Example |
|---|---|
| Starter too new / insufficient community evidence | Multiple proposals |
| Dependencies are in alpha/unstable state | Grafana OpenTelemetry starter (alpha logback appender) |
| Integration not consistent with similar existing entries | Grafana OpenTelemetry (vs. Micrometer Tracing entries) |
| Strategic direction mismatch | Dekorate, some AWS/cloud-specific entries |
| Functionality already covered by existing entries | Various |
| Does not meet broad user appeal threshold | Casdoor, PicoCLI |

---

## 11. Technical Compliance: Spring Boot Starter Rules

These come directly from Spring Boot's official documentation on what makes a compliant third-party starter.

### 11.1 Autoconfigure Module

The autoconfigure module must:

- Contain the `@AutoConfiguration`-annotated class
- List it in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Mark library dependencies as `<optional>true</optional>` (so the autoconfigure JAR can be included independently)
- NOT trigger component scanning
- Use appropriate `@Conditional*` annotations so auto-configuration backs off if user provides their own beans

### 11.2 Starter Module (POM)

The starter module must:

- Be an essentially empty JAR (no production code)
- Declare a dependency on `spring-boot-starter` (directly or via another starter)
- Declare a dependency on the autoconfigure module
- Include any other dependencies the user would typically need
- NOT include unnecessary optional dependencies

### 11.3 The `@AutoConfiguration` Annotation (Spring Boot 3.x)

```java
@AutoConfiguration
@ConditionalOnClass(SomeService.class)
public class SomeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SomeService someService() {
        return new SomeService();
    }
}
```

For class-conditional beans, wrap them in a nested static `@Configuration` class to prevent class-loading issues:

```java
@AutoConfiguration
public class SomeAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SomeService.class)
    static class SomeServiceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SomeService someService() {
            return new SomeService();
        }
    }
}
```

---

## 12. Summary: Complete Acceptance Checklist

Use this as a go/no-go checklist before submitting a proposal.

### Naming
- [ ] Artifact ID does NOT start with `spring-boot`
- [ ] Artifact follows `{name}-spring-boot-starter` pattern
- [ ] Configuration property namespace is unique and avoids Spring Boot's reserved namespaces (`server`, `management`, `spring`, etc.)

### Licensing
- [ ] Project is open source
- [ ] License is Apache 2.0-compatible
- [ ] All transitive dependencies are Apache 2.0-compatible
- [ ] All dependencies are on Maven Central

### Maven Central Publishing
- [ ] Released (non-SNAPSHOT) version published to Maven Central
- [ ] `sources.jar` published
- [ ] `javadoc.jar` published
- [ ] GPG signatures (`.asc`) on all artifacts
- [ ] POM has `<name>`, `<description>`, `<url>`, `<licenses>`, `<developers>`, `<scm>`

### Spring Boot Compatibility
- [ ] Actively maintains compatibility with current Spring Boot release
- [ ] Uses Spring Boot's dependency management without overriding managed versions
- [ ] CI verifies compatibility (link provided)
- [ ] Version mappings documented (Spring Boot version → starter version)

### Auto-Configuration
- [ ] `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file present
- [ ] Auto-configuration class annotated with `@AutoConfiguration` (not `@Configuration`)
- [ ] Uses `@ConditionalOnClass`, `@ConditionalOnMissingBean`, etc. appropriately
- [ ] Does NOT use `spring.factories` for auto-configuration (Spring Boot 3.x)
- [ ] `spring-boot-autoconfigure-processor` included to generate `META-INF/spring-autoconfigure-metadata.properties`

### Configuration Metadata
- [ ] If providing `@ConfigurationProperties`: `spring-boot-configuration-processor` included in build
- [ ] All `@ConfigurationProperties` fields have Javadoc documentation
- [ ] Configuration keys follow Javadoc style rules (no "The"/"A" start, "Whether" for booleans, etc.)
- [ ] Published JAR contains `META-INF/spring-configuration-metadata.json`

### Documentation
- [ ] Reference documentation URL available
- [ ] Sample/demo project URL available
- [ ] Dedicated Spring Boot integration section in documentation

### Testing
- [ ] Tests validate Spring Boot integration using `ApplicationContextRunner`
- [ ] Tests cover combinations of optional dependencies
- [ ] Standalone sample projects demonstrate integration

### Community
- [ ] Sizeable, active community (evidence: stars, contributors, Stack Overflow)
- [ ] Active issue tracker (public URL)
- [ ] Code of Conduct adopted
- [ ] Project has active CI

### Build Systems
- [ ] Both Maven AND Gradle supported (if providing custom build plugins)
- [ ] Custom build configuration (if any) generates correct snippets for both build systems

### Process
- [ ] Proposal submitted as a GitHub issue using "New Entry Proposal" template (NOT a PR)
- [ ] All template sections completed
- [ ] Maintainer commits to submitting PRs for future version upgrades
- [ ] Commits include `Signed-off-by:` trailer (DCO)

---

## Appendix: Key Source URLs

| Resource | URL |
|---|---|
| Spring Initializr Wiki: Guidelines for 3rd Party Starters | https://github.com/spring-io/initializr/wiki/Guidelines-for-3rd-Party-Starters |
| start.spring.io New Entry Proposal Template | https://github.com/spring-io/start.spring.io/blob/main/.github/ISSUE_TEMPLATE/add-entry-proposal.md |
| start.spring.io CONTRIBUTING.adoc | https://github.com/spring-io/start.spring.io/blob/main/CONTRIBUTING.adoc |
| Spring Boot Reference: Creating Your Own Auto-configuration | https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html |
| Spring Boot Reference: Build Systems (Naming) | https://docs.spring.io/spring-boot/reference/using/build-systems.html |
| Spring Boot Configuration Metadata | https://docs.spring.io/spring-boot/specification/configuration-metadata/index.html |
| Maven Central Publishing Requirements | https://central.sonatype.org/publish/requirements/ |
| Maven Central GPG Requirements | https://central.sonatype.org/publish/requirements/gpg/ |
| Example accepted proposal: SpringDoc OpenAPI (#2088) | https://github.com/spring-io/start.spring.io/issues/2088 |
| Example accepted proposal: Timefold Solver (#1331) | https://github.com/spring-io/start.spring.io/issues/1331 |
| Example accepted proposal: htmx-spring-boot (#1549) | https://github.com/spring-io/start.spring.io/issues/1549 |
| Example declined proposal: Grafana OTel starter (#1161) | https://github.com/spring-io/start.spring.io/issues/1161 |
| Example accepted proposal: Sentry (#1114) | https://github.com/spring-io/start.spring.io/issues/1114 |
