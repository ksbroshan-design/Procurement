package com.procurement.engine.approval.repository;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
    List<Approval> findByStatus(ApprovalStatus status);
    List<Approval> findByProcurementId(UUID procurementId);
    Optional<Approval> findTopByProcurementIdOrderByRequestedAtDesc(UUID procurementId);
}
