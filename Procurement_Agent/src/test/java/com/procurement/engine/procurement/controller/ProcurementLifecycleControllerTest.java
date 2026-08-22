package com.procurement.engine.procurement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.model.ConstraintInputDto;
import com.procurement.engine.procurement.model.CreateProcurementRequestDto;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.recommendation.service.RecommendationService;
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
import java.util.List;

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
class ProcurementLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private RecommendationService recommendationService;

    private User manager;

    @BeforeEach
    void setUp() {
        manager = userRepository.findByEmail("manager@procurement.com").orElseThrow();
    }

    @Test
    @DisplayName("GET /api/procurements/{id} successfully loads request with lazy constraints under open-in-view=false")
    void testGetProcurement_WithLazyConstraints_SucceedsWithoutLazyInitializationException() throws Exception {
        ProcurementRequest req = ProcurementRequest.builder()
                .user(manager)
                .category("Laptop")
                .quantity(5)
                .authorizationLimit(new BigDecimal("500000.00"))
                .status(ProcurementState.SUBMITTED)
                .build();

        req.addConstraint(ProcurementConstraint.builder()
                .attribute("ram")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("16")
                .mandatory(true)
                .build());

        req.addConstraint(ProcurementConstraint.builder()
                .attribute("storage")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("512")
                .mandatory(true)
                .build());

        req.addConstraint(ProcurementConstraint.builder()
                .attribute("price")
                .operator(ConstraintOperator.LESS_THAN_OR_EQUAL)
                .value("90000")
                .mandatory(false)
                .build());

        ProcurementRequest saved = procurementRequestRepository.save(req);

        mockMvc.perform(get("/api/procurements/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.data.category").value("Laptop"))
                .andExpect(jsonPath("$.data.quantity").value(5))
                .andExpect(jsonPath("$.data.constraintCount").value(3))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("GET /api/procurements/{id} after recommendation loads selectedProduct and selectedOffer.vendor without proxy issues")
    void testGetProcurement_WithSelectedProductAndOffer_Succeeds() throws Exception {
        ProcurementRequest req = ProcurementRequest.builder()
                .user(manager)
                .category("Laptop")
                .quantity(2)
                .authorizationLimit(new BigDecimal("200000.00"))
                .status(ProcurementState.SUBMITTED)
                .build();

        req.addConstraint(ProcurementConstraint.builder()
                .attribute("ram")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("16")
                .mandatory(true)
                .build());

        ProcurementRequest saved = procurementRequestRepository.save(req);

        // Run discovery & recommendation to populate selectedProduct and selectedOffer
        discoveryService.discoverAndEvaluate(saved.getId());
        recommendationService.generateRecommendation(saved.getId());

        mockMvc.perform(get("/api/procurements/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("RECOMMENDED"))
                .andExpect(jsonPath("$.data.selectedOfferId").isNotEmpty())
                .andExpect(jsonPath("$.data.selectedProductName").isNotEmpty())
                .andExpect(jsonPath("$.data.selectedVendorName").isNotEmpty())
                .andExpect(jsonPath("$.data.constraintCount").value(1));
    }

    @Test
    @DisplayName("POST /api/procurements creates request with constraints and returns summary")
    void testCreateProcurement_ReturnsSummaryWithConstraints() throws Exception {
        CreateProcurementRequestDto dto = new CreateProcurementRequestDto(
                "TV",
                2,
                new BigDecimal("150000.00"),
                List.of(
                        new ConstraintInputDto("screenSize", ">=", "55", true),
                        new ConstraintInputDto("resolution", "==", "4K", true)
                )
        );

        mockMvc.perform(post("/api/procurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category").value("TV"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.constraintCount").value(2))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("POST /api/procurements/{id}/execute runs deterministic orchestration to completion")
    void testExecuteProcurement_OrchestratesSuccessfully() throws Exception {
        CreateProcurementRequestDto dto = new CreateProcurementRequestDto(
                "TV",
                1,
                new BigDecimal("200000.00"),
                List.of(new ConstraintInputDto("screenSize", ">=", "55", true))
        );

        ProcurementRequest req = ProcurementRequest.builder()
                .user(manager)
                .category(dto.getCategory())
                .quantity(dto.getQuantity())
                .authorizationLimit(dto.getAuthorizationLimit())
                .status(ProcurementState.SUBMITTED)
                .build();
        req.addConstraint(ProcurementConstraint.builder()
                .attribute("screenSize")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("55")
                .mandatory(true)
                .build());

        ProcurementRequest saved = procurementRequestRepository.save(req);

        mockMvc.perform(post("/api/procurements/" + saved.getId() + "/execute")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.purchaseOrderId").isNotEmpty());
    }
}
