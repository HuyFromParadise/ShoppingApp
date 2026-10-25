package clothing.app.domain.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository
		extends JpaRepository<OrderStatusHistoryEntity, String> {

	List<OrderStatusHistoryEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);
}
