package com.ecommerce.auth.service;

import com.ecommerce.common.enums.Role;

public interface JwtService {
    String generateAccessToken(Long userId, String email, String name, Role role);
    String generateRefreshToken(Long userId, String email, String name, Role role);
    String extractEmail(String token);
    Long extractUserId(String token);
    String extractName(String token);
    Role extractRole(String token);
    boolean isValid(String token, String email);
    boolean isRefreshToken(String token);
}
