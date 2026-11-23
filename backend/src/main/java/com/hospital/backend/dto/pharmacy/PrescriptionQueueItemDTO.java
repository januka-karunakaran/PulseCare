package com.hospital.backend.dto.pharmacy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionQueueItemDTO {

    private String prescriptionId;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private Instant prescribedAt;
    private String notes;
    private List<PrescribedMedicineDetail> medicines;
    private boolean fullyDispensed;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PrescribedMedicineDetail {
        private String rawText;
        private String medicineId;
        private String medicineName;
        private Double totalAvailableStock;
        private String stockStatus; // AVAILABLE, LOW_STOCK, OUT_OF_STOCK, NOT_IN_CATALOG
        private Double prescribedQuantity;
        private Double alreadyDispensedQuantity;
        private Double remainingQuantity;
    }
}
