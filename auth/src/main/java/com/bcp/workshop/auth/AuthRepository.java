package com.bcp.workshop.auth;

public interface AuthRepository {
    UserRecord findByUsername(String username);
}
