package clothing.app.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A customer submits one review for a product (PROJECT_SPEC §7 Review). */
public record CreateReviewRequest(
		@NotNull(message = "Rating is required")
		@Min(value = 1, message = "Rating must be between 1 and 5")
		@Max(value = 5, message = "Rating must be between 1 and 5")
		Integer rating,

		@Size(max = 200, message = "Title must be at most 200 characters")
		String title,

		@Size(max = 2000, message = "Review must be at most 2000 characters")
		String comment) {
}
