# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

---

## [v0.8] - 2026-07-18

### Added
- **Batch Summary Endpoint**: Implemented a new backend REST endpoint (`GET /api/batch/{id}/summary`) that returns aggregate similarity metrics (`BatchSummaryDTO`) for an entire batch of analyzed files.
- **Batch State Management**: Updated `ReportStore` and `AnalysisService` to assign and cache a unique UUID `batchId` for grouped pairwise comparisons during an analysis run.
- **Visual Score Breakdown Chart**: Added responsive frontend stat cards to the batch overview section to immediately display highest, lowest, average similarities, and the total count of suspicious pairs.
- **Similarity Heatmap Grid**: Developed an interactive, dependency-free CSS-grid heatmap (N×N cells) mapped to the batch size. Cells dynamically display severity color bands (green/orange/red) with score-based opacity scaling and click-to-view diff integrations.

## [v0.7] - 2026-07-18

### Added
- **Backend Source Line Tracking**: Updated `CodeNormalizer` and `Tokenizer` to accurately map normalized/stripped tokens back to their exact original line numbers in the raw source files.
- **Fingerprint Line Ranges**: Enhanced `SimilarityEngine` fingerprints to capture and store source code line ranges (`startLine` and `endLine`) instead of just token indexes.
- **Matches API Endpoint**: Added a new REST endpoint (`GET /api/report/{id}/matches`) that returns the raw source code of two matched files along with a flat, deduplicated list of `MatchedRegionDTO` objects.
- **Side-by-Side Diff Viewer**: Introduced a new full-screen modal overlay in vanilla HTML/CSS to view matched files side-by-side with exact line-highlighting (gold borders and amber backgrounds) for plagiarized regions.

### Fixed
- **CSS Layout Resilience**: Fixed a DOM nesting issue where the diff viewer modal was trapped inside a hidden parent section, and corrected flex layout parameters to ensure panels match height and highlight styles span full line widths even when horizontally scrolled.


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
