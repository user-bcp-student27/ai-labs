package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoDemoSeedingTest {
    @Test
    void doesNotSeedDemoAccountsWhenSeedingIsDisabled() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path databaseFile = Files.createTempFile(
                Path.of("target"), "auth-no-demo-seed-", ".db");
        try {
            LegacyDatabase database = new LegacyDatabase();
            database.databaseUrl = "jdbc:sqlite:" + databaseFile;
            database.seedDemoUsers = false;
            database.passwordHasher = new PasswordHasher();
            database.initialize();

            try (Connection connection = DriverManager.getConnection(database.databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM legacy_users WHERE username IN (?,?)")) {
                statement.setString(1, "student");
                statement.setString(2, "trainer");
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    assertEquals(0, result.getInt(1));
                }
            }
        } finally {
            Files.deleteIfExists(databaseFile);
        }
    }
}
