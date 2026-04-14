package io.github.markpollack.prreview.steps;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.Comment;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.FileChange;
import io.github.markpollack.prreview.model.Issue;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.Review;
import io.github.markpollack.prreview.model.ReviewReport;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;

/**
 * Renders a self-contained HTML report from a {@link ReviewReport}.
 *
 * <p>
 * All CSS and JS are inline — no external dependencies. Designed for workshop
 * presentations with large fonts, Spring-branded colors, and projector-friendly contrast.
 */
class HtmlReportRenderer {

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
		.withZone(ZoneId.systemDefault());

	private static final DateTimeFormatter COMMENT_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm")
		.withZone(ZoneId.systemDefault());

	private HtmlReportRenderer() {
	}

	// ── File categorization ────────────────────────────────────────────

	private enum FileCategory {

		SOURCE("Source"), TEST("Test"), CONFIG("Config"), DOCS("Docs"), OTHER("Other");

		private final String label;

		FileCategory(String label) {
			this.label = label;
		}

		String label() {
			return this.label;
		}

		static FileCategory categorize(String filename) {
			String lower = filename.toLowerCase(Locale.ROOT);
			if (lower.endsWith(".java") && lower.contains("/test/")) {
				return TEST;
			}
			if (lower.endsWith(".java")) {
				return SOURCE;
			}
			if (lower.endsWith(".xml") || lower.endsWith(".yml") || lower.endsWith(".yaml")
					|| lower.endsWith(".properties") || lower.endsWith(".gradle") || lower.endsWith(".toml")) {
				return CONFIG;
			}
			if (lower.endsWith(".md") || lower.endsWith(".adoc") || lower.endsWith(".txt") || lower.endsWith(".rst")) {
				return DOCS;
			}
			return OTHER;
		}

	}

	// ── Main render ────────────────────────────────────────────────────

	static String render(ReviewReport report) {
		PrContext pr = report.prContext();
		StringBuilder sb = new StringBuilder(16384);

		appendHead(sb, pr);
		sb.append("<body>\n");
		appendHeader(sb, report);
		sb.append("<main class=\"container\">\n");
		appendExecutiveSummary(sb, report);
		appendJudgeCascade(sb, report.judgments());
		appendDetailedAnalysis(sb, report);
		appendCodeChanges(sb, pr.files());
		appendTestResults(sb, report);
		appendAdditionalInfo(sb, report);
		appendDiscussion(sb, pr);
		sb.append("</main>\n");
		appendFooter(sb, report);
		sb.append("</body>\n</html>\n");

		return sb.toString();
	}

	// ── Head & CSS ──────────────────────────────────────────────────────

	private static void appendHead(StringBuilder sb, PrContext pr) {
		sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
		sb.append("<meta charset=\"UTF-8\">\n");
		sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
		sb.append("<title>PR Review: #").append(pr.number()).append(" — ");
		sb.append(escapeHtml(pr.title())).append("</title>\n");
		sb.append("<style>\n");
		appendCss(sb);
		sb.append("</style>\n</head>\n");
	}

