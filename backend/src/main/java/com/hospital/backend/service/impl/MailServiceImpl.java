package com.hospital.backend.service.impl;

import com.hospital.backend.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:noreply@pulsecare.com}")
    private String mailFrom;

    @Value("${app.frontend.url:${app.frontend-url:http://localhost:3000}}")
    private String frontendBaseUrl;

    // ─── Appointment Confirmed ────────────────────────────────────────────────

    @Override
    @Async
    public void sendAppointmentConfirmedToPatient(
            String toEmail, String patientName,
            String doctorName, String date, String time) {
        if (!canSend(toEmail)) return;
        String subject = "✅ Appointment Confirmed — PulseCare";
        String html = baseTemplate(
                "Appointment Confirmed",
                "Hello " + escapeHtml(patientName) + ",",
                "Your appointment has been successfully booked.",
                new String[][]{
                        {"Doctor", "Dr. " + escapeHtml(doctorName)},
                        {"Date", escapeHtml(date)},
                        {"Time", escapeHtml(time)}
                },
                "#22c55e",
                "Please arrive 10 minutes early with any relevant medical records.",
                "View My Appointments",
                getCleanUrl("/patient/appointments")
        );
        send(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendAppointmentConfirmedToDoctor(
            String toEmail, String doctorName,
            String patientName, String date, String time) {
        if (!canSend(toEmail)) return;
        String subject = "📅 New Appointment — PulseCare";
        String html = baseTemplate(
                "New Appointment Scheduled",
                "Hello Dr. " + escapeHtml(doctorName) + ",",
                "A new appointment has been scheduled in your calendar.",
                new String[][]{
                        {"Patient", escapeHtml(patientName)},
                        {"Date", escapeHtml(date)},
                        {"Time", escapeHtml(time)}
                },
                "#3b82f6",
                "Please review patient history prior to consultation.",
                "View Doctor Schedule",
                getCleanUrl("/doctor/appointments")
        );
        send(toEmail, subject, html);
    }

    // ─── Appointment Cancelled ────────────────────────────────────────────────

    @Override
    @Async
    public void sendAppointmentCancelledToPatient(
            String toEmail, String patientName,
            String doctorName, String date, String time) {
        if (!canSend(toEmail)) return;
        String subject = "❌ Appointment Cancelled — PulseCare";
        String html = baseTemplate(
                "Appointment Cancelled",
                "Hello " + escapeHtml(patientName) + ",",
                "Your scheduled appointment has been cancelled.",
                new String[][]{
                        {"Doctor", "Dr. " + escapeHtml(doctorName)},
                        {"Date", escapeHtml(date)},
                        {"Time", escapeHtml(time)}
                },
                "#ef4444",
                "You may reschedule or select another specialist online at any time.",
                "Book New Appointment",
                getCleanUrl("/search")
        );
        send(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendAppointmentCancelledToDoctor(
            String toEmail, String doctorName,
            String patientName, String date, String time) {
        if (!canSend(toEmail)) return;
        String subject = "❌ Appointment Cancelled — PulseCare";
        String html = baseTemplate(
                "Appointment Cancelled",
                "Hello Dr. " + escapeHtml(doctorName) + ",",
                "An upcoming appointment has been cancelled.",
                new String[][]{
                        {"Patient", escapeHtml(patientName)},
                        {"Date", escapeHtml(date)},
                        {"Time", escapeHtml(time)}
                },
                "#ef4444",
                "Your calendar slot has been automatically released.",
                "View Updated Schedule",
                getCleanUrl("/doctor/appointments")
        );
        send(toEmail, subject, html);
    }

    // ─── Appointment Updated / Rescheduled ────────────────────────────────────

    @Override
    @Async
    public void sendAppointmentUpdatedToPatient(
            String toEmail, String patientName,
            String doctorName, String newStatus) {
        if (!canSend(toEmail)) return;
        String subject = "ℹ️ Appointment Updated — PulseCare";
        String html = baseTemplate(
                "Appointment Status Updated",
                "Hello " + escapeHtml(patientName) + ",",
                "Your appointment status has been updated.",
                new String[][]{
                        {"Doctor", "Dr. " + escapeHtml(doctorName)},
                        {"Status", escapeHtml(newStatus)}
                },
                "#3b82f6",
                "Check your patient portal for latest details.",
                "View Appointments",
                getCleanUrl("/patient/appointments")
        );
        send(toEmail, subject, html);
    }

    // ─── 24-Hour Reminder ─────────────────────────────────────────────────────

    @Override
    @Async
    public void sendAppointmentReminderToPatient(
            String toEmail, String patientName,
            String doctorName, String date, String time) {
        if (!canSend(toEmail)) return;
        String subject = "⏰ Reminder: Upcoming Appointment Tomorrow — PulseCare";
        String html = baseTemplate(
                "Upcoming Appointment Reminder",
                "Hello " + escapeHtml(patientName) + ",",
                "This is a reminder that you have an appointment scheduled for tomorrow.",
                new String[][]{
                        {"Doctor", "Dr. " + escapeHtml(doctorName)},
                        {"Date", escapeHtml(date)},
                        {"Time", escapeHtml(time)}
                },
                "#f59e0b",
                "If you need to reschedule or cancel, please do so at least 2 hours in advance.",
                "View Appointment Details",
                getCleanUrl("/patient/appointments")
        );
        send(toEmail, subject, html);
    }

    @Override
    @Async
    public void sendAppointmentReminderToDoctor(
            String toEmail, String doctorName,
            String patientName, String date, String time) {
        if (!canSend(toEmail)) return;
        String subject = "⏰ Schedule Reminder: Tomorrow's Appointment — PulseCare";
        String html = baseTemplate(
                "Upcoming Consultation Reminder",
                "Hello Dr. " + escapeHtml(doctorName) + ",",
                "You have an upcoming consultation scheduled for tomorrow.",
                new String[][]{
                        {"Patient", escapeHtml(patientName)},
                        {"Date", escapeHtml(date)},
                        {"Time", escapeHtml(time)}
                },
                "#f59e0b",
                "Please check patient history and records prior to consultation.",
                "View Tomorrow's Schedule",
                getCleanUrl("/doctor/appointments")
        );
        send(toEmail, subject, html);
    }

    // ─── Prescription Ready ───────────────────────────────────────────────────

    @Override
    @Async
    public void sendPrescriptionReadyToPatient(
            String toEmail, String patientName, String doctorName, String diagnosis) {
        if (!canSend(toEmail)) return;
        String subject = "💊 Prescription Available — PulseCare";
        String html = baseTemplate(
                "New Prescription Issued",
                "Hello " + escapeHtml(patientName) + ",",
                "Dr. " + escapeHtml(doctorName) + " has issued a prescription for your recent consultation.",
                new String[][]{
                        {"Doctor", "Dr. " + escapeHtml(doctorName)},
                        {"Diagnosis Note", "Prescription ready on portal"}
                },
                "#8b5cf6",
                "Prescription dosage instructions and active medicines are securely stored on your portal.",
                "View My Prescriptions",
                getCleanUrl("/patient/my-prescriptions")
        );
        send(toEmail, subject, html);
    }

    // ─── Direct Delivery helper with Exception propagation for Outbox ─────────

    public void deliverMail(String to, String subject, String html) throws MessagingException, UnsupportedEncodingException, MailException {
        if (!mailEnabled) {
            throw new IllegalStateException("SMTP mail delivery is disabled (app.mail.enabled=false)");
        }
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(mailFrom, "PulseCare");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(msg);
        log.info("PulseCare transactional mail delivered successfully to {} — {}", to, subject);
    }

    private void send(String to, String subject, String html) {
        try {
            deliverMail(to, subject, html);
        } catch (Exception e) {
            log.warn("Non-blocking mail dispatch failed to {}: {}", to, e.getMessage());
        }
    }

    private boolean canSend(String email) {
        return mailEnabled && email != null && email.contains("@");
    }

    private String getCleanUrl(String path) {
        String base = (frontendBaseUrl != null && !frontendBaseUrl.isBlank())
                ? frontendBaseUrl.trim().replaceAll("/+$", "")
                : "http://localhost:3000";
        return base + path;
    }

    private String baseTemplate(
            String heading, String greeting, String intro,
            String[][] rows, String accentColor,
            String footer, String ctaLabel, String ctaUrl) {

        StringBuilder rowsHtml = new StringBuilder();
        for (String[] row : rows) {
            rowsHtml.append("""
                    <tr>
                      <td style="padding:10px 0;color:#94a3b8;font-size:13px;width:120px;vertical-align:top">%s</td>
                      <td style="padding:10px 0;color:#f1f5f9;font-size:14px;font-weight:600">%s</td>
                    </tr>
                    """.formatted(row[0], row[1]));
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#0a0e1a;font-family:'Helvetica Neue',Arial,sans-serif">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:40px 16px">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px">
                        <!-- Header -->
                        <tr><td style="background:linear-gradient(135deg,#0d1b4b,#0a2b5e);border-radius:16px 16px 0 0;padding:32px 36px;border-top:4px solid %s">
                          <table width="100%%">
                            <tr>
                              <td>
                                <div style="display:inline-flex;align-items:center;gap:10px">
                                  <span style="display:inline-block;width:36px;height:36px;background:linear-gradient(135deg,#3b82f6,#06b6d4);border-radius:10px;text-align:center;line-height:36px;font-size:18px">🏥</span>
                                  <span style="color:#fff;font-size:18px;font-weight:700;vertical-align:middle">PulseCare</span>
                                </div>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <!-- Body -->
                        <tr><td style="background:#0f172a;padding:36px 36px 28px">
                          <h1 style="margin:0 0 8px;color:#fff;font-size:22px;font-weight:700;letter-spacing:-0.5px">%s</h1>
                          <p style="margin:0 0 24px;color:#94a3b8;font-size:14px;line-height:1.6">%s<br>%s</p>
                          <!-- Info table -->
                          <div style="background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.08);border-radius:12px;padding:4px 20px;margin-bottom:28px">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              %s
                            </table>
                          </div>
                          <!-- CTA -->
                          <a href="%s" style="display:block;text-align:center;background:linear-gradient(135deg,#2563eb,#0284c7);color:#fff;text-decoration:none;font-size:15px;font-weight:700;padding:15px 24px;border-radius:12px;margin-bottom:24px">
                            %s
                          </a>
                          <p style="margin:0;color:#64748b;font-size:12px;line-height:1.6;text-align:center">%s</p>
                        </td></tr>
                        <!-- Footer -->
                        <tr><td style="background:#080d19;border-radius:0 0 16px 16px;padding:20px 36px;border-top:1px solid rgba(255,255,255,0.06)">
                          <p style="margin:0;color:#475569;font-size:11px;text-align:center;line-height:1.6">
                            This email was sent by PulseCare · Do not reply directly to this automated email<br>
                            &copy; 2026 PulseCare Platform. All rights reserved.
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                accentColor, heading, greeting, intro,
                rowsHtml, ctaUrl, ctaLabel, footer);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
