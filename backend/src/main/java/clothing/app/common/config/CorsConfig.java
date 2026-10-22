package clothing.app.common.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS restricted to {@code FRONTEND_ORIGIN} with credentials enabled (refresh cookie).
 * When {@code FRONTEND_ORIGIN} is unset (or "*") all origins are allowed without credentials.
 */
@Configuration
public class CorsConfig {

	@Bean
	public CorsFilter corsFilter() {
		String origin = System.getenv("FRONTEND_ORIGIN");
		CorsConfiguration config = new CorsConfiguration();
		if (origin == null || origin.isBlank() || "*".equals(origin)) {
			config.addAllowedOriginPattern("*");
			config.setAllowCredentials(false);
		} else {
			config.setAllowedOrigins(List.of(origin));
			config.setAllowCredentials(true);
		}
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		config.addExposedHeader("Set-Cookie");
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/v1/**", config);
		return new CorsFilter(source);
	}
}
