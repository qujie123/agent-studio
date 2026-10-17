package com.agent.agentplatform.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "tasks")
public class AgentTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "execution_model", length = 100)
    private String executionModel;

    @Column(name = "execution_prompt", columnDefinition = "TEXT")
    private String executionPrompt;

    @Column(length = 10) private String executionMode;
    private Long providerRevision;
    private Long providerSettingsId;
    public Long getProviderSettingsId() { return providerSettingsId; }
    public void setProviderSettingsId(Long value) { providerSettingsId = value; }
    private Integer executionTimeoutSeconds;
    private Double executionTemperature;
    private Integer executionMaxOutputTokens;
    public Double getExecutionTemperature() { return executionTemperature; }
    public void setExecutionTemperature(Double value) { executionTemperature = value; }
    public Integer getExecutionMaxOutputTokens() { return executionMaxOutputTokens; }
    public void setExecutionMaxOutputTokens(Integer value) { executionMaxOutputTokens = value; }
    private Long inputTokens;
    private Long outputTokens;
    public void setUsage(com.agent.agentplatform.dto.ModelReply reply) { inputTokens = reply.inputTokens(); outputTokens = reply.outputTokens(); }
    @Column(columnDefinition = "TEXT") private String executionSkills;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

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
            status = TaskStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @Column(columnDefinition="TEXT") private String executionKnowledge;
    public String getExecutionKnowledge(){return executionKnowledge;}
    public void setExecutionKnowledge(String value){executionKnowledge=value;}
    @Column(nullable = false) private boolean projectMode;
    public boolean isProjectMode() { return projectMode; }
    public void setProjectMode(boolean value) { projectMode = value; }
    public Long getId() { return id; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String value) { executionMode = value; }
    public Long getProviderRevision() { return providerRevision; }
    public void setProviderRevision(Long value) { providerRevision = value; }
    public Integer getExecutionTimeoutSeconds() { return executionTimeoutSeconds; }
    public void setExecutionTimeoutSeconds(Integer value) { executionTimeoutSeconds = value; }
    public String getExecutionSkills() { return executionSkills; }
    public void setExecutionSkills(String value) { executionSkills = value; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public String getExecutionModel() { return executionModel; }
    public void setExecutionModel(String executionModel) { this.executionModel = executionModel; }
    public String getExecutionPrompt() { return executionPrompt; }
    public void setExecutionPrompt(String executionPrompt) { this.executionPrompt = executionPrompt; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
