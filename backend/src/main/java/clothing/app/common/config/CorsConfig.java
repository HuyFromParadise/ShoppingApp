package clothing.app.common.config;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CORS restricted to {@code FRONTEND_ORIGIN} with credentials enabled (refresh cookie).
 * When {@code FRONTEND_ORIGIN} is unset (or "*") all origins are allowed without credentials.
 *
 * <p>The filter is registered with the highest precedence so it answers the browser
 * preflight before the security chain does. A default-ordered filter ran after the
 * security chain, so the preflight was rejected with 401 and the browser never sent
 * the login request at all.
 */
@Configuration
public class CorsConfig {

	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilter(Environment environment) {
		String origin = environment.getProperty("FRONTEND_ORIGIN");
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
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/v1/**", config);

		FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(corsFilter(source));
		registration.addUrlPatterns("/api/v1/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

	private CorsFilter corsFilter(CorsConfigurationSource source) {
		CorsFilter filter = new CorsFilter(source);
		filter.setCorsProcessor(new PreflightCorsProcessor());
		return filter;
	}

	/**
	 * Rejects a disallowed origin for preflight requests, and for every actual
	 * request except {@code /auth/refresh}: that endpoint enforces the Origin
	 * rule itself so a cross-site call answers with the standard error envelope
	 * instead of a bare CORS rejection. The browser still hides the response,
	 * because no CORS headers are written for a foreign origin.
	 */
	private static final class PreflightCorsProcessor implements CorsProcessor {

		private static final String ORIGIN_CHECKED_PATH = "/api/v1/auth/refresh";

		private final CorsProcessor delegate = new DefaultCorsProcessor();

		@Override
		public boolean processRequest(CorsConfiguration config, HttpServletRequest request,
				HttpServletResponse response) throws IOException {
			String origin = request.getHeader(HttpHeaders.ORIGIN);
			if (!CorsUtils.isPreFlightRequest(request)
					&& ORIGIN_CHECKED_PATH.equals(request.getRequestURI())
					&& origin != null
					&& config.checkOrigin(origin) == null) {
				return true;
			}
			return delegate.processRequest(config, request, response);
		}
	}
}
