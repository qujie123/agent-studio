package com.agent.agentplatform.dto;
import jakarta.validation.constraints.*;

public final class AccountRequests {
    private AccountRequests() {}
    public record Profile(@NotBlank @Size(max = 80) String displayName, @NotBlank @Email @Size(max = 254) String email) {}
    public record Status(@NotNull Boolean enabled) {}
    public record Password(@NotBlank String currentPassword, @NotBlank @Size(min = 12, max = 72) String newPassword) {}
    public record ResetPassword(@NotBlank @Size(min = 12, max = 72) String newPassword) {}
    public record Create(@NotBlank @Size(min = 3, max = 50) String username,
                         @NotBlank @Email @Size(max = 254) String email,
                         @NotBlank @Size(max = 80) String displayName,
                         @NotBlank @Size(min = 12, max = 72) String password,
                         @NotBlank @Pattern(regexp = "ADMIN|USER") String role) {}
}
