package com.hospital.backend.service;

import com.hospital.backend.dto.lab.*;
import com.hospital.backend.enums.*;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.impl.LabServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabServiceTest {

    @Mock
    private LabTestDefinitionRepository testDefinitionRepository;
    @Mock
    private LabOrderRepository labOrderRepository;
    @Mock
    private LabSpecimenRepository labSpecimenRepository;
    @Mock
    private LabResultRepository labResultRepository;
    @Mock
    private ClinicalEncounterRepository encounterRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private AuthorizationService authz;
    @Mock
    private ClinicalAuditService auditService;
    @Mock
    private LabPdfService labPdfService;

    @InjectMocks
    private LabServiceImpl labService;

    private User doctorUser;
    private User patientUser;
    private User labTechUser;
    private LabTestDefinition activeTest;

    @BeforeEach
    void setUp() {
        doctorUser = User.builder()
                .id("doc-user-1")
                .fullName("Dr. Gregory House")
                .email("doctor@hospital.org")
                .role(Role.DOCTOR)
                .build();

        patientUser = User.builder()
                .id("pat-user-1")
                .fullName("John Doe")
                .email("patient@test.com")
                .role(Role.PATIENT)
                .build();

        labTechUser = User.builder()
                .id("tech-user-1")
                .fullName("Jane Tech")
                .email("tech@hospital.org")
                .role(Role.LAB_TECH)
                .build();

        activeTest = LabTestDefinition.builder()
                .id("test-def-1")
                .testCode("CBC")
                .testName("Complete Blood Count")
                .category("Hematology")
                .specimenType("Whole Blood")
                .defaultUnit("10^3/uL")
                .referenceRangeMetadata("4.5 - 11.0")
                .active(true)
                .build();
    }

    // =========================================================================
    // GROUP A: Test Catalog
    // =========================================================================

    @Test
    @DisplayName("A1: Create lab test definition succeeds")
    void createTestDefinition_Succeeds() {
        CreateLabTestRequest req = CreateLabTestRequest.builder()
                .testCode("GLUC")
                .testName("Fasting Blood Glucose")
                .category("Biochemistry")
                .specimenType("Serum")
                .defaultUnit("mg/dL")
                .referenceRangeMetadata("70 - 99 mg/dL")
                .build();

        when(testDefinitionRepository.existsByTestCode("GLUC")).thenReturn(false);
        when(testDefinitionRepository.save(any(LabTestDefinition.class))).thenAnswer(i -> i.getArgument(0));

        LabTestDefinition created = labService.createTestDefinition(req);
        assertNotNull(created);
        assertEquals("GLUC", created.getTestCode());
        assertTrue(created.isActive());
        verify(testDefinitionRepository).save(any(LabTestDefinition.class));
    }

    @Test
    @DisplayName("A2: Duplicate test code is rejected")
    void createTestDefinition_DuplicateCode_ThrowsBadRequest() {
        CreateLabTestRequest req = CreateLabTestRequest.builder()
                .testCode("CBC")
                .testName("Complete Blood Count")
                .category("Hematology")
                .specimenType("Blood")
                .build();

        when(testDefinitionRepository.existsByTestCode("CBC")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> labService.createTestDefinition(req));
    }

    @Test
    @DisplayName("A3: Update lab test definition succeeds")
    void updateTestDefinition_Succeeds() {
        when(testDefinitionRepository.findById("test-def-1")).thenReturn(Optional.of(activeTest));
        when(testDefinitionRepository.save(any(LabTestDefinition.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLabTestRequest req = UpdateLabTestRequest.builder()
                .testName("Complete Blood Count with Differential")
                .build();

        LabTestDefinition updated = labService.updateTestDefinition("test-def-1", req);
        assertEquals("Complete Blood Count with Differential", updated.getTestName());
    }

    @Test
    @DisplayName("A4: Deactivate lab test definition marks active false")
    void deactivateTestDefinition_Succeeds() {
        when(testDefinitionRepository.findById("test-def-1")).thenReturn(Optional.of(activeTest));
        when(testDefinitionRepository.save(any(LabTestDefinition.class))).thenAnswer(i -> i.getArgument(0));

        labService.deactivateTestDefinition("test-def-1");
        assertFalse(activeTest.isActive());
    }

    @Test
    @DisplayName("A5: Inactive test cannot be ordered")
    void createOrder_InactiveTest_ThrowsBadRequest() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(authz.canCreateLabOrder(anyString(), anyString())).thenReturn(true);
        when(patientRepository.existsById("pat-user-1")).thenReturn(true);

        activeTest.setActive(false);
        when(testDefinitionRepository.findByTestCode("CBC")).thenReturn(Optional.of(activeTest));

        CreateLabOrderRequest req = CreateLabOrderRequest.builder()
                .patientId("pat-user-1")
                .testCodes(List.of("CBC"))
                .build();

        assertThrows(BadRequestException.class, () -> labService.createOrder(req));
    }

    // =========================================================================
    // GROUP B: Lab Orders
    // =========================================================================

    @Test
    @DisplayName("B1: Authorized doctor creates lab order succeeds")
    void createOrder_AuthorizedDoctor_Succeeds() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(authz.canCreateLabOrder(anyString(), eq("pat-user-1"))).thenReturn(true);
        when(patientRepository.existsById("pat-user-1")).thenReturn(true);
        when(testDefinitionRepository.findByTestCode("CBC")).thenReturn(Optional.of(activeTest));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(i -> {
            LabOrder o = i.getArgument(0);
            o.setId("ord-123");
            return o;
        });

        CreateLabOrderRequest req = CreateLabOrderRequest.builder()
                .patientId("pat-user-1")
                .testCodes(List.of("CBC"))
                .priority(OrderPriority.ROUTINE)
                .clinicalNotes("Routine checkup")
                .build();

        LabOrder order = labService.createOrder(req);
        assertNotNull(order);
        assertNotNull(order.getOrderNumber());
        assertTrue(order.getOrderNumber().startsWith("ORD-"));
        assertEquals(LabOrderStatus.ORDERED, order.getStatus());
        assertEquals(1, order.getOrderedTests().size());
        verify(outboxRepository).save(any(NotificationOutbox.class));
        verify(auditService).audit(eq("pat-user-1"), eq("LabOrder"), eq("ord-123"), eq(ClinicalAuditAction.LAB_ORDER_CREATED), eq("SUCCESS"), any(), any());
    }

    @Test
    @DisplayName("B2: Unrelated doctor or unauthorized user denied order creation")
    void createOrder_UnauthorizedUser_ThrowsAccessDenied() {
        when(authz.getCurrentUser()).thenReturn(patientUser);
        when(authz.canCreateLabOrder(anyString(), eq("pat-user-1"))).thenReturn(false);

        CreateLabOrderRequest req = CreateLabOrderRequest.builder()
                .patientId("pat-user-1")
                .testCodes(List.of("CBC"))
                .build();

        assertThrows(AccessDeniedException.class, () -> labService.createOrder(req));
    }

    @Test
    @DisplayName("B3: Invalid cancellation of completed/released order denied")
    void cancelOrder_AlreadyCompletedOrReleased_ThrowsBadRequest() {
        LabOrder order = LabOrder.builder()
                .id("ord-1")
                .patientId("pat-1")
                .doctorId("doc-1")
                .status(LabOrderStatus.COMPLETED)
                .build();

        when(labOrderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(authz.canCancelLabOrder(order)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> labService.cancelOrder("ord-1", "Doctor mistake"));
    }

    // =========================================================================
    // GROUP C: Specimens
    // =========================================================================

    @Test
    @DisplayName("C1: Specimen collection recorded with timestamp and collector")
    void collectSpecimen_Succeeds() {
        when(authz.getCurrentUser()).thenReturn(labTechUser);
        when(authz.canEnterLabResult()).thenReturn(true);

        LabOrder order = LabOrder.builder()
                .id("ord-1")
                .patientId("pat-1")
                .status(LabOrderStatus.ORDERED)
                .build();
        when(labOrderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(labSpecimenRepository.save(any(LabSpecimen.class))).thenAnswer(i -> {
            LabSpecimen s = i.getArgument(0);
            s.setId("spec-1");
            return s;
        });

        CollectSpecimenRequest req = CollectSpecimenRequest.builder()
                .specimenType("Blood")
                .notes("Collected without complications")
                .build();

        LabSpecimen specimen = labService.collectSpecimen("ord-1", req);
        assertNotNull(specimen);
        assertEquals(SpecimenStatus.COLLECTED, specimen.getCollectionStatus());
        assertNotNull(specimen.getCollectedAt());
        assertEquals("tech-user-1", specimen.getCollectedByUserId());
        assertEquals(LabOrderStatus.IN_PROGRESS, order.getStatus());
    }

    @Test
    @DisplayName("C2: Specimen receipt recorded")
    void receiveSpecimen_Succeeds() {
        when(authz.getCurrentUser()).thenReturn(labTechUser);
        when(authz.canEnterLabResult()).thenReturn(true);

        LabSpecimen specimen = LabSpecimen.builder()
                .id("spec-1")
                .patientId("pat-1")
                .collectionStatus(SpecimenStatus.COLLECTED)
                .build();
        when(labSpecimenRepository.findById("spec-1")).thenReturn(Optional.of(specimen));
        when(labSpecimenRepository.save(any(LabSpecimen.class))).thenAnswer(i -> i.getArgument(0));

        LabSpecimen received = labService.receiveSpecimen("spec-1");
        assertEquals(SpecimenStatus.RECEIVED, received.getCollectionStatus());
        assertNotNull(received.getReceivedAt());
    }

    @Test
    @DisplayName("C3: Specimen rejection recorded with reason")
    void rejectSpecimen_Succeeds() {
        when(authz.getCurrentUser()).thenReturn(labTechUser);
        when(authz.canEnterLabResult()).thenReturn(true);

        LabSpecimen specimen = LabSpecimen.builder()
                .id("spec-1")
                .patientId("pat-1")
                .collectionStatus(SpecimenStatus.RECEIVED)
                .build();
        when(labSpecimenRepository.findById("spec-1")).thenReturn(Optional.of(specimen));
        when(labSpecimenRepository.save(any(LabSpecimen.class))).thenAnswer(i -> i.getArgument(0));

        RejectSpecimenRequest req = RejectSpecimenRequest.builder()
                .rejectionReason("Hemolyzed sample")
                .build();

        LabSpecimen rejected = labService.rejectSpecimen("spec-1", req);
        assertEquals(SpecimenStatus.REJECTED, rejected.getCollectionStatus());
        assertEquals("Hemolyzed sample", rejected.getRejectionReason());
    }

    // =========================================================================
    // GROUP D: Results Lifecycle & Verification
    // =========================================================================

    @Test
    @DisplayName("D1: Enter numeric result succeeds")
    void enterResult_Numeric_Succeeds() {
        when(authz.getCurrentUser()).thenReturn(labTechUser);
        when(authz.canEnterLabResult()).thenReturn(true);

        LabOrder order = LabOrder.builder()
                .id("ord-1")
                .patientId("pat-1")
                .status(LabOrderStatus.IN_PROGRESS)
                .orderedTests(List.of(LabOrder.OrderedTestItem.builder()
                        .testCode("CBC")
                        .testName("Complete Blood Count")
                        .defaultUnit("10^3/uL")
                        .build()))
                .build();
        when(labOrderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(labResultRepository.findByLabOrderIdAndTestCode("ord-1", "CBC")).thenReturn(Optional.empty());
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(i -> {
            LabResult r = i.getArgument(0);
            r.setId("res-1");
            return r;
        });

        EnterLabResultRequest req = EnterLabResultRequest.builder()
                .testCode("CBC")
                .resultType("NUMERIC")
                .numericValue(7.5)
                .unit("10^3/uL")
                .interpretation(LabResultInterpretation.NORMAL)
                .saveAsDraft(false)
                .build();

        LabResult result = labService.enterResult("ord-1", req);
        assertNotNull(result);
        assertEquals(LabResultWorkflowStatus.ENTERED, result.getWorkflowStatus());
        assertEquals(7.5, result.getNumericValue());
        assertEquals(LabResultInterpretation.NORMAL, result.getInterpretation());
    }

    @Test
    @DisplayName("D2: Unverified release is rejected")
    void releaseResult_Unverified_ThrowsBadRequest() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(authz.canVerifyOrReleaseLabResult()).thenReturn(true);

        LabResult result = LabResult.builder()
                .id("res-1")
                .workflowStatus(LabResultWorkflowStatus.ENTERED) // Not verified yet
                .build();
        when(labResultRepository.findById("res-1")).thenReturn(Optional.of(result));

        assertThrows(BadRequestException.class, () -> labService.releaseResult("res-1"));
    }

    @Test
    @DisplayName("D3: Verification and Release succeeds")
    void verifyAndReleaseResult_Succeeds() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(authz.canVerifyOrReleaseLabResult()).thenReturn(true);

        LabResult result = LabResult.builder()
                .id("res-1")
                .patientId("pat-1")
                .testCode("CBC")
                .testName("Complete Blood Count")
                .workflowStatus(LabResultWorkflowStatus.ENTERED)
                .build();
        when(labResultRepository.findById("res-1")).thenReturn(Optional.of(result));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(i -> i.getArgument(0));

        // 1. Verify
        LabResult verified = labService.verifyResult("res-1", new VerifyLabResultRequest("Clinically consistent"));
        assertEquals(LabResultWorkflowStatus.VERIFIED, verified.getWorkflowStatus());
        assertEquals("doc-user-1", verified.getVerifiedByUserId());

        // 2. Release
        LabResult released = labService.releaseResult("res-1");
        assertEquals(LabResultWorkflowStatus.RELEASED, released.getWorkflowStatus());
        assertNotNull(released.getReleasedAt());
        verify(outboxRepository).save(any(NotificationOutbox.class));
    }

    @Test
    @DisplayName("D4: Lab technician cannot verify or release results")
    void verifyResult_LabTech_ThrowsAccessDenied() {
        when(authz.getCurrentUser()).thenReturn(labTechUser);
        when(authz.canVerifyOrReleaseLabResult()).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> labService.verifyResult("res-1", null));
        assertThrows(AccessDeniedException.class, () -> labService.releaseResult("res-1"));
    }

    @Test
    @DisplayName("D5: Released result correction creates new revision and archives old")
    void correctResult_CreatesNewRevision_Succeeds() {
        when(authz.getCurrentUser()).thenReturn(doctorUser);
        when(authz.canVerifyOrReleaseLabResult()).thenReturn(true);

        LabResult original = LabResult.builder()
                .id("res-1")
                .patientId("pat-1")
                .doctorId("doc-1")
                .testCode("CBC")
                .testName("Complete Blood Count")
                .resultType("NUMERIC")
                .numericValue(7.5)
                .revision(1)
                .workflowStatus(LabResultWorkflowStatus.RELEASED)
                .build();

        when(labResultRepository.findById("res-1")).thenReturn(Optional.of(original));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(i -> i.getArgument(0));

        CorrectLabResultRequest req = CorrectLabResultRequest.builder()
                .correctionReason("Laboratory dilution calculation adjustment")
                .numericValue(8.2)
                .interpretation(LabResultInterpretation.NORMAL)
                .build();

        LabResult amended = labService.correctResult("res-1", req);

        // Original was marked CORRECTED
        assertEquals(LabResultWorkflowStatus.CORRECTED, original.getWorkflowStatus());

        // New revision is created
        assertNotNull(amended);
        assertEquals(2, amended.getRevision());
        assertEquals("res-1", amended.getCorrectedResultId());
        assertEquals("Laboratory dilution calculation adjustment", amended.getCorrectionReason());
        assertEquals(8.2, amended.getNumericValue());
        // New revision requires re-verification before release!
        assertEquals(LabResultWorkflowStatus.ENTERED, amended.getWorkflowStatus());
    }

    // =========================================================================
    // GROUP F: Integration & PDF
    // =========================================================================

    @Test
    @DisplayName("F1: Patient cannot download PDF for unreleased result")
    void generateReportPdf_PatientUnreleased_ThrowsAccessDenied() {
        LabResult result = LabResult.builder()
                .id("res-1")
                .patientId("pat-1")
                .workflowStatus(LabResultWorkflowStatus.ENTERED)
                .build();

        when(labResultRepository.findById("res-1")).thenReturn(Optional.of(result));
        when(authz.getCurrentUser()).thenReturn(patientUser);

        assertThrows(AccessDeniedException.class, () -> labService.generateReportPdf("res-1"));
    }

    @Test
    @DisplayName("F2: Authorized PDF download succeeds for released result")
    void generateReportPdf_Released_Succeeds() {
        LabResult result = LabResult.builder()
                .id("res-1")
                .patientId("pat-1")
                .doctorId("doc-1")
                .workflowStatus(LabResultWorkflowStatus.RELEASED)
                .build();

        when(labResultRepository.findById("res-1")).thenReturn(Optional.of(result));
        when(authz.getCurrentUser()).thenReturn(patientUser);
        when(labPdfService.generateLabReportPdf(any(), any(), any(), any(), any(), any()))
                .thenReturn(new byte[]{1, 2, 3});

        byte[] pdf = labService.generateReportPdf("res-1");
        assertNotNull(pdf);
        assertEquals(3, pdf.length);
        verify(auditService).audit(eq("pat-1"), eq("LabResult"), eq("res-1"), eq(ClinicalAuditAction.LAB_RESULT_DOWNLOADED), eq("SUCCESS"), any(), any());
    }
}
