package com.payroll.desktop.ui.auth;

import com.payroll.core.entity.UserRole;

public final class UserSession {

    private final String username;
    private final UserRole role;

    public UserSession(String username, UserRole role) {
        this.username = username;
        this.role = role;
    }

    public String getUsername() { return username; }
    public UserRole getRole() { return role; }
}
