package com.procurement.engine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.engine.security.model.AuthRequestDTO;
import com.procurement.engine.user.entity.Role;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        if (userRepository.findByEmail("auth_test@procurement.com").isEmpty()) {
            userRepository.save(User.builder()
                    .name("Auth Test User")
                    .email("auth_test@procurement.com")
                    .password(passwordEncoder.encode("secretPassword123"))
                    .role(Role.PROCUREMENT_MANAGER)
                    .authorizationLimit(new BigDecimal("100000.00"))
                    .build());
        }
    }

    @Test
    @DisplayName("Public login endpoint is accessible and returns valid JWT on successful authentication")
    void testLogin_Success() throws Exception {
        AuthRequestDTO request = new AuthRequestDTO("auth_test@procurement.com", "secretPassword123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value("auth_test@procurement.com"))
                .andExpect(jsonPath("$.data.role").value("PROCUREMENT_MANAGER"));
    }

    @Test
    @DisplayName("Public login endpoint returns 401 or bad credentials on incorrect password")
    void testLogin_WrongPassword_ReturnsUnauthorizedOrError() throws Exception {
        AuthRequestDTO request = new AuthRequestDTO("auth_test@procurement.com", "wrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }
}
