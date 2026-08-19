package com.procurement.engine.procurement.controller;

import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
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
class ProcurementDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
                .quantity(5)
                .authorizationLimit(new BigDecimal("350000.00"))
                .status(ProcurementState.SUBMITTED)
                .build();

        req.addConstraint(ProcurementConstraint.builder()
                .attribute("screenSize")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("55")
                .mandatory(true)
                .build());

        procurementRequest = procurementRequestRepository.save(req);
    }

    @Test
    @DisplayName("POST /api/procurements/{id}/discover executes discovery successfully")
    void testDiscoverEndpoint() throws Exception {
        mockMvc.perform(post("/api/procurements/{id}/discover", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.procurementId").value(procurementRequest.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.eligibleCandidatesCount", greaterThan(0)));
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/products returns discovered candidate products")
    void testGetProductsEndpoint() throws Exception {
        mockMvc.perform(get("/api/procurements/{id}/products", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].productName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].vendorName").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/comparison returns normalized comparison data")
    void testGetComparisonEndpoint() throws Exception {
        mockMvc.perform(get("/api/procurements/{id}/comparison", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category").value("TV"))
                .andExpect(jsonPath("$.data.totalCandidatesCompared", greaterThan(0)))
                .andExpect(jsonPath("$.data.minPrice", notNullValue()))
                .andExpect(jsonPath("$.data.offers", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/rejections returns rejected candidates with failure diagnostics")
    void testGetRejectionsEndpoint() throws Exception {
        mockMvc.perform(get("/api/procurements/{id}/rejections", procurementRequest.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].failedConstraints", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].failedConstraints[0].attribute").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/procurements/{id}/discover returns 404 for non-existent procurement")
    void testNonExistentProcurement() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(post("/api/procurements/{id}/discover", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }
}
