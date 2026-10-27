package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SigningKeyStartupCheckTest {
    private static final int RSA_KEY_SIZE = 2048;

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsValidRsaPrivateKey() throws Exception {
        Path signingKey = writePrivateKey();
        SigningKeyStartupCheck startupCheck = startupCheck(signingKey.toString());

        assertDoesNotThrow(() -> startupCheck.validateAtStartup(null));
    }

    @Test
    void rejectsMissingSigningKeyLocation() {
        SigningKeyStartupCheck missingLocation = startupCheck(null);
        SigningKeyStartupCheck blankLocation = startupCheck(" ");

        assertThrows(
                IllegalStateException.class,
                () -> missingLocation.validateAtStartup(null));
        assertThrows(
                IllegalStateException.class,
                () -> blankLocation.validateAtStartup(null));
    }

    @Test
    void rejectsUnavailableOrInvalidSigningKey() throws Exception {
        Path missingKey = temporaryDirectory.resolve("missing-key.pem");
        Path invalidKey = temporaryDirectory.resolve("invalid-key.pem");
        Files.writeString(invalidKey, "not a private key");
        SigningKeyStartupCheck missingFile = startupCheck(missingKey.toString());
        SigningKeyStartupCheck invalidFile = startupCheck(invalidKey.toString());

        assertThrows(
                IllegalStateException.class,
                () -> missingFile.validateAtStartup(null));
        assertThrows(
                IllegalStateException.class,
                () -> invalidFile.validateAtStartup(null));
    }

    private SigningKeyStartupCheck startupCheck(String keyLocation) {
        SigningKeyStartupCheck startupCheck = new SigningKeyStartupCheck();
        startupCheck.signingKeyLocation = keyLocation;
        return startupCheck;
    }

    private Path writePrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(RSA_KEY_SIZE);
        byte[] encodedKey = keyPairGenerator.generateKeyPair().getPrivate().getEncoded();
        String encodedContent = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(encodedKey);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + encodedContent
                + "\n-----END PRIVATE KEY-----\n";
        Path signingKey = temporaryDirectory.resolve("signing-key.pem");
        Files.writeString(signingKey, pem);
        return signingKey;
    }
}
