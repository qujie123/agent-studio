package com.agent.agentplatform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String actor;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(nullable = false, length = 40)
    private String resourceType;
    private Long resourceId;
    @Column(nullable = false)
    private Instant createdAt;

    protected AuditEvent() {}
    public AuditEvent(String actor, String action, String resourceType, Long resourceId) {
        this.actor = actor;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public Instant getCreatedAt() { return createdAt; }
}
