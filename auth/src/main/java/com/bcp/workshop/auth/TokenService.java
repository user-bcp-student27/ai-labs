package com.bcp.workshop.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class TokenService {
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);
    private static final String SUBJECT_CLAIM = "sub";
    private static final String ROLE_CLAIM = "role";
    private static final String EXPIRATION_CLAIM = "exp";

    public static final int TOKEN_LIFETIME_SECONDS = Math.toIntExact(TOKEN_LIFETIME.getSeconds());

    public String create(String username, String role) {
        long expiration = Instant.now().plus(TOKEN_LIFETIME).getEpochSecond();
        return Jwt.claims()
                .claim(SUBJECT_CLAIM, username)
                .claim(ROLE_CLAIM, role)
                .claim(EXPIRATION_CLAIM, expiration)
                .sign();
    }
}
