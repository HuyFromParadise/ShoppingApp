package clothing.app.domain.user.dto;

import clothing.app.domain.user.UserEntity;

/** User data safe to expose to the client; never includes the password hash. */
public record UserResponse(String id, String email, String firstName, String lastName,
		String phone, String role, String status) {

	public static UserResponse from(UserEntity user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(),
			user.getLastName(), user.getPhone(), user.getRole().name(), user.getStatus().name());
	}
}
