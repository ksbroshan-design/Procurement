package com.procurement.engine.seed;

import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.product.repository.ReliabilityHistoryRepository;
import com.procurement.engine.user.entity.Role;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import com.procurement.engine.vendor.entity.Vendor;
import com.procurement.engine.vendor.entity.VendorStatus;
import com.procurement.engine.vendor.repository.VendorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseDataSeederTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReliabilityHistoryRepository reliabilityHistoryRepository;

    @Test
    @DisplayName("Verify seed users are correctly populated with roles and authorization limits")
    void verifySeedUsers() {
        Optional<User> managerOpt = userRepository.findByEmail("manager@procurement.com");
        assertThat(managerOpt).isPresent();
        User manager = managerOpt.get();
        assertThat(manager.getRole()).isEqualTo(Role.PROCUREMENT_MANAGER);
        assertThat(manager.getAuthorizationLimit()).isEqualByComparingTo(new BigDecimal("450000.00"));

        Optional<User> adminOpt = userRepository.findByEmail("admin@procurement.com");
        assertThat(adminOpt).isPresent();
        User admin = adminOpt.get();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getAuthorizationLimit()).isEqualByComparingTo(new BigDecimal("5000000.00"));
    }

    @Test
    @DisplayName("Verify seed vendors are created with ratings, return policies, and status")
    void verifySeedVendors() {
        List<Vendor> vendors = vendorRepository.findAll();
        assertThat(vendors).hasSizeGreaterThanOrEqualTo(4);

        Optional<Vendor> activeVendor = vendorRepository.findByName("TechDirect Enterprises");
        assertThat(activeVendor).isPresent();
        assertThat(activeVendor.get().getStatus()).isEqualTo(VendorStatus.ACTIVE);
        assertThat(activeVendor.get().getSellerRating()).isGreaterThan(BigDecimal.ZERO);

        Optional<Vendor> unavailableVendor = vendorRepository.findByName("PrimeGoods Distribution (Suspended)");
        assertThat(unavailableVendor).isPresent();
        assertThat(unavailableVendor.get().getStatus()).isEqualTo(VendorStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("Verify 36 products across 6 categories (TV, Tablet, Laptop, Monitor, Chair, Keyboard)")
    void verifySeedProductsAcrossSixCategories() {
        List<Product> products = productRepository.findAll();
        assertThat(products).hasSizeGreaterThanOrEqualTo(36);

        List<Product> tvs = productRepository.findByCategoryIgnoreCase("TV");
        assertThat(tvs).hasSize(6);

        List<Product> tablets = productRepository.findByCategoryIgnoreCase("Tablet");
        assertThat(tablets).hasSize(6);

        List<Product> laptops = productRepository.findByCategoryIgnoreCase("Laptop");
        assertThat(laptops).hasSize(6);

        List<Product> monitors = productRepository.findByCategoryIgnoreCase("Monitor");
        assertThat(monitors).hasSize(6);

        List<Product> chairs = productRepository.findByCategoryIgnoreCase("Office chair");
        assertThat(chairs).hasSize(6);

        List<Product> keyboards = productRepository.findByCategoryIgnoreCase("Keyboard");
        assertThat(keyboards).hasSize(6);
    }

    @Test
    @DisplayName("Verify product JSONB specifications are correctly loaded and accessible")
    void verifyDynamicJsonbSpecifications() {
        List<Product> tvs = productRepository.findByCategoryIgnoreCase("TV");
        Product lgOled = tvs.stream()
                .filter(p -> p.getName().contains("LG C3"))
                .findFirst()
                .orElseThrow();

        assertThat(lgOled.getSpecifications()).isNotEmpty();
        assertThat(lgOled.getSpecifications().get("panelType")).isEqualTo("OLED");
        assertThat(lgOled.getSpecifications().get("resolution")).isEqualTo("4K");
        assertThat(lgOled.getSpecifications().get("screenSize")).isEqualTo(55);

        List<Product> chairs = productRepository.findByCategoryIgnoreCase("Office chair");
        Product aeron = chairs.stream()
                .filter(p -> p.getName().contains("Aeron"))
                .findFirst()
                .orElseThrow();

        assertThat(aeron.getSpecifications().get("material")).isEqualTo("Mesh");
        assertThat(aeron.getSpecifications().get("lumbarSupport")).isEqualTo(true);
    }

    @Test
    @DisplayName("Verify historical reliability records exist for all seeded products")
    void verifyReliabilityHistory() {
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            assertThat(reliabilityHistoryRepository.findTopByProductIdOrderByRecordedAtDesc(product.getId()))
                    .as("Reliability history for product: " + product.getName())
                    .isPresent();
        }
    }
}
