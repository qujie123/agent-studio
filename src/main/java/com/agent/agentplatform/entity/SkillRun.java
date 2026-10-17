package com.agent.agentplatform.entity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="skill_runs")
public class SkillRun {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="skill_id") public Skill skill;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="owner_id",nullable=false) public User owner;
    @Column(nullable=false,length=36) public String requestId;
    @Column(nullable=false,length=100) public String skillName;
    @Column(nullable=false) public Long skillVersion;
    @Column(nullable=false,columnDefinition="TEXT") public String code;
    @Column(nullable=false,columnDefinition="TEXT") public String input;
    @Column(columnDefinition="TEXT") public String output;
    @Column(length=500) public String error;
    @Column(nullable=false,length=20) public String status;
    @Column(nullable=false) public Instant createdAt;
    @Column(nullable=false) public Instant deadline;
}
