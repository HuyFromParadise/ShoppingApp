package clothing.app.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security skeleton.
 * Default: anyRequest().authenticated(). Explicit permitAll for health, auth and catalog;
 * ADMIN-only for /api/v1/admin/**.
 * (A full AuthenticationManager/UserDetailsService is wired in the auth phase; Spring Boot
 * auto-configures a default one until then so the context boots.)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	private final SecurityErrorHandlers securityErrorHandlers;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			SecurityErrorHandlers securityErrorHandlers) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.securityErrorHandlers = securityErrorHandlers;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health").permitAll()
				// Swagger UI + OpenAPI JSON (endpoints only exist when SPRINGDOC_ENABLED=true)
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
					"/webjars/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/register",
					"/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**",
					"/api/v1/categories", "/api/v1/categories/**").permitAll()
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated()
			)
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(securityErrorHandlers)
				.accessDeniedHandler(securityErrorHandlers)
			)
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}
}
