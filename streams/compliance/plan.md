# Plan: compliance
> Type: ops

## Objective
Ensure the project meets all technical and legal requirements for Spring Initializr community starter listing — licensing, naming conventions, Maven Central publishing, and Spring Boot version compatibility.

## Tasks
- [ ] Set up Apache 2.0 licensing (LICENSE file, headers)
- [ ] Configure artifact naming ({project}-spring-boot-starter)
- [ ] Set up Maven Central publishing (Sonatype OSSRH)
- [ ] Configure Spring Boot dependency BOM compatibility
- [ ] Set up CI/CD pipeline (GitHub Actions)
- [ ] Verify all transitive dependencies are Apache 2.0 compatible

## Acceptance Criteria
- Apache 2.0 LICENSE file present with headers on all source files
- Artifact published to Maven Central with correct naming
- Builds against latest Spring Boot release
- CI passes on all supported Spring Boot versions

## Notes
Can proceed in parallel with research stream.
