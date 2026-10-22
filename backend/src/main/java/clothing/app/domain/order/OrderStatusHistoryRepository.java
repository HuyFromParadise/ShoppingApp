package clothing.app.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository
		extends JpaRepository<OrderStatusHistoryEntity, String> {
}
