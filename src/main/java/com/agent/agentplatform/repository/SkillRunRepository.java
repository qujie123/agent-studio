package com.agent.agentplatform.repository;
import com.agent.agentplatform.entity.SkillRun;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.*;
import java.time.Instant;

public interface SkillRunRepository extends JpaRepository<SkillRun,Long> {
    Optional<SkillRun> findByOwnerUsernameAndRequestId(String username,String requestId);
    Optional<SkillRun> findByIdAndOwnerUsername(Long id,String username);
    Page<SkillRun> findByOwnerUsernameOrderByCreatedAtDescIdDesc(String username,Pageable pageable);
    List<SkillRun> findByStatusAndDeadlineBefore(String status,Instant before);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select r from SkillRun r where r.id=:id") Optional<SkillRun> locked(Long id);
}
