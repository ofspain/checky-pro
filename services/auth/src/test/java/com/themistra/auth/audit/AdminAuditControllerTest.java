package com.themistra.auth.audit;

import com.themistra.auth.audit.dto.AuditEventResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditControllerTest {

    @Mock
    private AuditService auditService;

    @Test
    void listPassesAccountFilterAndPageableThrough() {
        AdminAuditController controller = new AdminAuditController(auditService);
        UUID accountUuid = UUID.randomUUID();
        var expected = new PageImpl<>(List.of(new AuditEventResponse(
                1L, Instant.now(), "account.suspended", AuditOutcome.SUCCESS,
                accountUuid, null, null, null, null, Map.of())));
        when(auditService.list(eq(accountUuid), any())).thenReturn(expected);

        var response = controller.list(accountUuid, PageRequest.of(0, 50));

        assertThat(response).isEqualTo(expected);
        verify(auditService).list(eq(accountUuid), any());
    }

    @Test
    void listWithNoAccountFilterPassesNullThrough() {
        AdminAuditController controller = new AdminAuditController(auditService);
        when(auditService.list(eq(null), any())).thenReturn(new PageImpl<>(List.of()));

        controller.list(null, PageRequest.of(0, 50));

        verify(auditService).list(eq(null), any());
    }
}
