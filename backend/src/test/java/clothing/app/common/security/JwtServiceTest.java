package clothing.app.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the JWT access-token round-trip: generate -> parse -> validate.
 */
class JwtServiceTest {

	private static final String SECRET = "01234567890123456789012345678901";

	@Test
	void generateParseValidateRoundTrip() {
		JwtService jwtService = new JwtService(SECRET);
		String token = jwtService.generateAccessToken("user-1", "CUSTOMER");

		assertThat(jwtService.validateToken(token)).isTrue();
		assertThat(jwtService.extractUserId(token)).isEqualTo("user-1");
		assertThat(jwtService.extractRole(token)).isEqualTo("CUSTOMER");
		assertThat(jwtService.isTokenExpired(token)).isFalse();
	}

	@Test
	void invalidTokenIsRejected() {
		JwtService jwtService = new JwtService(SECRET);
		assertThat(jwtService.validateToken("not.a.valid.token")).isFalse();
		assertThatThrownBy(() -> jwtService.extractUserId("not.a.valid.token"))
			.isInstanceOf(RuntimeException.class);
	}

	@Test
	void shortSecretIsRejectedAtConstruction() {
		assertThatThrownBy(() -> new JwtService("too-short"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET must be at least 32 bytes");
	}
}
