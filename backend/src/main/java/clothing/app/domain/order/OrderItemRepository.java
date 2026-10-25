package clothing.app.domain.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, String> {

	List<OrderItemEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);

	List<OrderItemEntity> findByOrderIdInOrderByCreatedAtAsc(List<String> orderIds);
}
