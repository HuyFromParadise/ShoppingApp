package clothing.app.domain.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, String> {

	List<OrderItemEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);

	List<OrderItemEntity> findByOrderIdInOrderByCreatedAtAsc(List<String> orderIds);

	/**
	 * Purchases that make a review eligible: this customer has an order item for
	 * one of the given variants whose order reached a delivered-ish state. Used to
	 * verify a purchase (PROJECT_SPEC §7 Review), not to gate every review.
	 */
	@Query("select distinct i.orderId from OrderItemEntity i, OrderEntity o "
		+ "where o.id = i.orderId and o.userId = :userId and i.variantId in :variantIds "
		+ "and o.status in :statuses")
	List<String> findEligibleOrderIds(@Param("userId") String userId,
		@Param("variantIds") java.util.Collection<String> variantIds,
		@Param("statuses") java.util.Collection<OrderEntity.Status> statuses);
}
