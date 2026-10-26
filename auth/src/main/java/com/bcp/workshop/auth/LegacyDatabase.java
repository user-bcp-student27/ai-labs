package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LegacyDatabase implements AuthRepository {
    @ConfigProperty(name = "auth.database.url")
    String databaseUrl;

    @ConfigProperty(name = "auth.seed-demo-users", defaultValue = "false")
    boolean seedDemoUsers;

    @Inject
    PasswordHasher passwordHasher;

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(databaseUrl)) {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    createSchema(connection);
                    if (seedDemoUsers) {
                        seedDefaultUsers(connection);
                    }
                    migrateLegacyPasswords(connection);
                    connection.commit();
                } catch (SQLException | RuntimeException error) {
                    rollback(connection, error);
                    throw error;
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }
            }
        } catch (IOException | SQLException error) {
            throw new IllegalStateException("Unable to initialize authentication database", error);
        }
    }

    @Override
    public UserRecord findByUsername(String username) {
        String sql = "SELECT id, username, display_name, role, password FROM legacy_users WHERE username=?";
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new UserRecord(
                        result.getLong("id"),
                        result.getString("username"),
                        result.getString("display_name"),
                        result.getString("role"),
                        result.getString("password"));
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Authentication database lookup failed", error);
        }
    }

    private void createSchema(Connection connection) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void seedDefaultUsers(Connection connection) throws SQLException {
        seedUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
        seedUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    private void seedUser(
            Connection connection,
            String username,
            String password,
            String displayName,
            String role) throws SQLException {
        if (userExists(connection, username)) {
            return;
        }

        String sql = "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private boolean userExists(Connection connection, String username) throws SQLException {
        String sql = "SELECT 1 FROM legacy_users WHERE username=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void migrateLegacyPasswords(Connection connection) throws SQLException {
        for (StoredPassword storedPassword : readStoredPasswords(connection)) {
            if (!passwordHasher.isHash(storedPassword.value())) {
                updatePassword(
                        connection,
                        storedPassword.id(),
                        passwordHasher.hash(storedPassword.value()));
            }
        }
    }

    private List<StoredPassword> readStoredPasswords(Connection connection) throws SQLException {
        List<StoredPassword> passwords = new ArrayList<>();
        String sql = "SELECT id, password FROM legacy_users";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                passwords.add(new StoredPassword(
                        result.getLong("id"),
                        result.getString("password")));
            }
        }
        return passwords;
    }

    private void updatePassword(Connection connection, long userId, String passwordHash) throws SQLException {
        String sql = "UPDATE legacy_users SET password=? WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    private void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            failure.addSuppressed(rollbackError);
        }
    }

    private record StoredPassword(long id, String value) {}
}
