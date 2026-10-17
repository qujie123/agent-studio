package com.agent.agentplatform.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record SkillBindingRequest(@NotNull @Size(max = 10) List<@NotNull Long> skillIds, @Min(0) Long version) {
    public SkillBindingRequest(List<Long> skillIds) { this(skillIds, 0L); }
}
