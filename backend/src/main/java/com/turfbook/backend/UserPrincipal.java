package com.turfbook.backend;

import java.security.Principal;

/**
 * Lightweight principal representing an authenticated customer in Spring Security context.
 */
public class UserPrincipal implements Principal {

    private final String id;
    private final String email;
    private final String role;

    public UserPrincipal(String id, String email, String role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String getName() {
        return email;
    }
}
