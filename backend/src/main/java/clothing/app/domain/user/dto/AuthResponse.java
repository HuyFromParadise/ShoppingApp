package clothing.app.domain.user.dto;

/**
 * Login/register/refresh response body. The refresh token travels only as an
 * HttpOnly cookie set by the controller; it is never in the JSON body.
 */
public record AuthResponse(String accessToken, UserResponse user) {
}
