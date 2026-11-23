package com.hospital.backend.service;

import com.hospital.backend.model.LabOrder;
import com.hospital.backend.model.LabResult;
import com.hospital.backend.model.LabSpecimen;
import com.hospital.backend.model.User;

public interface LabPdfService {
    byte[] generateLabReportPdf(LabResult result, LabOrder order, LabSpecimen specimen, User patient, User doctor, User verifier);
}
