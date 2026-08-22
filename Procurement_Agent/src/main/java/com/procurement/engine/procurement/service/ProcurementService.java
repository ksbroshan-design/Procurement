package com.procurement.engine.procurement.service;

import com.procurement.engine.audit.entity.AuditEventType;
import com.procurement.engine.audit.service.AuditService;
import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.model.CreateProcurementRequestDto;
import com.procurement.engine.procurement.model.ProcurementSummaryDto;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.vendor.entity.Vendor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Service for Procurement Request lifecycle management and transactional DTO mapping.
 */
@Service
public class ProcurementService {

    private final ProcurementRequestRepository procurementRequestRepository;
    private final AuditService auditService;

    public ProcurementService(ProcurementRequestRepository procurementRequestRepository,
                              AuditService auditService) {
        this.procurementRequestRepository = procurementRequestRepository;
        this.auditService = auditService;
    }

    /**
     * Creates a new procurement request from structured JSON intent.
     */
    @Transactional
    public ProcurementSummaryDto createProcurement(CreateProcurementRequestDto requestDto, User user) {
        if (requestDto == null || requestDto.getCategory() == null || requestDto.getCategory().isBlank()) {
            throw new IllegalArgumentException("Category is required to create a procurement request.");
        }
        if (requestDto.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        ProcurementRequest request = ProcurementRequest.builder()
                .user(user)
                .category(requestDto.getCategory().trim())
                .quantity(requestDto.getQuantity())
                .authorizationLimit(requestDto.getAuthorizationLimit() != null ? requestDto.getAuthorizationLimit() : BigDecimal.ZERO)
                .status(ProcurementState.SUBMITTED)
                .build();

        if (requestDto.getConstraints() != null) {
            requestDto.getConstraints().forEach(c -> {
                if (c.getAttribute() != null && !c.getAttribute().isBlank()) {
                    request.addConstraint(ProcurementConstraint.builder()
                            .attribute(c.getAttribute().trim())
                            .operator(c.resolveOperator())
                            .value(c.getValue() != null ? c.getValue().trim() : "")
                            .mandatory(c.isMandatory())
                            .build());
                }
            });
        }

        ProcurementRequest saved = procurementRequestRepository.save(request);

        auditService.record(
                saved.getId(),
                AuditEventType.REQUEST_CREATED,
                ProcurementState.SUBMITTED,
                user != null ? user.getName() : "SYSTEM",
                "Procurement request created for category: " + saved.getCategory(),
                Map.of("category", saved.getCategory(), "quantity", saved.getQuantity(),
                        "authorizationLimit", saved.getAuthorizationLimit().toString(),
                        "constraintCount", saved.getConstraints().size())
        );

        return toSummaryDto(saved);
    }

    /**
     * Retrieves the status and summary DTO for a procurement request within a read-only transaction.
     * Ensures all lazy associations and collections are initialized within the Hibernate session.
     */
    @Transactional(readOnly = true)
    public ProcurementSummaryDto getProcurementSummary(UUID id) {
        ProcurementRequest request = procurementRequestRepository.findByIdWithDetails(id)
                .orElseGet(() -> procurementRequestRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + id)));

        return toSummaryDto(request);
    }

    /**
     * Retrieves all procurement requests ordered by creation timestamp descending.
     */
    @Transactional(readOnly = true)
    public List<ProcurementSummaryDto> getAllProcurements() {
        return procurementRequestRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    private ProcurementSummaryDto toSummaryDto(ProcurementRequest r) {

        VendorOffer selectedOffer = r.getSelectedOffer();
        Product selectedProduct = r.getSelectedProduct();
        Vendor selectedVendor = (selectedOffer != null) ? selectedOffer.getVendor() : null;

        UUID offerId = selectedOffer != null ? selectedOffer.getId() : null;
        String productName = selectedProduct != null ? selectedProduct.getName()
                : (selectedOffer != null && selectedOffer.getProduct() != null ? selectedOffer.getProduct().getName() : null);
        String vendorName = selectedVendor != null ? selectedVendor.getName() : null;
        int constraintCount = r.getConstraints() != null ? r.getConstraints().size() : 0;

        return new ProcurementSummaryDto(
                r.getId(),
                r.getCategory(),
                r.getQuantity(),
                r.getAuthorizationLimit(),
                r.getStatus(),
                offerId,
                productName,
                vendorName,
                constraintCount,
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
