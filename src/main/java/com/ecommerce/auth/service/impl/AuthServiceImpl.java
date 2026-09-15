package com.ecommerce.auth.service.impl;

import com.ecommerce.user.entity.User;
import com.ecommerce.common.enums.Role;
import com.ecommerce.common.exception.EmailAlreadyExistsException;
import com.ecommerce.common.exception.TokenRefreshException;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.auth.mapper.AuthMapper;
import com.ecommerce.auth.service.JwtService;
import com.ecommerce.auth.dto.response.AuthResponse;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.auth.dto.request.LoginRequest;
import com.ecommerce.auth.dto.request.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuthMapper authMapper;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.getEmail());
        }

        Role role = request.getRole() == Role.ADMIN ? Role.BUYER : request.getRole();

        User user = authMapper.toUser(request, passwordEncoder.encode(request.getPassword()), role);

        userRepository.save(user);
        return authMapper.toAuthResponse(
                user,
                jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getName(), user.getRole()),
                jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getName(), user.getRole()));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        return authMapper.toAuthResponse(
                user,
                jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getName(), user.getRole()),
                jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getName(), user.getRole()));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new TokenRefreshException("User not found for refresh token"));
        if (!jwtService.isValid(refreshToken, email)) {
            throw new TokenRefreshException("Refresh token expired or invalid");
        }
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new TokenRefreshException("Provided token is not a refresh token");
        }
        return authMapper.toAuthResponse(
                user,
                jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getName(), user.getRole()),
                refreshToken);
    }
}
