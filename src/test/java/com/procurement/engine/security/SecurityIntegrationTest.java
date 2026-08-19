package com.procurement.engine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.security.model.AuthRequestDTO;
import com.procurement.engine.security.service.CustomUserDetailsService;
import com.procurement.engine.security.service.JwtService;
import com.procurement.engine.statemachine.ProcurementState;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private com.procurement.engine.approval.repository.ApprovalRepository approvalRepository;

    private User managerUser;
    private User regularUser;
    private ProcurementRequest testRequest;

    @BeforeEach
    void setUp() {
        managerUser = userRepository.findByEmail("manager@procurement.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Manager User")
                        .email("manager@procurement.com")
                        .password(passwordEncoder.encode("password123"))
                        .role(Role.PROCUREMENT_MANAGER)
                        .authorizationLimit(new BigDecimal("500000.00"))
                        .build())
        );

        regularUser = userRepository.findByEmail("buyer@procurement.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .name("Regular Buyer")
                        .email("buyer@procurement.com")
                        .password(passwordEncoder.encode("password123"))
                        .role(Role.USER)
                        .authorizationLimit(new BigDecimal("50000.00"))
                        .build())
        );

        testRequest = procurementRequestRepository.save(ProcurementRequest.builder()
                .user(managerUser)
                .category("Laptop")
                .quantity(1)
                .authorizationLimit(new BigDecimal("100000.00"))
                .status(ProcurementState.WAITING_APPROVAL)
                .build());

        approvalRepository.save(com.procurement.engine.approval.entity.Approval.builder()
                .procurement(testRequest)
                .requestedAmount(new BigDecimal("100000.00"))
                .authorizationLimit(new BigDecimal("50000.00"))
                .difference(new BigDecimal("50000.00"))
                .reason("Budget limit approval required")
                .status(com.procurement.engine.approval.entity.ApprovalStatus.PENDING)
                .build());
    }

    private String generateValidToken(String email) {
        UserDetails details = userDetailsService.loadUserByUsername(email);
        return jwtService.generateToken(details);
    }

    @Test
    @DisplayName("A. Login success -> Valid JWT returned")
    void testA_LoginSuccess_ReturnsJwt() throws Exception {
        AuthRequestDTO loginReq = new AuthRequestDTO("manager@procurement.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("manager@procurement.com"));
    }

    @Test
    @DisplayName("B. No Authorization header -> 401 Unauthorized for protected endpoint")
    void testB_NoAuthHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/procurements/" + testRequest.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("C. Valid Bearer token -> 200 OK for protected endpoint")
    void testC_ValidBearerToken_Returns200() throws Exception {
        String token = generateValidToken("manager@procurement.com");

        mockMvc.perform(get("/api/procurements/" + testRequest.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testRequest.getId().toString()));
    }

    @Test
    @DisplayName("D. Expired JWT token -> 401 Unauthorized")
    void testD_ExpiredToken_Returns401() throws Exception {
        // Generate token with negative expiration (already expired)
        String expiredToken = jwtService.generateToken("manager@procurement.com", -1000L);

        mockMvc.perform(get("/api/procurements/" + testRequest.getId())
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("E. Invalid JWT signature / content -> 401 Unauthorized")
    void testE_InvalidToken_Returns401() throws Exception {
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlQHVzZXIuY29tIn0.invalidSignatureString12345";

        mockMvc.perform(get("/api/procurements/" + testRequest.getId())
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("F. Malformed Authorization header -> 401 Unauthorized")
    void testF_MalformedAuthHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/procurements/" + testRequest.getId())
                        .header("Authorization", "NotABearerToken"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("G. Wrong role (ROLE_USER) -> 403 Forbidden for approval endpoint")
    void testG_WrongRole_Returns403OnApproval() throws Exception {
        String userToken = generateValidToken("buyer@procurement.com");

        mockMvc.perform(post("/api/procurements/" + testRequest.getId() + "/approval/approve")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"Attempt by non-manager\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("H. Correct role (ROLE_PROCUREMENT_MANAGER) -> Approval endpoint reaches controller")
    void testH_CorrectRole_AllowsApproval() throws Exception {
        String managerToken = generateValidToken("manager@procurement.com");

        mockMvc.perform(post("/api/procurements/" + testRequest.getId() + "/approval/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"Approved by manager\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("I. Public auth endpoints remain accessible without any token")
    void testI_PublicAuthEndpoints_Accessible() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"manager@procurement.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }
}
