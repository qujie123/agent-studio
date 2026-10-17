package com.agent.agentplatform.dto;
import com.agent.agentplatform.entity.Skill;
import java.time.Instant;
public record SkillResponse(Long id, String name, String description, String instructions, boolean enabled,
                            Long version, Long ownerId, Instant updatedAt,String kind,String code) {
    public static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName(), skill.getDescription(), skill.getInstructions(),
                skill.isEnabled(), skill.getVersion(), skill.getOwner().getId(), skill.getUpdatedAt(),skill.getKind(),skill.getCode());
    }
}
