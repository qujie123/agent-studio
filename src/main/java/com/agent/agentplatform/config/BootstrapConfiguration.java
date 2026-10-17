package com.agent.agentplatform.config;

import com.agent.agentplatform.dto.AgentRequest;
import com.agent.agentplatform.entity.AgentStatus;
import com.agent.agentplatform.entity.User;
import com.agent.agentplatform.repository.UserRepository;
import com.agent.agentplatform.repository.AgentRepository;
import com.agent.agentplatform.service.AgentService;
import com.agent.agentplatform.service.AuditService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class BootstrapConfiguration implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AgentRepository agents;
    private final AgentService agentService;
    private final AuditService audit;
    private final Environment environment;
    private final String username;
    private final String password;
    private final String email;

    public BootstrapConfiguration(UserRepository users, PasswordEncoder encoder, AgentRepository agents,
                                  AgentService agentService, AuditService audit, Environment environment,
                                  @Value("${app.bootstrap.username:}") String username,
                                  @Value("${app.bootstrap.password:}") String password,
                                  @Value("${app.bootstrap.email:}") String email) {
        this.users = users;
        this.encoder = encoder;
        this.agents = agents;
        this.agentService = agentService;
        this.audit = audit;
        this.environment = environment;
        this.username = username.strip();
        this.password = password;
        this.email = email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (username.isBlank() || users.existsByRole("ADMIN")) { return; }
        if (username.length() < 3 || username.length() > 50 || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Bootstrap admin requires a valid username/email and a 12-character password (max 72 UTF-8 bytes)");
        }
        if (users.existsByUsername(username) || users.existsByEmail(email)) {
            throw new IllegalStateException("Bootstrap identity is already used; existing users are never promoted automatically");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setRole("ADMIN");
        users.saveAndFlush(user);
        audit.record("system", "ADMIN_BOOTSTRAPPED", "USER", user.getId());
        if (environment.acceptsProfiles(Profiles.of("demo", "local")) && agents.count() == 0) {
            agentService.createAgent(new AgentRequest("写作助手", "整理思路，打磨表达，让每一次写作更从容。",
                    "请帮助用户整理和改进中文内容。", "demo-model", AgentStatus.ACTIVE), username);
            agentService.createAgent(new AgentRequest("代码伙伴", "解释代码、梳理问题、协助构建清晰的解决方案。",
                    "你是一名耐心、严谨的编程助手。", "demo-model", AgentStatus.ACTIVE), username);
            agentService.createAgent(new AgentRequest("知识整理", "把零散信息转化为有条理的摘要与行动清单。",
                    "请提炼关键信息并列出行动建议。", "demo-model", AgentStatus.ACTIVE), username);
        }
    }
}
