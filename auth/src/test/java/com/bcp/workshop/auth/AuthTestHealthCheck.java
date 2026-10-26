package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class AuthTestHealthCheck implements HealthCheck {
    private static final String CHECK_NAME = "auth-test-health";

    private static volatile HealthStatus healthStatus = HealthStatus.UP;

    static void setHealthStatus(HealthStatus status) {
        healthStatus = status;
    }

    @Override
    public HealthCheckResponse call() {
        return healthStatus == HealthStatus.UP
                ? HealthCheckResponse.up(CHECK_NAME)
                : HealthCheckResponse.down(CHECK_NAME);
    }

    enum HealthStatus {
        UP,
        DOWN
    }
}
