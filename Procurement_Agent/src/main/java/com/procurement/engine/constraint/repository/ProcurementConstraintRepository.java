package com.procurement.engine.constraint.repository;

import com.procurement.engine.constraint.entity.ProcurementConstraint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcurementConstraintRepository extends JpaRepository<ProcurementConstraint, UUID> {
    List<ProcurementConstraint> findByProcurementId(UUID procurementId);
}
