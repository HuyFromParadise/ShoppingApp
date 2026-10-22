package clothing.app.common.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Constrains a money amount stored as BIGINT minor units to be non-negative.
 * VND has no subunit, so amounts are whole minor units (e.g. 499000 for 499,000 đ).
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MoneyValidator.class)
public @interface Money {
	String message() default "must be a non-negative money amount (minor units)";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
