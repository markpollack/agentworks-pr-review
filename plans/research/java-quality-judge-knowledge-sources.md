# Java Quality Judge — Knowledge Sources

> **Created**: 2026-06-30 · **Status**: reference — to be wired into a **Java quality judge** for the PR reviewer.
> **Provenance**: curated catalog (Mark) of on-disk Java/Spring/DDD best-practice corpora.
> **How it's used**: like the agent-experiment KB-consult (DESIGN Part 2, DD-11), the Java quality judge consults these sources **by reading them at the path** — they are *not* inlined (large, source-attributed corpora). Most start at an `index.md` router.
> **Lens**: *general Java / Spring / DDD code quality* — **complementary** to the agent-experiment *data-discipline* lens (these two are distinct judges over the same PR).

## Ranked by relevance to "best practices for Java development"

1. **`~/projects/bud-spring-advisor/knowledge/spring/`** — the single most on-target asset.
   A structured, source-attributed Spring engineering-review corpus: **84 records** with stable IDs and version-applicability windows. Files:
   - `architecture-principles.md` (DI, transactions/AOP, package layout, testing discipline)
   - `production-practices.md` (actuator exposure, probes, secrets, security defaults)
   - `version-specific-guidance.md` (Boot 2→3 / 3.5→4, deprecations)
   - `production-experience.md`, `spring-ai.md`, `VERSION-BASELINE.md`, and `index.md` (a question→record router)

   Each record carries principle / description / evidence / sources / confidence / contradiction / provenance. **Start at `index.md`.**

2. **`~/projects/bud-ddd/knowledge/ddd/`** — the largest corpus (~4,750 lines, 12 files).
   Domain-Driven Design best practices distilled from Evans, Vernon (IDDD), Khononov, Brandolini, Richardson, Kerr, Rayner — plus Java-specific `ddd-jpa-review-checklist.md` / `ddd-jpa-review-lessons.md` and a 981-line `spring-modulith-analysis.md`. Java/JPA/Spring-flavored.

3. **`~/tuvium/projects/tuvium-knowledge/spring/`** — broad federated Spring/Java KB.
   Front door `index.md` + `CHEATSHEET.md`. Strong subtrees: `testing/` (9 files, incl. a 685-line `jpa-repository-testing-best-practices.md`, plus AssertJ/Mockito/MVC/security idioms), `boot-2-to-3/`, `modulith/`, `spring-ai/`. Sibling `../java/` has Java idiom files (annotation patterns, import management, type changes).

4. **`~/community/spring-testing-skills/`** — the executable form.
   Agent-loadable skill packs (`spring-jpa-testing`, `spring-mvc-testing`, `spring-security-testing`, `spring-webflux-testing`, `spring-websocket-testing`, `spring-testing-fundamentals`) — the same ones loaded in this session. Cataloged in `SKILLS.md`.

5. **`~/tuvium/projects/tuvium-research-conversation-agent/analysis/executable-intellectual-frameworks.md`** — the index/rationale over all the above.
   Tiers DDD, OWASP Top 10, 12-Factor, Clean Architecture, SOLID, TDD/BDD, Contract Testing as "executable intellectual frameworks," each with language-specific overlays (DDD+JPA, OWASP+Java, 12-Factor+Spring Boot). Read this to understand how the corpora fit together.

**Lesser / narrow:** `~/acp/acp-java/plans/BEST-PRACTICES-REACTIVE-SCHEDULERS.md`, `~/tuvium/projects/tuvium-knowledge/patterns/gof-analysis-patterns.md`.
