package com.workshop.controller;

import com.workshop.dto.AuthResponse;
import com.workshop.dto.ForgotPasswordRequest;
import com.workshop.dto.LoginRequest;
import com.workshop.dto.RegisterRequest;
import com.workshop.dto.ResetPasswordRequest;
import com.workshop.dto.UserResponse;
import com.workshop.security.AppUserPrincipal;
import com.workshop.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register, log in and inspect the current user")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user account (public)",
            description = "Creates a USER account and returns a JWT. Admin accounts cannot be created here.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password (public)",
            description = "Works for both users and admins. Returns a JWT to send as 'Authorization: Bearer <token>'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid email or password")
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link (public)",
            description = "Always returns the same success response whether or not the email belongs to an account.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "If the account exists, a reset link has been emailed"))
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return Map.of("message", "If an account with that email exists, a password reset link has been sent.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a one-time token (public)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-used token, or invalid password")
    })
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Map.of("message", "Password has been reset successfully.");
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the currently authenticated user")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Current user"))
    public UserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal principal) {
        return authService.getCurrentUser(principal.id());
    }
}