	private static void appendCss(StringBuilder sb) {
		sb.append(
				"""
						:root {
						  --spring-green: #6db33f;
						  --spring-dark: #34302d;
						  --pass: #28a745;
						  --fail: #dc3545;
						  --warn: #f0ad4e;
						  --info: #17a2b8;
						  --bg: #f8f9fa;
						  --card-bg: #ffffff;
						  --text: #212529;
						  --text-muted: #6c757d;
						  --border: #dee2e6;
						  --shadow: 0 2px 8px rgba(0,0,0,0.08);
						}
						* { box-sizing: border-box; margin: 0; padding: 0; }
						body { font-family: system-ui, -apple-system, sans-serif; background: var(--bg); color: var(--text); line-height: 1.6; }
						.container { max-width: 1100px; margin: 0 auto; padding: 0 24px 48px; }

						/* Banner */
						.banner { background: linear-gradient(135deg, var(--spring-dark) 0%, #4a453f 100%); color: #fff; padding: 40px 0 32px; }
						.banner .container { max-width: 1100px; margin: 0 auto; padding: 0 24px; }
						.banner h1 { font-size: 1.8em; font-weight: 700; margin-bottom: 12px; }
						.banner .pr-description { font-size: 0.95em; opacity: 0.85; margin-bottom: 16px; max-width: 800px; line-height: 1.5; }
						.banner .badges { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 24px; }
						.badge { display: inline-block; padding: 4px 12px; border-radius: 12px; font-size: 0.85em; font-weight: 600; }
						.badge-author { background: rgba(255,255,255,0.15); color: #fff; }
						.badge-state { background: var(--spring-green); color: #fff; }
						.badge-branch { background: rgba(255,255,255,0.1); color: #ddd; font-family: monospace; font-size: 0.8em; }
						.badge-label { background: var(--info); color: #fff; }
						.badge-issue { background: var(--warn); color: #333; }

						/* Metric cards */
						.metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
						.metric-card { background: rgba(255,255,255,0.12); border-radius: 8px; padding: 16px; text-align: center; }
						.metric-card .label { font-size: 0.8em; text-transform: uppercase; letter-spacing: 0.05em; opacity: 0.8; margin-bottom: 4px; }
						.metric-card .value { font-size: 2em; font-weight: 700; }
						.metric-card .value.pass { color: var(--pass); }
						.metric-card .value.fail { color: var(--fail); }
						.metric-card .value.skipped { color: var(--info); }

						/* Sections */
						section { margin-top: 32px; }
						section h2 { font-size: 1.4em; font-weight: 700; color: var(--spring-dark); margin-bottom: 16px; padding-bottom: 8px; border-bottom: 2px solid var(--spring-green); }

						/* Executive summary */
						.summary-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
						.summary-card { background: var(--card-bg); border-radius: 10px; padding: 20px; box-shadow: var(--shadow); border-top: 3px solid var(--spring-green); }
						.summary-card h3 { font-size: 1em; font-weight: 700; color: var(--spring-dark); margin-bottom: 8px; }
						.summary-card p { font-size: 0.9em; color: var(--text-muted); line-height: 1.5; }

						/* Judge cascade */
						.cascade { display: flex; align-items: center; gap: 0; flex-wrap: wrap; margin-bottom: 8px; }
						.judge-node { background: var(--card-bg); border: 2px solid var(--border); border-radius: 10px; padding: 16px 20px; min-width: 200px; box-shadow: var(--shadow); }
						.judge-node.pass { border-color: var(--pass); }
						.judge-node.fail { border-color: var(--fail); }
						.judge-node .tier { font-size: 0.75em; text-transform: uppercase; letter-spacing: 0.1em; color: var(--text-muted); }
						.judge-node .name { font-size: 1.1em; font-weight: 600; margin: 4px 0; }
						.judge-node .status-badge { display: inline-block; padding: 2px 10px; border-radius: 10px; font-size: 0.85em; font-weight: 700; color: #fff; }
						.status-pass { background: var(--pass); }
						.status-fail { background: var(--fail); }
						.status-abstain { background: var(--warn); }
						.status-error { background: var(--fail); }
						.cascade-arrow { font-size: 1.5em; color: var(--text-muted); padding: 0 12px; }

						/* Cards */
						.card { background: var(--card-bg); border-radius: 10px; padding: 20px; margin-bottom: 16px; box-shadow: var(--shadow); border-left: 4px solid var(--border); }
						.card.pass { border-left-color: var(--pass); }
						.card.fail { border-left-color: var(--fail); }
						.card.skipped { border-left-color: var(--info); }
						.card h3 { font-size: 1.1em; margin-bottom: 8px; }
						.card .detail { color: var(--text-muted); font-size: 0.9em; margin: 4px 0; }

						/* Assessment cards */
						.assessment-card { background: var(--card-bg); border-radius: 10px; padding: 20px; margin-bottom: 16px; box-shadow: var(--shadow); }
						.assessment-card h3 { font-size: 1.1em; margin-bottom: 8px; }
						.score-bar { height: 8px; background: var(--border); border-radius: 4px; overflow: hidden; margin: 8px 0; }
						.score-fill { height: 100%; border-radius: 4px; }
						.findings-list { list-style: none; padding: 0; margin-top: 8px; }
						.findings-list li { padding: 4px 0 4px 20px; position: relative; font-size: 0.9em; }
						.findings-list li::before { content: "\\2022"; position: absolute; left: 4px; color: var(--text-muted); }

						/* Details/summary */
						details { margin-top: 8px; }
						summary { cursor: pointer; font-size: 0.9em; color: var(--text-muted); }
						summary:hover { color: var(--text); }
						details .checks-list { list-style: none; padding: 8px 0 0 16px; }
						details .checks-list li { padding: 2px 0; font-size: 0.85em; }
						.check-pass::before { content: "\\2713 "; color: var(--pass); font-weight: bold; }
						.check-fail::before { content: "\\2717 "; color: var(--fail); font-weight: bold; }

						/* Detailed analysis */
						.analysis-details { background: var(--card-bg); border-radius: 10px; box-shadow: var(--shadow); overflow: hidden; margin-bottom: 12px; }
						.analysis-details > summary { padding: 16px 20px; font-size: 1em; font-weight: 600; color: var(--text); background: var(--card-bg); list-style: none; }
						.analysis-details > summary::-webkit-details-marker { display: none; }
						.analysis-details > summary::before { content: "\\25B6 "; font-size: 0.7em; margin-right: 8px; display: inline-block; transition: transform 0.2s; }
						.analysis-details[open] > summary::before { transform: rotate(90deg); }
						.analysis-details .analysis-body { padding: 0 20px 16px; }

						/* Code changes grouped */
						.file-group { margin-bottom: 20px; }
						.file-group-header { font-size: 1em; font-weight: 600; color: var(--spring-dark); margin-bottom: 8px; padding: 8px 0; border-bottom: 1px solid var(--border); }
						.file-group-header .file-count { font-weight: 400; color: var(--text-muted); font-size: 0.85em; margin-left: 8px; }

						/* Files table */
						.files-table { width: 100%; border-collapse: collapse; font-size: 0.9em; }
						.files-table th { text-align: left; padding: 10px 12px; background: var(--spring-dark); color: #fff; }
						.files-table th:first-child { border-radius: 8px 0 0 0; }
						.files-table th:last-child { border-radius: 0 8px 0 0; }
						.files-table td { padding: 8px 12px; border-bottom: 1px solid var(--border); }
						.files-table tr:hover td { background: #f1f3f5; }
						.file-path { font-family: monospace; font-size: 0.85em; word-break: break-all; }
						.change-bar { display: inline-flex; height: 10px; border-radius: 3px; overflow: hidden; min-width: 60px; }
						.change-add { background: var(--pass); height: 100%; }
						.change-del { background: var(--fail); height: 100%; }
						.change-nums { font-family: monospace; font-size: 0.85em; white-space: nowrap; }
						.add-num { color: var(--pass); }
						.del-num { color: var(--fail); }

						/* Test results */
						.test-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
						.test-metric { background: var(--card-bg); border-radius: 10px; padding: 16px; text-align: center; box-shadow: var(--shadow); }
						.test-metric .tm-label { font-size: 0.8em; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); margin-bottom: 4px; }
						.test-metric .tm-value { font-size: 1.8em; font-weight: 700; }
						.test-metric .tm-value.pass { color: var(--pass); }
						.test-metric .tm-value.fail { color: var(--fail); }
						.test-metric .tm-value.skipped { color: var(--info); }

						/* Additional info grid */
						.info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
						.info-card { background: var(--card-bg); border-radius: 10px; padding: 20px; box-shadow: var(--shadow); }
						.info-card h3 { font-size: 1.1em; font-weight: 700; color: var(--spring-dark); margin-bottom: 12px; }
						.info-card .info-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid var(--border); font-size: 0.9em; }
						.info-card .info-row:last-child { border-bottom: none; }
						.info-card .info-label { color: var(--text-muted); }
						.info-card .info-value { font-weight: 600; }

						/* Discussion */
						.discussion-item { background: var(--card-bg); border-radius: 10px; padding: 16px 20px; margin-bottom: 12px; box-shadow: var(--shadow); border-left: 4px solid var(--border); }
						.discussion-item.review { border-left-color: var(--info); }
						.discussion-item.review-approved { border-left-color: var(--pass); }
						.discussion-item.review-changes-requested { border-left-color: var(--fail); }
						.discussion-item .di-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
						.discussion-item .di-author { font-weight: 600; }
						.discussion-item .di-time { color: var(--text-muted); font-size: 0.8em; }
						.discussion-item .di-body { font-size: 0.9em; color: var(--text); line-height: 1.5; }
						.review-badge { display: inline-block; padding: 2px 8px; border-radius: 8px; font-size: 0.75em; font-weight: 700; color: #fff; }
						.review-badge.approved { background: var(--pass); }
						.review-badge.changes-requested { background: var(--fail); }
						.review-badge.commented { background: var(--text-muted); }
						.review-badge.dismissed { background: var(--warn); }

						/* Not-run message */
						.not-run { background: #e9ecef; border-radius: 10px; padding: 24px; text-align: center; color: var(--text-muted); font-size: 1.1em; }

						/* Footer */
						.footer { text-align: center; padding: 32px 0; color: var(--text-muted); font-size: 0.85em; border-top: 1px solid var(--border); margin-top: 48px; }

						@media (max-width: 768px) {
						  .metrics { grid-template-columns: repeat(2, 1fr); }
						  .cascade { flex-direction: column; }
						  .cascade-arrow { transform: rotate(90deg); }
						  .summary-grid { grid-template-columns: 1fr; }
						  .info-grid { grid-template-columns: 1fr; }
						  .test-metrics { grid-template-columns: 1fr; }
						}
						""");
	}

