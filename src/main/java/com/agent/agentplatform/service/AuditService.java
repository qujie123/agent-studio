package com.agent.agentplatform.service;

import com.agent.agentplatform.entity.AuditEvent;
import com.agent.agentplatform.repository.AuditRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AuditService {
    private final AuditRepository repository;
    public AuditService(AuditRepository repository) { this.repository = repository; }
    public void record(String actor, String action, String resourceType, Long id) {
        repository.save(new AuditEvent(actor, action, resourceType, id));
    }
    public List<AuditEvent> recent() { return repository.findTop200ByOrderByCreatedAtDescIdDesc(); }
    public com.agent.agentplatform.dto.PageResult<AuditEvent> page(int page, int size) {
        var request = com.agent.agentplatform.dto.PageResult.request(page,size);
        return com.agent.agentplatform.dto.PageResult.from(repository.findAll(org.springframework.data.domain.PageRequest.of(request.getPageNumber(),request.getPageSize(),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"),org.springframework.data.domain.Sort.Order.desc("id")))), java.util.function.Function.identity());
    }
}
