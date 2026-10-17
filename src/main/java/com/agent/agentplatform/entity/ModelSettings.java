package com.agent.agentplatform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "model_settings")
public class ModelSettings {
    @Id private Long id = 1L;
    private Long ownerId;
    @Version private Long version;
    @Column(nullable = false, length = 10) private String mode;
    @Column(nullable = false, length = 20) private String protocol = "OPENAI";
    @Column(nullable = false, length = 1000) private String baseUrl;
    @Column(columnDefinition = "TEXT") private String encryptedKey;
    @Column(nullable = false, length = 100) private String defaultModel;
    @Column(nullable = false) private int timeoutSeconds;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { ownerId = value; }
    public long getRevision() { return version == null ? 0 : version + 1; }
    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String value) { protocol = value; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public String getEncryptedKey() { return encryptedKey; }
    public void setEncryptedKey(String value) { encryptedKey = value; }
    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String value) { defaultModel = value; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
}
