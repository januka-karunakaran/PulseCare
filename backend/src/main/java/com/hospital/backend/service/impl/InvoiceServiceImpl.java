package com.hospital.backend.service.impl;

import com.hospital.backend.enums.InvoiceStatus;
import com.hospital.backend.model.Invoice;
import com.hospital.backend.repository.InvoiceRepository;
import com.hospital.backend.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Override
    public Invoice createInvoice(Invoice invoice) {
        if (invoice.getInvoiceNumber() == null) {
            invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (invoice.getDate() == null) {
            invoice.setDate(LocalDate.now().toString());
        }
        if (invoice.getStatus() == null) {
            invoice.setStatus(InvoiceStatus.DRAFT);
        }
        if (invoice.getCreatedAt() == null) {
            invoice.setCreatedAt(Instant.now());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        if (invoice.getItems() != null) {
            for (Invoice.InvoiceItem item : invoice.getItems()) {
                if (item.getTotal() != null) {
                    subtotal = subtotal.add(item.getTotal());
                } else if (item.getQuantity() != null && item.getUnitPrice() != null) {
                    BigDecimal lineTot = item.getQuantity().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
                    item.setTotal(lineTot);
                    subtotal = subtotal.add(lineTot);
                }
            }
        }
        invoice.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));

        BigDecimal discount = invoice.getDiscount() != null ? invoice.getDiscount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        invoice.setDiscount(discount);

        BigDecimal tax = subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        invoice.setTax(tax);

        BigDecimal total = subtotal.add(tax).subtract(discount).setScale(2, RoundingMode.HALF_UP);
        invoice.setTotalAmount(total);
        invoice.setOutstandingAmount(total);
        invoice.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        return invoiceRepository.save(invoice);
    }

    @Override
    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    @Override
    public List<Invoice> getPatientInvoices(String patientId) {
        return invoiceRepository.findByPatientId(patientId);
    }

    @Override
    public Invoice getInvoiceById(String id) {
        return invoiceRepository.findById(id).orElseThrow(() -> new RuntimeException("Invoice not found"));
    }

    @Override
    public Invoice updateInvoiceStatus(String id, String status) {
        Invoice invoice = getInvoiceById(id);
        try {
            invoice.setStatus(InvoiceStatus.valueOf(status.toUpperCase()));
        } catch (Exception e) {
            invoice.setStatus(InvoiceStatus.ISSUED);
        }
        invoice.setUpdatedAt(Instant.now());
        return invoiceRepository.save(invoice);
    }
}
