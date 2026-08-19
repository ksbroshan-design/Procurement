package com.procurement.engine.product.repository;

import com.procurement.engine.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByCategoryIgnoreCase(String category);

    List<Product> findByCategoryIgnoreCaseAndAvailabilityTrue(String category);

    @Query("SELECT p FROM Product p WHERE LOWER(p.category) = LOWER(:category) AND p.availability = true AND p.availableQuantity >= :minQuantity")
    List<Product> findAvailableByCategoryAndMinQuantity(@Param("category") String category, @Param("minQuantity") int minQuantity);

    List<Product> findByVendorId(UUID vendorId);
}
