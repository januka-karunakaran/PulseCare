package com.hospital.backend.service;

import com.hospital.backend.dto.pharmacy.*;
import com.hospital.backend.enums.*;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.exception.DuplicateResourceException;
import com.hospital.backend.exception.InsufficientStockException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.impl.PharmacyServiceImpl;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PharmacyServiceTest {

    @Mock
    private PharmacyMedicineRepository medicineRepository;
    @Mock
    private PharmacyStockBatchRepository batchRepository;
    @Mock
    private PharmacyDispenseRepository dispenseRepository;
    @Mock
    private PharmacyStockMovementRepository movementRepository;
    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private AuthorizationService authz;
    @Mock
    private ClinicalAuditService auditService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PharmacyServiceImpl pharmacyService;

    private User pharmacistUser;
    private PharmacyMedicine amoxicillin;

    @BeforeEach
    void setUp() {
        pharmacistUser = User.builder()
                .id("pharm-user-1")
                .fullName("Alex Morgan, RPh")
                .email("pharmacist@hospital.org")
                .role(Role.PHARMACIST)
                .build();

        amoxicillin = PharmacyMedicine.builder()
                .id("med-1")
                .medicineCode("MED-AMOX-500")
                .genericName("Amoxicillin")
                .brandName("Amoxil")
                .strength("500mg")
                .dosageForm(DosageForm.CAPSULE)
                .dispensingUnit("capsule")
                .active(true)
                .reorderLevel(30.0)
                .build();
    }

    // =========================================================================
    // 1. Catalog Tests
    // =========================================================================

    @Test
    @DisplayName("createMedicine successfully saves and audits new medicine")
    void testCreateMedicine_Success() {
        when(authz.canManagePharmacyCatalog()).thenReturn(true);
        when(medicineRepository.findByMedicineCode("MED-AMOX-500")).thenReturn(Optional.empty());
        when(medicineRepository.save(any(PharmacyMedicine.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMedicineRequest req = CreateMedicineRequest.builder()
                .medicineCode("MED-AMOX-500")
                .genericName("Amoxicillin")
                .strength("500mg")
                .dosageForm(DosageForm.CAPSULE)
                .dispensingUnit("capsule")
                .reorderLevel(25.0)
                .build();

        PharmacyMedicine created = pharmacyService.createMedicine(req);

        assertNotNull(created);
        assertEquals("MED-AMOX-500", created.getMedicineCode());
        assertTrue(created.isActive());
        verify(medicineRepository).save(any(PharmacyMedicine.class));
        verify(auditService).audit(isNull(), eq("PharmacyMedicine"), any(), eq(ClinicalAuditAction.PHARMACY_MEDICINE_CREATED), eq("SUCCESS"), isNull(), anyString());
    }

    @Test
    @DisplayName("createMedicine throws DuplicateResourceException if code already exists")
    void testCreateMedicine_DuplicateCode() {
        when(authz.canManagePharmacyCatalog()).thenReturn(true);
        when(medicineRepository.findByMedicineCode("MED-AMOX-500")).thenReturn(Optional.of(amoxicillin));

        CreateMedicineRequest req = CreateMedicineRequest.builder()
                .medicineCode("MED-AMOX-500")
                .genericName("Amoxicillin")
                .strength("500mg")
                .dosageForm(DosageForm.CAPSULE)
                .dispensingUnit("capsule")
                .build();

        assertThrows(DuplicateResourceException.class, () -> pharmacyService.createMedicine(req));
        verify(medicineRepository, never()).save(any());
    }

    // =========================================================================
    // 2. Stock Receipt & Adjustment Tests
    // =========================================================================

    @Test
    @DisplayName("receiveStock creates new batch and records append-only movement ledger")
    void testReceiveStock_Success() {
        when(authz.canManagePharmacyInventory()).thenReturn(true);
        when(authz.getCurrentUser()).thenReturn(pharmacistUser);
        when(medicineRepository.findById("med-1")).thenReturn(Optional.of(amoxicillin));
        when(batchRepository.findByMedicineIdAndBatchNumber("med-1", "LOT-9921")).thenReturn(Optional.empty());
        when(batchRepository.save(any(PharmacyStockBatch.class))).thenAnswer(inv -> {
            PharmacyStockBatch b = inv.getArgument(0);
            b.setId("batch-1");
            return b;
        });

        ReceiveStockRequest req = ReceiveStockRequest.builder()
                .medicineId("med-1")
                .batchNumber("LOT-9921")
                .quantityReceived(100.0)
                .stockUnit("capsule")
                .expiryDate(Instant.now().plus(365, ChronoUnit.DAYS))
                .storageLocation("Aisle 2, Bin 4")
                .build();

        PharmacyStockBatch batch = pharmacyService.receiveStock(req);

        assertNotNull(batch);
        assertEquals("LOT-9921", batch.getBatchNumber());
        assertEquals(100.0, batch.getQuantityAvailable());
        assertEquals(BatchStatus.AVAILABLE, batch.getStatus());

        verify(batchRepository).save(any(PharmacyStockBatch.class));
        verify(movementRepository).save(argThat(m ->
                m.getMovementType() == StockMovementType.STOCK_RECEIVED &&
                m.getQuantity() == 100.0 &&
                "med-1".equals(m.getMedicineId())
        ));
    }

    @Test
    @DisplayName("receiveStock returns existing batch when idempotencyKey matches")
    void testReceiveStock_Idempotency() {
        when(authz.canManagePharmacyInventory()).thenReturn(true);
        PharmacyStockBatch existing = PharmacyStockBatch.builder()
                .id("batch-existing")
                .idempotencyKey("idem-key-1")
                .batchNumber("LOT-EXISTING")
                .build();
        when(batchRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(existing));

        ReceiveStockRequest req = ReceiveStockRequest.builder()
                .medicineId("med-1")
                .idempotencyKey("idem-key-1")
                .build();

        PharmacyStockBatch batch = pharmacyService.receiveStock(req);
        assertEquals("batch-existing", batch.getId());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock updates quantity and marks status DEPLETED when quantity reaches zero")
    void testAdjustStock_Depleted() {
        when(authz.canManagePharmacyInventory()).thenReturn(true);
        when(authz.getCurrentUser()).thenReturn(pharmacistUser);

        PharmacyStockBatch batch = PharmacyStockBatch.builder()
                .id("batch-1")
                .medicineId("med-1")
                .batchNumber("LOT-1")
                .quantityAvailable(20.0)
                .stockUnit("capsule")
                .status(BatchStatus.AVAILABLE)
                .build();
        when(batchRepository.findById("batch-1")).thenReturn(Optional.of(batch));
        when(batchRepository.save(any(PharmacyStockBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        AdjustStockRequest req = AdjustStockRequest.builder()
                .batchId("batch-1")
                .adjustedQuantity(0.0)
                .reasonCode("DAMAGED")
                .build();

        PharmacyStockBatch adjusted = pharmacyService.adjustStock(req);

        assertEquals(0.0, adjusted.getQuantityAvailable());
        assertEquals(BatchStatus.DEPLETED, adjusted.getStatus());
        verify(movementRepository).save(argThat(m -> m.getMovementType() == StockMovementType.STOCK_ADJUSTED && m.getQuantity() == -20.0));
    }

    // =========================================================================
    // 3. Dispensing & FEFO Tests
    // =========================================================================

    @Test
    @DisplayName("dispenseMedication adheres strictly to FEFO order (earliest expiry allocated first)")
    void testDispenseMedication_FEFO() {
        when(authz.canDispenseMedication()).thenReturn(true);
        when(authz.getCurrentUser()).thenReturn(pharmacistUser);
        when(medicineRepository.findById("med-1")).thenReturn(Optional.of(amoxicillin));

        Instant now = Instant.now();
        PharmacyStockBatch batchEarly = PharmacyStockBatch.builder()
                .id("batch-early")
                .batchNumber("LOT-EARLY")
                .expiryDate(now.plus(30, ChronoUnit.DAYS))
                .quantityAvailable(15.0)
                .stockUnit("capsule")
                .status(BatchStatus.AVAILABLE)
                .build();

        PharmacyStockBatch batchLate = PharmacyStockBatch.builder()
                .id("batch-late")
                .batchNumber("LOT-LATE")
                .expiryDate(now.plus(180, ChronoUnit.DAYS))
                .quantityAvailable(30.0)
                .stockUnit("capsule")
                .status(BatchStatus.AVAILABLE)
                .build();

        // Repository returns sorted by expiryDate ASC
        when(batchRepository.findByMedicineIdAndStatusAndExpiryDateAfterOrderByExpiryDateAsc(eq("med-1"), eq(BatchStatus.AVAILABLE), any(Instant.class)))
                .thenReturn(List.of(batchEarly, batchLate));

        UpdateResult mockUpdate = mock(UpdateResult.class);
        when(mockUpdate.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(PharmacyStockBatch.class)))
                .thenReturn(mockUpdate);

        when(dispenseRepository.save(any(PharmacyDispense.class))).thenAnswer(inv -> {
            PharmacyDispense d = inv.getArgument(0);
            d.setId("disp-1");
            return d;
        });

        // Request 25 capsules: 15 should come from batchEarly, 10 from batchLate
        DispenseMedicationRequest req = DispenseMedicationRequest.builder()
                .items(List.of(DispenseMedicationRequest.DispenseItemRequest.builder()
                        .medicineId("med-1")
                        .requestedQuantity(25.0)
                        .dispenseUnit("capsule")
                        .build()))
                .build();

        DispenseResponseDTO response = pharmacyService.dispenseMedication(req);

        assertNotNull(response);
        assertEquals(DispenseStatus.DISPENSED, response.getStatus());
        assertEquals(1, response.getItems().size());

        List<PharmacyDispense.BatchStockAllocation> allocations = response.getItems().get(0).getStockAllocations();
        assertEquals(2, allocations.size());

        // First allocation must be LOT-EARLY with 15 units
        assertEquals("batch-early", allocations.get(0).getBatchId());
        assertEquals(15.0, allocations.get(0).getQuantity());

        // Second allocation must be LOT-LATE with remaining 10 units
        assertEquals("batch-late", allocations.get(1).getBatchId());
        assertEquals(10.0, allocations.get(1).getQuantity());

        verify(movementRepository, times(2)).save(any(PharmacyStockMovement.class));
        verify(auditService).audit(isNull(), eq("PharmacyDispense"), eq("disp-1"), eq(ClinicalAuditAction.PHARMACY_DISPENSE_COMPLETED), eq("SUCCESS"), isNull(), anyString());
    }

    @Test
    @DisplayName("dispenseMedication throws InsufficientStockException when stock is inadequate")
    void testDispenseMedication_InsufficientStock() {
        when(authz.canDispenseMedication()).thenReturn(true);
        when(medicineRepository.findById("med-1")).thenReturn(Optional.of(amoxicillin));

        when(batchRepository.findByMedicineIdAndStatusAndExpiryDateAfterOrderByExpiryDateAsc(eq("med-1"), eq(BatchStatus.AVAILABLE), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        DispenseMedicationRequest req = DispenseMedicationRequest.builder()
                .items(List.of(DispenseMedicationRequest.DispenseItemRequest.builder()
                        .medicineId("med-1")
                        .requestedQuantity(10.0)
                        .build()))
                .build();

        assertThrows(InsufficientStockException.class, () -> pharmacyService.dispenseMedication(req));
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(PharmacyStockBatch.class));
        verify(dispenseRepository, never()).save(any());
    }

    // =========================================================================
    // 4. Returns & Reversals Tests
    // =========================================================================

    @Test
    @DisplayName("reverseDispense transitions status to REVERSED and logs quarantined stock movement")
    void testReverseDispense_Quarantined() {
        when(authz.canDispenseMedication()).thenReturn(true);
        when(authz.getCurrentUser()).thenReturn(pharmacistUser);

        PharmacyDispense.BatchStockAllocation alloc = PharmacyDispense.BatchStockAllocation.builder()
                .batchId("batch-1")
                .batchNumber("LOT-1")
                .quantity(10.0)
                .stockUnit("capsule")
                .build();

        PharmacyDispense.DispenseLineItem line = PharmacyDispense.DispenseLineItem.builder()
                .medicineId("med-1")
                .dispensedQuantity(10.0)
                .stockAllocations(List.of(alloc))
                .build();

        PharmacyDispense dispense = PharmacyDispense.builder()
                .id("disp-1")
                .dispenseNumber("DISP-9981")
                .patientId("pat-1")
                .items(List.of(line))
                .status(DispenseStatus.DISPENSED)
                .build();

        when(dispenseRepository.findById("disp-1")).thenReturn(Optional.of(dispense));
        when(dispenseRepository.save(any(PharmacyDispense.class))).thenAnswer(inv -> inv.getArgument(0));

        ReverseDispenseRequest req = ReverseDispenseRequest.builder()
                .dispenseId("disp-1")
                .reasonCode("PATIENT_RETURN")
                .notes("Unused package returned by patient")
                .build();

        DispenseResponseDTO reversed = pharmacyService.reverseDispense(req);

        assertEquals(DispenseStatus.REVERSED, reversed.getStatus());
        // Quarantined stock movement must be recorded
        verify(movementRepository).save(argThat(m ->
                m.getMovementType() == StockMovementType.STOCK_QUARANTINED &&
                m.getQuantity() == 10.0 &&
                "DISPENSE_REVERSAL".equals(m.getReferenceType())
        ));
        verify(auditService).audit(eq("pat-1"), eq("PharmacyDispense"), eq("disp-1"), eq(ClinicalAuditAction.PHARMACY_DISPENSE_REVERSED), eq("SUCCESS"), isNull(), anyString());
    }
}
