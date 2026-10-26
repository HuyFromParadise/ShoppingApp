package clothing.app.domain.review;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;

/**
 * Whether the signed-in customer may review a product. Kept separate from the
 * public review list so the public payload never depends on who is asking.
 */
@RestController
@RequestMapping("/api/v1/products/{slug}/review-eligibility")
public class ReviewEligibilityController {

	private final ReviewService reviewService;

	public ReviewEligibilityController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@GetMapping
	public ApiResponse<ReviewService.Eligibility> eligibility(@CurrentUser String userId,
			@PathVariable String slug) {
		return ApiResponse.success(reviewService.eligibility(userId, slug));
	}
}
