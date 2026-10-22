package clothing.app.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * JSON config: ISO-8601 dates (UTC), lenient on unknown properties, Java 8 date/time support.
 */
@Configuration
public class JacksonConfig {

	@Bean
	public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
		return builder
			.modules(new JavaTimeModule())
			.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.failOnUnknownProperties(false)
			.build();
	}
}
