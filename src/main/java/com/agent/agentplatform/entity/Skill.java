package com.agent.agentplatform.entity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "skills")
public class Skill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 500) private String description;
    @Column(nullable = false, columnDefinition = "TEXT") private String instructions;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable=false,length=20) private String kind="PROMPT";
    @Column(columnDefinition="TEXT") private String code;
    public String getKind(){return kind;}
    public void setKind(String value){kind=value;}
    public String getCode(){return code;}
    public void setCode(String value){code=value;}
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false) private User owner;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist @PreUpdate void timestamp() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String value) { instructions = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public User getOwner() { return owner; }
    public void setOwner(User value) { owner = value; }
    public Instant getUpdatedAt() { return updatedAt; }
}
