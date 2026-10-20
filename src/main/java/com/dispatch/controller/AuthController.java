package com.dispatch.controller;

import com.dispatch.dto.request.LoginRequest;
import com.dispatch.dto.request.RefreshTokenRequest;
import com.dispatch.dto.request.RegisterRequest;
import com.dispatch.dto.response.ApiError;
import com.dispatch.dto.response.AuthResponse;
import com.dispatch.dto.response.UserResponse;
import com.dispatch.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, authentication and JWT token lifecycle")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account with hashed password and generates initial JWT tokens.",
        responses = {
            @ApiResponse(responseCode = "201", description = "User successfully registered"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
        }
    )
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(
        summary = "Authenticate user",
        description = "Validates user email and password, returning JWT access and refresh tokens.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
        }
    )
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh access token",
        description = "Issues a new access and refresh token pair using a valid refresh token.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
        }
    )
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Get current user profile",
        description = "Returns profile details of the currently authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Current user profile"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
        }
    )
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponse response = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
