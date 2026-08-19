package com.procurement.engine.procurement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.engine.authorization.service.AuthorizationService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProcurementExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private UserRepository userRepository;

    private ProcurementRequest procurementRequest;

    @BeforeEach
    void setUp() {
        User manager = userRepository.findByEmail("manager@procurement.com").orElseThrow();

        ProcurementRequest req = ProcurementRequest.builder()
                .user(manager)
                .category("TV")
                .quantity(1)
                .authorizationLimit(new BigDecimal("300000.00"))
                .status(ProcurementState.SUBMITTED)
                .build();

        req.addConstraint(ProcurementConstraint.builder()
                .attribute("screenSize")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("55")
                .mandatory(true)
                .build());

        procurementRequest = procurementRequestRepository.save(req);
        discoveryService.discoverAndEvaluate(procurementRequest.getId());
        authorizationService.checkAuthorization(procurementRequest.getId());
    }

    @Test
    @DisplayName("POST /api/procurements/{id}/revalidate executes pre-purchase revalidation")
    void testRevalidateEndpoint() throws Exception {
        mockMvc.perform(post("/api/procurements/{id}/revalidate", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.nextState").value("PURCHASING"));
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/revalidate returns revalidation evaluation")
    void testGetRevalidationEndpoint() throws Exception {
        mockMvc.perform(get("/api/procurements/{id}/revalidate", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.checks").isArray());
    }

    @Test
    @DisplayName("POST /api/procurements/{id}/purchase executes purchase and returns confirmed order")
    void testPurchaseEndpoint() throws Exception {
        mockMvc.perform(post("/api/procurements/{id}/purchase", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.purchaseOrderId").isNotEmpty())
                .andExpect(jsonPath("$.data.nextState").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/purchase-order retrieves confirmed purchase order")
    void testGetPurchaseOrderEndpoint() throws Exception {
        // First execute purchase
        mockMvc.perform(post("/api/procurements/{id}/purchase", procurementRequest.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/procurements/{id}/purchase-order", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.totalAmount").isNotEmpty());
    }
}
