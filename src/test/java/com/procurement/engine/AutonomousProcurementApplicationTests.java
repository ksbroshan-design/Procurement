package com.procurement.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AutonomousProcurementApplicationTests {

    @Test
    @DisplayName("Verify Spring context loads successfully with H2/PostgreSQL mode and all beans")
    void contextLoads() {
    }
}
