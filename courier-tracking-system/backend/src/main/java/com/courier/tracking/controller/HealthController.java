package com.courier.tracking.controller;

import com.courier.tracking.dto.ApiResponse;
import com.courier.tracking.dto.SystemHealthDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller providing system health, environment readiness, and infrastructure diagnostics.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Value("${app.name:Courier Tracking System}")
    private String systemName;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SystemHealthDto>> checkHealth() {
        Map<String, Object> components = new HashMap<>();

        // Test database connectivity
        boolean dbHealthy = false;
        try (Connection conn = dataSource.getConnection()) {
            dbHealthy = conn.isValid(2);
            components.put("database", Map.of(
                    "status", dbHealthy ? "UP" : "DOWN",
                    "product", conn.getMetaData().getDatabaseProductName(),
                    "version", conn.getMetaData().getDatabaseProductVersion()
            ));
        } catch (SQLException ex) {
            components.put("database", Map.of(
                    "status", "DOWN",
                    "error", ex.getMessage()
            ));
        }

        String overallStatus = dbHealthy ? "UP" : "DEGRADED";

        SystemHealthDto health = new SystemHealthDto(
                overallStatus,
                systemName,
                appVersion,
                System.getProperty("spring.profiles.active", "default"),
                Instant.now(),
                components
        );

        return ResponseEntity.ok(ApiResponse.success("System health check completed", health));
    }
}
