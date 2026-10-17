package com.agent.agentplatform.dto;
import jakarta.validation.constraints.*;
public record SkillRequest(@NotBlank @Size(max = 100) String name,
                           @Size(max = 500) String description,
                           @NotBlank @Size(max = 10000) String instructions,
                           boolean enabled, Long version,@Pattern(regexp="PROMPT|PYTHON") String kind,@Size(max=10000) String code) {
    public SkillRequest(String name,String description,String instructions,boolean enabled,Long version){this(name,description,instructions,enabled,version,null,null);}
}
