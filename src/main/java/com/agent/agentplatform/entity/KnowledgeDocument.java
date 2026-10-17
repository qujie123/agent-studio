package com.agent.agentplatform.entity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="knowledge_documents")
public class KnowledgeDocument {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="owner_id",nullable=false) private User owner;
    @Column(nullable=false,length=100) private String title;
    @Column(nullable=false,columnDefinition="TEXT") private String content;
    @Column(nullable=false) private boolean enabled=true;
    @Column(nullable=false) private Instant updatedAt;
    @PrePersist @PreUpdate void timestamp(){updatedAt=Instant.now();}
    public Long getId(){return id;}
    public Long getVersion(){return version;}
    public User getOwner(){return owner;}
    public void setOwner(User value){owner=value;}
    public String getTitle(){return title;}
    public void setTitle(String value){title=value;}
    public String getContent(){return content;}
    public void setContent(String value){content=value;}
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean value){enabled=value;}
    public Instant getUpdatedAt(){return updatedAt;}
}
