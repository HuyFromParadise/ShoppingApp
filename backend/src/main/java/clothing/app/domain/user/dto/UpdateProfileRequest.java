package clothing.app.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial profile update: an absent field (null) is left unchanged, an empty
 * string clears an optional field. The email is the login identity and is not
 * editable here. A required field may not be blanked.
 */
public record UpdateProfileRequest(
		@Pattern(regexp = ".*\\S.*", message = "First name must not be blank")
		@Size(max = 100) String firstName,
		@Pattern(regexp = ".*\\S.*", message = "Last name must not be blank")
		@Size(max = 100) String lastName,
		@Size(max = 30) String phone) {
}
