package clothing.app.domain.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import clothing.app.common.exception.ApiException;

/**
 * Promotion maths and rules in isolation (ADR-008: BigDecimal, HALF_UP,
 * rounded to the minor unit). These are the cases a checkout total depends on,
 * so they are pinned without a database.
 */
class PromotionServiceTest {

	private PromotionRepository repository;
	private PromotionService service;
	private final Instant now = Instant.parse("2026-10-25T00:00:00Z");

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(PromotionRepository.class);
		service = new PromotionService(repository);
	}

	@Test
	void noCodeMeansNoDiscount() {
		assertThat(service.apply(null, 100000, now).applied()).isFalse();
		assertThat(service.apply("   ", 100000, now).applied()).isFalse();
	}

	@Test
	void normalisesTheCodeBeforeLookup() {
		PromotionEntity promotion = percent(10, null);
		when(repository.findByCode("SAVE10")).thenReturn(Optional.of(promotion));

		PromotionService.AppliedPromotion applied = service.apply("  save10 ", 500000, now);

		assertThat(applied.code()).isEqualTo("SAVE10");
		assertThat(applied.discountMinor()).isEqualTo(50000);
		assertThat(applied.type()).isEqualTo("PERCENT");
	}

	@Test
	void percentageRoundsHalfUpToTheMinorUnit() {
		// 599000 * 15% = 89850 exactly
		assertThat(service.apply(codeFor(percent(15, null)), 599000, now).discountMinor())
			.isEqualTo(89850);
		// 1005 * 33% = 331.65 → 332 (HALF_UP), never 331
		assertThat(service.apply(codeFor(percent(33, null)), 1005, now).discountMinor())
			.isEqualTo(332);
		// 101 * 50% = 50.5 → 51
		assertThat(service.apply(codeFor(percent(50, null)), 101, now).discountMinor())
			.isEqualTo(51);
	}

	@Test
	void appliesTheMaximumDiscountCap() {
		// 1,000,000 at 50% would be 500,000, capped at 100,000
		assertThat(service.apply(codeFor(percent(50, 100000L)), 1000000, now).discountMinor())
			.isEqualTo(100000);
	}

	@Test
	void aFixedDiscountNeverExceedsTheSubtotal() {
		PromotionEntity fixed = fixed(500000L);
		when(repository.findByCode("BIG")).thenReturn(Optional.of(fixed));

		assertThat(service.apply("BIG", 120000, now).discountMinor()).isEqualTo(120000);
	}

	@Test
	void rejectsACodeBelowTheMinimumOrder() {
		PromotionEntity promotion = percent(10, null);
		promotion.setMinOrder(500000L);
		when(repository.findByCode("SAVE10")).thenReturn(Optional.of(promotion));

		assertThatThrownBy(() -> service.apply("SAVE10", 499999, now))
			.isInstanceOf(ApiException.Validation.class)
			.hasMessageContaining("minimum");
	}

	@Test
	void rejectsAnUnknownInactiveOrExpiredCode() {
		when(repository.findByCode("NOPE")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.apply("NOPE", 100000, now))
			.isInstanceOf(ApiException.Validation.class)
			.hasMessageContaining("not valid");

		PromotionEntity inactive = percent(10, null);
		inactive.setIsActive(false);
		when(repository.findByCode("OFF")).thenReturn(Optional.of(inactive));
		assertThatThrownBy(() -> service.apply("OFF", 100000, now))
			.isInstanceOf(ApiException.Validation.class)
			.hasMessageContaining("no longer available");

		PromotionEntity expired = percent(10, null);
		expired.setEndsAt(now.minus(1, ChronoUnit.SECONDS));
		when(repository.findByCode("OLD")).thenReturn(Optional.of(expired));
		assertThatThrownBy(() -> service.apply("OLD", 100000, now))
			.isInstanceOf(ApiException.Validation.class)
			.hasMessageContaining("expired");

		PromotionEntity future = percent(10, null);
		future.setStartsAt(now.plus(1, ChronoUnit.DAYS));
		when(repository.findByCode("SOON")).thenReturn(Optional.of(future));
		assertThatThrownBy(() -> service.apply("SOON", 100000, now))
			.isInstanceOf(ApiException.Validation.class)
			.hasMessageContaining("not started");
	}

	@Test
	void rejectsAMisconfiguredPercentage() {
		// A percentage over 100 would make the discount exceed the goods.
		PromotionEntity broken = percent(150, null);
		when(repository.findByCode("BROKEN")).thenReturn(Optional.of(broken));

		assertThatThrownBy(() -> service.apply("BROKEN", 100000, now))
			.isInstanceOf(ApiException.Validation.class)
			.hasMessageContaining("misconfigured");
	}

	@Test
	void namesTheFieldForTheClient() {
		when(repository.findByCode(any())).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.apply("BAD", 1000, now))
			.isInstanceOfSatisfying(ApiException.Validation.class,
				ex -> assertThat(ex.getFieldErrors()).containsKey("promotionCode"));
	}

	private String codeFor(PromotionEntity promotion) {
		when(repository.findByCode(promotion.getCode())).thenReturn(Optional.of(promotion));
		return promotion.getCode();
	}

	private static PromotionEntity percent(long value, Long maxDiscount) {
		PromotionEntity promotion = new PromotionEntity();
		promotion.setCode("SAVE10");
		promotion.setType(PromotionEntity.Type.PERCENT);
		promotion.setValue(value);
		promotion.setMaxDiscount(maxDiscount);
		promotion.setMinOrder(0L);
		promotion.setIsActive(true);
		return promotion;
	}

	private static PromotionEntity fixed(long value) {
		PromotionEntity promotion = new PromotionEntity();
		promotion.setCode("BIG");
		promotion.setType(PromotionEntity.Type.FIXED);
		promotion.setValue(value);
		promotion.setMinOrder(0L);
		promotion.setIsActive(true);
		return promotion;
	}
}
