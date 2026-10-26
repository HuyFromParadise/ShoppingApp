package clothing.app.domain.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import clothing.app.common.exception.ApiException;

/**
 * Turns a promotion code into a discount (PROJECT_SPEC §7 Promotion; ADR-008).
 *
 * <p>Every condition is checked here, on the server, against the prices the
 * server just computed: the client sends a code, never an amount. A code that
 * does not qualify is rejected with the reason rather than silently ignored, so
 * the customer knows why the total did not change.
 *
 * <p>Percentages are applied with {@link BigDecimal} and rounded {@code HALF_UP}
 * to the minor unit, as ADR-008 requires; the result can never exceed the
 * subtotal, and a capped discount can never exceed {@code max_discount}.
 */
@Service
@Transactional(readOnly = true)
public class PromotionService {

	private static final int MAX_CODE_LENGTH = 50;

	private final PromotionRepository promotionRepository;

	public PromotionService(PromotionRepository promotionRepository) {
		this.promotionRepository = promotionRepository;
	}

	/**
	 * Resolves a code to a discount for a subtotal, or throws a validation error
	 * explaining why it cannot be used.
	 *
	 * @param subtotalMinor the server-computed subtotal
	 * @return the applied code and its discount; never negative, never above the
	 *     subtotal
	 */
	public AppliedPromotion apply(String code, long subtotalMinor, Instant now) {
		String normalized = normalize(code);
		if (normalized == null) {
			return AppliedPromotion.none();
		}
		if (normalized.length() > MAX_CODE_LENGTH) {
			throw rejected("That promotion code is too long");
		}

		PromotionEntity promotion = promotionRepository.findByCode(normalized)
			.orElseThrow(() -> rejected("That promotion code is not valid"));

		if (!Boolean.TRUE.equals(promotion.getIsActive())) {
			throw rejected("That promotion is no longer available");
		}
		if (promotion.getStartsAt() != null && now.isBefore(promotion.getStartsAt())) {
			throw rejected("That promotion has not started yet");
		}
		if (promotion.getEndsAt() != null && !now.isBefore(promotion.getEndsAt())) {
			throw rejected("That promotion has expired");
		}
		long minimum = promotion.getMinOrder() == null ? 0L : promotion.getMinOrder();
		if (subtotalMinor < minimum) {
			throw rejected("This order does not reach the minimum for that promotion");
		}

		long discount = computeDiscount(promotion, subtotalMinor);
		if (discount <= 0) {
			return AppliedPromotion.none();
		}
		return new AppliedPromotion(normalized, discount,
			promotion.getType() == PromotionEntity.Type.PERCENT ? "PERCENT" : "FIXED");
	}

	/** The discount a code is worth, before any order-level clamping. */
	long computeDiscount(PromotionEntity promotion, long subtotalMinor) {
		long value = promotion.getValue() == null ? 0L : promotion.getValue();
		long raw;
		switch (promotion.getType()) {
			case PERCENT -> {
				// value is a whole number of percent (10 = 10%).
				if (value <= 0 || value > 100) {
					throw rejected("That promotion is misconfigured");
				}
				raw = BigDecimal.valueOf(subtotalMinor)
					.multiply(BigDecimal.valueOf(value))
					.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
					.longValueExact();
			}
			case FIXED -> raw = value;
			default -> throw rejected("That promotion is misconfigured");
		}
		if (promotion.getMaxDiscount() != null && promotion.getMaxDiscount() > 0) {
			raw = Math.min(raw, promotion.getMaxDiscount());
		}
		// Never discount more than the goods are worth, and never below zero.
		return Math.max(0L, Math.min(raw, subtotalMinor));
	}

	/** Codes are stored and compared upper-cased and trimmed (PROJECT_SPEC §5.4). */
	public static String normalize(String code) {
		return StringUtils.hasText(code) ? code.trim().toUpperCase(Locale.ROOT) : null;
	}

	private static ApiException rejected(String message) {
		return new ApiException.Validation(message, Map.of("promotionCode", message));
	}

	/** The discount actually applied to a quote. */
	public record AppliedPromotion(String code, long discountMinor, String type) {

		public boolean applied() {
			return code != null && discountMinor > 0;
		}

		public static AppliedPromotion none() {
			return new AppliedPromotion(null, 0L, null);
		}
	}
}
