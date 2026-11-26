package com.hospital.backend.service;

import com.hospital.backend.dto.bed.AdmissionRequestDTO;
import com.hospital.backend.dto.bed.AdmissionResponseDTO;
import com.hospital.backend.dto.bed.DischargeRequestDTO;
import com.hospital.backend.dto.bed.TransferRequestDTO;
import com.hospital.backend.model.Bed;
import com.hospital.backend.model.BedOccupancyEvent;
import com.hospital.backend.model.BedTransferRecord;

import java.util.List;

public interface AdmissionService {

    AdmissionResponseDTO admitPatient(AdmissionRequestDTO request);

    BedTransferRecord transferBed(String admissionId, TransferRequestDTO request);

    AdmissionResponseDTO prepareDischarge(String admissionId, DischargeRequestDTO request);

    AdmissionResponseDTO dischargePatient(String admissionId, DischargeRequestDTO request);

    Bed markBedCleaning(String bedId);

    Bed markBedReady(String bedId);

    Bed markBedMaintenance(String bedId);

    AdmissionResponseDTO getAdmissionById(String admissionId);

    List<AdmissionResponseDTO> getActiveAdmissions();

    List<AdmissionResponseDTO> getPatientAdmissions(String patientId);

    List<AdmissionResponseDTO> getDoctorAdmissions(String doctorId);

    List<BedTransferRecord> getAdmissionTransfers(String admissionId);

    List<BedOccupancyEvent> getAdmissionEvents(String admissionId);

    List<BedOccupancyEvent> getBedOccupancyHistory(String bedId);
}
