package com.agent.agentplatform.entity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="workflows")
public class Workflow {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Version public Long version;
    public Long getId(){return id;}
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="owner_id",nullable=false) public User owner;
    @Column(nullable=false,length=100) public String name;
    @Column(nullable=false) public boolean enabled=true;
    @Column(nullable=false,columnDefinition="TEXT") public String definition;
    @Column(nullable=false) public Instant updatedAt;
    @PrePersist @PreUpdate void timestamp(){updatedAt=Instant.now();}
}
