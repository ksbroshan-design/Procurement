package com.procurement.engine.procurement.repository;

import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.VendorOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorOfferRepository extends JpaRepository<VendorOffer, UUID> {
    List<VendorOffer> findByProcurementId(UUID procurementId);
    List<VendorOffer> findByProcurementIdAndStatus(UUID procurementId, OfferStatus status);
    Optional<VendorOffer> findTopByProcurementIdAndStatusOrderByTcoAsc(UUID procurementId, OfferStatus status);
}
