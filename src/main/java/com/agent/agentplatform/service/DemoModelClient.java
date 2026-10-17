package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.TaskExecution;
import org.springframework.stereotype.Component;

@Component
public class DemoModelClient {
    public void requireConfigured() {}
    public String complete(TaskExecution task) {
        if (task.systemPrompt().contains(ProjectDelivery.MARKER)) { return ProjectDelivery.demo(); }
        return "【演示响应 · 未调用真实大模型】\n\n任务已完成排队、执行和结果保存。\n"
                + "模型配置：" + task.model() + "\n\n你的输入：\n" + task.input()
                + "\n\n如需真实结果，请管理员在「模型服务」中保存 API Key 并切换为真实 API 调用。";
    }
}
