package com.agent.agentplatform.repository;
import com.agent.agentplatform.entity.WorkflowRun;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun,Long> {
    Optional<WorkflowRun> findByWorkflowIdAndRequestId(Long workflowId,String requestId);
    Optional<WorkflowRun> findByIdAndOwnerUsername(Long id,String username);
    Page<WorkflowRun> findByWorkflowIdAndOwnerUsernameOrderByIdDesc(Long id,String username,Pageable pageable);
    boolean existsByWorkflowId(Long id);
    boolean existsByWorkflowIdAndStatus(Long id,String status);
    List<WorkflowRun> findByStatusAndDeadlineBefore(String status,Instant before);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select r from WorkflowRun r where r.id=:id") Optional<WorkflowRun> locked(Long id);
}
