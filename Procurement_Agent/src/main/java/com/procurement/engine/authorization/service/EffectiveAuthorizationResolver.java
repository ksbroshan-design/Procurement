package com.procurement.engine.authorization.service;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Centralized, authoritative resolver for procurement authorization limits.
 * <p>
 * Implements the core business rule:
 * - If an APPROVED Human-in-the-Loop approval exists for the procurement request,
 *   the approved exception amount (Approval.requestedAmount) becomes the authoritative effective limit.
 * - Otherwise, the authenticated user's configured authorization limit (User.authorizationLimit) is strictly used.
 * - Client-supplied authorization limits from ProcurementRequest are NEVER trusted or used as fallback.
 * - The User.authorizationLimit remains immutable.
 */
@Component
public class EffectiveAuthorizationResolver {

    private static final Logger log = LoggerFactory.getLogger(EffectiveAuthorizationResolver.class);

    private final ApprovalRepository approvalRepository;

    public EffectiveAuthorizationResolver(ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    /**
     * Resolves the authoritative effective authorization limit for a procurement request.
     *
     * @param request the procurement request
     * @return the effective authorization limit scaled to 2 decimal places
     * @throws IllegalStateException if user or user limit is not configured
     */
    public BigDecimal resolveEffectiveLimit(ProcurementRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ProcurementRequest cannot be null");
        }

        // 1. Check if an APPROVED Approval record exists for this procurement
        if (request.getId() != null) {
            Optional<Approval> approvedOpt = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(request.getId());
            if (approvedOpt.isPresent() && approvedOpt.get().getStatus() == ApprovalStatus.APPROVED) {
                BigDecimal approvedAmount = approvedOpt.get().getRequestedAmount();
                if (approvedAmount != null && approvedAmount.compareTo(BigDecimal.ZERO) > 0) {
                    log.debug("Resolved effective authorization limit for procurement [{}] from APPROVED approval: ₹{}",
                            request.getId(), approvedAmount);
                    return approvedAmount.setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        // 2. Resolve strictly from authenticated user entity (fail closed)
        if (request.getUser() == null) {
            log.error("Cannot resolve authorization limit for procurement [{}]: request has no associated user", request.getId());
            throw new IllegalStateException("Cannot determine authorization user for procurement " + request.getId());
        }

        BigDecimal userLimit = request.getUser().getAuthorizationLimit();
        if (userLimit == null) {
            log.error("Cannot resolve authorization limit for procurement [{}]: user [{}] has no authorization limit configured",
                    request.getId(), request.getUser().getEmail());
            throw new IllegalStateException("User authorization limit is not configured for user: " + request.getUser().getEmail());
        }

        if (userLimit.compareTo(BigDecimal.ZERO) < 0) {
            log.error("Invalid negative authorization limit [{}] for user [{}]", userLimit, request.getUser().getEmail());
            throw new IllegalStateException("User authorization limit cannot be negative for user: " + request.getUser().getEmail());
        }

        return userLimit.setScale(2, RoundingMode.HALF_UP);
    }
}
