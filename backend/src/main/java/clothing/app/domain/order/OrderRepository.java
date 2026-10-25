package clothing.app.domain.order;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Order persistence: reads, transitions and the payment-timeout sweep. */
public interface OrderRepository extends JpaRepository<OrderEntity, String> {

	Page<OrderEntity> findByUserIdOrderByPlacedAtDesc(String userId, Pageable pageable);

	Page<OrderEntity> findAllByOrderByPlacedAtDesc(Pageable pageable);

	Optional<OrderEntity> findByIdAndUserId(String id, String userId);

	Optional<OrderEntity> findByOrderNumber(String orderNumber);

	/** Ownership is checked by the caller; the lock serialises status changes. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from OrderEntity o where o.id = :id")
	Optional<OrderEntity> findByIdForUpdate(@Param("id") String id);

	/**
	 * A bounded page of {@code PENDING_PAYMENT} orders placed before the cutoff,
	 * with their rows locked, so a payment arriving at the same moment either
	 * wins the confirm or sees the cancelled order (ADR-005 timeout job).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from OrderEntity o where o.status = :status and o.placedAt < :placedBefore "
		+ "order by o.placedAt")
	Page<OrderEntity> findForTimeout(@Param("status") OrderEntity.Status status,
		@Param("placedBefore") java.time.Instant placedBefore, Pageable pageable);
}
