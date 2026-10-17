package com.agent.agentplatform.dto;

import com.agent.agentplatform.entity.User;

public class UserResponse {


    private Long id;

    private String username;

    private String email;

    private String role;
    private String displayName;
    private boolean enabled;
    private java.time.Instant createdAt;
    private java.time.Instant lastLoginAt;
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public java.time.Instant getLastLoginAt() { return lastLoginAt; }


    public UserResponse(Long id, String username, String email, String role) {

        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;

    }

    public static UserResponse fromUser(User user) {
        UserResponse response = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
        response.displayName = user.getDisplayName(); response.enabled = user.isEnabled();
        response.createdAt = user.getCreatedAt(); response.lastLoginAt = user.getLastLoginAt();
        return response;
    }


    public Long getId() {
        return id;
    }


    public String getUsername() {
        return username;
    }


    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

}
