package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOGGER = Logger.getLogger(AuthResource.class);
    private static final String REQUEST_NULL_MESSAGE = "Request is null";
    private static final String USERNAME_REQUIRED_MESSAGE = "Username is required";
    private static final String PASSWORD_REQUIRED_MESSAGE = "Password is required";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid user %s or password";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";
    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        if (request == null) {
            return badRequest(REQUEST_NULL_MESSAGE);
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return badRequest(USERNAME_REQUIRED_MESSAGE);
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return badRequest(PASSWORD_REQUIRED_MESSAGE);
        }

        try {
            LoginResponse response = authService.login(request.username, request.password);
            if (response == null) {
                return Response.status(401)
                        .entity(Map.of(
                                "message",
                                INVALID_CREDENTIALS_MESSAGE.formatted(request.username)))
                        .build();
            }
            return Response.ok(response).build();
        } catch (IllegalStateException error) {
            return internalServerError(error);
        }
    }

    private Response badRequest(String message) {
        return Response.status(400).entity(Map.of("message", message)).build();
    }

    private Response internalServerError(IllegalStateException error) {
        LOGGER.error("Login failed due to an internal processing error", error);
        return Response.status(500).entity(Map.of(
                "message", INTERNAL_ERROR_MESSAGE,
                "exception", INTERNAL_ERROR_CODE
        )).build();
    }
}
