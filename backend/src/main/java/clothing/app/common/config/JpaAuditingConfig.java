package clothing.app.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Enables Spring Data JPA auditing so every {@link clothing.app.domain.model.BaseEntity}
 * gets created_at / updated_at populated in UTC (DATETIME(6)).
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
@EnableJpaRepositories(basePackages = "clothing.app")
public class JpaAuditingConfig {

	@Bean
	public AuditorAware<String> auditorAware() {
		return () -> Optional.empty(); // populated once auth context exists (Phase 3)
	}

	@Bean(name = "utcDateTimeProvider")
	public org.springframework.data.auditing.DateTimeProvider utcDateTimeProvider() {
		return () -> Optional.of(Instant.now(Clock.systemUTC()));
	}
}
