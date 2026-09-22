package com.tradingreporting.api.security;

import com.tradingreporting.api.domain.User;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Authentication populated by {@link JwtAuthenticationFilter} once a bearer token has been verified. */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final User user;

    public JwtAuthenticationToken(User user) {
        super(List.of(new SimpleGrantedAuthority(user.isAdmin() ? "ROLE_ADMIN" : "ROLE_TRADER")));
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return user;
    }

    public User getUser() {
        return user;
    }
}
