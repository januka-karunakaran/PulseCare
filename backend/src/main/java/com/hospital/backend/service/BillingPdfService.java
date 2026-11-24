package com.hospital.backend.service;

import com.hospital.backend.model.BillingCreditNote;
import com.hospital.backend.model.BillingPayment;
import com.hospital.backend.model.Invoice;
import com.hospital.backend.model.User;

import java.util.List;

public interface BillingPdfService {
    byte[] generateInvoicePdf(Invoice invoice, User patient, List<BillingPayment> payments);
    byte[] generateReceiptPdf(BillingPayment payment, Invoice invoice, User patient, User cashier);
    byte[] generateCreditNotePdf(BillingCreditNote creditNote, Invoice invoice, User patient);
}
