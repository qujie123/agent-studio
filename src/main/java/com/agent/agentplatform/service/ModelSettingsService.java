package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.entity.ModelSettings;
import com.agent.agentplatform.repository.ModelSettingsRepository;
import com.agent.agentplatform.repository.UserRepository;
import com.agent.agentplatform.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ModelSettingsService {
    public record Connection(String mode, String baseUrl, String apiKey, String defaultModel,
                             int timeoutSeconds, long revision, Long settingsId, String protocol) {
        public Connection(String mode, String baseUrl, String apiKey, String defaultModel, int timeoutSeconds, long revision, Long settingsId) {
            this(mode, baseUrl, apiKey, defaultModel, timeoutSeconds, revision, settingsId, "OPENAI");
        }
        public Connection(String mode, String baseUrl, String apiKey, String defaultModel, int timeoutSeconds, long revision) {
            this(mode, baseUrl, apiKey, defaultModel, timeoutSeconds, revision, 1L);
        }
        @Override public String toString() { return "ModelConnection[credentials redacted]"; }
    }
    private final ModelSettingsRepository repository;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final Connection defaults;
    private final UserRepository users;
    public ModelSettingsService(ModelSettingsRepository repository, SecretCipher cipher, AuditService audit,
                                Environment environment,
                                @Value("${app.llm.base-url:}") String baseUrl,
                                @Value("${app.llm.api-key:}") String apiKey,
                                @Value("${app.llm.default-model:}") String defaultModel,
                                @Value("${app.llm.protocol:OPENAI}") String protocol,
                                @Value("${app.llm.timeout-seconds:60}") int timeout, UserRepository users) {
        this.repository = repository; this.cipher = cipher; this.audit = audit;
        this.users = users;
        boolean demo = environment.acceptsProfiles(Profiles.of("demo", "local"));
        if (timeout < 1 || timeout > 120) { throw new IllegalArgumentException("Invalid model timeout"); }
        defaults = new Connection(demo ? "DEMO" : "API", normalize(baseUrl), apiKey.strip(),
                defaultModel.isBlank() && demo ? "demo-model" : defaultModel.strip(), timeout, 0, 1L, protocol);
    }
    public Connection connection() {
        return connectionForSettings(1L);
    }
    public Connection connectionForSettings(Long id) {
        if (id == null || id == 1L) { return repository.findById(1L).map(this::decode).orElse(defaults); }
        var row = repository.findById(id).orElseThrow(() -> new ModelUnavailableException("Personal model configuration is no longer available"));
        if (row.getOwnerId() == null || !users.findById(row.getOwnerId()).map(u -> u.isEnabled()).orElse(false)) {
            throw new ModelUnavailableException("Personal model owner is unavailable");
        }
        return decode(row);
    }
    private Connection decode(ModelSettings row) {
        return new Connection(row.getMode(), row.getBaseUrl(), cipher.decrypt(row.getEncryptedKey()),
                row.getDefaultModel(), row.getTimeoutSeconds(), row.getRevision(), row.getId(), row.getProtocol());
    }
    private long personalId(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found")).getId() + 1;
    }
    public Connection connectionForUser(String username) {
        var row = repository.findById(personalId(username));
        return row.filter(r -> !"INHERIT".equals(r.getMode())).map(this::decode).orElseGet(this::connection);
    }
    public ModelSettingsResponse personalView(String username) {
        return repository.findById(personalId(username)).map(this::personalResponse)
                .orElse(new ModelSettingsResponse("INHERIT", "", false, "", 60, 0, "PERSONAL", "OPENAI"));
    }
    private ModelSettingsResponse personalResponse(ModelSettings row) {
        return new ModelSettingsResponse(row.getMode(), row.getBaseUrl(), row.getEncryptedKey() != null,
                row.getDefaultModel(), row.getTimeoutSeconds(), row.getRevision(), "PERSONAL", row.getProtocol());
    }
    public ModelSettingsResponse effectiveView(String username) {
        var personal = personalView(username); return "INHERIT".equals(personal.mode()) ? view() : personal;
    }
    @Transactional
    public ModelSettingsResponse savePersonal(ModelSettingsRequest request, String username) {
        long id = personalId(username);
        var row = repository.findById(id).orElseGet(() -> { var created = new ModelSettings(); created.setId(id); created.setOwnerId(id - 1); return created; });
        apply(row, request, new Connection("DEMO", "", "", "", 60, 0));
        audit.record(username, "PERSONAL_MODEL_UPDATED", "MODEL_SETTINGS", id);
        return personalResponse(row);
    }
    @Transactional
    public void inherit(String username, long revision) {
        long id = personalId(username);
        var row = repository.findById(id).orElseThrow(() -> new ConflictException("No personal configuration to remove"));
        if (row.getRevision() != revision) { throw new ConflictException("Model settings changed; refresh and try again"); }
        row.setMode("INHERIT"); row.setEncryptedKey(null); row.setBaseUrl(""); row.setDefaultModel(""); row.setProtocol("OPENAI"); repository.flush();
        audit.record(username, "PERSONAL_MODEL_REMOVED", "MODEL_SETTINGS", id);
    }
    public ModelSettingsResponse view() {
        return repository.findById(1L).map(row -> new ModelSettingsResponse(row.getMode(), row.getBaseUrl(),
                row.getEncryptedKey() != null && !row.getEncryptedKey().isBlank(), row.getDefaultModel(),
                row.getTimeoutSeconds(), row.getRevision(), "DATABASE", row.getProtocol()))
                .orElse(new ModelSettingsResponse(defaults.mode(), defaults.baseUrl(), !defaults.apiKey().isBlank(),
                        defaults.defaultModel(), defaults.timeoutSeconds(), 0, "ENVIRONMENT", defaults.protocol()));
    }
    @Transactional
    public ModelSettingsResponse save(ModelSettingsRequest request, String actor) {
        ModelSettings row = repository.findById(1L).orElseGet(ModelSettings::new);
        apply(row, request, defaults);
        audit.record(actor, "MODEL_SETTINGS_UPDATED", "MODEL_SETTINGS", 1L);
        return view();
    }
    private void apply(ModelSettings row, ModelSettingsRequest request, Connection initial) {
        if (request.revision() != row.getRevision()) { throw new ConflictException("Model settings changed; refresh and try again"); }
        String url = normalize(request.baseUrl());
        boolean supplied = request.apiKey() != null && !request.apiKey().isBlank();
        String oldUrl = row.getRevision() == 0 ? initial.baseUrl() : row.getBaseUrl();
        String oldProtocol = row.getRevision() == 0 ? initial.protocol() : row.getProtocol();
        if ((!url.equals(oldUrl) || !request.protocol().equals(oldProtocol)) && !supplied && !request.clearKey()) {
            throw new IllegalArgumentException("Re-enter the API key when changing the service address or protocol");
        }
        String key = request.clearKey() ? "" : supplied ? request.apiKey().strip()
                : row.getRevision() == 0 ? initial.apiKey() : cipher.decrypt(row.getEncryptedKey());
        if ("API".equals(request.mode())) {
            validate(new Connection("API", url, key, request.defaultModel(), request.timeoutSeconds(), 0, row.getId(), request.protocol()));
            if (request.defaultModel().isBlank() || "demo-model".equals(request.defaultModel().strip())) {
                throw new IllegalArgumentException("Enter a real model ID supported by your provider");
            }
        }
        row.setMode(request.mode()); row.setBaseUrl(url); row.setProtocol(request.protocol());
        row.setEncryptedKey(key.isBlank() ? null : cipher.encrypt(key));
        row.setDefaultModel(request.defaultModel().strip()); row.setTimeoutSeconds(request.timeoutSeconds());
        repository.saveAndFlush(row);
    }
    public static void validate(Connection config) {
        if (!Set.of("OPENAI", "ANTHROPIC").contains(config.protocol())) {
            throw new ModelUnavailableException("Unsupported model API protocol");
        }
        if (config.baseUrl().isBlank() || config.apiKey().isBlank()) {
            throw new ModelUnavailableException("Configure the model service address and API key first");
        }
        try {
            URI uri = URI.create(config.baseUrl());
            boolean local = "http".equals(uri.getScheme()) && Set.of("localhost", "127.0.0.1", "[::1]")
                    .contains(uri.getHost() == null ? "" : uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (!"https".equals(uri.getScheme()) && !local)
                    || config.apiKey().chars().anyMatch(Character::isISOControl)) { throw new IllegalArgumentException(); }
        } catch (IllegalArgumentException exception) {
            throw new ModelUnavailableException("Use an HTTPS API prefix, or local HTTP for testing");
        }
    }
    private static String normalize(String value) { return value.strip().replaceAll("/+$", ""); }
}
