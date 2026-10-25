package clothing.app.common.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bucket4j in-memory, per-IP throttling on /auth/login, /auth/register and the
 * payment webhooks (DESIGN_REFERENCE §6.3; swap for Redis when scaling out).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Set<String> PROTECTED_PATHS = Set.of(
		"/api/v1/auth/login", "/api/v1/auth/register");
	private static final String WEBHOOK_PREFIX = "/api/v1/payments/webhooks/";

	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	private final ObjectMapper objectMapper;

	private final long capacity;

	private final long refillMinutes;

	public RateLimitFilter(ObjectMapper objectMapper,
			@Value("${RATE_LIMIT_AUTH_CAPACITY:10}") long capacity,
			@Value("${RATE_LIMIT_AUTH_REFILL_MINUTES:1}") long refillMinutes) {
		this.objectMapper = objectMapper;
		this.capacity = capacity;
		this.refillMinutes = refillMinutes;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
		throws ServletException, IOException {
		if (PROTECTED_PATHS.contains(request.getRequestURI())
				|| request.getRequestURI().startsWith(WEBHOOK_PREFIX)) {
			Bucket bucket = buckets.computeIfAbsent(clientIp(request), ip -> newBucket());
			if (!bucket.tryConsume(1)) {
				writeRateLimited(response, request);
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	private void writeRateLimited(HttpServletResponse response, HttpServletRequest request)
			throws IOException {
		Map<String, Object> body = new java.util.LinkedHashMap<>();
		body.put("timestamp", java.time.Instant.now());
		body.put("status", 429);
		body.put("code", "RATE_LIMITED");
		body.put("message", "Too many requests");
		body.put("path", request.getRequestURI());
		response.setStatus(429);
		response.setContentType("application/json");
		objectMapper.writeValue(response.getWriter(), body);
	}

	private Bucket newBucket() {
		return Bucket.builder()
			.addLimit(Bandwidth.classic(capacity,
				io.github.bucket4j.Refill.greedy(capacity, Duration.ofMinutes(refillMinutes))))
			.build();
	}

	private static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
