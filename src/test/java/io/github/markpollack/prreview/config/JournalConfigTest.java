package io.github.markpollack.prreview.config;

import io.github.markpollack.journal.Journal;
import io.github.markpollack.journal.storage.JsonFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JournalConfigTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void tearDown() {
		Journal.reset();
	}

	@Test
	void initConfiguresJsonFileStorage() {
		WorkshopProperties properties = new WorkshopProperties(5774, false, false, this.tempDir.toString(), ".");
		JournalConfig config = new JournalConfig(properties);
		config.init();

		assertThat(Journal.storage()).isInstanceOf(JsonFileStorage.class);
	}

}
