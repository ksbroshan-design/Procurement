package com.procurement.engine.constraint.service;

import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.constraint.model.BatchConstraintEvaluationResult;
import com.procurement.engine.constraint.model.ProductConstraintEvaluation;
import com.procurement.engine.constraint.repository.ProcurementConstraintRepository;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating constraint evaluation across products and procurements.
 */
@Service
@Transactional(readOnly = true)
public class ConstraintService {

    private static final Logger log = LoggerFactory.getLogger(ConstraintService.class);

    private final ConstraintEvaluator constraintEvaluator;
    private final ProductRepository productRepository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final ProcurementConstraintRepository constraintRepository;

    public ConstraintService(ConstraintEvaluator constraintEvaluator,
                             ProductRepository productRepository,
                             ProcurementRequestRepository procurementRequestRepository,
                             ProcurementConstraintRepository constraintRepository) {
        this.constraintEvaluator = constraintEvaluator;
        this.productRepository = productRepository;
        this.procurementRequestRepository = procurementRequestRepository;
        this.constraintRepository = constraintRepository;
    }

    /**
     * Evaluates a single product against a list of constraints.
     */
    public ProductConstraintEvaluation evaluateProduct(Product product, List<ProcurementConstraint> constraints) {
        return constraintEvaluator.evaluate(product, constraints);
    }

    /**
     * Evaluates a product by its ID against constraints.
     */
    public ProductConstraintEvaluation evaluateProduct(UUID productId, List<ProcurementConstraint> constraints) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        return constraintEvaluator.evaluate(product, constraints);
    }

    /**
     * Evaluates a list of products against constraints.
     */
    public BatchConstraintEvaluationResult evaluateProducts(List<Product> products, List<ProcurementConstraint> constraints) {
        if (products == null || products.isEmpty()) {
            return new BatchConstraintEvaluationResult(List.of());
        }

        List<ProductConstraintEvaluation> evaluations = new ArrayList<>(products.size());
        for (Product product : products) {
            evaluations.add(constraintEvaluator.evaluate(product, constraints));
        }

        return new BatchConstraintEvaluationResult(evaluations);
    }

    /**
     * Evaluates all products of a given category for a procurement request against its constraints.
     */
    public BatchConstraintEvaluationResult evaluateForProcurement(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        List<ProcurementConstraint> constraints = request.getConstraints();
        if (constraints.isEmpty()) {
            constraints = constraintRepository.findByProcurementId(procurementId);
        }

        List<Product> products = productRepository.findByCategoryIgnoreCase(request.getCategory());
        log.info("Evaluating {} products in category '{}' for procurement {}", products.size(), request.getCategory(), procurementId);

        return evaluateProducts(products, constraints);
    }

    /**
     * Filters products, returning only those that satisfy all mandatory hard constraints.
     */
    public List<Product> filterEligibleProducts(List<Product> products, List<ProcurementConstraint> constraints) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }

        List<Product> eligibleProducts = new ArrayList<>();
        for (Product product : products) {
            ProductConstraintEvaluation eval = constraintEvaluator.evaluate(product, constraints);
            if (eval.isEligible()) {
                eligibleProducts.add(product);
            }
        }
        return eligibleProducts;
    }
}
