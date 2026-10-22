package clothing.app.common.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts the Bearer access token from the Authorization header, validates it,
 * and populates the SecurityContext. Invalid/absent tokens leave the context empty
 * (callers are rejected downstream by the filter chain).
 */
@Component 
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
		throws ServletException, IOException {
		final String authHeader = request.getHeader("Authorization");
		final String jwt;
		final String userId;

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		jwt = authHeader.substring(7);
		try {
			if (jwtService.validateToken(jwt) && SecurityContextHolder.getContext().getAuthentication() == null) {
				userId = jwtService.extractUserId(jwt);
				String role = jwtService.extractRole(jwt);
				UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
					userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
				authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authToken);
			}
		} catch (Exception e) {
			SecurityContextHolder.clearContext();
		}
		filterChain.doFilter(request, response);
	}
}
