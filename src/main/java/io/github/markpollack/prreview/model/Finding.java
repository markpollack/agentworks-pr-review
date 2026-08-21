package io.github.markpollack.prreview.model;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * One defect an AI assessment claims to have found.
 *
 * <p>
 * Every field here exists because something downstream needs it:
 * <ul>
 * <li>{@code severity} — so a missed blocker and a missed nit do not weigh the same.</li>
 * <li>{@code file} and {@code symbol} — the anchor. A judge matches a finding to a
 * recorded one on the anchor rather than on prose, and a review whose findings cannot be
 * located cannot be scored for recall at all. Note the file is where the defect
 * <em>is</em>, which is often not a file the PR changed.</li>
 * <li>{@code evidence} — separated from {@code claim} so groundedness is checkable: does
 * the evidence support the claim, or merely restate it?</li>
 * <li>{@code correction} — a finding without a fix is a complaint.</li>
 * </ul>
 *
 * <p>
 * This replaces the previous bare {@code String} finding. That shape could be rendered
 * but not evaluated, which made the assessment step the one step of the pipeline no judge
 * could score.
 *
 * @param severity BLOCKING, SIGNIFICANT or MINOR
 * @param file repository-relative path the defect is in
 * @param symbol enclosing class and method, e.g. {@code AcpClientSession.closeGracefully}
 * @param line 1-based line, or 0 when the reviewer did not commit to one
 * @param claim what is wrong, in one sentence
 * @param evidence the code or behaviour that makes the claim true
 * @param correction the smallest change that fixes it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Finding(@Nullable String severity, @Nullable String file, @Nullable String symbol, int line,
		@Nullable String claim, @Nullable String evidence, @Nullable String correction) {

	public static final String BLOCKING = "BLOCKING";

	public static final String SIGNIFICANT = "SIGNIFICANT";

	public static final String MINOR = "MINOR";

	/** Normalized severity, defaulting to {@link #MINOR} when absent or unrecognized. */
	public String normalizedSeverity() {
		if (this.severity == null) {
			return MINOR;
		}
		return switch (this.severity.trim().toUpperCase(Locale.ROOT)) {
			case BLOCKING, "BLOCKER", "CRITICAL" -> BLOCKING;
			case SIGNIFICANT, "MAJOR", "IMPORTANT" -> SIGNIFICANT;
			default -> MINOR;
		};
	}

	/** Where this finding points, for report rendering: {@code path:Class.method}. */
	public String location() {
		String path = (this.file != null && !this.file.isBlank()) ? this.file : "(unspecified)";
		if (this.symbol == null || this.symbol.isBlank()) {
			return (this.line > 0) ? path + ":" + this.line : path;
		}
		return path + ":" + this.symbol;
	}

	/** One-line rendering for the markdown report. */
	public String summary() {
		StringBuilder sb = new StringBuilder();
		sb.append(normalizedSeverity()).append(" — ").append(location());
		if (this.claim != null && !this.claim.isBlank()) {
			sb.append(" — ").append(this.claim);
		}
		return sb.toString();
	}

}
