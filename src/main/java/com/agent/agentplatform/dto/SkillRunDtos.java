package com.agent.agentplatform.dto;
import com.agent.agentplatform.entity.SkillRun;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class SkillRunDtos {
    private SkillRunDtos(){}
    public record Request(@NotNull UUID requestId,@NotNull @Size(max=20000) String input){}
    public record View(Long id,String requestId,String skillName,Long skillVersion,String input,String output,String error,String status,Instant createdAt){
        public static View from(SkillRun r){return new View(r.id,r.requestId,r.skillName,r.skillVersion,r.input,r.output,r.error,r.status,r.createdAt);}
    }
}
