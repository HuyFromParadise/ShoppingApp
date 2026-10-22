package clothing.app.domain.user;

import java.util.Map;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.common.exception.ApiException;
import clothing.app.common.security.JwtService;
import clothing.app.domain.user.dto.AuthResponse;
import clothing.app.domain.user.dto.LoginRequest;
import clothing.app.domain.user.dto.RegisterRequest;
import clothing.app.domain.user.dto.UserResponse;

/**
 * Register / login / refresh / logout / me. Ownership and identity rules live here,
 * never in the controller. Unknown email and wrong password share one message so
 * the endpoint does not leak which accounts exist.
 */
@Service
public class AuthService {

	public record AuthResult(AuthResponse response, String rawRefreshToken) {}

	// small denylist per DESIGN_REFERENCE §5.4; policy upgrade path is a real HIBP check
	private static final Set<String> COMMON_PASSWORDS = Set.of(
		"password", "password1", "password123", "12345678", "1234567890", "qwertyuiop",
		"letmein", "iloveyou", "admin123", "welcome1");

	private final UserRepository userRepository;

	private final RefreshTokenService refreshTokenService;

	private final JwtService jwtService;

	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, RefreshTokenService refreshTokenService,
			JwtService jwtService, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.refreshTokenService = refreshTokenService;
		this.jwtService = jwtService;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AuthResult register(RegisterRequest request) {
		String email = request.email().trim().toLowerCase();
		if (COMMON_PASSWORDS.contains(request.password().toLowerCase())) {
			throw new ApiException.Validation("Request validation failed",
				Map.of("password", "Password is too common"));
		}
		if (userRepository.existsByEmail(email)) {
			throw new ApiException.Conflict("Email already registered");
		}
		UserEntity user = new UserEntity();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setFirstName(request.firstName());
		user.setLastName(request.lastName());
		user.setPhone(request.phone());
		user.setRole(UserEntity.Role.CUSTOMER);
		user.setStatus(UserEntity.Status.ACTIVE);
		try {
			userRepository.saveAndFlush(user);
		}
		catch (org.springframework.dao.DataIntegrityViolationException e) {
			// lost a concurrent registration race on uk_users_email
			throw new ApiException.Conflict("Email already registered");
		}
		return issue(user);
	}

	@Transactional
	public AuthResult login(LoginRequest request) {
		String email = request.email().trim().toLowerCase();
		UserEntity user = userRepository.findByEmail(email).orElse(null);
		if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException.Unauthenticated("Invalid email or password");
		}
		if (user.getStatus() != UserEntity.Status.ACTIVE) {
			throw new ApiException.Forbidden("Account is locked or disabled");
		}
		return issue(user);
	}

	@Transactional
	public AuthResult refresh(String rawRefreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
		UserEntity user = rotation.user();
		return new AuthResult(
			new AuthResponse(jwtService.generateAccessToken(user.getId(), user.getRole().name()),
				UserResponse.from(user)),
			rotation.rawToken());
	}

	@Transactional(readOnly = true)
	public UserResponse me(String userId) {
		return userRepository.findById(userId)
			.map(UserResponse::from)
			.orElseThrow(() -> new ApiException.Unauthenticated("User not found"));
	}

	private AuthResult issue(UserEntity user) {
		String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
		String refreshToken = refreshTokenService.create(user);
		return new AuthResult(new AuthResponse(accessToken, UserResponse.from(user)), refreshToken);
	}
}
