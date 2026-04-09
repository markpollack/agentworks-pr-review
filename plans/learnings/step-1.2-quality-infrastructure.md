# Step 1.2: Quality Infrastructure

> **Completed**: 2026-04-08T22:12-04:00

## What was done

Configured ArchUnit (10 rules), JSpecify @NullMarked on all packages, JaCoCo coverage reporting, spring-javaformat validation. All passing on `./mvnw verify`.

## Key decisions

- **ArchUnit naming rules use `allowEmptyShould(true)`** — packages are empty until Steps/Judges are implemented. Without this, ArchUnit 1.4.x fails on empty rule targets by default.
- **JaCoCo minimum at 0.00 for now** — ratchet to 0.70 once real code lands. No point failing the build on an almost-empty project.
- **spring-javaformat at validate phase** — catches formatting before compile. Run `./mvnw spring-javaformat:apply` to auto-fix.

## ArchUnit rules (10 total)

Layered architecture (7 rules):
- steps ↛ judges, judges ↛ steps
- github ↛ steps, github ↛ judges
- model ↛ steps, model ↛ judges, model ↛ github

Naming conventions (2 rules, allowEmptyShould):
- Classes in `steps/` must end with "Step"
- Classes in `judges/` must end with "Judge"

No cycles (1 rule):
- No package-level cycles across `com.tuvium.prreview.(*)`

## Pitfall

- ArchUnit 1.4.x changed default `failOnEmptyShould` to true. Naming convention rules on empty packages fail without `allowEmptyShould(true)`.
