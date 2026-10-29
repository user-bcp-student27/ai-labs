package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public class IsolatedProductionAuthDatabaseTestResource implements QuarkusTestResourceLifecycleManager {
    private Path databaseFile;

    @Override
    public Map<String, String> start() {
        try {
            Class.forName("org.sqlite.JDBC");
            Files.createDirectories(Path.of("target"));
            databaseFile = Files.createTempFile(Path.of("target"), "auth-production-test-", ".db");
            createFixtureDatabase();
            return Map.of("auth.database.url", "jdbc:sqlite:" + databaseFile.toAbsolutePath());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to prepare isolated production authentication database", error);
        }
    }

    @Override
    public void stop() {
        try {
            if (databaseFile != null) {
                Files.deleteIfExists(databaseFile);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Unable to remove isolated production authentication database", error);
        }
    }

    private void createFixtureDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
            insertUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
            insertUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
            insertUser(connection, "legitimate-control", "LegitimatePass123!", "Legitimate Control", "STUDENT");
        }
    }

    private void insertUser(
            Connection connection,
            String username,
            String password,
            String displayName,
            String role) throws SQLException {
        String sql = "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, password);
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }
}