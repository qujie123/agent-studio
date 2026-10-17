package com.agent.agentplatform.repository;
import com.agent.agentplatform.entity.ChatSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    @EntityGraph(attributePaths = "agent")
    @Query("select c from ChatSession c where c.owner.username = :username and (:view='all' or (:view='archived' and c.archived=true) or (:view='active' and c.archived=false) or (:view='pinned' and c.archived=false and c.pinned=true)) and (lower(c.title) like concat('%',:q,'%') or lower(c.agent.name) like concat('%',:q,'%')) order by c.pinned desc,c.updatedAt desc,c.id desc")
    org.springframework.data.domain.Page<ChatSession> search(String username, String q, String view, org.springframework.data.domain.Pageable pageable);
    List<ChatSession> findByOwnerUsernameOrderByUpdatedAtDescIdDesc(String username);
    boolean existsByAgentId(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatSession c where c.id = :id")
    Optional<ChatSession> findForUpdate(Long id);
}
