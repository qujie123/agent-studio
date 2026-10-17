package com.agent.agentplatform.repository;

import com.agent.agentplatform.entity.Agent;
import com.agent.agentplatform.entity.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "owner")
    @org.springframework.data.jpa.repository.Query("select a from Agent a where (:all = true or a.owner.username = :owner or (:shared = true and a.status = com.agent.agentplatform.entity.AgentStatus.ACTIVE)) and (:status is null or a.status = :status) and (lower(a.name) like concat('%',:q,'%') or lower(coalesce(a.description,'')) like concat('%',:q,'%')) order by a.createdAt desc,a.id desc")
    org.springframework.data.domain.Page<Agent> search(String owner, boolean all, boolean shared, AgentStatus status, String q, org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Agent a where a.id = :id")
    java.util.Optional<Agent> findForUpdate(Long id);
    List<Agent> findByStatusOrderByCreatedAtDesc(AgentStatus status);
    List<Agent> findByOwnerUsernameOrderByCreatedAtDesc(String username);
    List<Agent> findAllByOrderByCreatedAtDesc();
    boolean existsBySkillsId(Long skillId);
}
