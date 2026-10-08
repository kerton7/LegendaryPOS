package app.backend.service;

import app.backend.model.Product;
import app.backend.model.OrderLine;
import app.backend.repository.SalesRepository;

import java.time.LocalDate;
import java.util.Set;
import java.util.List;

public final class SalesService {
    private static final Set<String> PAYMENT_METHODS = Set.of("Nakit", "Kart", "IBAN");
    private final SalesRepository sales;
    public SalesService(SalesRepository sales) { this.sales = sales; }

    public long record(Product product, LocalDate date, double quantity, double unitPrice, String paymentMethod, String note) {
        if (product == null) throw new IllegalArgumentException("Önce ürün seç.");
        if (date == null) throw new IllegalArgumentException("Satış tarihi seç.");
        if (!Double.isFinite(quantity) || quantity <= 0) throw new IllegalArgumentException("Miktar sıfırdan büyük olmalı.");
        if (!Double.isFinite(unitPrice) || unitPrice < 0) throw new IllegalArgumentException("Fiyat geçerli, negatif olmayan bir sayı olmalı.");
        if (!PAYMENT_METHODS.contains(paymentMethod)) throw new IllegalArgumentException("Ödeme yöntemi Nakit, Kart veya IBAN olmalı.");
        return sales.insertSale(product, date, quantity, unitPrice, paymentMethod, note == null ? "" : note.trim());
    }

    public boolean undo(long saleId) {
        if (saleId <= 0) throw new IllegalArgumentException("Geçersiz satış numarası.");
        return sales.deleteSale(saleId);
    }

    public String saveOrder(List<OrderLine> lines, LocalDate date, String paymentMethod) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Sipariş listesi boş olamaz.");
        if (date == null) throw new IllegalArgumentException("Sipariş tarihi gerekli.");
        if (!PAYMENT_METHODS.contains(paymentMethod)) throw new IllegalArgumentException("Ödeme yöntemi Nakit, Kart veya IBAN olmalı.");
        for (OrderLine line : lines) {
            if (line == null || line.product() == null) throw new IllegalArgumentException("Siparişte geçersiz ürün var.");
            if (!Double.isFinite(line.quantity()) || line.quantity() <= 0) throw new IllegalArgumentException("Ürün adedi sıfırdan büyük olmalı.");
            if (!Double.isFinite(line.product().price()) || line.product().price() <= 0) throw new IllegalArgumentException("Fiyatı belirlenmemiş ürün siparişe eklenemez.");
        }
        return sales.insertOrder(List.copyOf(lines), date, paymentMethod);
    }

    public boolean deleteOneSaleUnit(long saleId) {
        if (saleId <= 0) throw new IllegalArgumentException("Geçersiz satış numarası.");
        return sales.deleteOneSaleUnit(saleId);
    }
}
