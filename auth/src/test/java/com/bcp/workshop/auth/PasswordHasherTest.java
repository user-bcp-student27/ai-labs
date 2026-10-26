package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesWithUniqueSaltAndVerifiesMatchingPassword() {
        String firstHash = passwordHasher.hash("test-password");
        String secondHash = passwordHasher.hash("test-password");

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.isHash(firstHash));
        assertTrue(passwordHasher.verify("test-password", firstHash));
        assertFalse(passwordHasher.verify("wrong-password", firstHash));
    }

    @Test
    void rejectsMalformedHashes() {
        assertFalse(passwordHasher.isHash(null));
        assertFalse(passwordHasher.isHash("not-a-hash"));
        assertFalse(passwordHasher.isHash("pbkdf2-sha256$invalid$bad$bad"));
        assertFalse(passwordHasher.isHash("pbkdf2-sha256$1$YWFh$YWFh"));
        assertFalse(passwordHasher.isHash("pbkdf2-sha256$600000$%%%$%%%"));
        assertFalse(passwordHasher.verify("test-password", "pbkdf2-sha256$600000$YWFh$YWFh"));
    }

    @Test
    void rejectsNullPasswordWhenHashing() {
        assertThrows(IllegalArgumentException.class, () -> passwordHasher.hash(null));
    }
}
