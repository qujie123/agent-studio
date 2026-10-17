package com.agent.agentplatform.entity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "usage_records", indexes = @Index(name = "ix_usage_user_time", columnList = "user_id,recorded_at"))
public class UsageRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 80) private String sourceKey;
    @Column(nullable = false, length = 10) private String kind;
    private Long sourceId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 100) private String model;
    @Column(nullable = false, length = 10) private String mode;
    @Column(nullable = false, length = 20) private String status;
    private Long inputTokens;
    private Long outputTokens;
    @Column(nullable = false) private Instant recordedAt;
    private Instant finishedAt;
    public UsageRecord() {}
    public UsageRecord(String key, String kind, Long sourceId, Long userId, String model, String mode) {
        this.sourceKey = key; this.kind = kind; this.sourceId = sourceId; this.userId = userId;
        this.model = model; this.mode = mode; this.status = "RUNNING"; this.recordedAt = Instant.now();
    }
    public Long getId() { return id; }
    public void finish(String status, com.agent.agentplatform.dto.ModelReply reply) {
        if (!"RUNNING".equals(this.status)) { return; }
        this.status = status; this.inputTokens = reply.inputTokens(); this.outputTokens = reply.outputTokens(); this.finishedAt = Instant.now();
    }
}
