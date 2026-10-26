package clothing.app.domain.review.dto;

import java.time.Instant;

/**
 * A public review. The reviewer is shown as a first name and an initial only —
 * enough to be credible, not enough to identify a customer.
 */
public record ReviewResponse(
		String id,
		int rating,
		String title,
		String comment,
		String authorName,
		boolean verifiedPurchase,
		Instant createdAt) {
}
