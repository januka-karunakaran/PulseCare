package com.hospital.backend.service.impl;

import com.hospital.backend.model.BillingCreditNote;
import com.hospital.backend.model.BillingPayment;
import com.hospital.backend.model.Invoice;
import com.hospital.backend.model.User;
import com.hospital.backend.service.BillingPdfService;
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
import java.util.List;

@Service
@Slf4j
public class BillingPdfServiceImpl implements BillingPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public byte[] generateInvoicePdf(Invoice invoice, User patient, List<BillingPayment> payments) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setLeading(14.0f);

                // Header
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                stream.newLineAtOffset(50, 740);
                stream.showText("PulseCare Health System");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                stream.showText("HOSPITAL BILLING INVOICE");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText("Invoice Number: " + invoice.getInvoiceNumber() + " | Status: " + invoice.getStatus());
                stream.endText();

                // Separator
                stream.setLineWidth(1.0f);
                stream.moveTo(50, 685);
                stream.lineTo(550, 685);
                stream.stroke();

                // Patient Details
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.newLineAtOffset(50, 665);
                stream.showText("Patient Name: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(patient != null ? patient.getFullName() : (invoice.getPatientName() != null ? invoice.getPatientName() : "Unknown"));
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Issued Date: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(invoice.getIssuedAt() != null ? DATE_FMT.format(invoice.getIssuedAt()) : "N/A");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Currency: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(invoice.getCurrency() != null ? invoice.getCurrency() : "LKR");
                stream.endText();

                // Line Items Table Header
                float y = 590;
                stream.moveTo(50, y);
                stream.lineTo(550, y);
                stream.stroke();

                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.newLineAtOffset(50, y + 5);
                stream.showText("Description");
                stream.newLineAtOffset(280, 0);
                stream.showText("Qty");
                stream.newLineAtOffset(60, 0);
                stream.showText("Price");
                stream.newLineAtOffset(90, 0);
                stream.showText("Total");
                stream.endText();

                y -= 15;
                if (invoice.getItems() != null) {
                    for (Invoice.InvoiceItem item : invoice.getItems()) {
                        if (y < 120) break;
                        stream.beginText();
                        stream.setFont(PDType1Font.HELVETICA, 9);
                        stream.newLineAtOffset(50, y);
                        String desc = item.getDescription() != null ? item.getDescription() : (item.getServiceCode() != null ? item.getServiceCode() : "Service");
                        if (desc.length() > 40) desc = desc.substring(0, 37) + "...";
                        stream.showText(desc);

                        stream.newLineAtOffset(280, 0);
                        stream.showText(item.getQuantity() != null ? item.getQuantity().toPlainString() : "1");

                        stream.newLineAtOffset(60, 0);
                        stream.showText(item.getUnitPrice() != null ? item.getUnitPrice().toPlainString() : "0.00");

                        stream.newLineAtOffset(90, 0);
                        stream.showText(item.getTotal() != null ? item.getTotal().toPlainString() : "0.00");
                        stream.endText();
                        y -= 14;
                    }
                }

                // Totals Section
                y -= 10;
                stream.moveTo(350, y);
                stream.lineTo(550, y);
                stream.stroke();

                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.newLineAtOffset(350, y - 15);
                stream.showText("Subtotal: ");
                stream.newLineAtOffset(90, 0);
                stream.showText(invoice.getSubtotal() != null ? invoice.getSubtotal().toPlainString() : "0.00");
                stream.newLineAtOffset(-90, -14);

                stream.showText("Discount: ");
                stream.newLineAtOffset(90, 0);
                stream.showText(invoice.getDiscount() != null ? invoice.getDiscount().toPlainString() : "0.00");
                stream.newLineAtOffset(-90, -14);

                stream.showText("Tax: ");
                stream.newLineAtOffset(90, 0);
                stream.showText(invoice.getTax() != null ? invoice.getTax().toPlainString() : "0.00");
                stream.newLineAtOffset(-90, -14);

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Grand Total: ");
                stream.newLineAtOffset(90, 0);
                stream.showText(invoice.getTotalAmount() != null ? invoice.getTotalAmount().toPlainString() : "0.00");
                stream.newLineAtOffset(-90, -14);

                stream.showText("Paid Amount: ");
                stream.newLineAtOffset(90, 0);
                stream.showText(invoice.getPaidAmount() != null ? invoice.getPaidAmount().toPlainString() : "0.00");
                stream.newLineAtOffset(-90, -14);

                stream.showText("Balance Due: ");
                stream.newLineAtOffset(90, 0);
                stream.showText(invoice.getOutstandingAmount() != null ? invoice.getOutstandingAmount().toPlainString() : "0.00");
                stream.endText();

                // Footer
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
                stream.newLineAtOffset(50, 40);
                stream.showText("This is an electronically generated hospital billing statement from PulseCare Health System.");
                stream.endText();
            }

            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate invoice PDF for {}: {}", invoice.getInvoiceNumber(), e.getMessage());
            throw new RuntimeException("Error rendering invoice PDF: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] generateReceiptPdf(BillingPayment payment, Invoice invoice, User patient, User cashier) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setLeading(14.0f);

                // Title
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                stream.newLineAtOffset(50, 740);
                stream.showText("PulseCare Health System");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                stream.showText("OFFICIAL PAYMENT RECEIPT");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText("Receipt Number: " + payment.getPaymentNumber() + " | Status: " + payment.getStatus());
                stream.endText();

                // Divider
                stream.setLineWidth(1.0f);
                stream.moveTo(50, 685);
                stream.lineTo(550, 685);
                stream.stroke();

                // Receipt Fields
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.newLineAtOffset(50, 660);

                stream.showText("Invoice Reference: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(invoice != null ? invoice.getInvoiceNumber() : payment.getInvoiceId());
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Patient Name: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(patient != null ? patient.getFullName() : "Patient");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Amount Received: ");
                stream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                stream.showText(payment.getCurrency() + " " + (payment.getAmount() != null ? payment.getAmount().toPlainString() : "0.00"));
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Payment Method: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(payment.getMethod() != null ? payment.getMethod().name() : "CASH");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Transaction Timestamp: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(payment.getConfirmedAt() != null ? DATE_FMT.format(payment.getConfirmedAt()) : "N/A");
                stream.newLine();

                if (cashier != null) {
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                    stream.showText("Received By: ");
                    stream.setFont(PDType1Font.HELVETICA, 10);
                    stream.showText(cashier.getFullName());
                    stream.newLine();
                }

                if (invoice != null && invoice.getOutstandingAmount() != null) {
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                    stream.showText("Remaining Invoice Balance: ");
                    stream.setFont(PDType1Font.HELVETICA, 10);
                    stream.showText(invoice.getCurrency() + " " + invoice.getOutstandingAmount().toPlainString());
                    stream.newLine();
                }
                stream.endText();

                // Footer
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
                stream.newLineAtOffset(50, 40);
                stream.showText("Official electronic hospital payment confirmation voucher. PulseCare Health System.");
                stream.endText();
            }

            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate receipt PDF for {}: {}", payment.getPaymentNumber(), e.getMessage());
            throw new RuntimeException("Error rendering receipt PDF: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] generateCreditNotePdf(BillingCreditNote creditNote, Invoice invoice, User patient) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setLeading(14.0f);

                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                stream.newLineAtOffset(50, 740);
                stream.showText("PulseCare Health System");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                stream.showText("FINANCIAL CREDIT NOTE");
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText("Credit Note Number: " + creditNote.getCreditNoteNumber());
                stream.endText();

                stream.setLineWidth(1.0f);
                stream.moveTo(50, 685);
                stream.lineTo(550, 685);
                stream.stroke();

                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.newLineAtOffset(50, 660);

                stream.showText("Original Invoice: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(invoice != null ? invoice.getInvoiceNumber() : creditNote.getInvoiceId());
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Credited Amount: ");
                stream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                stream.showText(creditNote.getCurrency() + " " + creditNote.getAmount().toPlainString());
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Reason Code: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(creditNote.getReasonCode());
                stream.newLine();

                stream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                stream.showText("Issued At: ");
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.showText(creditNote.getIssuedAt() != null ? DATE_FMT.format(creditNote.getIssuedAt()) : "N/A");
                stream.endText();
            }

            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate credit note PDF for {}: {}", creditNote.getCreditNoteNumber(), e.getMessage());
            throw new RuntimeException("Error rendering credit note PDF: " + e.getMessage(), e);
        }
    }
}
