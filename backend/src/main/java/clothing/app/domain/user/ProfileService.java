package clothing.app.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import clothing.app.common.exception.ApiException;
import clothing.app.domain.user.dto.UpdateProfileRequest;
import clothing.app.domain.user.dto.UserResponse;

/**
 * The profile of the signed-in customer. Only the mutable contact fields are
 * writable; the email is the login identity and needs a verified change flow.
 */
@Service
public class ProfileService {

	private final UserRepository userRepository;

	public ProfileService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public UserResponse profile(String userId) {
		return UserResponse.from(require(userId));
	}

	@Transactional
	public UserResponse update(String userId, UpdateProfileRequest request) {
		UserEntity user = require(userId);
		if (request.firstName() != null) user.setFirstName(request.firstName().trim());
		if (request.lastName() != null) user.setLastName(request.lastName().trim());
		if (request.phone() != null) user.setPhone(trimToNull(request.phone()));
		return UserResponse.from(userRepository.save(user));
	}

	private UserEntity require(String userId) {
		if (userId == null) {
			throw new ApiException.Unauthenticated("Authentication required");
		}
		return userRepository.findById(userId)
			.orElseThrow(() -> new ApiException.Unauthenticated("Authentication required"));
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
