package com.hospital.backend.service;

import com.hospital.backend.dto.lab.*;
import com.hospital.backend.enums.LabOrderStatus;
import com.hospital.backend.model.LabOrder;
import com.hospital.backend.model.LabResult;
import com.hospital.backend.model.LabSpecimen;
import com.hospital.backend.model.LabTestDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LabService {

    // Catalog
    LabTestDefinition createTestDefinition(CreateLabTestRequest request);
    LabTestDefinition updateTestDefinition(String testId, UpdateLabTestRequest request);
    void deactivateTestDefinition(String testId);
    List<LabTestDefinition> getActiveTests();
    Page<LabTestDefinition> searchTests(String query, Pageable pageable);
    LabTestDefinition getTestById(String testId);

    // Orders
    LabOrder createOrder(CreateLabOrderRequest request);
    LabOrder getOrderById(String orderId);
    LabOrderResponseDTO getOrderDetails(String orderId);
    Page<LabOrderResponseDTO> getOrders(LabOrderStatus status, Pageable pageable);
    List<LabOrderResponseDTO> getOrdersForPatient(String patientId);
    List<LabOrderResponseDTO> getOrdersForDoctor(String doctorId);
    LabOrder cancelOrder(String orderId, String reason);

    // Specimens
    LabSpecimen collectSpecimen(String orderId, CollectSpecimenRequest request);
    LabSpecimen receiveSpecimen(String specimenId);
    LabSpecimen rejectSpecimen(String specimenId, RejectSpecimenRequest request);
    List<LabSpecimen> getSpecimensForOrder(String orderId);

    // Results & Lifecycle
    LabResult enterResult(String orderId, EnterLabResultRequest request);
    LabResult saveResultDraft(String resultId, EnterLabResultRequest request);
    LabResult verifyResult(String resultId, VerifyLabResultRequest request);
    LabResult releaseResult(String resultId);
    LabResult correctResult(String resultId, CorrectLabResultRequest request);
    List<LabResult> getResultsForOrder(String orderId);
    LabResult getResultById(String resultId);
    List<LabResult> getPatientReleasedReports(String patientId);

    // PDF
    byte[] generateReportPdf(String resultId);
}
