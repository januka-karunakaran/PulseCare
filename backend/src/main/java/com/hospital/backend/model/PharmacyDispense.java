package com.hospital.backend.model;

import com.hospital.backend.enums.DispenseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "pharmacy_dispenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "pharm_disp_patient_date_idx", def = "{'patientId': 1, 'dispensedAt': -1}"),
        @CompoundIndex(name = "pharm_disp_presc_date_idx", def = "{'prescriptionId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "pharm_disp_status_date_idx", def = "{'status': 1, 'createdAt': -1}")
})
public class PharmacyDispense {

    @Id
    private String id;

    @Indexed(unique = true)
    private String dispenseNumber;

    private String prescriptionId;

    private String patientId;

    private String doctorId;

    private String pharmacistUserId;

    private String pharmacistName;

    @Builder.Default
    private List<DispenseLineItem> items = new ArrayList<>();

    private DispenseStatus status;

    private Instant dispensedAt;

    private String notes;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DispenseLineItem {
        private String prescriptionLineId;
        private String medicineId;
        private String medicineName;
        private Double prescribedQuantity;
        private Double dispensedQuantity;
        private Double remainingQuantity;
        private String dispenseUnit;
        @Builder.Default
        private List<BatchStockAllocation> stockAllocations = new ArrayList<>();
        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchStockAllocation {
        private String batchId;
        private String batchNumber;
        private Instant expiryDate;
        private Double quantity;
        private String stockUnit;
    }
}
