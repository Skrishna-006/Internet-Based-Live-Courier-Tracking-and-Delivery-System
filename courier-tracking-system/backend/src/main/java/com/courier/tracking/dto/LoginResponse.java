package com.courier.tracking.dto;

/**
 * Outbound payload returned upon successful authentication.
 * Contains the JWT access token, standard bearer token type, validity duration in seconds,
 * and minimal safe user details (no password hashes or sensitive security credentials).
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public LoginResponse(String accessToken, long expiresIn, UserResponse user) {
        this(accessToken, "Bearer", expiresIn, user);
    }
}
