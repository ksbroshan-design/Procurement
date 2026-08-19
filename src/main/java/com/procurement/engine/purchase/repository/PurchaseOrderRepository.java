package com.procurement.engine.purchase.repository;

import com.procurement.engine.purchase.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    List<PurchaseOrder> findByProcurementId(UUID procurementId);
    Optional<PurchaseOrder> findTopByProcurementIdOrderByCreatedAtDesc(UUID procurementId);
    boolean existsByProcurementId(UUID procurementId);
}
