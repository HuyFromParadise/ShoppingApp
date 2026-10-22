package clothing.app.common.response;

import java.util.List;

/**
 * Standard success envelope: {@code { "data": ..., "message": "Success" }}.
 * Controllers never serialize entities; every response is a DTO.
 */
public record ApiResponse<T>(T data, String message) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(data, "Success");
	}

	public static <T> ApiResponse<T> success(T data, String message) {
		return new ApiResponse<>(data, message);
	}
}
