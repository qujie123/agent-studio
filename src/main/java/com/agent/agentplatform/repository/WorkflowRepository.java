package com.agent.agentplatform.repository;
import com.agent.agentplatform.entity.Workflow;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface WorkflowRepository extends JpaRepository<Workflow,Long> {
    @Query("select w from Workflow w where w.owner.username=:username and lower(w.name) like concat('%',:q,'%') order by w.updatedAt desc,w.id desc") Page<Workflow> search(String username,String q,Pageable pageable);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select w from Workflow w where w.id=:id and w.owner.username=:username") Optional<Workflow> owned(Long id,String username);
    long countByOwnerUsername(String username);
}
