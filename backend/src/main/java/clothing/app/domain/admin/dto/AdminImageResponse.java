package clothing.app.domain.admin.dto;

/** One product image as the admin editor lists it. */
public record AdminImageResponse(
		String id,
		String url,
		String altText,
		int sortOrder,
		boolean primary) {
}
