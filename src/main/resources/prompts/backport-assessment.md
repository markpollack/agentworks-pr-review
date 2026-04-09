You are evaluating whether a pull request is a good candidate for backporting to maintenance branches.

## PR Context
- **PR #{number}**: {title}
- **Author**: {author}
- **Labels**: {labels}

## Description
{description}

## Changed Files
{fileSummary}

## Diff
{diff}

## Task
Assess backport candidacy. Consider:
1. **Scope** — Is this a small, focused fix or a large feature?
2. **API compatibility** — Does it add/change/remove public API?
3. **Dependencies** — Does it require new dependencies or version bumps?
4. **Risk** — How likely is the change to conflict with older branches?

## Response Format
Respond with ONLY a JSON object (no markdown fences):
{
  "score": 0.0-1.0,
  "status": "PASS" or "FAIL",
  "rationale": "one paragraph summary",
  "findings": ["finding 1", "finding 2"]
}

Score >= 0.6 means PASS (good backport candidate). Below 0.6 means FAIL (not recommended).
PASS = safe to backport. FAIL = too risky or too large.
