package clothing.app.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import clothing.app.common.exception.ApiException;
import clothing.app.common.exception.ErrorCode;
import clothing.app.domain.order.OrderEntity.Status;
import clothing.app.domain.order.OrderStateMachine.Actor;

/** The §7 table, actor by actor, with no database involved. */
class OrderStateMachineTest {

	@ParameterizedTest(name = "{0} -> {1} by {2}")
	@CsvSource({
		// system: payment success and payment timeout only
		"PENDING_PAYMENT, CONFIRMED, SYSTEM",
		"PENDING_PAYMENT, CANCELLED, SYSTEM",
		// customers
		"PENDING_PAYMENT, CANCELLED, CUSTOMER",
		"CONFIRMED, CANCELLED, CUSTOMER",
		"SHIPPED, RETURN_REQUESTED, CUSTOMER",
		"DELIVERED, RETURN_REQUESTED, CUSTOMER",
		// administrators can do all of it
		"PENDING_PAYMENT, CONFIRMED, ADMIN",
		"PENDING_PAYMENT, CANCELLED, ADMIN",
		"CONFIRMED, PROCESSING, ADMIN",
		"CONFIRMED, CANCELLED, ADMIN",
		"PROCESSING, SHIPPED, ADMIN",
		"PROCESSING, CANCELLED, ADMIN",
		"SHIPPED, DELIVERED, ADMIN",
		"RETURN_REQUESTED, RETURNED, ADMIN",
		"RETURN_REQUESTED, SHIPPED, ADMIN",
		"RETURN_REQUESTED, DELIVERED, ADMIN"
	})
	void allowsTheDocumentedTransitions(String from, String to, Actor actor) {
		assertThat(OrderStateMachine.canMove(Status.valueOf(from), Status.valueOf(to), actor))
			.isTrue();
	}

	@ParameterizedTest(name = "{0} -> {1} by {2} is rejected")
	@CsvSource({
		// a customer cannot move an order the admin has taken over
		"PROCESSING, CANCELLED, CUSTOMER",
		"PROCESSING, SHIPPED, CUSTOMER",
		"CONFIRMED, PROCESSING, CUSTOMER",
		"SHIPPED, DELIVERED, CUSTOMER",
		"DELIVERED, RETURN_REQUESTED, SYSTEM",
		// the system actor never makes fulfillment decisions
		"CONFIRMED, PROCESSING, SYSTEM",
		"SHIPPED, DELIVERED, SYSTEM",
		// no skipping and no resurrection
		"PENDING_PAYMENT, PROCESSING, ADMIN",
		"PENDING_PAYMENT, SHIPPED, ADMIN",
		"CONFIRMED, SHIPPED, ADMIN",
		"SHIPPED, CANCELLED, ADMIN",
		"DELIVERED, CANCELLED, ADMIN",
		"CANCELLED, CONFIRMED, ADMIN",
		"CANCELLED, PENDING_PAYMENT, ADMIN",
		"RETURNED, DELIVERED, ADMIN",
		"RETURN_REQUESTED, CANCELLED, ADMIN",
		"RETURN_REQUESTED, RETURN_REQUESTED, ADMIN"
	})
	void rejectsUndocumentedTransitions(String from, String to, Actor actor) {
		assertThat(OrderStateMachine.canMove(Status.valueOf(from), Status.valueOf(to), actor))
			.isFalse();
	}

	@Test
	void aCustomerRetryOfAnAdminOnlyMoveIsForbiddenNotInvalid() {
		assertThatThrownBy(() -> OrderStateMachine.require(Status.PROCESSING, Status.CANCELLED,
			Actor.CUSTOMER))
			.isInstanceOf(ApiException.Forbidden.class)
			.extracting(ex -> ((ApiException) ex).getErrorCode())
			.isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	void anUnknownPairIsAnInvalidStateTransition() {
		assertThatThrownBy(() -> OrderStateMachine.require(Status.CANCELLED, Status.CONFIRMED,
			Actor.ADMIN))
			.isInstanceOf(ApiException.InvalidStateTransition.class)
			.hasMessageContaining("CANCELLED")
			.hasMessageContaining("CONFIRMED");
	}

	@Test
	void parsesAdminStatusNamesCaseInsensitively() {
		assertThat(OrderStateMachine.parse(" processing ")).isEqualTo(Status.PROCESSING);
		assertThatThrownBy(() -> OrderStateMachine.parse("LOST"))
			.isInstanceOf(ApiException.Validation.class);
		assertThatThrownBy(() -> OrderStateMachine.parse(" "))
			.isInstanceOf(ApiException.Validation.class);
	}
}
