package clothing.app.common.exception;

/**
 * Stable error codes returned in the error envelope {@code code} field.
 * Safe for clients; internal messages are logged with a correlation id, never returned.
 */
public enum ErrorCode {
	VALIDATION_ERROR,
	UNAUTHENTICATED,
	FORBIDDEN,
	NOT_FOUND,
	CONFLICT,
	OUT_OF_STOCK,
	INVALID_STATE_TRANSITION,
	IDEMPOTENCY_KEY_REUSED,
	RATE_LIMITED,
	PAYMENT_FAILED,
	PROVIDER_NOT_CONFIGURED,
	INTERNAL_ERROR
}
