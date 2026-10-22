package clothing.app.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;

import clothing.app.domain.order.OrderEntity;

/** Order persistence. Reads (history, detail) belong to the orders workflow. */
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
}
