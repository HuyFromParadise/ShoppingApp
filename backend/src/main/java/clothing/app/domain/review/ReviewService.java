package clothing.app.domain.review;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import clothing.app.catalog.product.ProductRepository;
import clothing.app.catalog.product.ProductVariantRepository;
import clothing.app.common.exception.ApiException;
import clothing.app.domain.catalog.product.ProductEntity;
import clothing.app.domain.catalog.product.ProductVariantEntity;
import clothing.app.domain.order.OrderEntity;
import clothing.app.domain.order.OrderItemRepository;
import clothing.app.domain.review.dto.CreateReviewRequest;
import clothing.app.domain.review.dto.ProductReviewsResponse;
import clothing.app.domain.review.dto.ReviewResponse;
import clothing.app.domain.user.UserEntity;
import clothing.app.domain.user.UserRepository;

/**
 * Product reviews (PROJECT_SPEC §7 Review, P1).
 *
 * <p><b>Policy</b> (chosen with the store owner): reviews publish immediately —
 * the V1 schema has no moderation column — and a review is marked
 * <i>verified</i> when the reviewer's own orders show they bought the product.
 * A verified badge is information, not a gate, so a customer may still review
 * what they did not buy.
 *
 * <p><b>One review per purchase.</b> The schema has no {@code order_item_id}, so
 * "per purchase" is enforced as one review per customer per product. A second
 * review is a 409 rather than a silent overwrite.
 */
@Service
public class ReviewService {

	/** Orders that mean the customer actually received the item. */
	static final List<OrderEntity.Status> RECEIVED_STATUSES = List.of(
		OrderEntity.Status.SHIPPED,
		OrderEntity.Status.DELIVERED,
		OrderEntity.Status.RETURN_REQUESTED,
		OrderEntity.Status.RETURNED);

	private static final int MAX_COMMENT = 2000;

	private final ReviewRepository reviewRepository;
	private final ProductRepository productRepository;
	private final ProductVariantRepository variantRepository;
	private final OrderItemRepository orderItemRepository;
	private final UserRepository userRepository;

