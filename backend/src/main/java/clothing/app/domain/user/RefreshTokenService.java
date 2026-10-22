package clothing.app.domain.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.common.exception.ApiException;

/**
 * Opaque 256-bit refresh tokens, SHA-256 hashed at rest (never store the raw token),
 * rotated on every refresh. Replaying an already-rotated token is treated as token
 * theft: every non-revoked refresh token of the user is revoked.
 */
@Service
public class RefreshTokenService {

	public record Rotation(UserEntity user, String rawToken) {}

	private final RefreshTokenRepository repository;

	private final long ttlDays;

	private final SecureRandom random = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository repository,
			@Value("${REFRESH_TOKEN_TTL_DAYS:14}") long ttlDays) {
		this.repository = repository;
		this.ttlDays = ttlDays;
	}

	@Transactional
	public String create(UserEntity user) {
		String raw = randomToken();
		RefreshTokenEntity token = new RefreshTokenEntity();
		token.setUser(user);
		token.setTokenHash(hash(raw));
		token.setExpiresAt(Instant.now().plus(Duration.ofDays(ttlDays)));
		repository.save(token);
		return raw;
	}

	/** Rotates the presented token; throws UNAUTHENTICATED when invalid, expired or replayed. */
	@Transactional
	public Rotation rotate(String rawToken) {
		RefreshTokenEntity current = load(rawToken);
		if (current.getExpiresAt().isBefore(Instant.now())) {
			throw new ApiException.Unauthenticated("Invalid refresh token");
		}
		if (current.getRevokedAt() != null) {
			// reuse of a rotated/revoked token: assume theft and revoke the whole chain
			revokeAllForUser(current.getUser());
			throw new ApiException.Unauthenticated("Invalid refresh token");
		}
		String raw = create(current.getUser());
		RefreshTokenEntity created = repository.findByTokenHash(hash(raw)).orElseThrow();
		current.setRevokedAt(Instant.now());
		current.setReplacedById(created.getId());
		repository.save(current);
		return new Rotation(current.getUser(), raw);
	}

	/** Revokes the presented token and its descendants; used on logout. */
	@Transactional
	public void revokeChain(String rawToken) {
		RefreshTokenEntity start = repository.findByTokenHash(hash(rawToken)).orElse(null);
		if (start == null) {
			return;
		}
		revoke(start.getId());
	}

	private void revoke(String tokenId) {
		// walk the rotation chain forward via replaced_by_id
		RefreshTokenEntity token = repository.findById(tokenId).orElse(null);
		if (token == null) {
			return;
		}
		if (token.getRevokedAt() == null) {
			token.setRevokedAt(Instant.now());
			repository.save(token);
		}
		for (RefreshTokenEntity child : repository.findByReplacedById(token.getId())) {
			revoke(child.getId());
		}
	}

	private void revokeAllForUser(UserEntity user) {
		for (RefreshTokenEntity token : repository.findByUserAndRevokedAtIsNull(user)) {
			token.setRevokedAt(Instant.now());
			repository.save(token);
		}
	}

	private RefreshTokenEntity load(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new ApiException.Unauthenticated("Refresh token missing");
		}
		return repository.findByTokenHash(hash(rawToken))
			.orElseThrow(() -> new ApiException.Unauthenticated("Invalid refresh token"));
	}

	private String randomToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
