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

import clothing.app.domain.user.UserEntity;
import clothing.app.domain.user.UserRepository;

/**
 * Extracts the Bearer access token from the Authorization header, validates it,
 * and populates the SecurityContext. Invalid/absent tokens leave the context empty
 * (callers are rejected downstream by the filter chain).
 *
 * <p>The role is read from the token but the account is re-read here, so a
 * locked or disabled user cannot keep acting for the token's 15-minute life and
 * a role revoked in the database takes effect immediately
 * (DESIGN_REFERENCE §6.1). This also keeps {@code @CurrentUser} from handing out
 * a deleted account's id.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
		throws ServletException, IOException {
		final String authHeader = request.getHeader("Authorization");

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		String jwt = authHeader.substring(7);
		try {
			if (jwtService.validateToken(jwt)
					&& SecurityContextHolder.getContext().getAuthentication() == null) {
				String userId = jwtService.extractUserId(jwt);
				UserEntity user = userRepository.findById(userId).orElse(null);
				if (user != null && user.getStatus() == UserEntity.Status.ACTIVE) {
					String role = user.getRole().name();
					UsernamePasswordAuthenticationToken authToken =
						new UsernamePasswordAuthenticationToken(userId, null,
							List.of(new SimpleGrantedAuthority("ROLE_" + role)));
					authToken.setDetails(
						new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authToken);
				}
			}
		}
		catch (Exception e) {
			SecurityContextHolder.clearContext();
		}
		filterChain.doFilter(request, response);
	}
}
