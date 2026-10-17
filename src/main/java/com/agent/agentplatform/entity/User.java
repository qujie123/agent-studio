package com.agent.agentplatform.entity;


import jakarta.persistence.*;


@Entity
@Table(name="users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false)
    private String username;


    @Column(nullable = false)
    private String email;


    @Column(nullable = false)
    private String password;


    @Column(nullable = false)
    private String role;

    @Column(length = 80) private String displayName;
    @Column(nullable = false) private boolean enabled = true;
    private java.time.Instant createdAt;
    private java.time.Instant lastLoginAt;
    @Column(nullable = false) private long tokenVersion;

    public String getDisplayName() { return displayName == null ? username : displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public java.time.Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(java.time.Instant value) { lastLoginAt = value; }
    public long getTokenVersion() { return tokenVersion; }
    public void revokeTokens() { tokenVersion++; }

    @PrePersist
    void initializeRole() {
        createdAt = java.time.Instant.now();
        if (role == null || role.isBlank()) {
            role = "USER";
        }
    }



    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }



    public String getUsername() {
        return username;
    }


    public void setUsername(String username) {
        this.username=username;
    }



    public String getEmail() {
        return email;
    }


    public void setEmail(String email){
        this.email=email;
    }



    public String getPassword(){
        return password;
    }


    public void setPassword(String password){
        this.password=password;
    }



    public String getRole(){
        return role;
    }


    public void setRole(String role){
        this.role=role;
    }

}
