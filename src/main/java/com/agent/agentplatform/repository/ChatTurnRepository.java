package com.agent.agentplatform.repository;
import com.agent.agentplatform.entity.ChatTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {
    boolean existsBySessionIdAndStatus(Long id, String status);
    @org.springframework.data.jpa.repository.Query("select t from ChatTurn t where t.session.id = :id and (:beforeId is null or t.id <= :beforeId) order by t.id desc")
    org.springframework.data.domain.Page<ChatTurn> page(Long id, Long beforeId, org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select distinct t.session.id from ChatTurn t where t.status = 'RUNNING' and t.deadline < :before order by t.session.id")
    List<Long> findExpiredSessions(java.time.Instant before, org.springframework.data.domain.Pageable pageable);
    List<ChatTurn> findBySessionIdOrderByIdAsc(Long id);
    List<ChatTurn> findBySessionIdAndStatus(Long id, String status);
    List<ChatTurn> findTop20BySessionIdAndStatusOrderByIdDesc(Long id, String status);
    Optional<ChatTurn> findBySessionIdAndRequestId(Long id, String requestId);
    void deleteBySessionId(Long id);
}
