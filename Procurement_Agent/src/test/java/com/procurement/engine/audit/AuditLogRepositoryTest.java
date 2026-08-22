package com.procurement.engine.audit;

import com.procurement.engine.audit.entity.AuditEventType;
import com.procurement.engine.audit.entity.AuditLog;
import com.procurement.engine.audit.repository.AuditLogRepository;
import com.procurement.engine.statemachine.ProcurementState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Verify audit log persistence and dynamic JSONB metadata retrieval")
    void testAuditLogPersistenceAndJsonbMetadata() {
        UUID procurementId = UUID.randomUUID();

        AuditLog log1 = AuditLog.builder()
                .procurementId(procurementId)
                .eventType(AuditEventType.REQUEST_CREATED)
                .state(ProcurementState.SUBMITTED)
                .actor("manager@procurement.com")
                .description("Procurement request created for 5 TVs")
                .metadata(Map.of("quantity", 5, "category", "TV", "authorizationLimit", 350000))
                .build();

        auditLogRepository.save(log1);

        AuditLog log2 = AuditLog.builder()
                .procurementId(procurementId)
                .eventType(AuditEventType.VENDOR_SEARCH_COMPLETED)
                .state(ProcurementState.EVALUATING)
                .actor("SYSTEM")
                .description("Discovered 6 matching vendor products")
                .metadata(Map.of("discoveredCount", 6, "vendorsContacted", List.of("TechDirect", "MegaRetail", "GlobalEquip")))
                .build();

        auditLogRepository.save(log2);

        List<AuditLog> logs = auditLogRepository.findByProcurementIdOrderByTimestampAsc(procurementId);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getEventType()).isEqualTo(AuditEventType.REQUEST_CREATED);
        assertThat(logs.get(0).getMetadata()).containsEntry("category", "TV");
        assertThat(logs.get(1).getEventType()).isEqualTo(AuditEventType.VENDOR_SEARCH_COMPLETED);
    }
}
