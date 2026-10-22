package clothing.app.domain.user;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import clothing.app.common.response.ApiResponse;
import clothing.app.common.security.CurrentUser;
import clothing.app.domain.user.dto.UpdateProfileRequest;
import clothing.app.domain.user.dto.UserResponse;

/**
 * Profile of the signed-in customer (DESIGN_REFERENCE §5.3). The customer is
 * taken from the access token, never from the request body.
 */
@RestController
@RequestMapping("/api/v1/account/profile")
public class ProfileController {

	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping
	public ApiResponse<UserResponse> profile(@CurrentUser String userId) {
		return ApiResponse.success(profileService.profile(userId));
	}

	@PatchMapping
	public ApiResponse<UserResponse> update(@CurrentUser String userId,
			@Valid @RequestBody UpdateProfileRequest request) {
		return ApiResponse.success(profileService.update(userId, request));
	}
}
