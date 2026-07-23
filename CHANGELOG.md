# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

---

## [v2.0.0] - 2026-07-23

### Added
- **Database & Storage Integration**: Migrated temporary in-memory history to a persistent PostgreSQL database for authenticated users.
- **History Dashboard**: Created a dedicated, paginated History page for users to view, search, sort, and pin past analysis runs.
- **Analysis Persistence**: Automatically saves analysis results (including batch summaries and detailed pairwise match data) upon successful run for logged-in users.
- **Usage Statistics**: Introduced a dynamic dashboard displaying usage streaks, total analyses, and a 30-day activity graph.
- **Cross-User Security**: Implemented strict ownership enforcement on all history endpoints, preventing ID enumeration and ensuring data privacy.

## [v1.5.0] - 2026-07-20

### Added
- **Authentication System**: Implemented a complete registration and login flow utilizing stateless JWT (JSON Web Tokens) for session management.
- **Email Verification**: Added an SMTP email-sending integration to enforce account verification before login.
- **Password Reset**: Developed a self-service password reset flow using secure, time-bound email tokens.
- **Profile Management**: Introduced an "Edit Profile" dashboard for users to update their personal details (Name, Email) and change passwords, styled with a responsive sidebar layout.
- **Rate Limiting**: Integrated `Bucket4j` to provide dual-key (IP and Email based) rate limiting on sensitive authentication endpoints to prevent brute-force attacks and email spam.
- **UI Redesign**: Revamped the main navigation bar for visual consistency (soft rounded pill buttons, unified styling) and consolidated Login/Signup entry points.

### Changed
- **Database Schema**: Added Flyway migrations (`V2` to `V6`) to introduce a robust `users` table, complete with `name`, `email_verified`, `verification_token`, and `reset_token` columns.
- **Frontend State**: Upgraded vanilla `app.js` routing logic to reactively handle authenticated states, decoding user identity dynamically and rendering secure views.

## [v1.0.0] - 2026-07-19

### Added

**Core Engine Enhancements**
- **Operator Divergence Detection**: Integrated JDT AST parsing into the core similarity engine to detect logic-mutating operator swaps (e.g., `>` vs `<`) that structurally identify semantic clones.
- **Identifier Rename Detection**: Expanded the AST builder to map and capture specific identifier literals (variable and method names) that were artificially renamed between two similar files.
- **Clone Explanation Synthesis Engine**: Developed a rule-based engine that analyzes scoring deltas combined with operator/rename data to synthesize human-readable classification tags (e.g., `Type-3 Clone (Logic Mutation)`).
- **Source Line Tracking**: Enhanced fingerprints to capture exact source code line ranges (`startLine` and `endLine`) for precise raw source mapping.
- **Verdict Utility**: Centralized similarity scoring ranges ("Clean" | "Review" | "Suspicious" | "High") into a single truth source.

**API & Architecture**
- **Standardized DTO Layer & Validation**: Created robust DTOs with Jakarta Bean Validation to prevent leaking internal structures and to catch invalid inputs early.
- **Batch State Management**: Implemented `ReportStore` and `AnalysisService` to manage state and assign unique UUIDs to grouped analysis batches.
- **REST Endpoints**: Added new unified endpoints for Batch Summaries (`GET /api/batch/{id}/summary`) and Raw Match Details (`GET /api/report/{id}/matches`).
- **HTML & PDF Export Pipelines**: Created an `ExportService` utilizing OpenPDF to generate offline, stylistically complete similarity reports.
- **Centralized Exception Handling**: Implemented `@RestControllerAdvice` in `GlobalExceptionHandler` to ensure the API returns uniform JSON error payloads.

**Frontend UI/UX**
- **Side-by-Side Diff Viewer**: Introduced a full-screen modal overlay to view matched files side-by-side with exact line-highlighting (gold borders and amber backgrounds) for plagiarized regions.
- **Diff Viewer Navigation & Explanations**: Added Jump-Between-Matches controls, synthesized Clone Explanation badges, and seamless Export UI downloads directly within the viewer.
- **Similarity Heatmap Grid**: Developed an interactive, dependency-free CSS-grid heatmap dynamically displaying severity color bands for large batch analyses.
- **Batch Overview Dashboard**: Added responsive visual score breakdown charts and top-level summary badges categorizing the severity of all matches.

**Deployment & Tooling**
- **Supabase Integration**: Migrated local database configurations to a managed PostgreSQL instance on Supabase for production readiness.
- **Production CI/CD**: Established a GitHub Actions workflow (`azure-deploy.yml`) for automated Maven building and zero-downtime deployments to Azure Web Apps.

### Changed
- **Architectural Separation**: Executed a thin-controller refactor on `AnalyzeController`, cleanly separating business flow orchestration and mapping logic.
- **Compiler Compliance**: Migrated `pom.xml` configuration to unified modern `<release>17</release>` Java standards.

### Fixed
- **CSS Layout Resilience**: Resolved DOM nesting and flex layout bugs in the diff viewer to ensure stable panel heights and full-width highlights across horizontally scrolled code.

### Removed
- Legacy debug controllers, unused experimental classes, and verbose test logging pipelines.
