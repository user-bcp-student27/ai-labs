package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;

public class AuthJwtTestResource implements QuarkusTestResourceLifecycleManager {
    private static final int RSA_KEY_SIZE = 2048;
    private static final int PEM_LINE_LENGTH = 64;
    private static final String PRIVATE_KEY_HEADER = "PRIVATE KEY";
    private static final String PUBLIC_KEY_HEADER = "PUBLIC KEY";

    private Path keyDirectory;
    private Path privateKeyPath;
    private Path publicKeyPath;

    @Override
    public Map<String, String> start() {
        try {
            DriverManager.registerDriver(new org.sqlite.JDBC());
            keyDirectory = Path.of("target", "auth-jwt-test-keys").toAbsolutePath();
            Files.createDirectories(keyDirectory);
            privateKeyPath = keyDirectory.resolve("private-test-key.pem");
            publicKeyPath = keyDirectory.resolve("public-test-key.pem");

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();
            Files.writeString(privateKeyPath, toPem(PRIVATE_KEY_HEADER, keyPair.getPrivate().getEncoded()));
            Files.writeString(publicKeyPath, toPem(PUBLIC_KEY_HEADER, keyPair.getPublic().getEncoded()));

            return Map.of(
                    "smallrye.jwt.sign.key.location", privateKeyPath.toString(),
                    "auth.test.public-key.location", publicKeyPath.toString());
        } catch (IOException | NoSuchAlgorithmException | SQLException error) {
            throw new IllegalStateException("Unable to create ephemeral JWT test keys", error);
        }
    }

    @Override
    public void stop() {
        try {
            if (privateKeyPath != null) {
                Files.deleteIfExists(privateKeyPath);
            }
            if (publicKeyPath != null) {
                Files.deleteIfExists(publicKeyPath);
            }
            if (keyDirectory != null) {
                Files.deleteIfExists(keyDirectory);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to remove ephemeral JWT test keys", error);
        }
    }

    private String toPem(String keyType, byte[] encodedKey) {
        String encoded = Base64.getMimeEncoder(PEM_LINE_LENGTH, new byte[] {'\n'})
                .encodeToString(encodedKey);
        return "-----BEGIN " + keyType + "-----\n"
                + encoded
                + "\n-----END " + keyType + "-----\n";
    }
}
