package com.procurement.engine.procurement.repository;

import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.statemachine.ProcurementState;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcurementRequestRepository extends JpaRepository<ProcurementRequest, UUID> {
    List<ProcurementRequest> findByUserId(UUID userId);
    List<ProcurementRequest> findByStatus(ProcurementState status);

    @EntityGraph(attributePaths = {"constraints", "selectedProduct", "selectedOffer", "selectedOffer.vendor", "selectedOffer.product"})
    @Query("SELECT r FROM ProcurementRequest r WHERE r.id = :id")
    Optional<ProcurementRequest> findByIdWithDetails(@Param("id") UUID id);
}
