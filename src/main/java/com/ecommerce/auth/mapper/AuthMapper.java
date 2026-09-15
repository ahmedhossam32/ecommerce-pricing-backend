package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dto.request.RegisterRequest;
import com.ecommerce.auth.dto.response.AuthResponse;
import com.ecommerce.common.enums.Role;
import com.ecommerce.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public User toUser(RegisterRequest request, String encodedPassword, Role resolvedRole) {
        return User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(encodedPassword)
                .role(resolvedRole)
                .build();
    }

    public AuthResponse toAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .profilePictureUrl(user.getProfilePictureUrl())
                .build();
    }
}
