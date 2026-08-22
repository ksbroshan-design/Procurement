package com.procurement.engine.constraint;

import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.constraint.model.BatchConstraintEvaluationResult;
import com.procurement.engine.constraint.model.ProductConstraintEvaluation;
import com.procurement.engine.constraint.service.ConstraintService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConstraintServiceTest {

    @Autowired
    private ConstraintService constraintService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Test
    @DisplayName("Evaluate TV category products: filters eligible vs ineligible products deterministically")
    void testBatchEvaluationTvCategory() {
        List<Product> tvs = productRepository.findByCategoryIgnoreCase("TV");
        assertThat(tvs).hasSize(6);

        // Constraints: Screen size >= 55 (hard), OLED panel (soft)
        List<ProcurementConstraint> constraints = List.of(
                ProcurementConstraint.builder()
                        .attribute("screenSize")
                        .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                        .value("55")
                        .mandatory(true)
                        .build(),
                ProcurementConstraint.builder()
                        .attribute("panelType")
                        .operator(ConstraintOperator.EQUALS)
                        .value("OLED")
                        .mandatory(false)
                        .weight(new BigDecimal("0.50"))
                        .build()
        );

        BatchConstraintEvaluationResult result = constraintService.evaluateProducts(tvs, constraints);

        assertThat(result.getTotalProductsEvaluated()).isEqualTo(6);
        // Xiaomi 43-inch TV fails hard constraint (screenSize < 55)
        assertThat(result.getEligibleProductsCount()).isEqualTo(5);
        assertThat(result.getIneligibleProductsCount()).isEqualTo(1);
        assertThat(result.hasMatches()).isTrue();

        List<Product> eligibleProducts = constraintService.filterEligibleProducts(tvs, constraints);
        assertThat(eligibleProducts).hasSize(5);
        assertThat(eligibleProducts).noneMatch(p -> p.getName().contains("43-Inch"));
    }

    @Test
    @DisplayName("Evaluate for ProcurementRequest by ID")
    void testEvaluateForProcurement() {
        User user = userRepository.findByEmail("manager@procurement.com").orElseThrow();

        ProcurementRequest request = ProcurementRequest.builder()
                .user(user)
                .category("Laptop")
                .quantity(10)
                .authorizationLimit(new BigDecimal("900000.00"))
                .status(ProcurementState.VALIDATING)
                .build();

        ProcurementConstraint ramConstraint = ProcurementConstraint.builder()
                .attribute("ram")
                .operator(ConstraintOperator.GREATER_THAN_OR_EQUAL)
                .value("16")
                .mandatory(true)
                .build();

        request.addConstraint(ramConstraint);
        ProcurementRequest saved = procurementRequestRepository.save(request);

        BatchConstraintEvaluationResult result = constraintService.evaluateForProcurement(saved.getId());
        assertThat(result.getTotalProductsEvaluated()).isEqualTo(6);
        // Acer Aspire 3 has 8GB RAM -> fails hard constraint
        assertThat(result.getEligibleProductsCount()).isEqualTo(5);
    }
}
