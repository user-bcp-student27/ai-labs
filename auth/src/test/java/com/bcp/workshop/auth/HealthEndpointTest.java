package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(AuthJwtTestResource.class)
class HealthEndpointTest {
    @AfterEach
    void restoreHealthyStatus() {
        AuthTestHealthCheck.setHealthStatus(AuthTestHealthCheck.HealthStatus.UP);
    }

    @Test
    void returnsUpWithoutAuthentication() {
        given()
                .accept("application/json")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks[0].name", is("auth-test-health"))
                .body("checks[0].status", is("UP"));
    }

    @Test
    void returnsServiceUnavailableWhenAHealthCheckIsDown() {
        AuthTestHealthCheck.setHealthStatus(AuthTestHealthCheck.HealthStatus.DOWN);

        given()
                .when().get("/q/health")
                .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body("checks[0].name", is("auth-test-health"))
                .body("checks[0].status", is("DOWN"));
    }

    @Test
    void supportsWildcardAcceptHeaderWithoutAuthentication() {
        given()
                .accept("*/*")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}
