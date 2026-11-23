package com.hospital.backend.service.impl;

import com.hospital.backend.dto.lab.*;
import com.hospital.backend.enums.*;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.ClinicalAuditService;
import com.hospital.backend.service.LabPdfService;
import com.hospital.backend.service.LabService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabServiceImpl implements LabService {

    private final LabTestDefinitionRepository testDefinitionRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabSpecimenRepository labSpecimenRepository;
    private final LabResultRepository labResultRepository;
    private final ClinicalEncounterRepository encounterRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final AuthorizationService authz;
    private final ClinicalAuditService auditService;
    private final LabPdfService labPdfService;

    // =========================================================================
    // 1. Test Catalog Management
    // =========================================================================

    @Override
    public LabTestDefinition createTestDefinition(CreateLabTestRequest request) {
        if (testDefinitionRepository.existsByTestCode(request.getTestCode().trim())) {
            throw new BadRequestException("A laboratory test with code '" + request.getTestCode() + "' already exists");
        }

        LabTestDefinition def = LabTestDefinition.builder()
                .testCode(request.getTestCode().trim().toUpperCase())
                .testName(request.getTestName().trim())
                .category(request.getCategory().trim())
                .description(request.getDescription())
                .specimenType(request.getSpecimenType().trim())
                .sampleVolume(request.getSampleVolume())
                .collectionInstructions(request.getCollectionInstructions())
                .defaultUnit(request.getDefaultUnit())
                .referenceRangeMetadata(request.getReferenceRangeMetadata())
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LabTestDefinition saved = testDefinitionRepository.save(def);
        log.info("Created lab test catalog definition: {} ({})", saved.getTestName(), saved.getTestCode());
        return saved;
    }

    @Override
    public LabTestDefinition updateTestDefinition(String testId, UpdateLabTestRequest request) {
        LabTestDefinition def = testDefinitionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory test definition not found: " + testId));

        if (request.getTestName() != null) def.setTestName(request.getTestName().trim());
        if (request.getCategory() != null) def.setCategory(request.getCategory().trim());
        if (request.getDescription() != null) def.setDescription(request.getDescription());
        if (request.getSpecimenType() != null) def.setSpecimenType(request.getSpecimenType().trim());
        if (request.getSampleVolume() != null) def.setSampleVolume(request.getSampleVolume());
        if (request.getCollectionInstructions() != null) def.setCollectionInstructions(request.getCollectionInstructions());
        if (request.getDefaultUnit() != null) def.setDefaultUnit(request.getDefaultUnit());
        if (request.getReferenceRangeMetadata() != null) def.setReferenceRangeMetadata(request.getReferenceRangeMetadata());
        if (request.getActive() != null) def.setActive(request.getActive());

        def.setUpdatedAt(Instant.now());
        return testDefinitionRepository.save(def);
    }

    @Override
    public void deactivateTestDefinition(String testId) {
        LabTestDefinition def = testDefinitionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory test definition not found: " + testId));
        def.setActive(false);
        def.setUpdatedAt(Instant.now());
        testDefinitionRepository.save(def);
        log.info("Deactivated laboratory test: {}", def.getTestCode());
    }

    @Override
    public List<LabTestDefinition> getActiveTests() {
        return testDefinitionRepository.findByActiveTrue();
    }

    @Override
    public Page<LabTestDefinition> searchTests(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return testDefinitionRepository.findAll(pageable);
        }
        return testDefinitionRepository.findByTestNameContainingIgnoreCaseOrTestCodeContainingIgnoreCase(query, query, pageable);
    }

    @Override
    public LabTestDefinition getTestById(String testId) {
        return testDefinitionRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory test definition not found: " + testId));
    }

    // =========================================================================
    // 2. Doctor Lab Order Creation & Management
    // =========================================================================

    @Override
    @Transactional
    public LabOrder createOrder(CreateLabOrderRequest request) {
        User currentUser = authz.getCurrentUser();

        // 1. Verify caller has permission to order tests
        String doctorId = resolveDoctorIdForCaller(currentUser);
        if (!authz.canCreateLabOrder(doctorId, request.getPatientId())) {
            auditService.audit(request.getPatientId(), "LabOrder", null, ClinicalAuditAction.LAB_ACCESS_DENIED, "FAILURE", null, "Unauthorized attempt to order lab tests");
            throw new AccessDeniedException("You are not authorized to create laboratory orders for this patient");
        }

        // 2. Validate patient existence
        String patientId = request.getPatientId();
        boolean patientExists = patientRepository.existsById(patientId) || patientRepository.findByUserId(patientId).isPresent() || userRepository.existsById(patientId);
        if (!patientExists) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }

        // 3. Validate encounter if specified
        if (request.getEncounterId() != null && !request.getEncounterId().isBlank()) {
            ClinicalEncounter enc = encounterRepository.findById(request.getEncounterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Clinical encounter not found: " + request.getEncounterId()));
            if (!authz.isPatientOwner(enc.getPatientId()) && !patientId.equals(enc.getPatientId())) {
                throw new BadRequestException("Specified clinical encounter does not belong to the requested patient");
            }
        }

        // 4. Validate selected test codes against active catalog
        List<LabOrder.OrderedTestItem> orderedItems = new ArrayList<>();
        for (String code : request.getTestCodes()) {
            LabTestDefinition def = testDefinitionRepository.findByTestCode(code.trim().toUpperCase())
                    .orElseThrow(() -> new BadRequestException("Unknown test code in order: " + code));
            if (!def.isActive()) {
                throw new BadRequestException("Cannot order inactive laboratory test: " + def.getTestName() + " (" + def.getTestCode() + ")");
            }
            orderedItems.add(LabOrder.OrderedTestItem.builder()
                    .testCode(def.getTestCode())
                    .testName(def.getTestName())
                    .category(def.getCategory())
                    .specimenType(def.getSpecimenType())
                    .defaultUnit(def.getDefaultUnit())
                    .referenceRange(def.getReferenceRangeMetadata())
                    .build());
        }

        // 5. Build order with unique order number
        String orderNum = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        LabOrder order = LabOrder.builder()
                .orderNumber(orderNum)
                .patientId(patientId)
                .doctorId(doctorId)
                .encounterId(request.getEncounterId())
                .appointmentId(request.getAppointmentId())
                .orderedTests(orderedItems)
                .priority(request.getPriority() != null ? request.getPriority() : OrderPriority.ROUTINE)
                .clinicalNotes(request.getClinicalNotes())
                .status(LabOrderStatus.ORDERED)
                .orderedAt(Instant.now())
                .orderedByUserId(currentUser.getId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LabOrder saved = labOrderRepository.save(order);

        // 6. Clinical audit log
        auditService.audit(patientId, "LabOrder", saved.getId(), ClinicalAuditAction.LAB_ORDER_CREATED, "SUCCESS", null, "Ordered " + orderedItems.size() + " lab tests");

        // 7. Emit durable notification to patient outbox
        publishOutboxNotification(
                patientId,
                "LAB_ORDER_CREATED",
                saved.getId(),
                "New Laboratory Order Created",
                "Your doctor has requested " + orderedItems.size() + " laboratory test(s). Order reference: " + saved.getOrderNumber(),
                "/patient/history"
        );

        log.info("Created LabOrder: {} with {} tests for patient: {}", saved.getOrderNumber(), orderedItems.size(), patientId);
        return saved;
    }

    @Override
    public LabOrder getOrderById(String orderId) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory order not found: " + orderId));
        authz.checkLabOrderAccess(order);
        auditService.audit(order.getPatientId(), "LabOrder", order.getId(), ClinicalAuditAction.LAB_RESULT_VIEWED, "SUCCESS", null, "Viewed lab order");
        return order;
    }

    @Override
    public LabOrderResponseDTO getOrderDetails(String orderId) {
        LabOrder order = getOrderById(orderId);
        return mapToOrderResponseDTO(order);
    }

    @Override
    public Page<LabOrderResponseDTO> getOrders(LabOrderStatus status, Pageable pageable) {
        User currentUser = authz.getCurrentUser();
        Page<LabOrder> page;

        if (currentUser.getRole() == Role.PATIENT) {
            // Patient sees their own orders
            page = labOrderRepository.findByPatientId(currentUser.getId(), pageable);
        } else if (status != null) {
            page = labOrderRepository.findByStatus(status, pageable);
        } else {
            page = labOrderRepository.findAll(pageable);
        }

        List<LabOrderResponseDTO> dtoList = page.getContent().stream()
                .filter(authz::canAccessLabOrder)
                .map(this::mapToOrderResponseDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    @Override
    public List<LabOrderResponseDTO> getOrdersForPatient(String patientId) {
        authz.checkPatientAccess(patientId);
        return labOrderRepository.findByPatientIdOrderByOrderedAtDesc(patientId).stream()
                .filter(authz::canAccessLabOrder)
                .map(this::mapToOrderResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<LabOrderResponseDTO> getOrdersForDoctor(String doctorId) {
        return labOrderRepository.findByDoctorIdOrderByOrderedAtDesc(doctorId).stream()
                .filter(authz::canAccessLabOrder)
                .map(this::mapToOrderResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public LabOrder cancelOrder(String orderId, String reason) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory order not found: " + orderId));

        if (!authz.canCancelLabOrder(order)) {
            auditService.audit(order.getPatientId(), "LabOrder", orderId, ClinicalAuditAction.LAB_ACCESS_DENIED, "FAILURE", null, "Unauthorized cancellation attempt");
            throw new AccessDeniedException("You are not authorized to cancel this laboratory order");
        }

        if (order.getStatus() == LabOrderStatus.COMPLETED || order.getStatus() == LabOrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot cancel an order in status: " + order.getStatus());
        }

        // Check if any results were already released
        List<LabResult> results = labResultRepository.findByLabOrderId(orderId);
        boolean hasReleasedResults = results.stream().anyMatch(r -> r.getWorkflowStatus() == LabResultWorkflowStatus.RELEASED);
        if (hasReleasedResults) {
            throw new BadRequestException("Cannot cancel order: clinical results have already been released to patient records");
        }

        User user = authz.getCurrentUser();
        order.setStatus(LabOrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelledByUserId(user.getId());
        order.setCancellationReason(reason != null && !reason.isBlank() ? reason : "Clinician cancelled");
        order.setUpdatedAt(Instant.now());

        LabOrder saved = labOrderRepository.save(order);
        auditService.audit(order.getPatientId(), "LabOrder", orderId, ClinicalAuditAction.LAB_ORDER_CANCELLED, "SUCCESS", null, "Cancelled order: " + reason);
        log.info("Lab order {} cancelled by user {}", orderId, user.getEmail());
        return saved;
    }

    // =========================================================================
    // 3. Specimen Collection & Tracking
    // =========================================================================

    @Override
    public LabSpecimen collectSpecimen(String orderId, CollectSpecimenRequest request) {
        User currentUser = authz.getCurrentUser();
        if (!authz.canEnterLabResult()) {
            throw new AccessDeniedException("Only laboratory staff or authorized clinical personnel can collect specimens");
        }

        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory order not found: " + orderId));

        if (order.getStatus() == LabOrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot collect specimen for a cancelled laboratory order");
        }

        String specNum = "SPEC-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        LabSpecimen specimen = LabSpecimen.builder()
                .specimenNumber(specNum)
                .labOrderId(orderId)
                .patientId(order.getPatientId())
                .specimenType(request.getSpecimenType().trim())
                .collectionStatus(SpecimenStatus.COLLECTED)
                .collectedAt(Instant.now())
                .collectedByUserId(currentUser.getId())
                .notes(request.getNotes())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LabSpecimen saved = labSpecimenRepository.save(specimen);

        // Update order status to IN_PROGRESS
        if (order.getStatus() == LabOrderStatus.ORDERED || order.getStatus() == LabOrderStatus.COLLECTION_PENDING) {
            order.setStatus(LabOrderStatus.IN_PROGRESS);
            order.setUpdatedAt(Instant.now());
            labOrderRepository.save(order);
        }

        auditService.audit(order.getPatientId(), "LabSpecimen", saved.getId(), ClinicalAuditAction.SPECIMEN_COLLECTED, "SUCCESS", null, "Collected specimen " + specNum);
        return saved;
    }

    @Override
    public LabSpecimen receiveSpecimen(String specimenId) {
        User currentUser = authz.getCurrentUser();
        if (!authz.canEnterLabResult()) {
            throw new AccessDeniedException("Only laboratory personnel can record specimen receipt");
        }

        LabSpecimen specimen = labSpecimenRepository.findById(specimenId)
                .orElseThrow(() -> new ResourceNotFoundException("Specimen not found: " + specimenId));

        specimen.setCollectionStatus(SpecimenStatus.RECEIVED);
        specimen.setReceivedAt(Instant.now());
        specimen.setReceivedByUserId(currentUser.getId());
        specimen.setUpdatedAt(Instant.now());

        LabSpecimen saved = labSpecimenRepository.save(specimen);
        auditService.audit(specimen.getPatientId(), "LabSpecimen", specimen.getId(), ClinicalAuditAction.SPECIMEN_RECEIVED, "SUCCESS", null, "Received specimen " + specimen.getSpecimenNumber());
        return saved;
    }

    @Override
    public LabSpecimen rejectSpecimen(String specimenId, RejectSpecimenRequest request) {
        User currentUser = authz.getCurrentUser();
        if (!authz.canEnterLabResult()) {
            throw new AccessDeniedException("Only laboratory personnel can reject specimens");
        }

        LabSpecimen specimen = labSpecimenRepository.findById(specimenId)
                .orElseThrow(() -> new ResourceNotFoundException("Specimen not found: " + specimenId));

        specimen.setCollectionStatus(SpecimenStatus.REJECTED);
        specimen.setRejectedAt(Instant.now());
        specimen.setRejectedByUserId(currentUser.getId());
        specimen.setRejectionReason(request.getRejectionReason());
        specimen.setUpdatedAt(Instant.now());

        LabSpecimen saved = labSpecimenRepository.save(specimen);
        auditService.audit(specimen.getPatientId(), "LabSpecimen", specimen.getId(), ClinicalAuditAction.SPECIMEN_REJECTED, "SUCCESS", null, "Rejected: " + request.getRejectionReason());
        return saved;
    }

    @Override
    public List<LabSpecimen> getSpecimensForOrder(String orderId) {
        return labSpecimenRepository.findByLabOrderId(orderId);
    }

    // =========================================================================
    // 4. Lab Result Entry, Verification & Release Lifecycle
    // =========================================================================

    @Override
    public LabResult enterResult(String orderId, EnterLabResultRequest request) {
        User currentUser = authz.getCurrentUser();
        if (!authz.canEnterLabResult()) {
            throw new AccessDeniedException("Only authorized laboratory technicians or clinicians can enter laboratory results");
        }

        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory order not found: " + orderId));

        if (order.getStatus() == LabOrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot enter results for a cancelled order");
        }

        // Validate testCode belongs to this order
        LabOrder.OrderedTestItem matchingItem = order.getOrderedTests().stream()
                .filter(t -> t.getTestCode().equalsIgnoreCase(request.getTestCode().trim()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Test code '" + request.getTestCode() + "' was not ordered in order " + order.getOrderNumber()));

        // Validate specimen if provided
        if (request.getSpecimenId() != null && !request.getSpecimenId().isBlank()) {
            LabSpecimen spec = labSpecimenRepository.findById(request.getSpecimenId())
                    .orElseThrow(() -> new ResourceNotFoundException("Specimen not found: " + request.getSpecimenId()));
            if (spec.getCollectionStatus() == SpecimenStatus.REJECTED) {
                throw new BadRequestException("Cannot record result against a REJECTED specimen");
            }
        }

        // Check if an uncorrected result already exists
        Optional<LabResult> existingOpt = labResultRepository.findByLabOrderIdAndTestCode(orderId, matchingItem.getTestCode());
        LabResult result;
        if (existingOpt.isPresent()) {
            result = existingOpt.get();
            if (result.getWorkflowStatus() == LabResultWorkflowStatus.RELEASED) {
                throw new BadRequestException("Result is already RELEASED. Use the clinical correction endpoint to amend it.");
            }
        } else {
            result = LabResult.builder()
                    .labOrderId(orderId)
                    .patientId(order.getPatientId())
                    .doctorId(order.getDoctorId())
                    .encounterId(order.getEncounterId())
                    .appointmentId(order.getAppointmentId())
                    .testCode(matchingItem.getTestCode())
                    .testName(matchingItem.getTestName())
                    .revision(1)
                    .createdAt(Instant.now())
                    .build();
        }

        result.setSpecimenId(request.getSpecimenId());
        result.setResultType(request.getResultType().toUpperCase());
        result.setNumericValue(request.getNumericValue());
        result.setTextValue(request.getTextValue());
        result.setResultValue(request.getNumericValue() != null ? String.valueOf(request.getNumericValue()) : request.getTextValue());
        result.setUnit(request.getUnit() != null ? request.getUnit() : matchingItem.getDefaultUnit());
        result.setReferenceRange(request.getReferenceRange() != null ? request.getReferenceRange() : matchingItem.getReferenceRange());

        LabResultInterpretation interp = request.getInterpretation() != null ? request.getInterpretation() : LabResultInterpretation.NORMAL;
        result.setInterpretation(interp);
        result.setStatus(interp.name()); // Legacy field synchronization

        result.setWorkflowStatus(request.isSaveAsDraft() ? LabResultWorkflowStatus.DRAFT : LabResultWorkflowStatus.ENTERED);
        result.setNotes(request.getNotes());
        result.setMeasuredAt(Instant.now());
        result.setEnteredByUserId(currentUser.getId());
        result.setEnteredAt(Instant.now());
        result.setUpdatedAt(Instant.now());

        LabResult saved = labResultRepository.save(result);
        auditService.audit(order.getPatientId(), "LabResult", saved.getId(), ClinicalAuditAction.LAB_RESULT_ENTERED, "SUCCESS", null,
                "Entered " + (request.isSaveAsDraft() ? "DRAFT" : "FINAL") + " result for " + matchingItem.getTestCode());

        return saved;
    }

    @Override
    public LabResult saveResultDraft(String resultId, EnterLabResultRequest request) {
        LabResult result = labResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab result not found: " + resultId));

        if (result.getWorkflowStatus() != LabResultWorkflowStatus.DRAFT) {
            throw new BadRequestException("Can only update draft on results currently in DRAFT status");
        }

        request.setSaveAsDraft(true);
        return enterResult(result.getLabOrderId(), request);
    }

    @Override
    public LabResult verifyResult(String resultId, VerifyLabResultRequest request) {
        User currentUser = authz.getCurrentUser();
        if (!authz.canVerifyOrReleaseLabResult()) {
            auditService.audit(null, "LabResult", resultId, ClinicalAuditAction.LAB_ACCESS_DENIED, "FAILURE", null, "Unauthorized verification attempt");
            throw new AccessDeniedException("Only licensed physicians or authorized laboratory supervisors may verify clinical results");
        }

        LabResult result = labResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab result not found: " + resultId));

        if (result.getWorkflowStatus() != LabResultWorkflowStatus.ENTERED && result.getWorkflowStatus() != LabResultWorkflowStatus.DRAFT) {
            throw new BadRequestException("Cannot verify result in status: " + result.getWorkflowStatus());
        }

        result.setWorkflowStatus(LabResultWorkflowStatus.VERIFIED);
        result.setVerifiedByUserId(currentUser.getId());
        result.setVerifiedAt(Instant.now());
        if (request != null && request.getNotes() != null && !request.getNotes().isBlank()) {
            result.setNotes((result.getNotes() != null ? result.getNotes() + " | " : "") + "Verifier Note: " + request.getNotes());
        }
        result.setUpdatedAt(Instant.now());

        LabResult saved = labResultRepository.save(result);
        auditService.audit(result.getPatientId(), "LabResult", saved.getId(), ClinicalAuditAction.LAB_RESULT_VERIFIED, "SUCCESS", null, "Verified result for " + result.getTestCode());
        log.info("Lab result {} verified by clinician {}", resultId, currentUser.getEmail());
        return saved;
    }

    @Override
    public LabResult releaseResult(String resultId) {
        User currentUser = authz.getCurrentUser();
        if (!authz.canVerifyOrReleaseLabResult()) {
            auditService.audit(null, "LabResult", resultId, ClinicalAuditAction.LAB_ACCESS_DENIED, "FAILURE", null, "Unauthorized release attempt");
            throw new AccessDeniedException("Only licensed clinicians or administrators may release laboratory results to patient records");
        }

        LabResult result = labResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab result not found: " + resultId));

        // Idempotent return if already released
        if (result.getWorkflowStatus() == LabResultWorkflowStatus.RELEASED) {
            return result;
        }

        // Enforce strict workflow gate: Must be verified before release
        if (result.getWorkflowStatus() != LabResultWorkflowStatus.VERIFIED) {
            throw new BadRequestException("Cannot release an unverified laboratory result. Current status: " + result.getWorkflowStatus());
        }

        result.setWorkflowStatus(LabResultWorkflowStatus.RELEASED);
        result.setReleasedByUserId(currentUser.getId());
        result.setReleasedAt(Instant.now());
        result.setTestDate(result.getReleasedAt().toString().substring(0, 10)); // sync legacy date
        result.setUpdatedAt(Instant.now());

        LabResult saved = labResultRepository.save(result);

        // Update LabOrder status if all tests are released
        if (saved.getLabOrderId() != null) {
            labOrderRepository.findById(saved.getLabOrderId()).ifPresent(order -> {
                List<LabResult> allResults = labResultRepository.findByLabOrderId(order.getId());
                boolean allDone = order.getOrderedTests().stream()
                        .allMatch(item -> allResults.stream().anyMatch(r ->
                                r.getTestCode().equalsIgnoreCase(item.getTestCode()) &&
                                r.getWorkflowStatus() == LabResultWorkflowStatus.RELEASED));
                if (allDone) {
                    order.setStatus(LabOrderStatus.COMPLETED);
                    order.setUpdatedAt(Instant.now());
                    labOrderRepository.save(order);
                }
            });
        }

        auditService.audit(result.getPatientId(), "LabResult", saved.getId(), ClinicalAuditAction.LAB_RESULT_RELEASED, "SUCCESS", null, "Released report for " + result.getTestCode());

        // Emit durable outbox notification to patient
        publishOutboxNotification(
                result.getPatientId(),
                "LAB_RESULT_RELEASED",
                saved.getId(),
                "Laboratory Report Available",
                "Your laboratory test result for " + result.getTestName() + " is now verified and available in your health portal.",
                "/patient/history"
        );

        log.info("Lab result {} released to patient {} by clinician {}", resultId, result.getPatientId(), currentUser.getEmail());
        return saved;
    }

    @Override
    public LabResult correctResult(String resultId, CorrectLabResultRequest request) {
        User currentUser = authz.getCurrentUser();
        if (!authz.canVerifyOrReleaseLabResult()) {
            auditService.audit(null, "LabResult", resultId, ClinicalAuditAction.LAB_ACCESS_DENIED, "FAILURE", null, "Unauthorized correction attempt");
            throw new AccessDeniedException("Only qualified clinicians may execute laboratory report corrections");
        }

        LabResult original = labResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Original lab result not found: " + resultId));

        if (original.getWorkflowStatus() != LabResultWorkflowStatus.RELEASED) {
            throw new BadRequestException("Only RELEASED results can be corrected through the amendment workflow");
        }

        // Mark original as CORRECTED (archived)
        original.setWorkflowStatus(LabResultWorkflowStatus.CORRECTED);
        original.setUpdatedAt(Instant.now());
        labResultRepository.save(original);

        // Create new revision
        int newRevision = (original.getRevision() != null ? original.getRevision() : 1) + 1;
        LabResultInterpretation interp = request.getInterpretation() != null ? request.getInterpretation() : original.getInterpretation();

        LabResult amended = LabResult.builder()
                .labOrderId(original.getLabOrderId())
                .specimenId(original.getSpecimenId())
                .encounterId(original.getEncounterId())
                .appointmentId(original.getAppointmentId())
                .patientId(original.getPatientId())
                .doctorId(original.getDoctorId())
                .testCode(original.getTestCode())
                .testName(original.getTestName())
                .resultType(original.getResultType())
                .numericValue(request.getNumericValue() != null ? request.getNumericValue() : original.getNumericValue())
                .textValue(request.getTextValue() != null ? request.getTextValue() : original.getTextValue())
                .resultValue(request.getNumericValue() != null ? String.valueOf(request.getNumericValue()) : request.getTextValue())
                .unit(request.getUnit() != null ? request.getUnit() : original.getUnit())
                .referenceRange(request.getReferenceRange() != null ? request.getReferenceRange() : original.getReferenceRange())
                .interpretation(interp)
                .status(interp != null ? interp.name() : "NORMAL")
                .workflowStatus(LabResultWorkflowStatus.ENTERED) // Requires re-verification and re-release!
                .measuredAt(Instant.now())
                .enteredByUserId(currentUser.getId())
                .enteredAt(Instant.now())
                .correctedResultId(original.getId())
                .correctionReason(request.getCorrectionReason())
                .revision(newRevision)
                .notes(request.getNotes())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LabResult saved = labResultRepository.save(amended);
        auditService.audit(original.getPatientId(), "LabResult", saved.getId(), ClinicalAuditAction.LAB_RESULT_CORRECTED, "SUCCESS", null,
                "Amended result revision " + newRevision + ": " + request.getCorrectionReason());

        log.info("Lab result {} corrected to revision {} by {}", resultId, newRevision, currentUser.getEmail());
        return saved;
    }

    @Override
    public List<LabResult> getResultsForOrder(String orderId) {
        User currentUser = authz.getCurrentUser();
        List<LabResult> results = labResultRepository.findByLabOrderId(orderId);

        if (currentUser.getRole() == Role.PATIENT) {
            // Patient only sees RELEASED results
            return results.stream()
                    .filter(r -> r.getWorkflowStatus() == LabResultWorkflowStatus.RELEASED)
                    .collect(Collectors.toList());
        }

        return results;
    }

    @Override
    public LabResult getResultById(String resultId) {
        LabResult result = labResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab result not found: " + resultId));
        authz.checkLabResultAccess(result);
        auditService.audit(result.getPatientId(), "LabResult", result.getId(), ClinicalAuditAction.LAB_RESULT_VIEWED, "SUCCESS", null, "Viewed result " + result.getTestCode());
        return result;
    }

    @Override
    public List<LabResult> getPatientReleasedReports(String patientId) {
        authz.checkPatientAccess(patientId);
        return labResultRepository.findByPatientIdAndWorkflowStatus(patientId, LabResultWorkflowStatus.RELEASED);
    }

    // =========================================================================
    // 5. PDF Report Generation
    // =========================================================================

    @Override
    public byte[] generateReportPdf(String resultId) {
        LabResult result = labResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab result not found: " + resultId));

        authz.checkLabResultAccess(result);

        // Patients can only download RELEASED reports
        User caller = authz.getCurrentUser();
        if (caller.getRole() == Role.PATIENT && result.getWorkflowStatus() != LabResultWorkflowStatus.RELEASED) {
            throw new AccessDeniedException("Results must be verified and released by clinical staff before PDF download");
        }

        LabOrder order = result.getLabOrderId() != null ? labOrderRepository.findById(result.getLabOrderId()).orElse(null) : null;
        LabSpecimen specimen = result.getSpecimenId() != null ? labSpecimenRepository.findById(result.getSpecimenId()).orElse(null) : null;

        User patientUser = resolveUser(result.getPatientId());
        User doctorUser = result.getDoctorId() != null ? resolveUser(result.getDoctorId()) : null;
        User verifierUser = result.getVerifiedByUserId() != null ? resolveUser(result.getVerifiedByUserId()) : null;

        byte[] pdf = labPdfService.generateLabReportPdf(result, order, specimen, patientUser, doctorUser, verifierUser);
        auditService.audit(result.getPatientId(), "LabResult", result.getId(), ClinicalAuditAction.LAB_RESULT_DOWNLOADED, "SUCCESS", null, "Downloaded PDF report");

        return pdf;
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private LabOrderResponseDTO mapToOrderResponseDTO(LabOrder order) {
        User patient = resolveUser(order.getPatientId());
        User doctor = order.getDoctorId() != null ? resolveUser(order.getDoctorId()) : null;
        List<LabSpecimen> specimens = labSpecimenRepository.findByLabOrderId(order.getId());
        List<LabResult> results = getResultsForOrder(order.getId());

        return LabOrderResponseDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .patientId(order.getPatientId())
                .patientName(patient != null ? patient.getFullName() : "Patient")
                .doctorId(order.getDoctorId())
                .doctorName(doctor != null ? doctor.getFullName() : "Doctor")
                .encounterId(order.getEncounterId())
                .appointmentId(order.getAppointmentId())
                .orderedTests(order.getOrderedTests())
                .priority(order.getPriority())
                .clinicalNotes(order.getClinicalNotes())
                .status(order.getStatus())
                .orderedAt(order.getOrderedAt())
                .orderedByUserId(order.getOrderedByUserId())
                .cancelledAt(order.getCancelledAt())
                .cancellationReason(order.getCancellationReason())
                .specimens(specimens)
                .results(results)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private String resolveDoctorIdForCaller(User user) {
        if (user.getRole() == Role.DOCTOR) {
            return doctorRepository.findByUserId(user.getId())
                    .map(Doctor::getId)
                    .orElse(user.getId());
        }
        return user.getId();
    }

    private User resolveUser(String idOrUserId) {
        if (idOrUserId == null) return null;
        return userRepository.findById(idOrUserId)
                .or(() -> patientRepository.findById(idOrUserId).flatMap(p -> userRepository.findById(p.getUserId())))
                .or(() -> doctorRepository.findById(idOrUserId).flatMap(d -> userRepository.findById(d.getUserId())))
                .orElse(null);
    }

    private void publishOutboxNotification(String patientIdOrUserId, String eventType, String aggregateId, String title, String message, String actionUrl) {
        try {
            User patientUser = resolveUser(patientIdOrUserId);
            String recipientUserId = patientUser != null ? patientUser.getId() : patientIdOrUserId;

            NotificationOutbox inApp = NotificationOutbox.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .recipientUserId(recipientUserId)
                    .recipientRole("PATIENT")
                    .channel(OutboxChannel.IN_APP)
                    .payloadVersion(1)
                    .status(OutboxStatus.PENDING)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .nextAttemptAt(Instant.now())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .deduplicationKey("lab:" + eventType.toLowerCase() + ":" + aggregateId + ":" + recipientUserId)
                    .title(title)
                    .message(message)
                    .actionUrl(actionUrl)
                    .metadata(Map.of("aggregateId", aggregateId, "eventType", eventType))
                    .build();

            outboxRepository.save(inApp);
        } catch (Exception e) {
            log.warn("Failed to publish outbox notification for {}: {}", aggregateId, e.getMessage());
        }
    }
}
