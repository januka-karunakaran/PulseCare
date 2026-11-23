package com.hospital.backend.service.impl;

import com.hospital.backend.model.LabOrder;
import com.hospital.backend.model.LabResult;
import com.hospital.backend.model.LabSpecimen;
import com.hospital.backend.model.User;
import com.hospital.backend.service.LabPdfService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class LabPdfServiceImpl implements LabPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public byte[] generateLabReportPdf(LabResult result, LabOrder order, LabSpecimen specimen, User patient, User doctor, User verifier) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setLeading(14.0f);

                // Title Banner
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                stream.newLineAtOffset(50, 740);
                stream.showText("PulseCare Health System");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                stream.showText("CLINICAL LABORATORY DIAGNOSTIC REPORT");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText("Status: " + (result.getWorkflowStatus() != null ? result.getWorkflowStatus().name() : "RELEASED"));
                if (result.getRevision() != null && result.getRevision() > 1) {
                    stream.showText(" (REVISION " + result.getRevision() + " - AMENDED)");
                }
                stream.endText();

                // Horizontal Rule
                stream.setLineWidth(1.0f);
                stream.moveTo(50, 685);
                stream.lineTo(550, 685);
                stream.stroke();

                // Patient & Order Metadata
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 11);
                stream.newLineAtOffset(50, 665);
                stream.showText("PATIENT & ORDER INFORMATION");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA, 10);
                String pName = patient != null ? patient.getFullName() : "Patient ID: " + result.getPatientId();
                String pEmail = patient != null && patient.getEmail() != null ? patient.getEmail() : "N/A";
                stream.showText("Patient Name: " + pName);
                stream.newLine();
                stream.showText("Patient Email: " + pEmail);
                stream.newLine();

                String docName = doctor != null ? "Dr. " + doctor.getFullName() : (result.getDoctorId() != null ? "ID: " + result.getDoctorId() : "Staff Clinician");
                stream.showText("Ordering Clinician: " + docName);
                stream.newLine();

                String ordNum = order != null ? order.getOrderNumber() : (result.getLabOrderId() != null ? result.getLabOrderId() : "N/A");
                stream.showText("Order Number: " + ordNum);
                stream.newLine();

                if (specimen != null) {
                    stream.showText("Specimen Number: " + specimen.getSpecimenNumber() + " | Type: " + specimen.getSpecimenType());
                    stream.newLine();
                }
                stream.endText();

                // Results Table Header
                stream.moveTo(50, 560);
                stream.lineTo(550, 560);
                stream.stroke();

                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 11);
                stream.newLineAtOffset(50, 540);
                stream.showText("TEST RESULTS & OBSERVATIONS");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText(String.format("%-25s %-15s %-12s %-20s %-10s", "Test Name", "Result", "Unit", "Reference Range", "Flag"));
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA, 10);
                String tName = result.getTestName() != null ? result.getTestName() : "Laboratory Test";
                String val = result.getResultValue() != null ? result.getResultValue() :
                        (result.getNumericValue() != null ? String.valueOf(result.getNumericValue()) :
                        (result.getTextValue() != null ? result.getTextValue() : "N/A"));
                String unit = result.getUnit() != null ? result.getUnit() : "";
                String ref = result.getReferenceRange() != null ? result.getReferenceRange() : "Standard";
                String flag = result.getInterpretation() != null ? result.getInterpretation().name() : (result.getStatus() != null ? result.getStatus() : "NORMAL");

                stream.showText(String.format("%-25s %-15s %-12s %-20s %-10s",
                        truncate(tName, 24), truncate(val, 14), truncate(unit, 11), truncate(ref, 19), flag));
                stream.newLine();

                if (result.getNotes() != null && !result.getNotes().isBlank()) {
                    stream.newLine();
                    stream.setFont(PDType1Font.HELVETICA_OBLIQUE, 9);
                    stream.showText("Clinical Note: " + truncate(result.getNotes(), 70));
                    stream.newLine();
                }

                if (result.getCorrectionReason() != null && !result.getCorrectionReason().isBlank()) {
                    stream.newLine();
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 9);
                    stream.showText("Amendment Reason: " + truncate(result.getCorrectionReason(), 70));
                    stream.newLine();
                }

                stream.endText();

                // Verification and Release Sign-Off
                stream.moveTo(50, 380);
                stream.lineTo(550, 380);
                stream.stroke();

                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.newLineAtOffset(50, 360);
                stream.showText("CLINICAL VERIFICATION & RELEASE SIGN-OFF");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA, 9);
                String verifierName = verifier != null ? ("Dr. " + verifier.getFullName()) : "Authorized Medical Laboratory Officer";
                stream.showText("Verified By: " + verifierName);
                stream.newLine();

                String verDate = result.getVerifiedAt() != null ? DATE_FMT.format(result.getVerifiedAt()) : "Pending";
                stream.showText("Verification Timestamp: " + verDate);
                stream.newLine();

                String relDate = result.getReleasedAt() != null ? DATE_FMT.format(result.getReleasedAt()) : "Pending";
                stream.showText("Official Release Timestamp: " + relDate);
                stream.newLine();

                stream.newLine();
                stream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
                stream.showText("CONFIDENTIAL HEALTHCARE DOCUMENT. Generated electronically by PulseCare 2.0 Clinical Information System.");
                stream.newLine();
                stream.showText("This report is intended strictly for the patient and authorized medical personnel.");
                stream.endText();
            }

            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate laboratory PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("Error rendering laboratory PDF report: " + e.getMessage(), e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }
}
