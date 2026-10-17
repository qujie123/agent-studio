package com.agent.agentplatform.entity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="workflow_runs")
public class WorkflowRun {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="workflow_id",nullable=false) public Workflow workflow;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="owner_id",nullable=false) public User owner;
    @Column(nullable=false,length=36) public String requestId;
    @Column(nullable=false,columnDefinition="TEXT") public String input;
    @Column(columnDefinition="TEXT") public String output;
    @Column(length=500) public String error;
    @Column(nullable=false,length=20) public String status;
    @Column(nullable=false,columnDefinition="TEXT") public String definition;
    @Column(nullable=false,columnDefinition="TEXT") public String steps;
    @Column(nullable=false) public Instant createdAt;
    @Column(nullable=false) public Instant deadline;
}
