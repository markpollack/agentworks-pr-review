package io.github.markpollack.prreview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrReviewRunnerTest {

	@Test
	void parsePrNumber_bareNumber() {
		assertThat(PrReviewRunner.parsePrNumber(new String[] { "5774" })).isEqualTo(5774);
	}

	@Test
	void parsePrNumber_withFlags() {
		assertThat(PrReviewRunner.parsePrNumber(new String[] { "--check", "1234" })).isEqualTo(1234);
	}

	@Test
	void parsePrNumber_noNumber() {
		assertThat(PrReviewRunner.parsePrNumber(new String[] { "--check" })).isEqualTo(-1);
	}

	@Test
	void parsePrNumber_emptyArgs() {
		assertThat(PrReviewRunner.parsePrNumber(new String[] {})).isEqualTo(-1);
	}

	@Test
	void parsePrNumber_invalidNumber() {
		assertThat(PrReviewRunner.parsePrNumber(new String[] { "abc" })).isEqualTo(-1);
	}

}
