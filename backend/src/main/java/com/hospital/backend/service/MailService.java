package com.hospital.backend.service;

/**
 * Sends transactional HTML emails via SMTP.
 * All methods are no-ops or safely handle errors when mail is disabled.
 */
public interface MailService {

    void sendAppointmentConfirmedToPatient(
            String toEmail, String patientName,
            String doctorName, String date, String time);

    void sendAppointmentConfirmedToDoctor(
            String toEmail, String doctorName,
            String patientName, String date, String time);

    void sendAppointmentCancelledToPatient(
            String toEmail, String patientName,
            String doctorName, String date, String time);

    void sendAppointmentCancelledToDoctor(
            String toEmail, String doctorName,
            String patientName, String date, String time);

    void sendAppointmentUpdatedToPatient(
            String toEmail, String patientName,
            String doctorName, String newStatus);

    void sendAppointmentReminderToPatient(
            String toEmail, String patientName,
            String doctorName, String date, String time);

    void sendAppointmentReminderToDoctor(
            String toEmail, String doctorName,
            String patientName, String date, String time);

    void sendPrescriptionReadyToPatient(
            String toEmail, String patientName, String doctorName, String diagnosis);
}
