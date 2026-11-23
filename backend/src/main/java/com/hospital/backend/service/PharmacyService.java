package com.hospital.backend.service;

import com.hospital.backend.dto.pharmacy.*;
import com.hospital.backend.model.PharmacyDispense;
import com.hospital.backend.model.PharmacyMedicine;
import com.hospital.backend.model.PharmacyStockBatch;
import com.hospital.backend.model.PharmacyStockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PharmacyService {

    // Medicine Catalog
    PharmacyMedicine createMedicine(CreateMedicineRequest request);
    PharmacyMedicine updateMedicine(String id, UpdateMedicineRequest request);
    PharmacyMedicine deactivateMedicine(String id);
    MedicineResponseDTO getMedicineById(String id);
    Page<MedicineResponseDTO> getAllMedicines(Pageable pageable, String query);

    // Stock & Batches
    PharmacyStockBatch receiveStock(ReceiveStockRequest request);
    PharmacyStockBatch adjustStock(AdjustStockRequest request);
    List<BatchResponseDTO> getBatchesByMedicine(String medicineId);
    List<BatchResponseDTO> getNearExpiryBatches(int days);
    List<MedicineResponseDTO> getLowStockMedicines();

    // Prescription Queue
    Page<PrescriptionQueueItemDTO> getPrescriptionQueue(Pageable pageable);
    PrescriptionQueueItemDTO getPrescriptionQueueItem(String prescriptionId);

    // Dispensing & Reversals
    DispenseResponseDTO dispenseMedication(DispenseMedicationRequest request);
    DispenseResponseDTO reverseDispense(ReverseDispenseRequest request);

    // History & Movements
    Page<DispenseResponseDTO> getDispensesByPatient(String patientId, Pageable pageable);
    DispenseResponseDTO getDispenseById(String id);
    Page<PharmacyStockMovement> getStockMovements(String medicineId, Pageable pageable);
}
