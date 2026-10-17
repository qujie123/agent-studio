package com.agent.agentplatform.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class WorkflowDtos {
    private WorkflowDtos(){}
    public record Step(@NotBlank String type,Long resourceId,@Size(max=4000) String value){}
    public record Save(@NotBlank @Size(max=100) String name,boolean enabled,Long version,@NotNull @Size(min=1,max=10) List<@Valid Step> steps){}
    public record View(Long id,Long version,String name,boolean enabled,List<Step> steps,Instant updatedAt){}
    public record Request(@NotNull UUID requestId,@NotBlank @Size(max=20000) String input){}
    public record Trace(int index,String type,String status,String output,Long taskId,Long skillRunId){}
    public record Run(Long id,String requestId,Long workflowId,String input,String output,String error,String status,List<Step> definition,List<Trace> steps,Instant createdAt){}
}
