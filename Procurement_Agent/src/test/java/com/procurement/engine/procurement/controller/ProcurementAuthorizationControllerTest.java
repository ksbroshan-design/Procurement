package com.procurement.engine.procurement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.engine.authorization.model.ApprovalActionRequest;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "manager@procurement.com", roles = {"PROCUREMENT_MANAGER"})
class ProcurementAuthorizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private UserRepository userRepository;

    private ProcurementRequest procurementRequest;

    @BeforeEach
    void setUp() {
        User manager = userRepository.findByEmail("manager@procurement.com").orElseThrow();

        // High quantity to exceed manager limit (10 * 56k = 560k > 450k)
        ProcurementRequest req = ProcurementRequest.builder()
                .user(manager)
                .category("TV")
                .quantity(10)
                .authorizationLimit(new BigDecimal("100000.00"))
                .status(ProcurementState.SUBMITTED)
                .build();

        req.addConstraint(ProcurementConstraint.builder()
                .attribute("screenSize")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("55")
                .mandatory(true)
                .build());
        req.addConstraint(ProcurementConstraint.builder()
                .attribute("panelType")
                .operator(ConstraintOperator.EQUALS)
                .value("OLED")
                .mandatory(true)
                .build());

        procurementRequest = procurementRequestRepository.save(req);
        discoveryService.discoverAndEvaluate(procurementRequest.getId());
    }

    @Test
    @DisplayName("POST /api/procurements/{id}/authorize checks limits and creates pending approval")
    void testAuthorizeEndpoint() throws Exception {
        mockMvc.perform(post("/api/procurements/{id}/authorize", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.procurementId").value(procurementRequest.getId().toString()))
                .andExpect(jsonPath("$.data.decision").value("REQUIRES_APPROVAL"))
                .andExpect(jsonPath("$.data.nextState").value("WAITING_APPROVAL"))
                .andExpect(jsonPath("$.data.withinAuthorization").value(false));
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/approval returns pending approval details")
    void testGetApprovalEndpoint() throws Exception {
        // Authorize first to create pending approval
        mockMvc.perform(post("/api/procurements/{id}/authorize", procurementRequest.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/procurements/{id}/approval", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.proposedProductName").isNotEmpty())
                .andExpect(jsonPath("$.data.reason").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/procurements/{id}/approval/approve approves procurement and transitions to REVALIDATING")
    void testApproveEndpoint() throws Exception {
        mockMvc.perform(post("/api/procurements/{id}/authorize", procurementRequest.getId()))
                .andExpect(status().isOk());

        ApprovalActionRequest request = ApprovalActionRequest.ofComments("Manager signed off");

        mockMvc.perform(post("/api/procurements/{id}/approval/approve", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.comments").value("Manager signed off"));
    }

    @Test
    @DisplayName("POST /api/procurements/{id}/approval/reject rejects procurement and transitions to REJECTED")
    void testRejectEndpoint() throws Exception {
        mockMvc.perform(post("/api/procurements/{id}/authorize", procurementRequest.getId()))
                .andExpect(status().isOk());

        ApprovalActionRequest request = ApprovalActionRequest.ofComments("Budget cuts");

        mockMvc.perform(post("/api/procurements/{id}/approval/reject", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.comments").value("Budget cuts"));
    }
}
