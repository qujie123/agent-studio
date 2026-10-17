package com.agent.agentplatform.dto;
import com.agent.agentplatform.entity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ChatDtos {
    private ChatDtos() {}
    public record Create(@NotNull Long agentId) {}
    public record Rename(@NotBlank @Size(max = 100) String title) {}
    public record Preferences(@NotNull Boolean pinned, @NotNull Boolean archived) {}
    public record Send(@NotNull UUID requestId, @NotBlank @Size(max = 20000) String input, Boolean projectMode) {
        public Send { projectMode = Boolean.TRUE.equals(projectMode); }
        public Send(UUID requestId, String input) { this(requestId, input, false); }
    }
    public record Session(Long id, String title, Long agentId, String agentName, AgentStatus agentStatus, Instant updatedAt, boolean running, boolean pinned, boolean archived) {
        public static Session from(ChatSession s) { return new Session(s.getId(), s.getTitle(), s.getAgent().getId(),
                s.getAgent().getName(), s.getAgent().getStatus(), s.getUpdatedAt(), false, s.isPinned(), s.isArchived()); }
    }
    public record Turn(Long id, String requestId, String input, String output, String error, String status,
                       String model, String mode, String skills, int contextTurns, Instant createdAt, Double temperature, Integer maxOutputTokens, String knowledge, boolean projectMode) {
        public static Turn from(ChatTurn t) { return new Turn(t.getId(), t.getRequestId(), t.getInput(), t.getOutput(),
                t.getError(), t.getStatus(), t.getModel(), t.getMode(), t.getSkills(), t.getContextTurns(), t.getCreatedAt(),t.getTemperature(),t.getMaxOutputTokens(),java.util.Objects.requireNonNullElse(t.getKnowledge(),""),t.isProjectMode()); }
    }
    public record Detail(Session session, List<Turn> turns) {}
}
