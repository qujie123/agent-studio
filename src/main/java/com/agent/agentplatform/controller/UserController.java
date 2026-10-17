package com.agent.agentplatform.controller;

import com.agent.agentplatform.dto.LoginRequest;
import com.agent.agentplatform.dto.LoginResponse;
import com.agent.agentplatform.dto.RegisterRequest;
import com.agent.agentplatform.dto.UserResponse;
import com.agent.agentplatform.dto.AccountRequests;
import com.agent.agentplatform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.createUser(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(userService.login(request));
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(Authentication authentication) {
        return userService.getCurrentUser(authentication.getName());
    }

    @PutMapping("/me") public UserResponse profile(@Valid @RequestBody AccountRequests.Profile request, Authentication auth) {
        return userService.updateProfile(userService.findByUsername(auth.getName()).getId(), request);
    }
    @PostMapping("/me/password") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody AccountRequests.Password request, Authentication auth) { userService.changePassword(auth.getName(), request); }
    @PostMapping("/me/revoke-sessions") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void revoke(Authentication auth) { userService.revokeSessions(userService.findByUsername(auth.getName()).getId()); }
}
