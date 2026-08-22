package com.procurement.engine.vendor.repository;

import com.procurement.engine.vendor.entity.Vendor;
import com.procurement.engine.vendor.entity.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    List<Vendor> findByStatus(VendorStatus status);
    Optional<Vendor> findByName(String name);
}
