package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.ModelReply;
import com.agent.agentplatform.entity.UsageRecord;
import com.agent.agentplatform.repository.UsageRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class UsageLedgerService {
    private final UsageRecordRepository records;
    private final UserService users;
    public UsageLedgerService(UsageRecordRepository records, UserService users) { this.records = records; this.users = users; }
    public Long start(String kind, Long sourceId, String username, String model, String mode) {
        String key = kind + ":" + (sourceId == null ? UUID.randomUUID() : sourceId);
        return records.findBySourceKey(key).orElseGet(() -> records.saveAndFlush(
                new UsageRecord(key, kind, sourceId, users.findByUsername(username).getId(), model, mode))).getId();
    }
    public void finish(String kind, Long sourceId, String status, ModelReply reply) {
        records.findBySourceKey(kind + ":" + sourceId).ifPresent(record -> record.finish(status, reply));
    }
    public void finishTest(Long id, String status, ModelReply reply) {
        records.findById(id).ifPresent(record -> record.finish(status, reply));
    }
    public void recoverInterruptedTests() {
        for (var record : records.findTop100ByKindAndStatusAndRecordedAtBeforeOrderByIdAsc("TEST", "RUNNING", java.time.Instant.now().minusSeconds(150))) {
            record.finish("FAILED", ModelReply.text(null));
        }
    }
}
