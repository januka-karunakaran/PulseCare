package com.hospital.backend.service;

import com.hospital.backend.enums.ClinicalAuditAction;

public interface ClinicalAuditService {
    void audit(String patientId,
               String resourceType,
               String resourceId,
               ClinicalAuditAction action,
               String outcome,
               String correlationId,
               String details);
}
