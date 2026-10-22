package clothing.app.domain.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository
		extends JpaRepository<IdempotencyRecordEntity, String> {

	Optional<IdempotencyRecordEntity> findByUserIdAndIdempotencyKey(String userId,
		String idempotencyKey);
}
