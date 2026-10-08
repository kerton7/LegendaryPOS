package app.backend.repository;

import app.backend.model.ProductTotal;
import app.backend.model.Sale;
import app.backend.model.SalesSummary;
import java.time.LocalDate;
import java.util.List;

public interface ReportRepository {
    SalesSummary summarize(LocalDate from, LocalDate to);
    List<ProductTotal> findProductTotals(LocalDate from, LocalDate to);
    List<Sale> findSales(LocalDate from, LocalDate to);

    default SalesSummary summarize(LocalDate from, LocalDate to, String paymentMethod) {
        return summarize(from, to);
    }
    default List<ProductTotal> findProductTotals(LocalDate from, LocalDate to, String paymentMethod) {
        return findProductTotals(from, to);
    }
    default List<Sale> findSales(LocalDate from, LocalDate to, String paymentMethod) {
        return findSales(from, to);
    }
}
