# Stream: compliance

## Project: Plan: Spring Error Handler Starter
A Spring Boot starter that provides a comprehensive, opinionated error handling platform for Spring applications — targeting inclusion as an officially recommended community starter on start.spring.io. The starter will offer auto-configured exception handling, structured error responses, and extensible error resolution strategies, meeting all Spring Initializr third-party starter guidelines for licensing, quality, and community standards.

## This Stream
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

## Context
- Worktree: /Users/landon/repos/spring-error-handler-starter/.worktrees/compliance
- Branch: stream/compliance
- Base: main
- Repo: /Users/landon/repos/spring-error-handler-starter
- Meta branch: meta/spring-error-handler-starter

## Instructions
- Work only within this worktree
- Commit on branch stream/compliance
- Do not modify files outside this stream's scope
- Follow codebase conventions
- Always commit on a feature branch, never on main
- When done, signal readiness for review

## Model Selection
Use the right model for each task type:
- **Mechanical tasks** (1-2 files, clear specs, boilerplate): use Haiku or fast mode
- **Integration tasks** (multi-file, pattern matching, standard features): use Sonnet
- **Architecture/design/review** (complex decisions, cross-cutting concerns): use Opus
Switch with /model or let the orchestrator choose.

## On Start
1. If launched with `/stream-plan`, the planning skill will handle context gathering and planning
2. If launched with just `claude`, read the stream plan above and begin implementation
3. Read relevant project context from the meta branch if needed:
   `git show meta/spring-error-handler-starter:design.md`, `git show meta/spring-error-handler-starter:requirements.md`, etc.
4. Reference capability files for full requirements:
   `git show meta/spring-error-handler-starter:requirements/<cap-file>.md`
