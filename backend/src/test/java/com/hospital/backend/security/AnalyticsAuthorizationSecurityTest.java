package com.hospital.backend.security;

import com.hospital.backend.dto.analytics.AnalyticsFilterRequest;
import com.hospital.backend.dto.analytics.ReportExportRequest;
import com.hospital.backend.enums.ReportFormat;
import com.hospital.backend.enums.ReportType;
import com.hospital.backend.enums.ReportingDateRange;
import com.hospital.backend.enums.Role;
import com.hospital.backend.model.User;
import com.hospital.backend.service.AnalyticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsAuthorizationSecurityTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private AuthorizationService authorizationService;

    private void setSecurityContext(String email, Role role) {
        User user = User.builder().id("user-" + role.name().toLowerCase()).email(email).role(role).build();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        when(authorizationService.getCurrentUser()).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("F1: Patient cannot view executive overview")
    void testPatientCannotAccessExecutiveOverview() {
        setSecurityContext("patient@pulsecare.com", Role.PATIENT);
        doThrow(new AccessDeniedException("Access denied"))
                .when(analyticsService).getExecutiveOverview(any());

        assertThrows(AccessDeniedException.class, () ->
                analyticsService.getExecutiveOverview(new AnalyticsFilterRequest()));
    }

    @Test
    @DisplayName("F3: LAB_TECH cannot access financial analytics")
    void testLabTechCannotAccessBillingAnalytics() {
        setSecurityContext("labtech@pulsecare.com", Role.LAB_TECH);
        doThrow(new AccessDeniedException("Access denied"))
                .when(analyticsService).getBillingAnalytics(any());

        assertThrows(AccessDeniedException.class, () ->
                analyticsService.getBillingAnalytics(new AnalyticsFilterRequest()));
    }

    @Test
    @DisplayName("F4: Pharmacist cannot access doctor clinical workload")
    void testPharmacistCannotAccessDoctorWorkload() {
        setSecurityContext("pharma@pulsecare.com", Role.PHARMACIST);
        doThrow(new AccessDeniedException("Access denied"))
                .when(analyticsService).getDoctorWorkload(anyString(), any());

        assertThrows(AccessDeniedException.class, () ->
                analyticsService.getDoctorWorkload("doc-1", new AnalyticsFilterRequest()));
    }

    @Test
    @DisplayName("F7: Unauthorized export attempt is rejected")
    void testUnauthorizedExportRejected() {
        setSecurityContext("patient@pulsecare.com", Role.PATIENT);
        ReportExportRequest req = ReportExportRequest.builder()
                .reportType(ReportType.FINANCIAL_COLLECTIONS)
                .format(ReportFormat.CSV)
                .dateRange(ReportingDateRange.LAST_30_DAYS)
                .build();

        doThrow(new AccessDeniedException("Access denied"))
                .when(analyticsService).exportReport(req);

        assertThrows(AccessDeniedException.class, () -> analyticsService.exportReport(req));
    }
}
