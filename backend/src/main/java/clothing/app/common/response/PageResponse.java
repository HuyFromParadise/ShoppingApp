package clothing.app.common.response;

import java.util.List;

/**
 * Paginated success envelope:
 * {@code { "data": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "message": "Success" }}
 */
public record PageResponse<T>(
	List<T> data,
	int page,
	int size,
	long totalElements,
	int totalPages,
	String message
) {

	public static <T> PageResponse<T> of(List<T> data, int page, int size, long totalElements) {
		int totalPages = (int) Math.ceil((double) totalElements / Math.max(size, 1));
		return new PageResponse<>(data, page, size, totalElements, totalPages, "Success");
	}
}
