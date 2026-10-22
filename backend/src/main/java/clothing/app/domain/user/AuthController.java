package clothing.app.domain.user;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.exception.ApiException;
import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.user.dto.AuthResponse;
import clothing.app.domain.user.dto.LoginRequest;
import clothing.app.domain.user.dto.RegisterRequest;
import clothing.app.domain.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Auth endpoints (DESIGN_REFERENCE §5.3). The refresh token is only ever an
 * HttpOnly cookie scoped to /api/v1/auth; it never appears in a response body.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	static final String REFRESH_COOKIE = "refresh_token";

	private final AuthService authService;

	private final RefreshTokenService refreshTokenService;

	private final Environment environment;

	private final Duration refreshTtl;

	public AuthController(AuthService authService, RefreshTokenService refreshTokenService,
			Environment environment, @Value("${REFRESH_TOKEN_TTL_DAYS:14}") long refreshTtlDays) {
		this.authService = authService;
		this.refreshTokenService = refreshTokenService;
		this.environment = environment;
		this.refreshTtl = Duration.ofDays(refreshTtlDays);
	}

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
			HttpServletResponse response) {
		AuthService.AuthResult result = authService.register(request);
		setRefreshCookie(response, result.rawRefreshToken());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(result.response()));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
			HttpServletResponse response) {
		AuthService.AuthResult result = authService.login(request);
		setRefreshCookie(response, result.rawRefreshToken());
		return ResponseEntity.ok(ApiResponse.success(result.response()));
	}

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<AuthResponse>> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
			HttpServletRequest request, HttpServletResponse response) {
		// cookie-only defense in depth: browsers send Origin on cross-site POSTs,
		// so a present-but-mismatched Origin means cross-site cookie use (CSRF)
		requireMatchingOrigin(request);
		AuthService.AuthResult result = authService.refresh(cookie);
		setRefreshCookie(response, result.rawRefreshToken());
		return ResponseEntity.ok(ApiResponse.success(result.response()));
	}

	@PostMapping("/logout")
	@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
	public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String cookie) {
		if (cookie != null) {
			refreshTokenService.revokeChain(cookie);
		}
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
	public ApiResponse<UserResponse> me(@CurrentUser String userId) {
		return ApiResponse.success(authService.me(userId));
	}

	private void requireMatchingOrigin(HttpServletRequest request) {
		String frontendOrigin = environment.getProperty("FRONTEND_ORIGIN");
		String origin = request.getHeader("Origin");
		if (frontendOrigin != null && !frontendOrigin.isBlank() && origin != null
				&& !origin.equals(frontendOrigin)) {
			throw new ApiException.Forbidden("Origin not allowed");
		}
	}

	private boolean isProd() {
		String[] profiles = environment.getActiveProfiles();
		return Arrays.asList(profiles).contains("prod");
	}

	private void setRefreshCookie(HttpServletResponse response, String rawToken) {
		ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, rawToken)
			.httpOnly(true)
			.secure(isProd())
			.sameSite("Lax")
			.path("/api/v1/auth")
			.maxAge(refreshTtl)
			.build();
		response.addHeader("Set-Cookie", cookie.toString());
	}
}
