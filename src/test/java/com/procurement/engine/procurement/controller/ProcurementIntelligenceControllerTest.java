package com.procurement.engine.procurement.controller;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProcurementIntelligenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

        procurementRequest = procurementRequestRepository.save(req);
        discoveryService.discoverAndEvaluate(procurementRequest.getId());
    }

    @Test
    @DisplayName("POST /api/procurements/{id}/analyze-tco runs TCO calculations successfully")
    void testAnalyzeTcoEndpoint() throws Exception {
        mockMvc.perform(post("/api/procurements/{id}/analyze-tco", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.procurementId").value(procurementRequest.getId().toString()))
                .andExpect(jsonPath("$.data.allBreakdowns", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.allBreakdowns[0].totalTco", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/tco returns TCO breakdowns for offers")
    void testGetTcoEndpoint() throws Exception {
        mockMvc.perform(get("/api/procurements/{id}/tco", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].productName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].unitTco").exists())
                .andExpect(jsonPath("$.data[0].totalTco").exists());
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/ranking returns multi-dimensional ranking")
    void testGetRankingEndpoint() throws Exception {
        mockMvc.perform(get("/api/procurements/{id}/ranking", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category").value("Laptop"))
                .andExpect(jsonPath("$.data.eligibleOffers", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.topRankedOffer.rank").value(1))
                .andExpect(jsonPath("$.data.weightsUsed.tco").value(0.30));
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/recommendation returns explainable recommendation")
    void testGetRecommendationEndpoint() throws Exception {
        mockMvc.perform(get("/api/procurements/{id}/recommendation", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category").value("Laptop"))
                .andExpect(jsonPath("$.data.recommendationType").isNotEmpty())
                .andExpect(jsonPath("$.data.bestEligibleOption").exists())
                .andExpect(jsonPath("$.data.selectedOfferId").exists())
                .andExpect(jsonPath("$.data.explanation").isNotEmpty())
                .andExpect(jsonPath("$.data.tradeOffs", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/ranking returns 404 for non-existent procurement")
    void testNonExistentProcurement() throws Exception {
        UUID nonExistent = UUID.randomUUID();
        mockMvc.perform(get("/api/procurements/{id}/ranking", nonExistent)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }
}
