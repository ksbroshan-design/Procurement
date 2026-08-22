package com.procurement.engine.product.repository;

import com.procurement.engine.product.entity.ReliabilityHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReliabilityHistoryRepository extends JpaRepository<ReliabilityHistory, UUID> {
    Optional<ReliabilityHistory> findTopByProductIdOrderByRecordedAtDesc(UUID productId);
}
