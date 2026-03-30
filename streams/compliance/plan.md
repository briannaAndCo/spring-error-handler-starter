# Plan: compliance
> Type: ops

## Objective
Ensure the project meets all technical and legal requirements for Spring Initializr community starter listing — licensing, naming conventions, Maven Central publishing, and Spring Boot version compatibility.

## Tasks
- [x] Set up Apache 2.0 licensing (LICENSE file, headers)
- [x] Configure artifact naming ({project}-spring-boot-starter)
- [x] Set up Maven Central publishing (Sonatype OSSRH)
- [x] Configure Spring Boot dependency BOM compatibility
- [x] Set up CI/CD pipeline (GitHub Actions)
- [x] Verify all transitive dependencies are Apache 2.0 compatible

## Acceptance Criteria
- Apache 2.0 LICENSE file present with headers on all source files
- Artifact published to Maven Central with correct naming
- Builds against latest Spring Boot release
- CI passes on all supported Spring Boot versions

## Notes
Can proceed in parallel with research stream.
Build system uses Gradle (Kotlin DSL) with Spring Boot BOM and version matrix CI.