	public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository,
			ProductVariantRepository variantRepository, OrderItemRepository orderItemRepository,
			UserRepository userRepository) {
		this.reviewRepository = reviewRepository;
		this.productRepository = productRepository;
		this.variantRepository = variantRepository;
		this.orderItemRepository = orderItemRepository;
		this.userRepository = userRepository;
	}

	/** Public: every review of an active product, newest first. */
	@Transactional(readOnly = true)
	public ProductReviewsResponse list(String productSlug) {
		ProductEntity product = requirePublicProduct(productSlug);
		List<ReviewEntity> reviews = reviewRepository
			.findByProductIdOrderByCreatedAtDesc(product.getId());
		Map<String, UserEntity> authors = userRepository
			.findAllById(reviews.stream().map(ReviewEntity::getUserId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(UserEntity::getId, Function.identity()));

		long[] counts = new long[5];
		long total = 0;
		for (ReviewEntity review : reviews) {
			int rating = review.getRating() == null ? 0 : review.getRating();
			if (rating >= 1 && rating <= 5) {
				counts[rating - 1]++;
				total += rating;
			}
		}
		double average = reviews.isEmpty() ? 0d
			: Math.round((total / (double) reviews.size()) * 10.0) / 10.0;

		List<ReviewResponse> items = reviews.stream()
			.map(review -> toResponse(review, authors.get(review.getUserId())))
			.toList();
		return new ProductReviewsResponse(average, reviews.size(),
			List.of(counts[0], counts[1], counts[2], counts[3], counts[4]), items);
	}

	/**
	 * Publishes a review. The rating and text are the client's; the product,
	 * author and verified flag are the server's.
	 */
	@Transactional
	public ReviewResponse create(String userId, String productSlug, CreateReviewRequest request) {
		if (userId == null) {
			throw new ApiException.Unauthenticated("Sign in to write a review");
		}
		ProductEntity product = requirePublicProduct(productSlug);
		validate(request);

		if (reviewRepository.existsByProductIdAndUserId(product.getId(), userId)) {
			throw new ApiException.Conflict("You have already reviewed this product");
		}
		UserEntity user = userRepository.findById(userId)
			.orElseThrow(() -> new ApiException.Unauthenticated("Authentication required"));

		ReviewEntity review = new ReviewEntity();
		review.setProductId(product.getId());
		review.setUserId(userId);
		review.setRating(request.rating());
		review.setTitle(trimToNull(request.title()));
		review.setComment(trimToNull(request.comment()));
		review.setIsVerified(hasPurchased(userId, product.getId()));
		review = reviewRepository.save(review);
		return toResponse(review, user);
	}

	/** Whether this customer may write a review, for the product page's UI. */
	@Transactional(readOnly = true)
	public Eligibility eligibility(String userId, String productSlug) {
		ProductEntity product = requirePublicProduct(productSlug);
		if (userId == null) {
			return new Eligibility(false, false, false);
		}
		boolean written = reviewRepository.existsByProductIdAndUserId(product.getId(), userId);
		boolean purchased = hasPurchased(userId, product.getId());
		return new Eligibility(true, !written, purchased);
	}

	/* --- rules -------------------------------------------------------------- */

	/**
	 * Verified means the customer received an order containing one of this
	 * product's variants. A cancelled or unpaid order does not count.
	 */
	boolean hasPurchased(String userId, String productId) {
		List<String> variantIds = variantRepository
			.findByProductIdOrderBySizeAscColorAsc(productId)
			.stream()
			.map(ProductVariantEntity::getId)
			.toList();
		if (variantIds.isEmpty()) {
			return false;
		}
		return !orderItemRepository
			.findEligibleOrderIds(userId, variantIds, RECEIVED_STATUSES)
			.isEmpty();
	}

	private static void validate(CreateReviewRequest request) {
		if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
			throw new ApiException.Validation("Rating must be between 1 and 5",
				Map.of("rating", "Must be between 1 and 5"));
		}
		if (StringUtils.hasText(request.comment()) && request.comment().trim().length() > MAX_COMMENT) {
			throw new ApiException.Validation("Review is too long",
				Map.of("comment", "Must be at most " + MAX_COMMENT + " characters"));
		}
		if (StringUtils.hasText(request.title()) && request.title().trim().length() > 200) {
			throw new ApiException.Validation("Title is too long",
				Map.of("title", "Must be at most 200 characters"));
		}
	}

	private ProductEntity requirePublicProduct(String slug) {
		return productRepository.findBySlugAndStatus(slug, ProductEntity.Status.ACTIVE)
			.orElseThrow(() -> new ApiException.NotFound("Product not found"));
	}

	/**
	 * A display name that cannot be used to contact the reviewer: first name plus
	 * the initial of the last name.
	 */
	static String authorName(UserEntity user) {
		if (user == null) {
			return "A customer";
		}
		String first = StringUtils.hasText(user.getFirstName()) ? user.getFirstName().trim() : "";
		String last = StringUtils.hasText(user.getLastName()) ? user.getLastName().trim() : "";
		if (first.isEmpty() && last.isEmpty()) {
			return "A customer";
		}
		if (first.isEmpty()) {
			return last;
		}
		return last.isEmpty() ? first : first + " " + last.substring(0, 1).toUpperCase() + ".";
	}

	private static ReviewResponse toResponse(ReviewEntity review, UserEntity author) {
		return new ReviewResponse(
			review.getId(),
			review.getRating() == null ? 0 : review.getRating(),
			review.getTitle(),
			review.getComment(),
			authorName(author),
			Boolean.TRUE.equals(review.getIsVerified()),
			review.getCreatedAt());
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	/** What the product page may offer: sign in, write, or already done. */
	public record Eligibility(boolean signedIn, boolean canReview, boolean verifiedPurchase) {
	}

}
