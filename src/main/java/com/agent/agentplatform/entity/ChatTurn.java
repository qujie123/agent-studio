package com.agent.agentplatform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_turns", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "request_id"}))
public class ChatTurn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id") private ChatSession session;
    @Column(nullable = false, length = 36) private String requestId;
    @Column(nullable = false, columnDefinition = "TEXT") private String input;
    @Column(columnDefinition = "TEXT") private String output;
    @Column(length = 500) private String error;
    @Column(nullable = false, length = 20) private String status = "RUNNING";
    @Column(nullable = false, length = 100) private String model;
    @Column(nullable = false, length = 10) private String mode;
    @Column(nullable = false, columnDefinition = "TEXT") private String skills;
    @Column(nullable = false) private int contextTurns;
    private Long inputTokens;
    private Long outputTokens;
    private Double temperature;
    private Integer maxOutputTokens;
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double value) { temperature = value; }
    public Integer getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(Integer value) { maxOutputTokens = value; }
    public void setUsage(com.agent.agentplatform.dto.ModelReply reply) { inputTokens = reply.inputTokens(); outputTokens = reply.outputTokens(); }
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant deadline;
    @Column(columnDefinition="TEXT") private String knowledge;
    public String getKnowledge(){return knowledge;}
    public void setKnowledge(String value){knowledge=value;}
    @Column(nullable = false) private boolean projectMode;
    public boolean isProjectMode() { return projectMode; }
    public void setProjectMode(boolean value) { projectMode = value; }
    public Long getId() { return id; }
    public ChatSession getSession() { return session; }
    public void setSession(ChatSession value) { session = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public String getInput() { return input; }
    public void setInput(String value) { input = value; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public String getStatus() { return status; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }
    public String getSkills() { return skills; }
    public void setSkills(String value) { skills = value; }
    public int getContextTurns() { return contextTurns; }
    public void setContextTurns(int value) { contextTurns = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant value) { deadline = value; }
    public void finish(String answer, String failure) {
        output = answer; error = failure; status = failure == null ? "SUCCEEDED" : "FAILED";
    }
}
