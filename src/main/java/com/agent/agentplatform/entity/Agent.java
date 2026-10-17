package com.agent.agentplatform.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "agents")
public class Agent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    public Long getVersion() { return version; }

    @Column(name = "agent_name", nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;
    private Double temperature;
    private Integer maxOutputTokens;
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double value) { temperature = value; }
    public Integer getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(Integer value) { maxOutputTokens = value; }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User owner;

    @ManyToMany
    @JoinTable(name = "agent_skills", joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    @OrderColumn(name = "position")
    private List<Skill> skills = new ArrayList<>();
    @ManyToMany
    @JoinTable(name="agent_knowledge",joinColumns=@JoinColumn(name="agent_id"),inverseJoinColumns=@JoinColumn(name="document_id"))
    @OrderColumn(name="position") private List<KnowledgeDocument> knowledge=new ArrayList<>();
    public List<KnowledgeDocument> getKnowledge(){return knowledge;}

    @Column(name = "create_time", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "update_time", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = AgentStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public List<Skill> getSkills() { return skills; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) { this.status = status; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
