package com.dispatch.service.impl;

import com.dispatch.dto.request.LoginRequest;
import com.dispatch.dto.request.RefreshTokenRequest;
import com.dispatch.dto.request.RegisterRequest;
import com.dispatch.dto.response.AuthResponse;
import com.dispatch.dto.response.UserResponse;
import com.dispatch.entity.User;
import com.dispatch.entity.enums.UserRole;
import com.dispatch.exception.DuplicateResourceException;
import com.dispatch.exception.ResourceNotFoundException;
import com.dispatch.mapper.UserMapper;
import com.dispatch.repository.UserRepository;
import com.dispatch.security.CustomUserDetails;
import com.dispatch.security.JwtService;
import com.dispatch.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User with email '" + email + "' already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .role(UserRole.USER)
                .build();

        user = userRepository.save(user);
        log.info("Registered new user with id '{}' and email '{}'", user.getId(), user.getEmail());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getExpirationMs())
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        log.info("User '{}' logged in successfully", email);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getExpirationMs())
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        String email = jwtService.extractUsername(refreshToken);

        if (email == null) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("User associated with refresh token not found"));

        CustomUserDetails userDetails = new CustomUserDetails(user);

        if (!jwtService.isRefreshTokenValid(refreshToken, userDetails)) {
            throw new BadCredentialsException("Refresh token is expired or invalid");
        }

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        log.debug("Refreshed JWT tokens for user '{}'", email);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtService.getExpirationMs())
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> ResourceNotFoundException.user(email));
        return userMapper.toResponse(user);
    }
}
