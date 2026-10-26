package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT = "pbkdf2-sha256";
    private static final String FIELD_SEPARATOR = "$";
    private static final int FIELD_COUNT = 4;
    private static final int FORMAT_FIELD = 0;
    private static final int ITERATIONS_FIELD = 1;
    private static final int SALT_FIELD = 2;
    private static final int HASH_FIELD = 3;
    private static final int ITERATION_COUNT = 600_000;
    private static final int MINIMUM_ITERATIONS = 100_000;
    private static final int MAXIMUM_ITERATIONS = 1_000_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int HASH_BYTES = HASH_BITS / Byte.SIZE;
    private static final String FIELD_SEPARATOR_REGEX = "\\$";

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }

        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derivedHash = derive(password, salt, ITERATION_COUNT);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return String.join(
                FIELD_SEPARATOR,
                FORMAT,
                Integer.toString(ITERATION_COUNT),
                encoder.encodeToString(salt),
                encoder.encodeToString(derivedHash));
    }

    public boolean verify(String password, String encodedHash) {
        if (password == null) {
            return false;
        }

        EncodedHash parsedHash = parse(encodedHash);
        if (parsedHash == null) {
            return false;
        }

        byte[] derivedHash = derive(password, parsedHash.salt(), parsedHash.iterations());
        return MessageDigest.isEqual(parsedHash.hash(), derivedHash);
    }

    public boolean isHash(String value) {
        return parse(value) != null;
    }

    private EncodedHash parse(String encodedHash) {
        if (encodedHash == null) {
            return null;
        }

        String[] fields = encodedHash.split(FIELD_SEPARATOR_REGEX, -1);
        if (fields.length != FIELD_COUNT || !FORMAT.equals(fields[FORMAT_FIELD])) {
            return null;
        }

        try {
            int iterations = Integer.parseInt(fields[ITERATIONS_FIELD]);
            byte[] salt = Base64.getUrlDecoder().decode(fields[SALT_FIELD]);
            byte[] hash = Base64.getUrlDecoder().decode(fields[HASH_FIELD]);
            if (!isSupportedIterationCount(iterations)
                    || salt.length != SALT_BYTES
                    || hash.length != HASH_BYTES) {
                return null;
            }
            return new EncodedHash(iterations, salt, hash);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private boolean isSupportedIterationCount(int iterations) {
        return iterations >= MINIMUM_ITERATIONS && iterations <= MAXIMUM_ITERATIONS;
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        char[] passwordCharacters = password.toCharArray();
        PBEKeySpec keySpec = new PBEKeySpec(passwordCharacters, salt, iterations, HASH_BITS);
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);
            return keyFactory.generateSecret(keySpec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException error) {
            throw new IllegalStateException("Password hashing algorithm is unavailable", error);
        } finally {
            keySpec.clearPassword();
            Arrays.fill(passwordCharacters, '\0');
        }
    }

    private record EncodedHash(int iterations, byte[] salt, byte[] hash) {}
}
