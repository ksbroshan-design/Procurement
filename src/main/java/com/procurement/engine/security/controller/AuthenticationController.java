package com.procurement.engine.security.controller;

import com.procurement.engine.common.model.ApiResponse;
import com.procurement.engine.security.model.AuthRequestDTO;
import com.procurement.engine.security.model.AuthResponseDTO;
import com.procurement.engine.security.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public REST Controller for Authentication and JWT token issuance.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthService authService;

    public AuthenticationController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login
     * Authenticates user credentials and issues a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> login(@Valid @RequestBody AuthRequestDTO request) {
        AuthResponseDTO response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
    }

    /**
     * POST /api/auth/token
     * Alternative token endpoint.
     */
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> token(@Valid @RequestBody AuthRequestDTO request) {
        AuthResponseDTO response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Token issued successfully", response));
    }
}