	// ── Header / Banner ─────────────────────────────────────────────────

	private static void appendHeader(StringBuilder sb, ReviewReport report) {
		PrContext pr = report.prContext();
		int additions = pr.files().stream().mapToInt(FileChange::additions).sum();
		int deletions = pr.files().stream().mapToInt(FileChange::deletions).sum();

		sb.append("<header class=\"banner\">\n<div class=\"container\">\n");

		sb.append("<h1>PR #").append(pr.number()).append(" — ").append(escapeHtml(pr.title())).append("</h1>\n");

		// Description snippet
		if (pr.description() != null && !pr.description().isEmpty()) {
			sb.append("<p class=\"pr-description\">")
				.append(escapeHtml(truncateText(pr.description(), 200)))
				.append("</p>\n");
		}

		// Badges
		sb.append("<div class=\"badges\">\n");
		sb.append("<span class=\"badge badge-author\">").append(escapeHtml(pr.author())).append("</span>\n");
		sb.append("<span class=\"badge badge-state\">").append(escapeHtml(pr.state())).append("</span>\n");
		sb.append("<span class=\"badge badge-branch\">").append(escapeHtml(pr.headBranch()));
		sb.append(" &rarr; ").append(escapeHtml(pr.baseBranch())).append("</span>\n");
		for (String label : pr.labels()) {
			sb.append("<span class=\"badge badge-label\">").append(escapeHtml(label)).append("</span>\n");
		}
		for (Issue issue : pr.linkedIssues()) {
			sb.append("<span class=\"badge badge-issue\">#")
				.append(issue.number())
				.append(" ")
				.append(escapeHtml(truncateText(issue.title(), 40)))
				.append("</span>\n");
		}
		sb.append("</div>\n");

		// Metric cards
		sb.append("<div class=\"metrics\">\n");
		String verdict = overallVerdict(report);
		String verdictClass = "PASS".equals(verdict) ? "pass" : "fail";
		appendMetricCard(sb, "Verdict", verdict, verdictClass);
		appendMetricCard(sb, "Files Changed", String.valueOf(pr.files().size()), null);
		appendMetricCard(sb, "Code Changes", "+" + additions + " / -" + deletions, null);
		String buildStatus = buildStatusLabel(report.buildResult());
		String buildClass = buildStatusClass(report.buildResult());
		appendMetricCard(sb, "Build", buildStatus, buildClass);
		sb.append("</div>\n");

		sb.append("</div>\n</header>\n");
	}

