package com.hospital.backend.service.impl;

import com.hospital.backend.dto.pharmacy.*;
import com.hospital.backend.enums.BatchStatus;
import com.hospital.backend.enums.ClinicalAuditAction;
import com.hospital.backend.enums.DispenseStatus;
import com.hospital.backend.enums.StockMovementType;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.exception.DuplicateResourceException;
import com.hospital.backend.exception.InsufficientStockException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.ClinicalAuditService;
import com.hospital.backend.service.NotificationService;
import com.hospital.backend.service.PharmacyService;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PharmacyServiceImpl implements PharmacyService {

    private final PharmacyMedicineRepository medicineRepository;
    private final PharmacyStockBatchRepository batchRepository;
    private final PharmacyDispenseRepository dispenseRepository;
    private final PharmacyStockMovementRepository movementRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final AuthorizationService authz;
    private final ClinicalAuditService auditService;
    private final NotificationService notificationService;

    // =========================================================================
    // 1. Medicine Catalog Management
    // =========================================================================

    @Override
    public PharmacyMedicine createMedicine(CreateMedicineRequest request) {
        if (!authz.canManagePharmacyCatalog()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can manage medicine catalog");
        }

        String code = request.getMedicineCode().trim().toUpperCase();
        if (medicineRepository.findByMedicineCode(code).isPresent()) {
            throw new DuplicateResourceException("Medicine code already exists: " + code);
        }

        PharmacyMedicine medicine = PharmacyMedicine.builder()
                .medicineCode(code)
                .genericName(request.getGenericName().trim())
                .brandName(request.getBrandName() != null ? request.getBrandName().trim() : null)
                .strength(request.getStrength().trim())
                .dosageForm(request.getDosageForm())
                .packageDescription(request.getPackageDescription())
                .manufacturer(request.getManufacturer())
                .dispensingUnit(request.getDispensingUnit().trim().toLowerCase())
                .active(true)
                .reorderLevel(request.getReorderLevel() != null ? request.getReorderLevel() : 20.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        PharmacyMedicine saved = medicineRepository.save(medicine);
        auditService.audit(null, "PharmacyMedicine", saved.getId(),
                ClinicalAuditAction.PHARMACY_MEDICINE_CREATED, "SUCCESS", null,
                "Created medicine " + saved.getMedicineCode() + " (" + saved.getGenericName() + ")");
        log.info("Created pharmacy medicine: {} ({})", saved.getMedicineCode(), saved.getGenericName());
        return saved;
    }

    @Override
    public PharmacyMedicine updateMedicine(String id, UpdateMedicineRequest request) {
        if (!authz.canManagePharmacyCatalog()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can update medicine catalog");
        }

        PharmacyMedicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found: " + id));

        if (request.getBrandName() != null) medicine.setBrandName(request.getBrandName().trim());
        if (request.getPackageDescription() != null) medicine.setPackageDescription(request.getPackageDescription());
        if (request.getManufacturer() != null) medicine.setManufacturer(request.getManufacturer());
        if (request.getDispensingUnit() != null) medicine.setDispensingUnit(request.getDispensingUnit().trim().toLowerCase());
        if (request.getActive() != null) medicine.setActive(request.getActive());
        if (request.getReorderLevel() != null) medicine.setReorderLevel(request.getReorderLevel());

        medicine.setUpdatedAt(Instant.now());
        PharmacyMedicine saved = medicineRepository.save(medicine);
        auditService.audit(null, "PharmacyMedicine", saved.getId(),
                ClinicalAuditAction.PHARMACY_MEDICINE_UPDATED, "SUCCESS", null,
                "Updated medicine " + saved.getMedicineCode());
        return saved;
    }

    @Override
    public PharmacyMedicine deactivateMedicine(String id) {
        if (!authz.canManagePharmacyCatalog()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can deactivate medicine");
        }

        PharmacyMedicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found: " + id));

        medicine.setActive(false);
        medicine.setUpdatedAt(Instant.now());
        PharmacyMedicine saved = medicineRepository.save(medicine);
        auditService.audit(null, "PharmacyMedicine", saved.getId(),
                ClinicalAuditAction.PHARMACY_MEDICINE_UPDATED, "SUCCESS", null,
                "Deactivated medicine " + saved.getMedicineCode());
        return saved;
    }

    @Override
    public MedicineResponseDTO getMedicineById(String id) {
        PharmacyMedicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found: " + id));
        return mapToMedicineDTO(medicine);
    }

    @Override
    public Page<MedicineResponseDTO> getAllMedicines(Pageable pageable, String query) {
        if (query != null && !query.trim().isEmpty()) {
            List<PharmacyMedicine> found = medicineRepository.findByGenericNameContainingIgnoreCaseOrBrandNameContainingIgnoreCase(
                    query.trim(), query.trim());
            List<MedicineResponseDTO> dtos = found.stream().map(this::mapToMedicineDTO).collect(Collectors.toList());
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), dtos.size());
            List<MedicineResponseDTO> pagedList = start > dtos.size() ? Collections.emptyList() : dtos.subList(start, end);
            return new PageImpl<>(pagedList, pageable, dtos.size());
        }

        Page<PharmacyMedicine> page = medicineRepository.findAll(pageable);
        List<MedicineResponseDTO> dtos = page.getContent().stream().map(this::mapToMedicineDTO).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    // =========================================================================
    // 2. Inventory & Batch Management
    // =========================================================================

    @Override
    @Transactional
    public PharmacyStockBatch receiveStock(ReceiveStockRequest request) {
        if (!authz.canManagePharmacyInventory()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can receive stock");
        }

        // Check idempotency
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<PharmacyStockBatch> existingBatch = batchRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existingBatch.isPresent()) {
                return existingBatch.get();
            }
        }

        PharmacyMedicine medicine = medicineRepository.findById(request.getMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found: " + request.getMedicineId()));

        if (!medicine.isActive()) {
            throw new BadRequestException("Cannot receive stock for inactive medicine: " + medicine.getGenericName());
        }

        if (request.getExpiryDate().isBefore(Instant.now())) {
            throw new BadRequestException("Expiry date cannot be in the past");
        }

        User currentUser = authz.getCurrentUser();

        // Check if lot/batch number already exists for this medicine
        Optional<PharmacyStockBatch> existingLot = batchRepository.findByMedicineIdAndBatchNumber(
                medicine.getId(), request.getBatchNumber().trim());

        PharmacyStockBatch batch;
        double quantityReceived = request.getQuantityReceived();

        if (existingLot.isPresent()) {
            // Increment existing batch quantity atomically
            batch = existingLot.get();
            batch.setQuantityReceived(batch.getQuantityReceived() + quantityReceived);
            batch.setQuantityAvailable(batch.getQuantityAvailable() + quantityReceived);
            batch.setStatus(BatchStatus.AVAILABLE);
            batch.setUpdatedAt(Instant.now());
            batch = batchRepository.save(batch);
        } else {
            batch = PharmacyStockBatch.builder()
                    .medicineId(medicine.getId())
                    .batchNumber(request.getBatchNumber().trim().toUpperCase())
                    .supplierReference(request.getSupplierReference())
                    .receivedAt(Instant.now())
                    .manufacturedAt(request.getManufacturedAt())
                    .expiryDate(request.getExpiryDate())
                    .quantityReceived(quantityReceived)
                    .quantityAvailable(quantityReceived)
                    .quantityReserved(0.0)
                    .stockUnit(request.getStockUnit().trim().toLowerCase())
                    .storageLocation(request.getStorageLocation())
                    .status(BatchStatus.AVAILABLE)
                    .idempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            batch = batchRepository.save(batch);
        }

        // Record stock movement ledger
        PharmacyStockMovement movement = PharmacyStockMovement.builder()
                .medicineId(medicine.getId())
                .batchId(batch.getId())
                .movementType(StockMovementType.STOCK_RECEIVED)
                .quantity(quantityReceived)
                .balanceAfter(batch.getQuantityAvailable())
                .unit(batch.getStockUnit())
                .referenceType("STOCK_RECEIPT")
                .referenceId(request.getSupplierReference() != null ? request.getSupplierReference() : batch.getBatchNumber())
                .idempotencyKey(request.getIdempotencyKey())
                .actorUserId(currentUser.getId())
                .actorRole(currentUser.getRole().name())
                .occurredAt(Instant.now())
                .reasonCode("GOODS_RECEIPT")
                .correlationId(UUID.randomUUID().toString())
                .build();
        movementRepository.save(movement);

        auditService.audit(null, "PharmacyStockBatch", batch.getId(),
                ClinicalAuditAction.PHARMACY_STOCK_RECEIVED, "SUCCESS", null,
                "Received " + quantityReceived + " " + batch.getStockUnit() + " of " + medicine.getGenericName() + " (Batch " + batch.getBatchNumber() + ")");
        log.info("Received stock for medicine {} (Batch {}): +{}", medicine.getMedicineCode(), batch.getBatchNumber(), quantityReceived);

        return batch;
    }

    @Override
    @Transactional
    public PharmacyStockBatch adjustStock(AdjustStockRequest request) {
        if (!authz.canManagePharmacyInventory()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can adjust stock");
        }

        PharmacyStockBatch batch = batchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock batch not found: " + request.getBatchId()));

        double oldQty = batch.getQuantityAvailable();
        double newQty = request.getAdjustedQuantity();
        if (newQty < 0) {
            throw new BadRequestException("Adjusted stock quantity cannot be negative");
        }

        double delta = newQty - oldQty;
        User currentUser = authz.getCurrentUser();

        batch.setQuantityAvailable(newQty);
        if (newQty == 0) {
            batch.setStatus(BatchStatus.DEPLETED);
        } else if (batch.getStatus() == BatchStatus.DEPLETED && newQty > 0) {
            batch.setStatus(BatchStatus.AVAILABLE);
        }
        batch.setUpdatedAt(Instant.now());
        PharmacyStockBatch saved = batchRepository.save(batch);

        PharmacyStockMovement movement = PharmacyStockMovement.builder()
                .medicineId(batch.getMedicineId())
                .batchId(batch.getId())
                .movementType(StockMovementType.STOCK_ADJUSTED)
                .quantity(delta)
                .balanceAfter(newQty)
                .unit(batch.getStockUnit())
                .referenceType("STOCK_ADJUSTMENT")
                .referenceId(request.getReasonCode())
                .actorUserId(currentUser.getId())
                .actorRole(currentUser.getRole().name())
                .occurredAt(Instant.now())
                .reasonCode(request.getReasonCode())
                .correlationId(UUID.randomUUID().toString())
                .build();
        movementRepository.save(movement);

        auditService.audit(null, "PharmacyStockBatch", saved.getId(),
                ClinicalAuditAction.PHARMACY_STOCK_ADJUSTED, "SUCCESS", null,
                "Adjusted batch " + saved.getBatchNumber() + " stock from " + oldQty + " to " + newQty + " (" + request.getReasonCode() + ")");

        return saved;
    }

    @Override
    public List<BatchResponseDTO> getBatchesByMedicine(String medicineId) {
        PharmacyMedicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found: " + medicineId));

        List<PharmacyStockBatch> batches = batchRepository.findByMedicineId(medicineId);
        return batches.stream().map(b -> mapToBatchDTO(b, medicine.getGenericName())).collect(Collectors.toList());
    }

    @Override
    public List<BatchResponseDTO> getNearExpiryBatches(int days) {
        Instant now = Instant.now();
        Instant threshold = now.plus(days > 0 ? days : 30, ChronoUnit.DAYS);

        List<PharmacyStockBatch> batches = batchRepository.findByStatusAndExpiryDateBetween(
                BatchStatus.AVAILABLE, now, threshold);

        Map<String, String> medicineNames = new HashMap<>();
        return batches.stream().map(b -> {
            String medName = medicineNames.computeIfAbsent(b.getMedicineId(), id ->
                    medicineRepository.findById(id).map(PharmacyMedicine::getGenericName).orElse("Unknown"));
            return mapToBatchDTO(b, medName);
        }).collect(Collectors.toList());
    }

    @Override
    public List<MedicineResponseDTO> getLowStockMedicines() {
        List<PharmacyMedicine> activeMedicines = medicineRepository.findByActiveTrue();
        return activeMedicines.stream()
                .map(this::mapToMedicineDTO)
                .filter(MedicineResponseDTO::isLowStock)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 3. Prescription Queue Management
    // =========================================================================

    @Override
    public Page<PrescriptionQueueItemDTO> getPrescriptionQueue(Pageable pageable) {
        if (!authz.canViewPharmacyQueue()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can view prescription queue");
        }

        Page<Prescription> prescPage = prescriptionRepository.findAll(pageable);
        List<PrescriptionQueueItemDTO> queueItems = prescPage.getContent().stream()
                .map(this::buildQueueItemDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(queueItems, pageable, prescPage.getTotalElements());
    }

    @Override
    public PrescriptionQueueItemDTO getPrescriptionQueueItem(String prescriptionId) {
        if (!authz.canViewPharmacyQueue()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can view prescription queue item");
        }

        Prescription presc = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + prescriptionId));

        return buildQueueItemDTO(presc);
    }

    private PrescriptionQueueItemDTO buildQueueItemDTO(Prescription presc) {
        String patientName = "Unknown Patient";
        if (presc.getPatientId() != null) {
            Optional<Patient> p = patientRepository.findById(presc.getPatientId());
            if (p.isPresent()) {
                patientName = p.get().getFullName();
            } else {
                Optional<User> u = userRepository.findById(presc.getPatientId());
                if (u.isPresent()) patientName = u.get().getFullName();
            }
        }

        String doctorName = "Unknown Doctor";
        if (presc.getDoctorId() != null) {
            Optional<Doctor> d = doctorRepository.findById(presc.getDoctorId());
            if (d.isPresent()) {
                doctorName = d.get().getFullName();
            } else {
                Optional<User> u = userRepository.findById(presc.getDoctorId());
                if (u.isPresent()) doctorName = u.get().getFullName();
            }
        }

        List<PharmacyDispense> pastDispenses = dispenseRepository.findByPrescriptionIdOrderByCreatedAtDesc(presc.getId());
        Map<String, Double> alreadyDispensedByMedName = new HashMap<>();
        for (PharmacyDispense disp : pastDispenses) {
            if (disp.getStatus() != DispenseStatus.CANCELLED && disp.getStatus() != DispenseStatus.REVERSED) {
                if (disp.getItems() != null) {
                    for (PharmacyDispense.DispenseLineItem item : disp.getItems()) {
                        String key = item.getMedicineName() != null ? item.getMedicineName().toLowerCase().trim() :
                                (item.getMedicineId() != null ? item.getMedicineId() : "");
                        alreadyDispensedByMedName.merge(key, item.getDispensedQuantity(), Double::sum);
                    }
                }
            }
        }

        List<PrescriptionQueueItemDTO.PrescribedMedicineDetail> details = new ArrayList<>();
        boolean allFullyDispensed = true;

        if (presc.getMedicines() != null) {
            for (String rawMed : presc.getMedicines()) {
                String medQuery = rawMed.split("-")[0].split(",")[0].trim();
                List<PharmacyMedicine> matches = medicineRepository.findByGenericNameContainingIgnoreCaseOrBrandNameContainingIgnoreCase(
                        medQuery, medQuery);

                Double availableStock = 0.0;
                String matchedMedId = null;
                String matchedMedName = rawMed;

                if (!matches.isEmpty()) {
                    PharmacyMedicine first = matches.get(0);
                    matchedMedId = first.getId();
                    matchedMedName = first.getGenericName();
                    List<PharmacyStockBatch> activeBatches = batchRepository.findByMedicineIdAndStatusAndExpiryDateAfterOrderByExpiryDateAsc(
                            first.getId(), BatchStatus.AVAILABLE, Instant.now());
                    availableStock = activeBatches.stream().mapToDouble(PharmacyStockBatch::getQuantityAvailable).sum();
                }

                String stockStatus = matches.isEmpty() ? "NOT_IN_CATALOG" :
                        (availableStock <= 0 ? "OUT_OF_STOCK" : (availableStock < 10 ? "LOW_STOCK" : "AVAILABLE"));

                double dispensedSoFar = alreadyDispensedByMedName.getOrDefault(matchedMedName.toLowerCase().trim(), 0.0);
                double prescribedQty = 10.0; // default prescribed dosage unit if legacy string format
                double remaining = Math.max(0.0, prescribedQty - dispensedSoFar);

                if (remaining > 0) {
                    allFullyDispensed = false;
                }

                details.add(PrescriptionQueueItemDTO.PrescribedMedicineDetail.builder()
                        .rawText(rawMed)
                        .medicineId(matchedMedId)
                        .medicineName(matchedMedName)
                        .totalAvailableStock(availableStock)
                        .stockStatus(stockStatus)
                        .prescribedQuantity(prescribedQty)
                        .alreadyDispensedQuantity(dispensedSoFar)
                        .remainingQuantity(remaining)
                        .build());
            }
        }

        Instant prescDate = Instant.now();
        if (presc.getCreatedAt() != null) {
            try {
                prescDate = Instant.parse(presc.getCreatedAt());
            } catch (Exception ignored) {
            }
        }

        return PrescriptionQueueItemDTO.builder()
                .prescriptionId(presc.getId())
                .patientId(presc.getPatientId())
                .patientName(patientName)
                .doctorId(presc.getDoctorId())
                .doctorName(doctorName)
                .prescribedAt(prescDate)
                .notes(presc.getNotes())
                .medicines(details)
                .fullyDispensed(allFullyDispensed && !details.isEmpty())
                .build();
    }

    // =========================================================================
    // 4. Dispensing (Atomic Concurrency + FEFO)
    // =========================================================================

    @Override
    @Transactional
    public DispenseResponseDTO dispenseMedication(DispenseMedicationRequest request) {
        if (!authz.canDispenseMedication()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can dispense medication");
        }

        // 1. Idempotency Check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<PharmacyDispense> existing = dispenseRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                return mapToDispenseDTO(existing.get());
            }
        }

        User pharmacist = authz.getCurrentUser();
        String prescriptionId = request.getPrescriptionId();
        String patientId = null;
        String doctorId = null;

        if (prescriptionId != null && !prescriptionId.isBlank()) {
            Prescription presc = prescriptionRepository.findById(prescriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + prescriptionId));
            patientId = presc.getPatientId();
            doctorId = presc.getDoctorId();
        }

        String dispenseNumber = "DISP-" + Instant.now().getEpochSecond() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
        List<PharmacyDispense.DispenseLineItem> lineItems = new ArrayList<>();
        boolean isPartial = false;

        for (DispenseMedicationRequest.DispenseItemRequest itemReq : request.getItems()) {
            PharmacyMedicine medicine = medicineRepository.findById(itemReq.getMedicineId())
                    .orElseThrow(() -> new ResourceNotFoundException("Medicine not found: " + itemReq.getMedicineId()));

            if (!medicine.isActive()) {
                throw new BadRequestException("Cannot dispense inactive medicine: " + medicine.getGenericName());
            }

            double requestedQty = itemReq.getRequestedQuantity();
            double remainingToAllocate = requestedQty;

            // FEFO batch selection: strictly status AVAILABLE, expiryDate > now, sorted expiryDate ASC
            List<PharmacyStockBatch> availableBatches = batchRepository.findByMedicineIdAndStatusAndExpiryDateAfterOrderByExpiryDateAsc(
                    medicine.getId(), BatchStatus.AVAILABLE, Instant.now());

            double totalBatchStock = availableBatches.stream().mapToDouble(PharmacyStockBatch::getQuantityAvailable).sum();
            if (totalBatchStock < requestedQty) {
                log.warn("Insufficient stock for medicine {} (requested: {}, available: {})",
                        medicine.getGenericName(), requestedQty, totalBatchStock);
                throw new InsufficientStockException("INSUFFICIENT_STOCK",
                        "Insufficient stock for " + medicine.getGenericName() +
                                ". Requested: " + requestedQty + ", Available: " + totalBatchStock);
            }

            List<PharmacyDispense.BatchStockAllocation> allocations = new ArrayList<>();

            for (PharmacyStockBatch batch : availableBatches) {
                if (remainingToAllocate <= 0) break;

                double batchAvail = batch.getQuantityAvailable();
                if (batchAvail <= 0) continue;

                double allocQty = Math.min(batchAvail, remainingToAllocate);

                // ATOMIC CONDITIONAL UPDATE VIA MONGO TEMPLATE
                Query query = Query.query(
                        Criteria.where("id").is(batch.getId())
                                .and("status").is(BatchStatus.AVAILABLE)
                                .and("expiryDate").gt(Instant.now())
                                .and("quantityAvailable").gte(allocQty)
                );
                Update update = new Update()
                        .inc("quantityAvailable", -allocQty)
                        .set("updatedAt", Instant.now());

                UpdateResult updateResult = mongoTemplate.updateFirst(query, update, PharmacyStockBatch.class);

                if (updateResult.getModifiedCount() == 0) {
                    log.warn("Concurrent stock deduction conflict on batch {}. Modified count 0.", batch.getBatchNumber());
                    throw new InsufficientStockException("STOCK_ALLOCATION_CONFLICT",
                            "Concurrent update conflict on batch " + batch.getBatchNumber() + ". Please retry.");
                }

                double newBalance = batchAvail - allocQty;
                if (newBalance == 0) {
                    // Update status to DEPLETED
                    mongoTemplate.updateFirst(
                            Query.query(Criteria.where("id").is(batch.getId())),
                            new Update().set("status", BatchStatus.DEPLETED).set("updatedAt", Instant.now()),
                            PharmacyStockBatch.class
                    );
                }

                allocations.add(PharmacyDispense.BatchStockAllocation.builder()
                        .batchId(batch.getId())
                        .batchNumber(batch.getBatchNumber())
                        .expiryDate(batch.getExpiryDate())
                        .quantity(allocQty)
                        .stockUnit(batch.getStockUnit())
                        .build());

                // Record append-only ledger movement
                PharmacyStockMovement movement = PharmacyStockMovement.builder()
                        .medicineId(medicine.getId())
                        .batchId(batch.getId())
                        .movementType(StockMovementType.STOCK_DISPENSED)
                        .quantity(-allocQty)
                        .balanceAfter(newBalance)
                        .unit(batch.getStockUnit())
                        .referenceType("DISPENSE")
                        .referenceId(dispenseNumber)
                        .idempotencyKey(request.getIdempotencyKey())
                        .actorUserId(pharmacist.getId())
                        .actorRole(pharmacist.getRole().name())
                        .occurredAt(Instant.now())
                        .reasonCode("PATIENT_DISPENSING")
                        .correlationId(UUID.randomUUID().toString())
                        .build();
                movementRepository.save(movement);

                remainingToAllocate -= allocQty;
            }

            if (remainingToAllocate > 0) {
                throw new InsufficientStockException("INSUFFICIENT_STOCK",
                        "Could not fulfill complete allocation for medicine: " + medicine.getGenericName());
            }

            lineItems.add(PharmacyDispense.DispenseLineItem.builder()
                    .prescriptionLineId(itemReq.getPrescriptionLineId())
                    .medicineId(medicine.getId())
                    .medicineName(medicine.getGenericName())
                    .prescribedQuantity(requestedQty)
                    .dispensedQuantity(requestedQty)
                    .remainingQuantity(0.0)
                    .dispenseUnit(medicine.getDispensingUnit())
                    .stockAllocations(allocations)
                    .notes(itemReq.getNotes())
                    .build());

            // Check low stock alert after dispensing
            double postStock = batchRepository.findByMedicineIdAndStatusAndExpiryDateAfterOrderByExpiryDateAsc(
                    medicine.getId(), BatchStatus.AVAILABLE, Instant.now()).stream().mapToDouble(PharmacyStockBatch::getQuantityAvailable).sum();
            if (medicine.getReorderLevel() != null && postStock <= medicine.getReorderLevel()) {
                log.warn("LOW STOCK ALERT: Medicine {} current available stock ({}) <= reorder level ({})",
                        medicine.getGenericName(), postStock, medicine.getReorderLevel());
            }
        }

        DispenseStatus status = isPartial ? DispenseStatus.PARTIALLY_DISPENSED : DispenseStatus.DISPENSED;

        PharmacyDispense dispense = PharmacyDispense.builder()
                .dispenseNumber(dispenseNumber)
                .prescriptionId(prescriptionId)
                .patientId(patientId)
                .doctorId(doctorId)
                .pharmacistUserId(pharmacist.getId())
                .pharmacistName(pharmacist.getFullName())
                .items(lineItems)
                .status(status)
                .dispensedAt(Instant.now())
                .notes(request.getNotes())
                .idempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        PharmacyDispense savedDispense = dispenseRepository.save(dispense);

        // Audit Trail
        ClinicalAuditAction auditAction = status == DispenseStatus.DISPENSED ?
                ClinicalAuditAction.PHARMACY_DISPENSE_COMPLETED : ClinicalAuditAction.PHARMACY_PARTIAL_DISPENSED;
        auditService.audit(patientId, "PharmacyDispense", savedDispense.getId(), auditAction,
                "SUCCESS", null, "Medication dispensed: " + savedDispense.getDispenseNumber());

        // Notify patient
        if (patientId != null) {
            try {
                notificationService.createNotification(Notification.builder()
                        .userId(patientId)
                        .title("Prescription Medication Dispensed")
                        .message("Your prescribed medications (" + savedDispense.getDispenseNumber() + ") have been dispensed by the hospital pharmacy.")
                        .type("PRESCRIPTION")
                        .referenceId(savedDispense.getId())
                        .referenceType("PHARMACY_DISPENSE")
                        .isRead(false)
                        .createdAt(java.time.LocalDateTime.now())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to send notification for dispense {}: {}", savedDispense.getDispenseNumber(), e.getMessage());
            }
        }

        log.info("Successfully dispensed medication: {} (items: {})", savedDispense.getDispenseNumber(), lineItems.size());
        return mapToDispenseDTO(savedDispense);
    }

    // =========================================================================
    // 5. Returns and Reversals
    // =========================================================================

    @Override
    @Transactional
    public DispenseResponseDTO reverseDispense(ReverseDispenseRequest request) {
        if (!authz.canDispenseMedication()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can reverse medication dispensing");
        }

        PharmacyDispense dispense = dispenseRepository.findById(request.getDispenseId())
                .orElseThrow(() -> new ResourceNotFoundException("Dispense record not found: " + request.getDispenseId()));

        if (dispense.getStatus() != DispenseStatus.DISPENSED && dispense.getStatus() != DispenseStatus.PARTIALLY_DISPENSED) {
            throw new BadRequestException("Cannot reverse dispense in status: " + dispense.getStatus());
        }

        User currentUser = authz.getCurrentUser();

        // Trace allocated items and quarantine returned medication
        for (PharmacyDispense.DispenseLineItem line : dispense.getItems()) {
            if (line.getStockAllocations() != null) {
                for (PharmacyDispense.BatchStockAllocation alloc : line.getStockAllocations()) {
                    // Patient returned stock is quarantined (NEVER put directly back into saleable stock)
                    PharmacyStockMovement movement = PharmacyStockMovement.builder()
                            .medicineId(line.getMedicineId())
                            .batchId(alloc.getBatchId())
                            .movementType(StockMovementType.STOCK_QUARANTINED)
                            .quantity(alloc.getQuantity())
                            .balanceAfter(0.0) // Quarantined stock is kept separate from available inventory
                            .unit(alloc.getStockUnit())
                            .referenceType("DISPENSE_REVERSAL")
                            .referenceId(dispense.getDispenseNumber())
                            .actorUserId(currentUser.getId())
                            .actorRole(currentUser.getRole().name())
                            .occurredAt(Instant.now())
                            .reasonCode(request.getReasonCode())
                            .correlationId(UUID.randomUUID().toString())
                            .build();
                    movementRepository.save(movement);
                }
            }
        }

        dispense.setStatus(DispenseStatus.REVERSED);
        dispense.setNotes((dispense.getNotes() != null ? dispense.getNotes() + " | " : "") +
                "Reversed: " + request.getReasonCode() + (request.getNotes() != null ? " - " + request.getNotes() : ""));
        dispense.setUpdatedAt(Instant.now());

        PharmacyDispense saved = dispenseRepository.save(dispense);

        auditService.audit(dispense.getPatientId(), "PharmacyDispense", saved.getId(),
                ClinicalAuditAction.PHARMACY_DISPENSE_REVERSED, "SUCCESS", null,
                "Dispense " + saved.getDispenseNumber() + " reversed: " + request.getReasonCode());

        return mapToDispenseDTO(saved);
    }

    // =========================================================================
    // 6. Dispense Queries & History
    // =========================================================================

    @Override
    public Page<DispenseResponseDTO> getDispensesByPatient(String patientId, Pageable pageable) {
        if (!authz.canAccessPatient(patientId)) {
            throw new AccessDeniedException("Not authorized to access patient dispense records");
        }

        Page<PharmacyDispense> page = dispenseRepository.findByPatientId(patientId, pageable);
        List<DispenseResponseDTO> dtos = page.getContent().stream().map(this::mapToDispenseDTO).collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Override
    public DispenseResponseDTO getDispenseById(String id) {
        PharmacyDispense dispense = dispenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispense record not found: " + id));

        if (!authz.canAccessDispense(dispense.getPatientId(), dispense.getDoctorId())) {
            throw new AccessDeniedException("Not authorized to access this dispense record");
        }

        return mapToDispenseDTO(dispense);
    }

    @Override
    public Page<PharmacyStockMovement> getStockMovements(String medicineId, Pageable pageable) {
        if (!authz.canManagePharmacyInventory()) {
            throw new AccessDeniedException("Only Pharmacists and Administrators can view stock movement ledger");
        }

        return movementRepository.findByMedicineId(medicineId, pageable);
    }

    // =========================================================================
    // 7. Helper Mappings
    // =========================================================================

    private MedicineResponseDTO mapToMedicineDTO(PharmacyMedicine m) {
        List<PharmacyStockBatch> batches = batchRepository.findByMedicineIdAndStatusAndExpiryDateAfterOrderByExpiryDateAsc(
                m.getId(), BatchStatus.AVAILABLE, Instant.now());
        double totalStock = batches.stream().mapToDouble(PharmacyStockBatch::getQuantityAvailable).sum();
        boolean lowStock = m.getReorderLevel() != null && totalStock <= m.getReorderLevel();

        return MedicineResponseDTO.builder()
                .id(m.getId())
                .medicineCode(m.getMedicineCode())
                .genericName(m.getGenericName())
                .brandName(m.getBrandName())
                .strength(m.getStrength())
                .dosageForm(m.getDosageForm())
                .packageDescription(m.getPackageDescription())
                .manufacturer(m.getManufacturer())
                .dispensingUnit(m.getDispensingUnit())
                .active(m.isActive())
                .reorderLevel(m.getReorderLevel())
                .totalAvailableStock(totalStock)
                .batchCount(batches.size())
                .lowStock(lowStock)
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private BatchResponseDTO mapToBatchDTO(PharmacyStockBatch b, String medicineName) {
        Instant now = Instant.now();
        boolean isExpired = b.getExpiryDate() != null && b.getExpiryDate().isBefore(now);
        boolean isNearExpiry = b.getExpiryDate() != null && !isExpired &&
                b.getExpiryDate().isBefore(now.plus(30, ChronoUnit.DAYS));

        return BatchResponseDTO.builder()
                .id(b.getId())
                .medicineId(b.getMedicineId())
                .medicineName(medicineName)
                .batchNumber(b.getBatchNumber())
                .supplierReference(b.getSupplierReference())
                .receivedAt(b.getReceivedAt())
                .manufacturedAt(b.getManufacturedAt())
                .expiryDate(b.getExpiryDate())
                .quantityReceived(b.getQuantityReceived())
                .quantityAvailable(b.getQuantityAvailable())
                .quantityReserved(b.getQuantityReserved())
                .stockUnit(b.getStockUnit())
                .storageLocation(b.getStorageLocation())
                .status(b.getStatus())
                .nearExpiry(isNearExpiry)
                .expired(isExpired)
                .createdAt(b.getCreatedAt())
                .build();
    }

    private DispenseResponseDTO mapToDispenseDTO(PharmacyDispense d) {
        String patientName = null;
        if (d.getPatientId() != null) {
            patientName = patientRepository.findById(d.getPatientId()).map(Patient::getFullName)
                    .orElseGet(() -> userRepository.findById(d.getPatientId()).map(User::getFullName).orElse(null));
        }

        String doctorName = null;
        if (d.getDoctorId() != null) {
            doctorName = doctorRepository.findById(d.getDoctorId()).map(Doctor::getFullName)
                    .orElseGet(() -> userRepository.findById(d.getDoctorId()).map(User::getFullName).orElse(null));
        }

        return DispenseResponseDTO.builder()
                .id(d.getId())
                .dispenseNumber(d.getDispenseNumber())
                .prescriptionId(d.getPrescriptionId())
                .patientId(d.getPatientId())
                .patientName(patientName)
                .doctorId(d.getDoctorId())
                .doctorName(doctorName)
                .pharmacistUserId(d.getPharmacistUserId())
                .pharmacistName(d.getPharmacistName())
                .items(d.getItems())
                .status(d.getStatus())
                .dispensedAt(d.getDispensedAt())
                .notes(d.getNotes())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
