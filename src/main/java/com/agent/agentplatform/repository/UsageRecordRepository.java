package com.agent.agentplatform.repository;
import com.agent.agentplatform.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {
    Optional<UsageRecord> findBySourceKey(String key);
    void deleteByUserId(Long id);
    java.util.List<UsageRecord> findTop100ByKindAndStatusAndRecordedAtBeforeOrderByIdAsc(String kind, String status, java.time.Instant before);
}
