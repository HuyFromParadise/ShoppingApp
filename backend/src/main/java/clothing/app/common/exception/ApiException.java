package clothing.app.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base business exception carrying a stable {@link ErrorCode}, HTTP status and optional field errors.
 * Thrown by services; translated to the error envelope by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;
	private final int status;
	private final Map<String, String> fieldErrors;

	public ApiException(ErrorCode errorCode, int status, String message) {
		super(message);
		this.errorCode = errorCode;
		this.status = status;
		this.fieldErrors = Map.of();
	}

	public ApiException(ErrorCode errorCode, int status, String message, Map<String, String> fieldErrors) {
		super(message);
		this.errorCode = errorCode;
		this.status = status;
		this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public int getStatus() {
		return status;
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}

	/* convenience subclasses */

	public static class Validation extends ApiException {
		public Validation(String message, Map<String, String> fieldErrors) {
			super(ErrorCode.VALIDATION_ERROR, 400, message, fieldErrors);
		}

		public Validation(String message) {
			super(ErrorCode.VALIDATION_ERROR, 400, message);
		}
	}

	public static class NotFound extends ApiException {
		public NotFound(String message) {
			super(ErrorCode.NOT_FOUND, 404, message);
		}
	}

	public static class Forbidden extends ApiException {
		public Forbidden(String message) {
			super(ErrorCode.FORBIDDEN, 403, message);
		}
	}

	public static class Unauthenticated extends ApiException {
		public Unauthenticated(String message) {
			super(ErrorCode.UNAUTHENTICATED, 401, message);
		}
	}

	public static class Conflict extends ApiException {
		public Conflict(String message) {
			super(ErrorCode.CONFLICT, 409, message);
		}
	}

	public static class OutOfStock extends ApiException {
		public OutOfStock(String message) {
			super(ErrorCode.OUT_OF_STOCK, 409, message);
		}
	}

	public static class InvalidStateTransition extends ApiException {
		public InvalidStateTransition(String message) {
			super(ErrorCode.INVALID_STATE_TRANSITION, 409, message);
		}
	}

	public static class IdempotencyKeyReused extends ApiException {
		public IdempotencyKeyReused(String message) {
			super(ErrorCode.IDEMPOTENCY_KEY_REUSED, 409, message);
		}
	}

	public static class ProviderNotConfigured extends ApiException {
		public ProviderNotConfigured() {
			super(ErrorCode.PROVIDER_NOT_CONFIGURED, 503, "Payment provider is not configured");
		}
	}

	public static class PaymentFailed extends ApiException {
		public PaymentFailed(String message) {
			super(ErrorCode.PAYMENT_FAILED, 402, message);
		}
	}

	public static class RateLimited extends ApiException {
		public RateLimited(String message) {
			super(ErrorCode.RATE_LIMITED, 429, message);
		}
	}
}
