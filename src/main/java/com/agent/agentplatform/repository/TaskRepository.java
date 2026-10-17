package com.agent.agentplatform.repository;

import com.agent.agentplatform.entity.AgentTask;
import com.agent.agentplatform.entity.TaskStatus;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<AgentTask, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"agent", "user"})
    @Query("select t from AgentTask t where (:all = true or (:assigned = true and t.agent.owner.username = :username) or (:assigned = false and t.user.username = :username)) and (:status is null or t.status=:status) and (lower(t.input) like concat('%',:q,'%') or lower(t.agent.name) like concat('%',:q,'%')) order by t.createdAt desc,t.id desc")
    org.springframework.data.domain.Page<AgentTask> search(String username, boolean all, boolean assigned, String q, TaskStatus status, org.springframework.data.domain.Pageable pageable);
    List<AgentTask> findByUserUsernameOrderByCreatedAtDesc(String username);
    List<AgentTask> findAllByOrderByCreatedAtDesc();
    boolean existsByAgentId(Long agentId);
    List<AgentTask> findByAgentOwnerUsernameOrderByCreatedAtDesc(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AgentTask t where t.id = :id")
    Optional<AgentTask> findForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AgentTask> findFirstByStatusOrderByCreatedAtAscIdAsc(TaskStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AgentTask> findByStatusAndUpdatedAtBefore(TaskStatus status, Instant before);
}