	private static void appendMetricCard(StringBuilder sb, String label, String value, String cssClass) {
		sb.append("<div class=\"metric-card\"><div class=\"label\">");
		sb.append(escapeHtml(label));
		sb.append("</div><div class=\"value");
		if (cssClass != null) {
			sb.append(' ').append(cssClass);
		}
		sb.append("\">").append(escapeHtml(value)).append("</div></div>\n");
	}

	// ── Executive Summary ──────────────────────────────────────────────

	private static void appendExecutiveSummary(StringBuilder sb, ReviewReport report) {
		sb.append("<section class=\"executive-summary\">\n");
		sb.append("<h2>Executive Summary</h2>\n");
		sb.append("<div class=\"summary-grid\">\n");

		// Card 1: Problem / PR Description
		sb.append("<div class=\"summary-card\">\n");
		sb.append("<h3>Problem</h3>\n");
		String description = report.prContext().description();
		if (description != null && !description.isEmpty()) {
			sb.append("<p>").append(escapeHtml(truncateText(description, 300))).append("</p>\n");
		}
		else {
			sb.append("<p><em>No description provided</em></p>\n");
		}
		sb.append("</div>\n");

		// Card 2: Quality Assessment
		AssessmentResult quality = findAssessment(report.assessments(), "quality");
		sb.append("<div class=\"summary-card\">\n");
		sb.append("<h3>Quality Assessment</h3>\n");
		if (quality != null) {
			sb.append("<p>").append(escapeHtml(quality.rationale())).append("</p>\n");
		}
		else {
			sb.append("<p><em>Not available</em></p>\n");
		}
		sb.append("</div>\n");

		// Card 3: Backport Assessment
		AssessmentResult backport = findAssessment(report.assessments(), "backport", "version");
		sb.append("<div class=\"summary-card\">\n");
		sb.append("<h3>Backport Assessment</h3>\n");
		if (backport != null) {
			sb.append("<p>").append(escapeHtml(backport.rationale())).append("</p>\n");
		}
		else {
			sb.append("<p><em>Not available</em></p>\n");
		}
		sb.append("</div>\n");

		sb.append("</div>\n</section>\n");
	}

	// ── Judge Cascade ───────────────────────────────────────────────────

