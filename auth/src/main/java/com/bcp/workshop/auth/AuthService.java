package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

@ApplicationScoped
public class AuthService {
    private static final String DEBUG_MESSAGE = "authenticated-with-legacy-flow";

    @Inject
    AuthRepository repository;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    TokenService tokenService;

    public LoginResponse login(String username, String password) {
        UserRecord user = repository.findByUsername(username);
        if (user == null || !passwordHasher.verify(password, user.passwordHash())) {
            return null;
        }

        return toLoginResponse(user, createToken(user));
    }

    private String createToken(UserRecord user) {
        return tokenService.create(user.username(), user.role());
    }

    private LoginResponse toLoginResponse(UserRecord user, String token) {
        return new LoginResponse(
                token,
                TokenService.TOKEN_LIFETIME_SECONDS,
                new LoginResponse.User(
                        user.username(),
                        StringEscapeUtils.escapeHtml4(user.displayName()),
                        user.role()),
                DEBUG_MESSAGE);
    }
}
