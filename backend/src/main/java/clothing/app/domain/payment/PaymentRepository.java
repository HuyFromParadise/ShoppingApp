package clothing.app.domain.payment;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {

	List<PaymentEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);

	Optional<PaymentEntity> findFirstByOrderIdOrderByCreatedAtDesc(String orderId);

	/** Locks the order's payments so a confirm and a webhook cannot both settle it. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PaymentEntity p where p.orderId = :orderId order by p.createdAt")
	List<PaymentEntity> findAllForUpdateByOrderId(@Param("orderId") String orderId);
}
