# Step 2.4: ConflictDetectionStep — Learnings

**Completed**: 2026-04-08

## What Was Built

`ConflictDetectionStep` implementing `Step<RebaseResult, ConflictReport>` — classifies each conflicted file from a failed rebase as SIMPLE or COMPLEX based on filename patterns.

## Key Decisions

1. **Classification by filename pattern, not conflict markers** — The design called for parsing git conflict markers, but filename-based classification is simpler and sufficient for the workshop. Build files (pom.xml, build.gradle), property files, and package-info.java are SIMPLE; everything else is COMPLEX.

2. **Static Pattern list** — Five compiled `Pattern` objects cover: `pom.xml`, `build.gradle(.kts)?`, `gradle.properties`, `*.properties`, `package-info.java`. Using `Pattern.matcher().find()` (not `matches()`) so patterns match anywhere in the path.

3. **Clean rebase path** — `ConflictReport.clean()` factory returns an empty report. Not silent — logger.info announces "Clean rebase, no conflicts to classify".

4. **Human-readable descriptions** — Each ConflictFile gets a description: "version bump or dependency conflict" for build files, "property value conflict" for .properties, "overlapping code changes requiring human review" for COMPLEX.

## Patterns

- `buildSummary()` handles singular/plural grammar: "1 conflict" vs "3 conflicts"
- `hasComplex` boolean tracked during iteration — avoids second stream pass

## Test Coverage (8 tests)

- Clean rebase → empty report
- Single pom.xml → SIMPLE
- Single .java → COMPLEX
- All SIMPLE patterns: pom.xml, build.gradle, build.gradle.kts, gradle.properties, *.properties, package-info.java
- Mixed report with summary message
- Singular grammar ("1 conflict:")
