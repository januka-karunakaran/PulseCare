package com.hospital.backend.security;

import com.hospital.backend.enums.Role;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.Appointment;
import com.hospital.backend.model.Doctor;
import com.hospital.backend.model.Invoice;
import com.hospital.backend.model.Patient;
import com.hospital.backend.model.Prescription;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.AppointmentRepository;
import com.hospital.backend.repository.DoctorRepository;
import com.hospital.backend.repository.PatientRepository;
import com.hospital.backend.repository.PrescriptionRepository;
import com.hospital.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reusable clinical object-level authorization service.
 * Enforces strict record ownership and verified doctor-patient clinical relationships.
 */
@Service("authz")
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new AccessDeniedException("User is not authenticated");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user account not found"));
    }

    public boolean isCurrentUser(String userId) {
        if (userId == null) return false;
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || userId.equals(user.getId());
    }

    public boolean canAccessPatient(String patientId) {
        if (patientId == null) return false;
        User user = getCurrentUser();

        // 1. Administrators and Pharmacists have organizational access
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.PHARMACIST) {
            return true;
        }

        // 2. Patients can only access their own patient profile via stable account-to-patient identity mapping
        if (user.getRole() == Role.PATIENT) {
            return isPatientOwner(user, patientId);
        }

        // 3. Doctors can access patient records only when a valid clinical relationship exists
        if (user.getRole() == Role.DOCTOR) {
            return hasDoctorPatientRelationship(user.getId(), patientId);
        }

        return false;
    }

    public boolean hasDoctorPatientRelationship(String doctorUserId, String patientId) {
        if (doctorUserId == null || patientId == null) return false;

        Optional<Doctor> docOpt = doctorRepository.findByUserId(doctorUserId)
                .or(() -> doctorRepository.findById(doctorUserId));

        Set<String> doctorIds = new HashSet<>();
        doctorIds.add(doctorUserId);
        docOpt.ifPresent(d -> {
            if (d.getId() != null) doctorIds.add(d.getId());
            if (d.getUserId() != null) doctorIds.add(d.getUserId());
        });

        Set<String> patientIds = new HashSet<>();
        patientIds.add(patientId);
        patientRepository.findById(patientId).ifPresent(p -> {
            if (p.getUserId() != null) patientIds.add(p.getUserId());
        });
        patientRepository.findByUserId(patientId).ifPresent(p -> {
            if (p.getUserId() != null) patientIds.add(p.getUserId());
        });

        // Check if doctor has an appointment scheduled or completed with this patient
        for (String docId : doctorIds) {
            List<Appointment> doctorAppts = appointmentRepository.findByDoctorId(docId);
            boolean hasAppointment = doctorAppts.stream().anyMatch(a ->
                    patientIds.contains(a.getPatientId()));
            if (hasAppointment) return true;

            List<Prescription> docPrescriptions = prescriptionRepository.findByDoctorId(docId);
            boolean hasPrescription = docPrescriptions.stream().anyMatch(p ->
                    patientIds.contains(p.getPatientId()));
            if (hasPrescription) return true;
        }

        return false;
    }

    public void checkPatientAccess(String patientId) {
        if (!canAccessPatient(patientId)) {
            log.warn("Access denied for user to patient record: {}", patientId);
            throw new AccessDeniedException("You are not authorized to view or modify this patient's medical records");
        }
    }

    public void checkAppointmentAccess(Appointment appointment) {
        if (appointment == null) {
            throw new ResourceNotFoundException("Appointment not found");
        }
        User user = getCurrentUser();

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.BILLING_STAFF) {
            return;
        }

        if (user.getRole() == Role.PATIENT) {
            if (isPatientOwner(user, appointment.getPatientId())) {
                return;
            }
        }

        if (user.getRole() == Role.DOCTOR) {
            if (isDoctorOwner(user, appointment.getDoctorId())) {
                return;
            }
        }

        log.warn("Unauthorized attempt to access appointment id: {} by user: {}", appointment.getId(), user.getEmail());
        throw new AccessDeniedException("You are not authorized to access this appointment");
    }

    public void checkAppointmentCancellation(Appointment appointment) {
        if (appointment == null) {
            throw new ResourceNotFoundException("Appointment not found");
        }
        User user = getCurrentUser();

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.BILLING_STAFF) {
            return;
        }

        if (user.getRole() == Role.PATIENT && isPatientOwner(user, appointment.getPatientId())) {
            return;
        }

        if (user.getRole() == Role.DOCTOR && isDoctorOwner(user, appointment.getDoctorId())) {
            return;
        }

        log.warn("Unauthorized attempt to cancel appointment id: {} by user: {}", appointment.getId(), user.getEmail());
        throw new AccessDeniedException("You are not authorized to cancel this appointment");
    }

    public void checkPrescriptionAccess(Prescription prescription) {
        if (prescription == null) {
            throw new ResourceNotFoundException("Prescription not found");
        }
        User user = getCurrentUser();

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.BILLING_STAFF) {
            return;
        }

        if (user.getRole() == Role.PATIENT && isPatientOwner(user, prescription.getPatientId())) {
            return;
        }

        if (user.getRole() == Role.DOCTOR && isDoctorOwner(user, prescription.getDoctorId())) {
            return;
        }

        log.warn("Unauthorized attempt to access prescription id: {} by user: {}", prescription.getId(), user.getEmail());
        throw new AccessDeniedException("You are not authorized to view or download this prescription");
    }

    public void checkInvoiceAccess(Invoice invoice) {
        if (invoice == null) {
            throw new ResourceNotFoundException("Invoice not found");
        }
        User user = getCurrentUser();

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.BILLING_STAFF) {
            return;
        }

        if (user.getRole() == Role.PATIENT && isPatientOwner(user, invoice.getPatientId())) {
            return;
        }

        log.warn("Unauthorized attempt to view invoice id: {} by user: {}", invoice.getId(), user.getEmail());
        throw new AccessDeniedException("You are not authorized to access this billing invoice");
    }

    public boolean isPatientOwner(String patientId) {
        return isPatientOwner(getCurrentUser(), patientId);
    }

    public boolean isPatientOwner(User user, String patientId) {
        if (patientId == null || user == null) return false;
        // Direct match against user's primary key
        if (patientId.equals(user.getId())) return true;
        // Match against Patient document's userId or id
        boolean matchesPatientRecord = patientRepository.findById(patientId)
                .map(p -> user.getId().equals(p.getUserId()) || user.getId().equals(p.getId()))
                .orElseGet(() -> patientRepository.findByUserId(patientId)
                        .map(p -> user.getId().equals(p.getUserId()) || user.getId().equals(p.getId()))
                        .orElse(false));
        if (matchesPatientRecord) return true;

        // Check if caller's patient profile ID matches the requested patientId
        return patientRepository.findByUserId(user.getId())
                .map(p -> patientId.equals(p.getId()) || patientId.equals(p.getUserId()))
                .orElse(false);
    }

    public boolean isDoctorOwner(String doctorId) {
        return isDoctorOwner(getCurrentUser(), doctorId);
    }

    public boolean isDoctorOwner(User user, String doctorId) {
        if (doctorId == null || user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        if (user.getRole() != Role.DOCTOR) return false;
        if (doctorId.equals(user.getId())) return true;
        return doctorRepository.findById(doctorId)
                .map(d -> user.getId().equals(d.getUserId()) || user.getId().equals(d.getId()))
                .orElseGet(() -> doctorRepository.findByUserId(doctorId)
                        .map(d -> user.getId().equals(d.getUserId()))
                        .orElse(false));
    }

    public boolean canAccessEncounter(com.hospital.backend.model.ClinicalEncounter encounter) {
        if (encounter == null) return false;
        User user = getCurrentUser();

        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        if (user.getRole() == Role.PATIENT) {
            // Patients can only view SIGNED or AMENDED encounters for their own identity
            if (encounter.getStatus() == com.hospital.backend.enums.EncounterStatus.DRAFT ||
                encounter.getStatus() == com.hospital.backend.enums.EncounterStatus.IN_PROGRESS) {
                return false;
            }
            return isPatientOwner(user, encounter.getPatientId());
        }

        if (user.getRole() == Role.DOCTOR) {
            // Doctors can access if assigned doctor or has verified clinical relationship
            return isDoctorOwner(user, encounter.getDoctorId()) ||
                    hasDoctorPatientRelationship(user.getId(), encounter.getPatientId());
        }

        return false;
    }

    public void checkEncounterAccess(com.hospital.backend.model.ClinicalEncounter encounter) {
        if (encounter == null) {
            throw new ResourceNotFoundException("Clinical encounter not found");
        }
        if (!canAccessEncounter(encounter)) {
            log.warn("Unauthorized attempt to access clinical encounter id: {} by user: {}", encounter.getId(), getCurrentUser().getEmail());
            throw new AccessDeniedException("You are not authorized to access this clinical encounter");
        }
    }

    public void checkEncounterSigning(com.hospital.backend.model.ClinicalEncounter encounter) {
        if (encounter == null) {
            throw new ResourceNotFoundException("Clinical encounter not found");
        }
        User user = getCurrentUser();
        if (user.getRole() != Role.DOCTOR || !isDoctorOwner(user, encounter.getDoctorId())) {
            log.warn("Unauthorized attempt to sign clinical encounter id: {} by user: {}", encounter.getId(), user.getEmail());
            throw new AccessDeniedException("Only the assigned doctor is authorized to sign or amend this clinical encounter");
        }
    }

    public boolean canModifyClinicalProfile(String patientId) {
        if (patientId == null) return false;
        User user = getCurrentUser();
        // Only clinical staff (Doctors) or Admins can modify clinical profile/allergies/conditions
        if (user.getRole() == Role.ADMIN) return true;
        if (user.getRole() == Role.DOCTOR) {
            return hasDoctorPatientRelationship(user.getId(), patientId);
        }
        return false;
    }

    public boolean canAccessLabOrder(com.hospital.backend.model.LabOrder order) {
        if (order == null) return false;
        User user = getCurrentUser();

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.LAB_TECH) {
            return true;
        }

        if (user.getRole() == Role.PATIENT) {
            // Patient can only see their own order if not in DRAFT
            if (order.getStatus() == com.hospital.backend.enums.LabOrderStatus.DRAFT) {
                return false;
            }
            return isPatientOwner(user, order.getPatientId());
        }

        if (user.getRole() == Role.DOCTOR) {
            return isDoctorOwner(user, order.getDoctorId()) ||
                    hasDoctorPatientRelationship(user.getId(), order.getPatientId());
        }

        return false;
    }

    public void checkLabOrderAccess(com.hospital.backend.model.LabOrder order) {
        if (order == null) {
            throw new ResourceNotFoundException("Lab order not found");
        }
        if (!canAccessLabOrder(order)) {
            log.warn("Unauthorized attempt to access lab order id: {} by user: {}", order.getId(), getCurrentUser().getEmail());
            throw new AccessDeniedException("You are not authorized to access this laboratory order");
        }
    }

    public boolean canCreateLabOrder(String doctorId, String patientId) {
        User user = getCurrentUser();
        if (user.getRole() == Role.ADMIN) return true;
        if (user.getRole() == Role.DOCTOR) {
            return isDoctorOwner(user, doctorId) || hasDoctorPatientRelationship(user.getId(), patientId);
        }
        return false;
    }

    public boolean canCancelLabOrder(com.hospital.backend.model.LabOrder order) {
        if (order == null) return false;
        User user = getCurrentUser();
        if (user.getRole() == Role.ADMIN) return true;
        if (user.getRole() == Role.DOCTOR) {
            return isDoctorOwner(user, order.getDoctorId());
        }
        return false;
    }

    public boolean canAccessLabResult(com.hospital.backend.model.LabResult result) {
        if (result == null) return false;
        User user = getCurrentUser();

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.LAB_TECH) {
            return true;
        }

        if (user.getRole() == Role.PATIENT) {
            // Patients can ONLY view explicitly RELEASED results for their own identity (unverified legacy records are strictly hidden)
            if (result.getWorkflowStatus() != com.hospital.backend.enums.LabResultWorkflowStatus.RELEASED) {
                return false;
            }
            return isPatientOwner(user, result.getPatientId());
        }

        if (user.getRole() == Role.DOCTOR) {
            return isDoctorOwner(user, result.getDoctorId()) ||
                    hasDoctorPatientRelationship(user.getId(), result.getPatientId());
        }

        return false;
    }

    public void checkLabResultAccess(com.hospital.backend.model.LabResult result) {
        if (result == null) {
            throw new ResourceNotFoundException("Lab result not found");
        }
        if (!canAccessLabResult(result)) {
            log.warn("Unauthorized attempt to access lab result id: {} by user: {}", result.getId(), getCurrentUser().getEmail());
            throw new AccessDeniedException("You are not authorized to access this laboratory result");
        }
    }

    public boolean canVerifyLabResult() {
        User user = getCurrentUser();
        // Clinical verification requires qualified medical authority (physicians only; ADMIN is not automatically a clinician)
        return user.getRole() == Role.DOCTOR;
    }

    public boolean canReleaseLabResult() {
        User user = getCurrentUser();
        return user.getRole() == Role.DOCTOR || user.getRole() == Role.ADMIN;
    }

    public boolean canVerifyOrReleaseLabResult() {
        return canReleaseLabResult();
    }

    public boolean canEnterLabResult() {
        User user = getCurrentUser();
        return user.getRole() == Role.LAB_TECH || user.getRole() == Role.DOCTOR || user.getRole() == Role.ADMIN;
    }

    public boolean canManagePharmacyCatalog() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.PHARMACIST;
    }

    public boolean canManagePharmacyInventory() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.PHARMACIST;
    }

    public boolean canDispenseMedication() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.PHARMACIST;
    }

    public boolean canViewPharmacyQueue() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.PHARMACIST;
    }

    public boolean canAccessDispense(String patientId, String doctorId) {
        if (patientId == null) return false;
        User user = getCurrentUser();
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.PHARMACIST) return true;
        if (user.getRole() == Role.PATIENT) {
            return isPatientOwner(user, patientId);
        }
        if (user.getRole() == Role.DOCTOR) {
            Optional<Doctor> doc = doctorRepository.findByUserId(user.getId());
            if (doc.isPresent() && doc.get().getId().equals(doctorId)) return true;
            return hasDoctorPatientRelationship(user.getId(), patientId);
        }
        return false;
    }

    public boolean canManageBilling() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.BILLING_STAFF;
    }

    public boolean canManageBillingCatalog() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.BILLING_STAFF;
    }

    public boolean canRecordPayments() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.BILLING_STAFF;
    }

    public boolean canProcessRefunds() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.BILLING_STAFF;
    }

    public void checkPaymentAccess(com.hospital.backend.model.BillingPayment payment) {
        if (payment == null) {
            throw new ResourceNotFoundException("Payment not found");
        }
        User user = getCurrentUser();
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.BILLING_STAFF) {
            return;
        }
        if (user.getRole() == Role.PATIENT && isPatientOwner(user, payment.getPatientId())) {
            return;
        }
        log.warn("Unauthorized attempt to access payment id: {} by user: {}", payment.getId(), user.getEmail());
        throw new AccessDeniedException("You are not authorized to access this payment record");
    }
}
