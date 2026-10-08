package app.backend.service;

import app.backend.model.ProductTotal;
import app.backend.model.Sale;
import app.backend.model.SalesSummary;
import app.backend.repository.ReportRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class ReportService {
    private static final Set<String> PAYMENT_METHODS = Set.of("Nakit", "Kart", "IBAN");
    private final ReportRepository reports;
    public ReportService(ReportRepository reports) { this.reports = reports; }

    public SalesSummary summary(LocalDate from, LocalDate to) {
        return summary(from, to, null);
    }
    public SalesSummary summary(LocalDate from, LocalDate to, String paymentMethod) {
        validateRange(from, to); return reports.summarize(from, to, normalizePaymentMethod(paymentMethod));
    }
    public List<ProductTotal> productTotals(LocalDate from, LocalDate to) {
        return productTotals(from, to, null);
    }
    public List<ProductTotal> productTotals(LocalDate from, LocalDate to, String paymentMethod) {
        validateRange(from, to); return reports.findProductTotals(from, to, normalizePaymentMethod(paymentMethod));
    }
    public List<Sale> sales(LocalDate from, LocalDate to) {
        return sales(from, to, null);
    }
    public List<Sale> sales(LocalDate from, LocalDate to, String paymentMethod) {
        validateRange(from, to); return reports.findSales(from, to, normalizePaymentMethod(paymentMethod));
    }
    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.equals("Tümü")) return null;
        if (!PAYMENT_METHODS.contains(paymentMethod)) throw new IllegalArgumentException("Ödeme yöntemi Tümü, Nakit, Kart veya IBAN olmalı.");
        return paymentMethod;
    }
    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("Tarih aralığını kontrol et.");
    }
}
