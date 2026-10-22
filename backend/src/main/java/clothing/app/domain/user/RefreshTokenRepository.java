package clothing.app.domain.user;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

	// pessimistic lock serializes concurrent refreshes of the same token so only
	// one rotation can win; without it a double-submit mints two live chains
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

	List<RefreshTokenEntity> findByReplacedById(String id);

	List<RefreshTokenEntity> findByUserAndRevokedAtIsNull(UserEntity user);
}