	private static void appendJudgeCascade(StringBuilder sb, List<Judgment> judgments) {
		sb.append("<section class=\"judge-cascade\">\n");
		sb.append("<h2>Judge Cascade</h2>\n");

		if (judgments.isEmpty()) {
			sb.append("<p>No judge verdicts recorded.</p>\n");
			sb.append("</section>\n");
			return;
		}

		sb.append("<div class=\"cascade\">\n");
		for (int i = 0; i < judgments.size(); i++) {
			if (i > 0) {
				sb.append("<span class=\"cascade-arrow\">&rarr;</span>\n");
			}
			Judgment j = judgments.get(i);
			String tier = metaOrDefault(j, "tier", "T" + i);
			String name = metaOrDefault(j, "judge_name", "Judge " + (i + 1));
			String nodeClass = (j.status() == JudgmentStatus.PASS) ? "pass" : "fail";
			String statusClass = statusCssClass(j.status());

			sb.append("<div class=\"judge-node ").append(nodeClass).append("\">\n");
			sb.append("<div class=\"tier\">").append(escapeHtml(tier)).append("</div>\n");
			sb.append("<div class=\"name\">").append(escapeHtml(name)).append("</div>\n");
			sb.append("<span class=\"status-badge ").append(statusClass).append("\">");
			sb.append(j.status().name()).append("</span>\n");

			// Checks as collapsible
			if (!j.checks().isEmpty()) {
				sb.append("<details open>\n<summary>").append(j.checks().size()).append(" checks</summary>\n");
				sb.append("<ul class=\"checks-list\">\n");
				for (var check : j.checks()) {
					String checkClass = check.passed() ? "check-pass" : "check-fail";
					sb.append("<li class=\"").append(checkClass).append("\">");
					sb.append(escapeHtml(check.name()));
					if (check.message() != null && !check.message().isEmpty()) {
						sb.append(" — ").append(escapeHtml(check.message()));
					}
					sb.append("</li>\n");
				}
				sb.append("</ul>\n</details>\n");
			}

			// Reasoning
			sb.append("<details>\n<summary>Reasoning</summary>\n<p style=\"font-size:0.85em;margin-top:4px;\">");
			sb.append(escapeHtml(j.reasoning())).append("</p>\n</details>\n");

			sb.append("</div>\n");
		}
		sb.append("</div>\n</section>\n");
	}

	// ── Detailed Analysis (collapsible) ────────────────────────────────

	private static void appendDetailedAnalysis(StringBuilder sb, ReviewReport report) {
		sb.append("<section class=\"detailed-analysis\">\n");
		sb.append("<h2>Detailed Analysis</h2>\n");

		// Rebase
		RebaseResult rebase = report.rebaseResult();
		String rebaseStatus = rebase.success() ? "Clean" : "Conflicts";
		appendAnalysisDetail(sb, "Rebase — " + rebaseStatus, true, () -> {
			sb.append("<p class=\"detail\">Branch: <code>").append(escapeHtml(rebase.branch())).append("</code></p>\n");
			sb.append("<p class=\"detail\">Status: ").append(rebaseStatus).append("</p>\n");
			if (!rebase.conflictFiles().isEmpty()) {
				sb.append("<p class=\"detail\">Conflicted files: ")
					.append(rebase.conflictFiles().size())
					.append("</p>\n");
				sb.append("<ul>\n");
				for (String f : rebase.conflictFiles()) {
					sb.append("<li><code>").append(escapeHtml(f)).append("</code></li>\n");
				}
				sb.append("</ul>\n");
			}
		});

		// Conflicts
		ConflictReport conflicts = report.conflictReport();
		appendAnalysisDetail(sb, "Conflict Detection", false, () -> {
			sb.append("<p class=\"detail\">").append(escapeHtml(conflicts.summary())).append("</p>\n");
			if (!conflicts.conflicts().isEmpty()) {
				sb.append("<table class=\"files-table\" style=\"margin-top:8px\">\n");
				sb.append("<tr><th>File</th><th>Classification</th><th>Description</th></tr>\n");
				for (var c : conflicts.conflicts()) {
					sb.append("<tr><td><code>").append(escapeHtml(c.path())).append("</code></td>");
					sb.append("<td>").append(c.classification()).append("</td>");
					sb.append("<td>").append(escapeHtml(c.description())).append("</td></tr>\n");
				}
				sb.append("</table>\n");
			}
		});

		// Build
		BuildResult build = report.buildResult();
		String buildLabel = build.skipped() ? "Build — Skipped" : "Build — " + (build.success() ? "PASS" : "FAIL");
		appendAnalysisDetail(sb, buildLabel, false, () -> {
			if (build.skipped()) {
				sb.append("<p class=\"detail\">Build skipped (complex conflicts detected)</p>\n");
			}
			else {
				sb.append("<p class=\"detail\">Status: ").append(build.success() ? "PASS" : "FAIL").append("</p>\n");
				if (!build.modules().isEmpty()) {
					sb.append("<p class=\"detail\">Modules: ")
						.append(escapeHtml(String.join(", ", build.modules())))
						.append("</p>\n");
				}
				sb.append("<p class=\"detail\">Duration: ").append(formatDuration(build.durationMs())).append("</p>\n");
			}
		});

		// Quality findings
		AssessmentResult quality = findAssessment(report.assessments(), "quality");
		if (quality != null) {
			appendAnalysisDetail(sb, "Quality Findings", false, () -> {
				sb.append("<p class=\"detail\">").append(escapeHtml(quality.rationale())).append("</p>\n");
				appendFindingsList(sb, quality.findings());
			});
		}

		// Backport / version findings
		AssessmentResult backport = findAssessment(report.assessments(), "backport", "version");
		if (backport != null) {
			appendAnalysisDetail(sb, "Backport / Version Findings", false, () -> {
				sb.append("<p class=\"detail\">").append(escapeHtml(backport.rationale())).append("</p>\n");
				appendFindingsList(sb, backport.findings());
			});
		}

		sb.append("</section>\n");
	}

