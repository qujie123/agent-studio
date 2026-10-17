package com.agent.agentplatform.repository;
import com.agent.agentplatform.entity.ModelSettings;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ModelSettingsRepository extends JpaRepository<ModelSettings, Long> {
    void deleteByOwnerId(Long id);
}
