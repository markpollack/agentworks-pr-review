package io.github.markpollack.prreview.config;

import java.nio.file.Path;

import io.github.markpollack.journal.Journal;
import io.github.markpollack.journal.storage.JsonFileStorage;
import io.github.markpollack.workflow.journal.WorkflowJournal;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.agents.client.AgentClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
		WorkflowJournal.registerEventType();
		logger.info("Journal configured with directory: {}", journalDir);
	}

	/**
	 * The default AgentClient. Skipped under the {@code agent-experiment} profile, which
	 * supplies its own {@code @Primary} trace-wired client
	 * ({@code AgentExperimentReviewerConfig}) rooted in the target clone. The default is
	 * both redundant there and unsatisfiable on this classpath: nothing publishes an
	 * {@link AgentClient.Builder} bean (it is created via the static
	 * {@code AgentClient.builder(model)} factory), so DI cannot supply one. The spring-ai
	 * (default-profile) path still needs a builder provider — tracked separately.
	 */
	@Bean
	@Profile("!agent-experiment")
	AgentClient agentClient(AgentClient.Builder builder) {
		return builder.build();
	}

}
