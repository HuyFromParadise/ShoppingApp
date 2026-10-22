package clothing.app.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Translates exceptions into the standard error envelope:
 * {@code { timestamp, status, code, message, fieldErrors?, path }}.
 * {@code fieldErrors} is omitted for non-validation errors.
 * Internal messages are never returned to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorBody> handleApiException(ApiException ex, WebRequest request) {
		ErrorBody body = ErrorBuilder.build(
			ex.getErrorCode(),
			ex.getStatus(),
			safeMessage(ex),
			ex.getFieldErrors().isEmpty() ? null : ex.getFieldErrors(),
			path(request)
		);
		return ResponseEntity.status(ex.getStatus()).body(body);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.merge(fe.getField(), fe.getDefaultMessage(), (a, b) -> a + "; " + b);
		}
		ErrorBody body = ErrorBuilder.build(
			ErrorCode.VALIDATION_ERROR,
			400,
			"Request validation failed",
			fieldErrors,
			path(request)
		);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorBody> handleTypeMismatch(
		MethodArgumentTypeMismatchException ex, WebRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		fieldErrors.put(ex.getName(), "Invalid value");
		ErrorBody body = ErrorBuilder.build(
			ErrorCode.VALIDATION_ERROR,
			400,
			"Request validation failed",
			fieldErrors,
			path(request)
		);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ErrorBody> handleNotFound(NoHandlerFoundException ex, WebRequest request) {
		ErrorBody body = ErrorBuilder.build(
			ErrorCode.NOT_FOUND,
			404,
			"Resource not found",
			null,
			path(request)
		);
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorBody> handleInternal(Exception ex, WebRequest request) {
		// Internal messages are logged, never returned.
		ErrorBody body = ErrorBuilder.build(
			ErrorCode.INTERNAL_ERROR,
			500,
			"An unexpected error occurred",
			null,
			path(request)
		);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

	private static String safeMessage(ApiException ex) {
		return ex.getMessage() == null || ex.getMessage().isBlank()
			? ex.getErrorCode().name()
			: ex.getMessage();
	}

	private static String path(WebRequest request) {
		String p = request.getDescription(false);
		if (p != null && p.startsWith("uri=")) {
			p = p.substring(4);
		}
		return p;
	}

	public record ErrorBody(
		Instant timestamp,
		int status,
		ErrorCode code,
		String message,
		Map<String, String> fieldErrors,
		String path
	) {}

	static final class ErrorBuilder {
		private ErrorBuilder() {}

		static ErrorBody build(ErrorCode code, int status, String message,
			Map<String, String> fieldErrors, String path) {
			return new ErrorBody(Instant.now(), status, code, message, fieldErrors, path);
		}
	}
}
