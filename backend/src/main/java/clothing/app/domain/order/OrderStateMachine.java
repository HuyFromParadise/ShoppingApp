package clothing.app.domain.order;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import clothing.app.common.exception.ApiException;
import clothing.app.domain.order.OrderEntity.Status;

/**
 * The one place fulfillment transitions are declared (DESIGN_REFERENCE §7).
 *
 * <p>Actors are enforced together with the target state: a customer may cancel
 * a {@code PENDING_PAYMENT} or {@code CONFIRMED} order and may ask for a return
 * once the order shipped, while {@code PROCESSING -> CANCELLED} is admin-only.
 * Every other pair is rejected as {@code INVALID_STATE_TRANSITION}.
 */
public final class OrderStateMachine {

	private static final Map<Status, Set<Status>> TRANSITIONS = allowedTransitions();

	/** Who is driving the change; the same pair can be legal for one actor only. */
	public enum Actor {
		CUSTOMER, ADMIN, SYSTEM
	}

	private OrderStateMachine() {
	}

	public static Set<Status> allowedFrom(Status from) {
		return TRANSITIONS.getOrDefault(from, Set.of());
	}

	public static boolean canMove(Status from, Status to, Actor actor) {
		return from != null && to != null
			&& allowedFrom(from).contains(to)
			&& isActorAllowed(from, to, actor);
	}

	/** Validates a transition or fails with {@code INVALID_STATE_TRANSITION}. */
	public static void require(Status from, Status to, Actor actor) {
		if (canMove(from, to, actor)) {
			return;
		}
		if (from != null && allowedFrom(from).contains(to)) {
			throw new ApiException.Forbidden("This order change requires an administrator");
		}
		throw new ApiException.InvalidStateTransition(
			"An order cannot go from " + describe(from) + " to " + describe(to));
	}

	/** Parses a client-supplied status name for the admin API. */
	public static Status parse(String value) {
		if (value == null || value.isBlank()) {
			throw new ApiException.Validation("Status is required");
		}
		try {
			return Status.valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException.Validation("Unknown order status");
		}
	}

	private static boolean isActorAllowed(Status from, Status to, Actor actor) {
		if (actor == Actor.ADMIN) {
			return true;
		}
		// The system actor is the payment gateway, timeouts and returns: it never
		// ships or confirms anything an admin must decide, except a paid order.
		if (actor == Actor.SYSTEM) {
			return (from == Status.PENDING_PAYMENT && to == Status.CONFIRMED)
				|| (from == Status.PENDING_PAYMENT && to == Status.CANCELLED);
		}
		return switch (from) {
			case PENDING_PAYMENT, CONFIRMED -> to == Status.CANCELLED;
			case SHIPPED, DELIVERED -> to == Status.RETURN_REQUESTED;
			default -> false;
		};
	}

	private static Map<Status, Set<Status>> allowedTransitions() {
		Map<Status, Set<Status>> map = new EnumMap<>(Status.class);
		map.put(Status.PENDING_PAYMENT, EnumSet.of(Status.CONFIRMED, Status.CANCELLED));
		map.put(Status.CONFIRMED, EnumSet.of(Status.PROCESSING, Status.CANCELLED));
		map.put(Status.PROCESSING, EnumSet.of(Status.SHIPPED, Status.CANCELLED));
		map.put(Status.SHIPPED, EnumSet.of(Status.DELIVERED, Status.RETURN_REQUESTED));
		map.put(Status.DELIVERED, EnumSet.of(Status.RETURN_REQUESTED));
		map.put(Status.RETURN_REQUESTED, EnumSet.of(Status.RETURNED, Status.SHIPPED,
			Status.DELIVERED));
		return Collections.unmodifiableMap(map);
	}

	private static String describe(Status status) {
		return status == null ? "NEW" : status.name();
	}
}
