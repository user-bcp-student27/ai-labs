package com.bcp.workshop.auth;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthResourceUnitTest {
    @Test
    void returnsGenericErrorWithoutExposingExceptionDetails() {
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public LoginResponse login(String username, String password) {
                throw new IllegalStateException("fixture database failure");
            }
        };

        Response response = resource.login(loginRequest());
        Map<?, ?> body = (Map<?, ?>) response.getEntity();

        assertEquals(500, response.getStatus());
        assertEquals("Internal server error", body.get("message"));
        assertEquals("INTERNAL_ERROR", body.get("exception"));
    }

    @Test
    void escapesDisplayNameInCurrentLoginResponse() {
        PasswordHasher passwordHasher = new PasswordHasher();
        AuthService service = new AuthService();
        service.passwordHasher = passwordHasher;
        service.tokenService = new TokenService() {
            @Override
            public String create(String username, String role) {
                return "test-token";
            }
        };
        String passwordHash = passwordHasher.hash("fixture-password");
        service.repository = username -> new UserRecord(
                1,
                username,
                "Ada & <Admin>",
                "STUDENT",
                passwordHash);

        LoginResponse response = service.login("fixture-user", "fixture-password");

        assertEquals("Ada &amp; &lt;Admin&gt;", response.user().displayName());
    }

    @Test
    void returnsBadRequestForNullRequest() {
        AuthResource resource = new AuthResource();

        Response response = resource.login(null);

        assertEquals(400, response.getStatus());
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.username = "fixture-user";
        request.password = "fixture-password";
        return request;
    }
}
