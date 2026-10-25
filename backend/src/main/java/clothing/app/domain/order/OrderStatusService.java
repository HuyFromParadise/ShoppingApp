package clothing.app.domain.order;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.domain.inventory.InventoryService;
import clothing.app.domain.order.OrderEntity.PaymentStatus;
import clothing.app.domain.order.OrderEntity.Status;

/**
 * Applies one fulfillment transition inside a transaction that already owns the
 * order's row lock, including its side effects: release (restock) on
 * cancellation/return and the payment-status change that goes with it.
 *
 * <p>It always runs joined to the caller's transaction, never on its own, so the
 * status update, the stock movements, the history row and any payment change
 * either all commit or all roll back. Idempotence comes from the current status:
 * a transition is only applied from a state that still allows it.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OrderStatusService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderStatusHistoryRepository historyRepository;
	private final InventoryService inventoryService;

	public OrderStatusService(OrderRepository orderRepository,
			OrderItemRepository orderItemRepository,
			OrderStatusHistoryRepository historyRepository,
			InventoryService inventoryService) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.historyRepository = historyRepository;
		this.inventoryService = inventoryService;
	}

	/**
	 * Validates and applies {@code to}, restocking when the target releases the
	 * inventory the order holds, and recording who caused the change.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public OrderEntity transition(String orderId, Status to, OrderStateMachine.Actor actor,
			String actorUserId, String note) {
		OrderEntity order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new clothing.app.common.exception.ApiException.NotFound(
				"Order not found"));
		applyTransition(order, to, actor, actorUserId, note);
		return order;
	}

	/** Same rules, for a caller that already holds the locked entity. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void applyTransition(OrderEntity order, Status to, OrderStateMachine.Actor actor,
			String actorUserId, String note) {
		OrderStateMachine.require(order.getStatus(), to, actor);
		Status from = order.getStatus();
		order.setStatus(to);

		// A cancelled or returned order no longer holds any stock, so this is the
		// one release path (DESIGN_REFERENCE §7).
		if (releasesInventory(to)) {
			releaseStock(order);
		}
		if (to == Status.CANCELLED && order.getPaymentStatus() == PaymentStatus.PAID) {
			order.setPaymentStatus(PaymentStatus.REFUNDED);
		}
		orderRepository.save(order);
		saveHistory(order, from, to, actorUserId, note);
	}

	/** Records a history row without moving the status (payment transitions). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordHistory(OrderEntity order, Status from, Status to, String actorUserId,
			String note) {
		saveHistory(order, from, to, actorUserId, note);
	}

	private static boolean releasesInventory(Status to) {
		// CANCELLED can only be reached from PENDING_PAYMENT, CONFIRMED or
		// PROCESSING, so those are exactly the orders that may restock.
		return to == Status.CANCELLED || to == Status.RETURNED;
	}

	private void releaseStock(OrderEntity order) {
		List<OrderItemEntity> items = orderItemRepository
			.findByOrderIdOrderByCreatedAtAsc(order.getId());
		for (OrderItemEntity item : items) {
			inventoryService.release(item.getVariantId(), item.getQuantity());
		}
	}

	private void saveHistory(OrderEntity order, Status from, Status to, String actorUserId,
			String note) {
		OrderStatusHistoryEntity history = new OrderStatusHistoryEntity();
		history.setOrderId(order.getId());
		history.setFromStatus(from);
		history.setToStatus(to);
		history.setActorUserId(actorUserId);
		history.setNote(note);
		historyRepository.save(history);
	}
}
