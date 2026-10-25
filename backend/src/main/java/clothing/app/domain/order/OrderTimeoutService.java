package clothing.app.domain.order;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.domain.order.OrderEntity.Status;

/**
 * Cancels unpaid orders after the payment window and releases their stock
 * (ADR-005). Stock is taken at creation, so an abandoned {@code PENDING_PAYMENT}
 * order would otherwise hold a unit forever.
 *
 * <p>Each order is cancelled in its own transaction while holding its row lock,
 * so a payment that settles at the same moment either confirms the order first
 * (this sweep then finds it no longer pending) or arrives after the cancellation
 * and is ignored by the payment service. A crash mid-sweep leaves the remaining
 * expired orders for the next run; cancelling twice is impossible because the
 * order is no longer pending.
 */
@Service
public class OrderTimeoutService {

	private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);
	private static final int BATCH_SIZE = 100;

	private final OrderRepository orderRepository;
	private final OrderStatusService orderStatusService;
	private final Duration paymentWindow;

	public OrderTimeoutService(OrderRepository orderRepository,
			OrderStatusService orderStatusService,
			@Value("${app.orders.payment-window-minutes:30}") long paymentWindowMinutes) {
		this.orderRepository = orderRepository;
		this.orderStatusService = orderStatusService;
		this.paymentWindow = Duration.ofMinutes(Math.max(1, paymentWindowMinutes));
	}

	@Scheduled(fixedDelayString = "${app.orders.timeout-sweep-interval-ms:60000}",
		initialDelayString = "${app.orders.timeout-sweep-interval-ms:60000}")
	public void sweep() {
		int cancelled = cancelExpired(Instant.now());
		if (cancelled > 0) {
			log.info("Cancelled {} unpaid order(s) past the {} minute payment window",
				cancelled, paymentWindow.toMinutes());
		}
	}

	/**
	 * Cancels a bounded page of expired orders as of {@code now} and returns how
	 * many were cancelled. Accepts an explicit clock so tests do not wait.
	 */
	@Transactional
	public int cancelExpired(Instant now) {
		Instant cutoff = now.minus(paymentWindow);
		List<OrderEntity> expired = orderRepository
			.findForTimeout(Status.PENDING_PAYMENT, cutoff, PageRequest.of(0, BATCH_SIZE))
			.getContent();
		for (OrderEntity order : expired) {
			orderStatusService.applyTransition(order, Status.CANCELLED,
				OrderStateMachine.Actor.SYSTEM, null, "Payment window expired");
		}
		return expired.size();
	}
}