	private static void appendAnalysisDetail(StringBuilder sb, String title, boolean open, Runnable bodyWriter) {
		sb.append("<details class=\"analysis-details\"");
		if (open) {
			sb.append(" open");
		}
		sb.append(">\n<summary>");
		sb.append(escapeHtml(title));
		sb.append("</summary>\n<div class=\"analysis-body\">\n");
		bodyWriter.run();
		sb.append("</div>\n</details>\n");
	}

	private static void appendFindingsList(StringBuilder sb, List<String> findings) {
		if (!findings.isEmpty()) {
			sb.append("<ul class=\"findings-list\">\n");
			for (String finding : findings) {
				sb.append("<li>").append(escapeHtml(finding)).append("</li>\n");
			}
			sb.append("</ul>\n");
		}
	}

	// ── Code Changes (grouped by category) ─────────────────────────────

	private static void appendCodeChanges(StringBuilder sb, List<FileChange> files) {
		sb.append("<section class=\"code-changes\">\n<h2>Code Changes</h2>\n");

		if (files.isEmpty()) {
			sb.append("<p>No files changed.</p>\n");
			sb.append("</section>\n");
			return;
		}

		// Group files by category
		Map<FileCategory, List<FileChange>> grouped = new LinkedHashMap<>();
		for (FileCategory cat : FileCategory.values()) {
			grouped.put(cat, new ArrayList<>());
		}
		for (FileChange file : files) {
			FileCategory cat = FileCategory.categorize(file.filename());
			grouped.get(cat).add(file);
		}

		int maxChange = files.stream().mapToInt(f -> f.additions() + f.deletions()).max().orElse(1);

		for (var entry : grouped.entrySet()) {
			List<FileChange> groupFiles = entry.getValue();
			if (groupFiles.isEmpty()) {
				continue;
			}
			FileCategory cat = entry.getKey();

			sb.append("<div class=\"file-group\">\n");
			sb.append("<div class=\"file-group-header\">")
				.append(escapeHtml(cat.label()))
				.append("<span class=\"file-count\">(")
				.append(groupFiles.size())
				.append(")</span></div>\n");

			sb.append("<table class=\"files-table\">\n");
			sb.append("<tr><th>File</th><th>Status</th><th>Changes</th><th></th></tr>\n");

			for (FileChange file : groupFiles) {
				int total = file.additions() + file.deletions();
				int barWidth = (maxChange > 0) ? Math.max(1, (total * 100) / maxChange) : 0;
				int addPct = (total > 0) ? (file.additions() * 100) / total : 50;

				sb.append("<tr><td class=\"file-path\">").append(escapeHtml(file.filename())).append("</td>");
				sb.append("<td>").append(escapeHtml(file.status())).append("</td>");
				sb.append("<td class=\"change-nums\">");
				sb.append("<span class=\"add-num\">+").append(file.additions()).append("</span> ");
				sb.append("<span class=\"del-num\">-").append(file.deletions()).append("</span>");
				sb.append("</td>");
				sb.append("<td><div class=\"change-bar\" style=\"width:").append(barWidth).append("px\">");
				sb.append("<div class=\"change-add\" style=\"width:").append(addPct).append("%\"></div>");
				sb.append("<div class=\"change-del\" style=\"width:").append(100 - addPct).append("%\"></div>");
				sb.append("</div></td></tr>\n");
			}

			sb.append("</table>\n</div>\n");
		}

		sb.append("</section>\n");
	}

	// ── Test Results ───────────────────────────────────────────────────

