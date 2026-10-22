package clothing.app.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that a money amount (Long minor units) is non-negative.
 */
public class MoneyValidator implements ConstraintValidator<Money, Long> {

	@Override
	public boolean isValid(Long value, ConstraintValidatorContext context) {
		return value == null || value >= 0;
	}
}
