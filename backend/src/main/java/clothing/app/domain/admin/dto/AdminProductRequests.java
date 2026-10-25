package clothing.app.domain.admin.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Admin product writes (DESIGN_REFERENCE §5.3, Phase 6).
 *
 * <p>Prices are integer minor units like everywhere else (ADR-008). The slug is
 * optional on create (derived from the name) and on update is write-once by
 * convention: changing it would break links, so the service rejects a change
 * with 409 rather than silently doing it.
 */
public final class AdminProductRequests {

	private static final String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

	private AdminProductRequests() {
	}

	public record CreateProductRequest(
			@NotBlank(message = "Name is required")
			@Size(max = 200, message = "Name must be at most 200 characters")
			String name,

			@Pattern(regexp = SLUG_PATTERN,
				message = "Slug must be lowercase letters, digits and single hyphens")
			@Size(max = 200, message = "Slug must be at most 200 characters")
			String slug,

			@Size(max = 500, message = "Short description must be at most 500 characters")
			String shortDescription,

			String description,

			@NotBlank(message = "Category is required")
			@Size(max = 36)
			String categoryId,

			@NotNull(message = "Base price is required")
			@Positive(message = "Base price must be greater than zero")
			Long basePrice,

			@Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter code")
			String currency,

			@Pattern(regexp = "DRAFT|ACTIVE|ARCHIVED",
				message = "Status must be DRAFT, ACTIVE or ARCHIVED")
			String status,

			@Size(max = 200, message = "Material must be at most 200 characters")
			String material,

			@Size(max = 100, message = "Fit must be at most 100 characters")
			String fit,

			String careInstructions,

			Boolean featured,

			@NotEmpty(message = "At least one variant is required")
			@Size(max = 100, message = "A product may have at most 100 variants")
			@Valid
			List<VariantRequest> variants) {
	}

	public record UpdateProductRequest(
			@Size(min = 1, max = 200, message = "Name must be 1-200 characters")
			String name,

			@Size(max = 500, message = "Short description must be at most 500 characters")
			String shortDescription,

			String description,

			@Size(max = 36)
			String categoryId,

			@Positive(message = "Base price must be greater than zero")
			Long basePrice,

			@Pattern(regexp = "DRAFT|ACTIVE|ARCHIVED",
				message = "Status must be DRAFT, ACTIVE or ARCHIVED")
			String status,

			@Size(max = 200, message = "Material must be at most 200 characters")
			String material,

			@Size(max = 100, message = "Fit must be at most 100 characters")
			String fit,

			String careInstructions,

			Boolean featured) {
	}

	public record VariantRequest(
			@Size(max = 64, message = "SKU must be at most 64 characters")
			String sku,

			@Size(max = 32, message = "Size must be at most 32 characters")
			String size,

			@Size(max = 64, message = "Color must be at most 64 characters")
			String color,

			@Positive(message = "Price override must be greater than zero")
			Long priceOverride,

			@NotNull(message = "Stock is required")
			@PositiveOrZero(message = "Stock cannot be negative")
			Integer stockQuantity,

			Boolean active) {
	}

	/** Partial variant edit; absent fields stay unchanged. */
	public record UpdateVariantRequest(
			@Size(min = 1, max = 64, message = "SKU must be 1-64 characters")
			String sku,

			@Size(max = 32, message = "Size must be at most 32 characters")
			String size,

			@Size(max = 64, message = "Color must be at most 64 characters")
			String color,

			// explicit null is meaningful: it clears the override
			Long priceOverride,

			Boolean active) {
	}

	/**
	 * Absolute stock set (DESIGN_REFERENCE §5.3). The value is the new absolute
	 * quantity, never a delta, and the change is audited.
	 */
	public record SetStockRequest(
			@NotNull(message = "Stock is required")
			@PositiveOrZero(message = "Stock cannot be negative")
			Integer stockQuantity,

			@Size(max = 200, message = "Reason must be at most 200 characters")
			String reason) {
	}
}