	private static void appendTestResults(StringBuilder sb, ReviewReport report) {
		BuildResult build = report.buildResult();
		sb.append("<section class=\"test-results\">\n<h2>Test Results</h2>\n");

		if (build.skipped()) {
			sb.append("<div class=\"not-run\">Tests were not run (build skipped)</div>\n");
			sb.append("</section>\n");
			return;
		}

		// 3 metric cards
		sb.append("<div class=\"test-metrics\">\n");
		String statusLabel = build.success() ? "PASS" : "FAIL";
		String statusClass = build.success() ? "pass" : "fail";
		appendTestMetric(sb, "Status", statusLabel, statusClass);
		appendTestMetric(sb, "Duration", formatDuration(build.durationMs()), null);
		String modulesLabel = build.modules().isEmpty() ? "all" : String.valueOf(build.modules().size());
		appendTestMetric(sb, "Modules", modulesLabel, null);
		sb.append("</div>\n");

		// Modules list
		if (!build.modules().isEmpty()) {
			sb.append("<p class=\"detail\" style=\"margin-bottom:8px;\">Modules tested: <code>")
				.append(escapeHtml(String.join(", ", build.modules())))
				.append("</code></p>\n");
		}

		// Collapsible build output
		if (build.output() != null && !build.output().isEmpty()) {
			sb.append(
					"<details>\n<summary>Build output</summary>\n<pre style=\"font-size:0.8em;overflow-x:auto;padding:8px;background:#f1f3f5;border-radius:4px;margin-top:4px;\">");
			sb.append(escapeHtml(build.output()));
			sb.append("</pre>\n</details>\n");
		}

		sb.append("</section>\n");
	}

	private static void appendTestMetric(StringBuilder sb, String label, String value, String cssClass) {
		sb.append("<div class=\"test-metric\"><div class=\"tm-label\">");
		sb.append(escapeHtml(label));
		sb.append("</div><div class=\"tm-value");
		if (cssClass != null) {
			sb.append(' ').append(cssClass);
		}
		sb.append("\">").append(escapeHtml(value)).append("</div></div>\n");
	}

	// ── Additional Information ─────────────────────────────────────────

	private static void appendAdditionalInfo(StringBuilder sb, ReviewReport report) {
		sb.append("<section class=\"additional-info\">\n<h2>Additional Information</h2>\n");
		sb.append("<div class=\"info-grid\">\n");

		// PR metadata card
		PrContext pr = report.prContext();
		int additions = pr.files().stream().mapToInt(FileChange::additions).sum();
		int deletions = pr.files().stream().mapToInt(FileChange::deletions).sum();

		sb.append("<div class=\"info-card\">\n<h3>PR Metadata</h3>\n");
		appendInfoRow(sb, "Number", "#" + pr.number());
		appendInfoRow(sb, "Author", pr.author());
		appendInfoRow(sb, "State", pr.state());
		appendInfoRow(sb, "Branch", pr.headBranch() + " → " + pr.baseBranch());
		appendInfoRow(sb, "Files", String.valueOf(pr.files().size()));
		appendInfoRow(sb, "Lines", "+" + additions + " / -" + deletions);
		if (!pr.labels().isEmpty()) {
			appendInfoRow(sb, "Labels", String.join(", ", pr.labels()));
		}
		if (!pr.linkedIssues().isEmpty()) {
			String issues = pr.linkedIssues()
				.stream()
				.map(i -> "#" + i.number())
				.reduce((a, b) -> a + ", " + b)
				.orElse("");
			appendInfoRow(sb, "Issues", issues);
		}
		sb.append("</div>\n");

		// Assessment summary card
		sb.append("<div class=\"info-card\">\n<h3>Assessment Summary</h3>\n");
		List<AssessmentResult> aiAssessments = report.assessments()
			.stream()
			.filter(a -> !"BuildJudge".equals(a.judgeName()) && !"VersionPatternJudge".equals(a.judgeName()))
			.toList();
		if (aiAssessments.isEmpty()) {
			appendInfoRow(sb, "Status", "AI assessments not run");
		}
		else {
			for (var assessment : aiAssessments) {
				int pct = (int) (assessment.score() * 100);
				appendInfoRow(sb, assessment.judgeName(), assessment.status().name() + " (" + pct + "%)");
			}
		}
		appendInfoRow(sb, "Build", buildStatusLabel(report.buildResult()));
		appendInfoRow(sb, "Overall", overallVerdict(report));
		sb.append("</div>\n");

		sb.append("</div>\n</section>\n");
	}

	private static void appendInfoRow(StringBuilder sb, String label, String value) {
		sb.append("<div class=\"info-row\"><span class=\"info-label\">")
			.append(escapeHtml(label))
			.append("</span><span class=\"info-value\">")
			.append(escapeHtml(value))
			.append("</span></div>\n");
	}

	// ── Discussion ─────────────────────────────────────────────────────

