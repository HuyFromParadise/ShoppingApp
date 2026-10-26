package clothing.app.domain.admin.dto;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Admin promotion writes (PROJECT_SPEC §7 Promotion). The database stores
 * {@code PERCENT}/{@code FIXED}; the spec's {@code PERCENTAGE}/{@code
 * FIXED_AMOUNT} names are accepted as aliases so an operator can use either.
 */
public final class AdminPromotionRequests {

	private static final String TYPE_PATTERN = "PERCENT|PERCENTAGE|FIXED|FIXED_AMOUNT";

	private AdminPromotionRequests() {
	}

	public record CreatePromotionRequest(
			@NotBlank(message = "Code is required")
			@Size(max = 50, message = "Code must be at most 50 characters")
			@Pattern(regexp = "[A-Za-z0-9_-]+",
				message = "Code may contain letters, digits, _ and - only")
			String code,

			@Size(max = 200, message = "Name must be at most 200 characters")
			String name,

			String description,

			@NotBlank(message = "Type is required")
			@Pattern(regexp = TYPE_PATTERN, message = "Type must be PERCENT or FIXED")
			String type,

			/** Percent as a whole number (10 = 10%), or a minor-unit amount. */
			@NotNull(message = "Value is required")
			@Positive(message = "Value must be greater than zero")
			Long value,

			@PositiveOrZero(message = "Minimum order cannot be negative")
			Long minOrder,

			@Positive(message = "Maximum discount must be greater than zero")
			Long maxDiscount,

			Instant startsAt,

			Instant endsAt,

			Boolean active) {
	}

	public record UpdatePromotionRequest(
			@Size(min = 1, max = 200, message = "Name must be 1-200 characters")
			String name,

			String description,

			@Pattern(regexp = TYPE_PATTERN, message = "Type must be PERCENT or FIXED")
			String type,

			// The percent cap (100) depends on the type, so it is checked in the
			// service; a FIXED amount above 100 is legitimate.
			@Positive(message = "Value must be greater than zero")
			Long value,

			@PositiveOrZero(message = "Minimum order cannot be negative")
			Long minOrder,

			@Positive(message = "Maximum discount must be greater than zero")
			Long maxDiscount,

			Instant startsAt,

			Instant endsAt,

			Boolean active) {
	}
}
