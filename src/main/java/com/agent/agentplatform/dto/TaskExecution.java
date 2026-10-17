package com.agent.agentplatform.dto;

public record TaskExecution(Long taskId, String model, String systemPrompt, String input,
                            String executionMode, Long providerRevision, Long settingsId, Double temperature, Integer maxOutputTokens, String knowledge) {
    public TaskExecution(Long taskId,String model,String systemPrompt,String input,String executionMode,Long providerRevision,Long settingsId,Double temperature,Integer maxOutputTokens){this(taskId,model,systemPrompt,input,executionMode,providerRevision,settingsId,temperature,maxOutputTokens,"");}
    public TaskExecution(Long taskId, String model, String systemPrompt, String input, String executionMode, Long providerRevision) {
        this(taskId, model, systemPrompt, input, executionMode, providerRevision, 1L, null, null);
    }
    public TaskExecution(Long taskId, String model, String systemPrompt, String input, String executionMode, Long providerRevision, Long settingsId) {
        this(taskId, model, systemPrompt, input, executionMode, providerRevision, settingsId, null, null);
    }
}
