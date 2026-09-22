package com.tradingreporting.api.service;

import com.tradingreporting.api.config.AppProperties;
import com.tradingreporting.api.domain.User;
import com.tradingreporting.api.dto.CurrentUserResponse;
import com.tradingreporting.api.dto.MockCallbackRequest;
import com.tradingreporting.api.dto.TokenRequest;
import com.tradingreporting.api.dto.TokenResponse;
import com.tradingreporting.api.exception.ApiException;
import com.tradingreporting.api.repository.UserRepository;
import com.tradingreporting.api.security.JwtService;
import com.tradingreporting.api.security.PasswordHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors {@code app.routes_auth}: login, the development mock SSO callback, and {@code /auth/me}. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, JwtService jwtService,
                        AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(TokenRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null || !user.isActive() || !passwordHasher.verify(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }
        return tokenResponse(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse mockCallback(MockCallbackRequest request) {
        if (!properties.seedDevUsers()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Mock callback is disabled.");
        }
        User user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null || !user.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unknown development user.");
        }
        return tokenResponse(user);
    }

    public CurrentUserResponse currentUser(User user) {
        return new CurrentUserResponse(user.getUsername(), user.isAdmin() ? "admin" : "trader");
    }

    private TokenResponse tokenResponse(User user) {
        return new TokenResponse(jwtService.createAccessToken(user.getId()));
    }
}
