package clothing.app.domain.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin category writes (DESIGN_REFERENCE §5.3, Phase 6). The slug is optional
 * on create (derived from the name) and immutable afterwards, because published
 * category URLs depend on it.
 */
public final class AdminCategoryRequests {

	private static final String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

	private AdminCategoryRequests() {
	}

	public record CreateCategoryRequest(
			@NotBlank(message = "Name is required")
			@Size(max = 200, message = "Name must be at most 200 characters")
			String name,

			@Pattern(regexp = SLUG_PATTERN,
				message = "Slug must be lowercase letters, digits and single hyphens")
			@Size(max = 200, message = "Slug must be at most 200 characters")
			String slug,

			String description,

			@Size(max = 36)
			String parentId,

			Boolean active,

			@Min(value = 0, message = "Sort order must be zero or greater")
			@Max(value = 1_000_000, message = "Sort order is too large")
			Integer sortOrder) {
	}

	public record UpdateCategoryRequest(
			@Size(min = 1, max = 200, message = "Name must be 1-200 characters")
			String name,

			String description,

			@Size(max = 36)
			String parentId,

			Boolean active,

			@Min(value = 0, message = "Sort order must be zero or greater")
			@Max(value = 1_000_000, message = "Sort order is too large")
			Integer sortOrder) {
	}
}
