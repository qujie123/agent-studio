package com.agent.agentplatform.controller;

import com.agent.agentplatform.dto.UpdateRoleRequest;
import com.agent.agentplatform.dto.UserResponse;
import com.agent.agentplatform.dto.AccountRequests;
import com.agent.agentplatform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {
    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.page("","",null,0,100).items();
    }

    @PostMapping public UserResponse create(@Valid @RequestBody AccountRequests.Create request) { return userService.createManaged(request); }
    @GetMapping("/page") public com.agent.agentplatform.dto.PageResult<UserResponse> page(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String role, @RequestParam(required=false) Boolean enabled,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return userService.page(q,role,enabled,page,size); }
    @GetMapping("/summary") public java.util.Map<String, Long> summary() { return userService.summary(); }
    @PutMapping("/{id}") public UserResponse edit(@PathVariable Long id, @Valid @RequestBody AccountRequests.Profile request) { return userService.updateProfile(id, request); }
    @PatchMapping("/{id}/status") public UserResponse status(@PathVariable Long id, @Valid @RequestBody AccountRequests.Status request) { return userService.setEnabled(id, request.enabled()); }
    @PostMapping("/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@PathVariable Long id, @Valid @RequestBody AccountRequests.ResetPassword request) { userService.resetPassword(id, request.newPassword()); }
    @PostMapping("/{id}/revoke-sessions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id) { userService.revokeSessions(id); }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @PatchMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return userService.updateRole(id, request.getRole());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
