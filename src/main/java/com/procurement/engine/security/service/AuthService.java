package com.procurement.engine.security.service;

import com.procurement.engine.common.exception.InvalidRequestException;
import com.procurement.engine.security.model.AuthRequestDTO;
import com.procurement.engine.security.model.AuthResponseDTO;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for user authentication and JWT issuance.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    public AuthService(AuthenticationManager authenticationManager,
                       CustomUserDetailsService userDetailsService,
                       JwtService jwtService,
                       UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /**
     * Authenticates credentials and returns a signed JWT with user metadata.
     */
    public AuthResponseDTO login(AuthRequestDTO request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            throw new InvalidRequestException("Email and password are required.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail().trim(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidRequestException("User account not found for: " + userDetails.getUsername()));

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("role", user.getRole().name());
        claims.put("authorizationLimit", user.getAuthorizationLimit().toString());

        String token = jwtService.generateToken(claims, userDetails);

        return new AuthResponseDTO(
                token,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getAuthorizationLimit(),
                expirationMs
        );
    }
}
