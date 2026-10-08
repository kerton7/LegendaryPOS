package app.backend.repository;

import app.backend.model.Product;
import app.backend.model.OrderLine;
import java.time.LocalDate;
import java.util.List;

public interface SalesRepository {
    long insertSale(Product product, LocalDate date, double quantity, double unitPrice, String paymentMethod, String note);
    boolean deleteSale(long saleId);
    String insertOrder(List<OrderLine> lines, LocalDate date, String paymentMethod);
    boolean deleteOneSaleUnit(long saleId);
}
