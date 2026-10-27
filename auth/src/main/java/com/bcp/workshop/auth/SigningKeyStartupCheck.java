package com.bcp.workshop.auth;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.StartupEvent;

import java.io.IOException;
import java.security.Key;
import java.security.PrivateKey;

@ApplicationScoped
public class SigningKeyStartupCheck {
    @Inject
    @ConfigProperty(name = "smallrye.jwt.sign.key.location")
    String signingKeyLocation;

    void validateAtStartup(@Observes StartupEvent event) {
        validateSigningKey(signingKeyLocation);
    }

    void validateSigningKey(String keyLocation) {
        if (keyLocation == null || keyLocation.isBlank()) {
            throw new IllegalStateException("JWT signing key location is required");
        }

        Key signingKey;
        try {
            signingKey = KeyUtils.readSigningKey(keyLocation, null, SignatureAlgorithm.RS256);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Unable to load the configured RS256 signing key",
                    error);
        }

        if (!(signingKey instanceof PrivateKey)) {
            throw new IllegalStateException("Configured RS256 signing key is not a private key");
        }
    }
}
