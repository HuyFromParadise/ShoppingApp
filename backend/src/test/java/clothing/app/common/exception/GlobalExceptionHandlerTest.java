package clothing.app.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the error envelope shape produced by {@link GlobalExceptionHandler}:
 * timestamp, status, code, message, fieldErrors (validation only), path.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	private ServletWebRequest request(String path) {
		return new ServletWebRequest(new MockHttpServletRequest("GET", path));
	}

	@Test
	void apiExceptionRendersErrorEnvelope() {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		fieldErrors.put("email", "Must be a valid email address");
		ApiException ex = new ApiException.Validation("Request validation failed", fieldErrors);

		GlobalExceptionHandler.ErrorBody body = handler.handleApiException(ex, request("/api/v1/auth/register")).getBody();

		assertThat(body.status())
.isEqualTo(400);
		assertThat(body.code())
.isEqualTo(ErrorCode.VALIDATION_ERROR);
		assertThat(body.message())
.isEqualTo("Request validation failed");
		assertThat(body.fieldErrors())
.containsEntry("email", "Must be a valid email address");
		assertThat(body.path())
.isEqualTo("/api/v1/auth/register");
		assertThat(body.timestamp())
.isNotNull();
	}

	@Test
	void nonValidationErrorOmitsFieldErrors() {
		NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/api/v1/nope", null);
		GlobalExceptionHandler.ErrorBody body = handler.handleNotFound(ex, request("/api/v1/nope")).getBody();

		assertThat(body.status())
.isEqualTo(404);
		assertThat(body.code())
.isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(body.message())
.isEqualTo("Resource not found");
		assertThat(body.fieldErrors())
.isNull();
		assertThat(body.path())
.isEqualTo("/api/v1/nope");
	}

	@Test
	void malformedBodyIsAValidationError() {
		HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json");
		GlobalExceptionHandler.ErrorBody body = handler.handleUnreadableBody(ex, request("/api/v1/account/addresses")).getBody();

		assertThat(body.status())
.isEqualTo(400);
		assertThat(body.code())
.isEqualTo(ErrorCode.VALIDATION_ERROR);
		assertThat(body.message())
.isEqualTo("Request validation failed");
		assertThat(body.path())
.isEqualTo("/api/v1/account/addresses");
	}

	@Test
	void internalErrorIsClientSafe() {
		GlobalExceptionHandler.ErrorBody body = handler.handleInternal(new RuntimeException("boom"), request("/x")).getBody();

		assertThat(body.status())
.isEqualTo(500);
		assertThat(body.code())
.isEqualTo(ErrorCode.INTERNAL_ERROR);
		assertThat(body.message())
.isEqualTo("An unexpected error occurred");
		assertThat(body.fieldErrors())
.isNull();
	}
}
