package com.tradingreporting.api.web;

import com.tradingreporting.api.domain.User;
import com.tradingreporting.api.dto.CurrentUserResponse;
import com.tradingreporting.api.dto.MockCallbackRequest;
import com.tradingreporting.api.dto.TokenRequest;
import com.tradingreporting.api.dto.TokenResponse;
import com.tradingreporting.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors {@code app.routes_auth}. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody TokenRequest request) {
        return authService.login(request);
    }

    @PostMapping("/mock/callback")
    public TokenResponse mockCallback(@Valid @RequestBody MockCallbackRequest request) {
        return authService.mockCallback(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(@AuthenticationPrincipal User user) {
        return authService.currentUser(user);
    }
}
