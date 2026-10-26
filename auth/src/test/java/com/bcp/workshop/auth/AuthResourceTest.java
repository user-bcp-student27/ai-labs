package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(AuthJwtTestResource.class)
class AuthResourceTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Inject
    LegacyDatabase database;

    @ConfigProperty(name = "auth.test.public-key.location")
    String testPublicKeyLocation;

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void logsInWithWorkshopTrainer() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"trainer\",\"password\":\"Trainer123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("trainer"))
                .body("user.role", is("TRAINER"))
                .body("token", notNullValue());
    }

    @Test
    void logsInWithoutOptionalClientField() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void issuesSignedTokenWithIdentityRoleAndExpiration() throws Exception {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");

        String[] segments = token.split("\\.", -1);
        assertEquals(3, segments.length);

        JsonNode header = decodeJsonSegment(segments[0]);
        JsonNode claims = decodeJsonSegment(segments[1]);
        assertEquals("RS256", header.path("alg").asText());
        assertEquals("student", claims.path("sub").asText());
        assertEquals("STUDENT", claims.path("role").asText());
        assertTrue(claims.path("exp").asLong() > Instant.now().getEpochSecond());
        assertTrue(hasValidSignature(segments));
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"invalid\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void rejectsMissingRequiredFields() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400);

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsBlankRequiredFields() {
        given()
                .contentType("application/json")
                .body("{\"username\":\" \",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400);

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\" \",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsMalformedJson() {
        given()
                .contentType("application/json")
                .body("{")
                .when().post("/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsUnsupportedContentType() {
        given()
                .contentType("text/plain")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(415);
    }

    @Test
    void migratesLegacyPasswordIdempotentlyAndPreservesLogin() throws SQLException {
        database.initialize();
        String databaseUrl = System.getenv().getOrDefault(
                "AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE legacy_users SET password=? WHERE username=?")) {
            statement.setString(1, "Workshop123!");
            statement.setString(2, "student");
            statement.executeUpdate();
        }

        database.initialize();
        String migratedPassword = readStoredPassword(databaseUrl, "student");

        assertTrue(migratedPassword.startsWith("pbkdf2-sha256$"));

        database.initialize();
        assertEquals(migratedPassword, readStoredPassword(databaseUrl, "student"));

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200);
    }

    @Test
    void rejectsCredentialsContainingAQuote() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student'\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void allowsCurrentCorsConfigurationForLocalAndUnlistedOrigins() {
        assertOriginAllowed("http://localhost:4200");
        assertOriginAllowed("https://unlisted.example");
    }

    private void assertOriginAllowed(String origin) {
        given()
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .header("Access-Control-Allow-Origin", is(origin));
    }

    private String readStoredPassword(String databaseUrl, String username) throws SQLException {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("Test user is missing from the isolated database");
                }
                return result.getString("password");
            }
        }
    }

    private JsonNode decodeJsonSegment(String segment) throws Exception {
        byte[] json = Base64.getUrlDecoder().decode(segment);
        return JSON_MAPPER.readTree(json);
    }

    private boolean hasValidSignature(String[] segments) throws Exception {
        String publicKeyPem = Files.readString(Path.of(testPublicKeyLocation));
        String encodedPublicKey = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] publicKeyBytes = Base64.getDecoder().decode(encodedPublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        var publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(segments[2]));
    }
}
