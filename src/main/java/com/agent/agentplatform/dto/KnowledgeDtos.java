package com.agent.agentplatform.dto;
import jakarta.validation.constraints.*;
import com.agent.agentplatform.entity.KnowledgeDocument;
import java.time.Instant;
import java.util.List;

public final class KnowledgeDtos {
    private KnowledgeDtos(){}
    public record Save(@NotBlank @Size(max=100) String title,@NotBlank @Size(max=100000) String content,boolean enabled,Long version){}
    public record View(Long id,Long version,String title,String content,boolean enabled,Instant updatedAt){
        public static View from(KnowledgeDocument d){return new View(d.getId(),d.getVersion(),d.getTitle(),d.getContent(),d.isEnabled(),d.getUpdatedAt());}
    }
    public record Bind(@NotNull @Size(max=10) List<@NotNull Long> documentIds,@NotNull Long version){}
    public record Binding(Long version,List<View> documents){}
    public record Query(@NotBlank @Size(max=100) String query){}
    public record Match(Long documentId,Long version,String title,int chunk,double score,String text){}
}
