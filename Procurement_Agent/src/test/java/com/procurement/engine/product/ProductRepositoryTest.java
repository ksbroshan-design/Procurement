package com.procurement.engine.product;

import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Verify searching available products by category with quantity constraint")
    void testFindAvailableByCategoryAndMinQuantity() {
        // Query for available TVs with quantity >= 5
        List<Product> availableTvs = productRepository.findAvailableByCategoryAndMinQuantity("TV", 5);
        assertThat(availableTvs).isNotEmpty();
        for (Product tv : availableTvs) {
            assertThat(tv.isAvailability()).isTrue();
            assertThat(tv.getAvailableQuantity()).isGreaterThanOrEqualTo(5);
            assertThat(tv.getCategory()).isEqualToIgnoringCase("TV");
        }
    }
}
