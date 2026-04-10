package io.github.markpollack.prreview.config;

import java.nio.file.Path;

import io.github.markpollack.journal.Journal;
import io.github.markpollack.journal.storage.JsonFileStorage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.client.AgentClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the journal storage backend and the AgentClient bean.
 */
@Configuration
public class JournalConfig {

	private static final Logger logger = LoggerFactory.getLogger(JournalConfig.class);

	private final WorkshopProperties workshopProperties;

	public JournalConfig(WorkshopProperties workshopProperties) {
		this.workshopProperties = workshopProperties;
	}

	@PostConstruct
	void init() {
		Path journalDir = Path.of(this.workshopProperties.journalDir());
		Journal.configure(new JsonFileStorage(journalDir));
		logger.info("Journal configured with directory: {}", journalDir);
	}

	@Bean
	AgentClient agentClient(AgentClient.Builder builder) {
		return builder.build();
	}

}
