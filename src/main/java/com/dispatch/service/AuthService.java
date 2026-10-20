package com.dispatch.service;

import com.dispatch.dto.request.LoginRequest;
import com.dispatch.dto.request.RefreshTokenRequest;
import com.dispatch.dto.request.RegisterRequest;
import com.dispatch.dto.response.AuthResponse;
import com.dispatch.dto.response.UserResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    UserResponse getCurrentUser(String email);
}
