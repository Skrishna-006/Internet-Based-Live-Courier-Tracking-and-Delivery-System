package com.courier.tracking.security;

import com.courier.tracking.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only REST controller exposing a protected endpoint to verify JWT request filter integration.
 * Resides exclusively in src/test/java and is never packaged into production artifacts.
 */
@RestController
@RequestMapping("/api/v1/test/protected")
public class TestProtectedController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProtectedData(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthenticated"));
        }

        Map<String, Object> data = Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );

        return ResponseEntity.ok(ApiResponse.success("Access granted", data));
    }
}
