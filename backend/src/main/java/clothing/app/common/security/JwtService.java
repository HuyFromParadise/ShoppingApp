package clothing.app.common.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * JWT access-token service (HS256).
 * Claims: sub (user id), role, iat, exp, jti. TTL 15 minutes.
 * JWT_SECRET must be >= 32 bytes; the app refuses to boot otherwise.
 */
@Component
public class JwtService {

	private final SecretKey signingKey;

	private long accessTtlMinutes = 15;

	@Value("${JWT_ACCESS_TTL_MINUTES:15}")
	public void setAccessTtlMinutes(long accessTtlMinutes) {
		this.accessTtlMinutes = accessTtlMinutes;
	}

	public JwtService(@Value("${JWT_SECRET:hueiuwhfffwn98fj294f24f24298ufheuefhiwuh87hf8484f48hf49842jwijw9qj9q8jwdj89dqj89dqjejuiwfhihguwehiwhuhwiegw}") String jwtSecret) {
		if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException(
				"JWT_SECRET must be at least 32 bytes; set it in the environment or .env");
		}
		this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
	}

	public String generateAccessToken(String userId, String role) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + accessTtlMinutes * 60_000L);
		return Jwts.builder()
			.subject(userId)
			.claim("role", role)
			.issuedAt(now)
			.expiration(expiry)
			.id(java.util.UUID.randomUUID().toString())
			.signWith(signingKey, SignatureAlgorithm.HS256)
			.compact();
	}

	public Claims parseAccessToken(String token) {
		return Jwts.parser()
			.verifyWith(signingKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	public String extractUserId(String token) {
		return parseAccessToken(token).getSubject();
	}

	public String extractRole(String token) {
		return String.valueOf(parseAccessToken(token).get("role"));
	}

	public boolean isTokenExpired(String token) {
		return parseAccessToken(token).getExpiration().before(new Date());
	}

	public boolean validateToken(String token) {
		try {
			parseAccessToken(token);
			return !isTokenExpired(token);
		} catch (Exception e) {
			return false;
		}
	}
}
