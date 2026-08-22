package com.procurement.engine.security.model;

import com.procurement.engine.user.entity.Role;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO returned after successful authentication.
 */
public class AuthResponseDTO {

    private String token;
    private String tokenType = "Bearer";
    private UUID userId;
    private String email;
    private String name;
    private Role role;
    private BigDecimal authorizationLimit;
    private long expiresInMs;

    public AuthResponseDTO() {}

    public AuthResponseDTO(String token,
                           UUID userId,
                           String email,
                           String name,
                           Role role,
                           BigDecimal authorizationLimit,
                           long expiresInMs) {
        this.token = token;
        this.tokenType = "Bearer";
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.authorizationLimit = authorizationLimit;
        this.expiresInMs = expiresInMs;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public BigDecimal getAuthorizationLimit() { return authorizationLimit; }
    public void setAuthorizationLimit(BigDecimal authorizationLimit) { this.authorizationLimit = authorizationLimit; }
    public long getExpiresInMs() { return expiresInMs; }
    public void setExpiresInMs(long expiresInMs) { this.expiresInMs = expiresInMs; }
}
