package com.agent.agentplatform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private User owner;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "agent_id") private Agent agent;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false) private Instant updatedAt = Instant.now();
    @Column(nullable = false) private boolean pinned;
    @Column(nullable = false) private boolean archived;
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean value) { pinned = value; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean value) { archived = value; }
    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public void setOwner(User value) { owner = value; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent value) { agent = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { updatedAt = Instant.now(); }
}
