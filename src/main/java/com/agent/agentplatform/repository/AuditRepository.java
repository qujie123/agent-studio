package com.agent.agentplatform.repository;

import com.agent.agentplatform.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop200ByOrderByCreatedAtDescIdDesc();
}
