package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.LoginRequest;
import com.agent.agentplatform.dto.RegisterRequest;
import com.agent.agentplatform.dto.UserResponse;
import com.agent.agentplatform.dto.AccountRequests;
import com.agent.agentplatform.entity.User;
import com.agent.agentplatform.exception.ConflictException;
import com.agent.agentplatform.exception.ResourceNotFoundException;
import com.agent.agentplatform.exception.UnauthorizedException;
import com.agent.agentplatform.repository.UserRepository;
import com.agent.agentplatform.utils.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditService audit;
    private final com.agent.agentplatform.repository.ModelSettingsRepository modelSettings;
    private final com.agent.agentplatform.repository.UsageRecordRepository usageRecords;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       AuditService audit, com.agent.agentplatform.repository.ModelSettingsRepository modelSettings,
                       com.agent.agentplatform.repository.UsageRecordRepository usageRecords) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.audit = audit;
        this.modelSettings = modelSettings;
        this.usageRecords = usageRecords;
    }

    @Transactional
    public UserResponse createUser(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalArgumentException("Username must contain 3 to 50 characters after trimming");
        }
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        userRepository.saveAndFlush(user);
        audit.record(username, "USER_REGISTERED", "USER", user.getId());
        return UserResponse.fromUser(user);
    }

    @Transactional
    public String login(LoginRequest request) {
        User user = userRepository.findByUsernameForUpdate(request.getUsername().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }
        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("USER");
            userRepository.save(user);
        }
        user.setLastLoginAt(java.time.Instant.now());
        return jwtUtil.generateToken(user);
    }

    public UserResponse getCurrentUser(String username) {
        return UserResponse.fromUser(findByUsername(username));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::fromUser).toList();
    }

    public com.agent.agentplatform.dto.PageResult<UserResponse> page(String q, String role, Boolean enabled, int page, int size) {
        if (!java.util.Set.of("", "ADMIN", "USER").contains(role)) { throw new IllegalArgumentException("Invalid role filter"); }
        return com.agent.agentplatform.dto.PageResult.from(userRepository.search(com.agent.agentplatform.dto.PageResult.query(q), role, enabled,
                com.agent.agentplatform.dto.PageResult.request(page,size)), UserResponse::fromUser);
    }
    public java.util.Map<String, Long> summary() {
        return java.util.Map.of("total",userRepository.count(),"enabled",userRepository.countByEnabled(true),
                "admins",userRepository.countByRoleAndEnabled("ADMIN",true),"disabled",userRepository.countByEnabled(false));
    }

    public UserResponse getUserById(Long id) {
        return UserResponse.fromUser(findById(id));
    }

    @Transactional
    public UserResponse updateRole(Long id, String requestedRole) {
        String role = requestedRole.trim().toUpperCase(Locale.ROOT);
        if (!role.equals("USER") && !role.equals("ADMIN")) {
            throw new IllegalArgumentException("Role must be USER or ADMIN");
        }
        var administrators = userRepository.lockAdministrators();
        User user = locked(id);
        if (user.isEnabled() && "ADMIN".equals(user.getRole()) && "USER".equals(role)
                && administrators.stream().filter(User::isEnabled).count() <= 1) {
            throw new ConflictException("The last administrator cannot be demoted");
        }
        user.setRole(role);
        userRepository.saveAndFlush(user);
        audit.record(currentActor(), "USER_ROLE_CHANGED", "USER", id);
        return UserResponse.fromUser(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        var administrators = userRepository.lockAdministrators();
        User user = locked(id);
        if (user.isEnabled() && "ADMIN".equals(user.getRole()) && administrators.stream().filter(User::isEnabled).count() <= 1) {
            throw new ConflictException("The last administrator cannot be deleted");
        }
        modelSettings.deleteByOwnerId(id); modelSettings.flush();
        usageRecords.deleteByUserId(id); usageRecords.flush();
        userRepository.delete(user);
        userRepository.flush();
        audit.record(currentActor(), "USER_DELETED", "USER", id);
    }

    @Transactional
    public UserResponse createManaged(AccountRequests.Create request) {
        validatePassword(request.password());
        RegisterRequest registration = new RegisterRequest(); registration.setUsername(request.username());
        registration.setEmail(request.email()); registration.setPassword(request.password());
        UserResponse created = createUser(registration);
        User user = locked(created.getId()); user.setDisplayName(request.displayName().strip());
        if (!"USER".equals(request.role()) && !"ADMIN".equals(request.role())) { throw new IllegalArgumentException("Invalid role"); }
        user.setRole(request.role()); userRepository.flush();
        audit.record(currentActor(), "USER_CREATED_BY_ADMIN", "USER", user.getId());
        return UserResponse.fromUser(user);
    }

    @Transactional
    public UserResponse updateProfile(Long id, AccountRequests.Profile request) {
        User user = locked(id); String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) { throw new ConflictException("Email already exists"); }
        user.setEmail(email); user.setDisplayName(request.displayName().strip()); userRepository.flush();
        audit.record(currentActor(), "USER_PROFILE_UPDATED", "USER", id);
        return UserResponse.fromUser(user);
    }

    @Transactional
    public UserResponse setEnabled(Long id, boolean enabled) {
        var admins = userRepository.lockAdministrators(); User user = locked(id);
        if (!enabled && user.isEnabled() && "ADMIN".equals(user.getRole()) && admins.stream().filter(User::isEnabled).count() <= 1) {
            throw new ConflictException("不能停用最后一个有效管理员");
        }
        if (user.isEnabled() != enabled) { user.setEnabled(enabled); user.revokeTokens(); }
        audit.record(currentActor(), enabled ? "USER_ENABLED" : "USER_DISABLED", "USER", id);
        return UserResponse.fromUser(user);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        validatePassword(password); User user = locked(id);
        user.setPassword(passwordEncoder.encode(password)); user.revokeTokens();
        audit.record(currentActor(), "USER_PASSWORD_RESET", "USER", id);
    }

    @Transactional
    public void changePassword(String username, AccountRequests.Password request) {
        User user = locked(findByUsername(username).getId());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) { throw new IllegalArgumentException("当前密码不正确"); }
        resetPassword(user.getId(), request.newPassword());
    }

    @Transactional
    public void revokeSessions(Long id) {
        locked(id).revokeTokens(); audit.record(currentActor(), "USER_SESSIONS_REVOKED", "USER", id);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码至少 12 字符且不超过 72 个 UTF-8 字节");
        }
    }
    private User locked(Long id) { return userRepository.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); }

    private String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