	private static void appendDiscussion(StringBuilder sb, PrContext pr) {
		if (pr.reviews().isEmpty() && pr.comments().isEmpty()) {
			return;
		}

		sb.append("<section class=\"discussion\">\n<h2>Discussion</h2>\n");

		// Reviews first
		for (Review review : pr.reviews()) {
			String itemClass = "discussion-item review " + reviewStateClass(review.state());
			sb.append("<div class=\"").append(itemClass).append("\">\n");
			sb.append("<div class=\"di-header\">\n");
			sb.append("<span class=\"di-author\">").append(escapeHtml(review.author())).append("</span>\n");
			sb.append("<span class=\"review-badge ").append(reviewBadgeClass(review.state())).append("\">");
			sb.append(escapeHtml(review.state())).append("</span>\n");
			sb.append("<span class=\"di-time\">")
				.append(COMMENT_TIME_FORMAT.format(review.submittedAt()))
				.append("</span>\n");
			sb.append("</div>\n");
			if (review.body() != null && !review.body().isEmpty()) {
				sb.append("<div class=\"di-body\">").append(escapeHtml(review.body())).append("</div>\n");
			}
			sb.append("</div>\n");
		}

		// Comments
		for (Comment comment : pr.comments()) {
			sb.append("<div class=\"discussion-item\">\n");
			sb.append("<div class=\"di-header\">\n");
			sb.append("<span class=\"di-author\">").append(escapeHtml(comment.author())).append("</span>\n");
			sb.append("<span class=\"di-time\">")
				.append(COMMENT_TIME_FORMAT.format(comment.createdAt()))
				.append("</span>\n");
			sb.append("</div>\n");
			sb.append("<div class=\"di-body\">").append(escapeHtml(comment.body())).append("</div>\n");
			sb.append("</div>\n");
		}

		sb.append("</section>\n");
	}

	// ── Footer ──────────────────────────────────────────────────────────

	private static void appendFooter(StringBuilder sb, ReviewReport report) {
		sb.append("<div class=\"footer\">\n");
		sb.append("Generated by <strong>AgentWorks PR Review Pipeline</strong> &mdash; ");
		sb.append(TIMESTAMP_FORMAT.format(report.generatedAt()));
		sb.append("\n</div>\n");
	}

	// ── Helpers ─────────────────────────────────────────────────────────

	static String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	private static String overallVerdict(ReviewReport report) {
		if (report.judgments().isEmpty()) {
			return "N/A";
		}
		boolean allPass = report.judgments().stream().allMatch(j -> j.status() == JudgmentStatus.PASS);
		return allPass ? "PASS" : "FAIL";
	}

	private static String buildStatusLabel(BuildResult build) {
		if (build.skipped()) {
			return "SKIPPED";
		}
		return build.success() ? "PASS" : "FAIL";
	}

	private static String buildStatusClass(BuildResult build) {
		if (build.skipped()) {
			return "skipped";
		}
		return build.success() ? "pass" : "fail";
	}

	private static String statusCssClass(JudgmentStatus status) {
		return switch (status) {
			case PASS -> "status-pass";
			case FAIL -> "status-fail";
			case ABSTAIN -> "status-abstain";
			case ERROR -> "status-error";
		};
	}

	private static String metaOrDefault(Judgment judgment, String key, String defaultValue) {
		Object value = judgment.metadata().get(key);
		return (value instanceof String s) ? s : defaultValue;
	}

	private static String formatDuration(long durationMs) {
		long seconds = durationMs / 1000;
		if (seconds >= 60) {
			return (seconds / 60) + "m " + (seconds % 60) + "s";
		}
		return seconds + "s";
	}

	private static String truncateText(String text, int maxLength) {
		if (text == null || text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength) + "...";
	}

	private static AssessmentResult findAssessment(List<AssessmentResult> assessments, String... keywords) {
		for (var assessment : assessments) {
			String nameLower = assessment.judgeName().toLowerCase(Locale.ROOT);
			for (String keyword : keywords) {
				if (nameLower.contains(keyword.toLowerCase(Locale.ROOT))) {
					return assessment;
				}
			}
		}
		return null;
	}

	private static String reviewStateClass(String state) {
		return switch (state.toUpperCase(Locale.ROOT)) {
			case "APPROVED" -> "review-approved";
			case "CHANGES_REQUESTED" -> "review-changes-requested";
			default -> "";
		};
	}

	private static String reviewBadgeClass(String state) {
		return switch (state.toUpperCase(Locale.ROOT)) {
			case "APPROVED" -> "approved";
			case "CHANGES_REQUESTED" -> "changes-requested";
			case "COMMENTED" -> "commented";
			case "DISMISSED" -> "dismissed";
			default -> "commented";
		};
	}

}
