package clothing.app.domain.review.dto;

import java.util.List;

/** Reviews of one product, with the summary the page shows above them. */
public record ProductReviewsResponse(
		double averageRating,
		long reviewCount,
		/** Count of reviews per rating, index 0 = 1 star … index 4 = 5 stars. */
		List<Long> ratingCounts,
		List<ReviewResponse> items) {
}
