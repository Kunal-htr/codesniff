# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

---

## [v0.6] - 2026-07-17

### Added
- **Standardized DTO Layer**: Created `InfoResponse`, `BatchSummaryDTO`, `FileMetadataDTO`, `ApiErrorDTO` in `backend.dto` package to prevent leaking internal token/fingerprint structures.
- **Verdict Utility**: Created `VerdictUtil` in `backend.dto` to act as a single source of truth for similarity scoring ranges ("Clean" | "Review" | "Suspicious" | "High").
- **Centralized Exception Handling**: Implemented `@RestControllerAdvice` in `GlobalExceptionHandler` and a custom `ReportNotFoundException` returning uniform JSON payloads.
- **Request Validation**: Added Jakarta Bean Validation (`@NotEmpty`, `@Size`, `@NotBlank`, `@Min`, `@Max`) parameters to DTOs to catch invalid data formats, parameter bounds, or empty files early.

### Changed
- **Architectural Separation**: Thin-controller refactor on `AnalyzeController`, transferring business flow orchestration to `AnalysisService` and mapping/retrieval functions to `ReportService`.
- **In-Memory Store Separation**: Relocated caching structures out of the controller into a thread-safe `ReportStore` component.
- **Report API Protocol**: Changed `GET /api/report/{id}` response payload from legacy server-side HTML rendering to clean, structure-aligned JSON data.
- **Compiler Compliance**: Migrated `pom.xml` configuration from dual `<source>` and `<target>` property bindings to unified modern `<release>17</release>` configuration.

### Removed
- Unused classes and legacy debug controllers.
- Verbose test logging pipelines inside `ASTBuilder.java`.
