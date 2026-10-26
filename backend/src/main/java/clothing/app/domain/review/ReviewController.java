package clothing.app.domain.review;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.review.dto.CreateReviewRequest;
import clothing.app.domain.review.dto.ProductReviewsResponse;
import clothing.app.domain.review.dto.ReviewResponse;

/**
 * Product reviews (PROJECT_SPEC §11: {@code GET /products/{slug}/reviews}).
 *
 * <p>Reading is public (and only for active products); writing needs a signed-in
 * customer. The author is always the token's user, never a body field.
 */
@RestController
@RequestMapping("/api/v1/products/{slug}/reviews")
public class ReviewController {

	private final ReviewService reviewService;

	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@GetMapping
	public ApiResponse<ProductReviewsResponse> list(
			@org.springframework.web.bind.annotation.PathVariable String slug) {
		return ApiResponse.success(reviewService.list(slug));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ReviewResponse>> create(@CurrentUser String userId,
			@org.springframework.web.bind.annotation.PathVariable String slug,
			@Valid @RequestBody CreateReviewRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(reviewService.create(userId, slug, request)));
	}
}
