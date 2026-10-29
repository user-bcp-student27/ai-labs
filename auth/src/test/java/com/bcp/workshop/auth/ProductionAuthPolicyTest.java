package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestProfile(ProductionAuthPolicyTest.ProductionProfile.class)
@QuarkusTestResource(value = AuthJwtTestResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = IsolatedProductionAuthDatabaseTestResource.class, restrictToAnnotatedClass = true)
class ProductionAuthPolicyTest {
    @Test
    void rejectsPersistedDemoAccountsAndPreservesLegitimateControl() {
        assertLoginRejected("student", "Workshop123!");
        assertLoginRejected("trainer", "Trainer123!");
        assertLoginSucceeds("legitimate-control", "LegitimatePass123!");
    }

    @Test
    void allowsOnlyTheApprovedProductionCorsOrigin() {
        assertOriginAllowed("https://www.viabcp.com");
        assertOriginRejected("https://unlisted.example");
    }

    private void assertLoginRejected(String username, String password) {
        given()
                .contentType("application/json")
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }

    private void assertLoginSucceeds(String username, String password) {
        given()
                .contentType("application/json")
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is(username));
    }

    private void assertOriginAllowed(String origin) {
        given()
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .header("Access-Control-Allow-Origin", is(origin));
    }

    private void assertOriginRejected(String origin) {
        given()
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .header("Access-Control-Allow-Origin", org.hamcrest.Matchers.nullValue());
    }

    public static class ProductionProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "prod";
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("auth.seed-demo-users", "false");
        }
    }
}