package com.courier.tracking.controller;

import com.courier.tracking.dto.ApiResponse;
import com.courier.tracking.dto.CustomerRegistrationRequest;
import com.courier.tracking.dto.UserResponse;
import com.courier.tracking.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller handling customer registration and authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Public endpoint for customer onboarding.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody CustomerRegistrationRequest request) {
        UserResponse response = authService.registerCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer account registered successfully", response));
    }
}
