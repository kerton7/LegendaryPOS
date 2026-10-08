package app.frontend.ui;

import javafx.collections.FXCollections;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import app.backend.model.SalesSummary;
import app.backend.model.ProductTotal;
import app.backend.model.Sale;
import app.backend.service.ReportService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class ReportsView {
    private ReportsView() { }

    static VBox create(ReportService reports) {
        VBox page = Ui.page("Raporlar", "Tarih aralığına göre ciro, kâr ve ürün satışlarını incele");
        DatePicker from = new DatePicker(LocalDate.now().withDayOfMonth(1));
        DatePicker to = new DatePicker(LocalDate.now());
        ComboBox<String> paymentMethod = new ComboBox<>(FXCollections.observableArrayList("Tümü", "Nakit", "Kart", "IBAN"));
        paymentMethod.setValue("Tümü"); paymentMethod.setPrefWidth(120);
        Button apply = Ui.primary("Raporu Göster");
        HBox filters = new HBox(10, Ui.label("Başlangıç"), from, Ui.label("Bitiş"), to,
                Ui.label("Ödeme yöntemi"), paymentMethod, apply);
        page.getChildren().add(filters);

        HBox metrics = new HBox(14);
        HBox paymentBreakdown = new HBox(14);
        VBox paymentCard = Ui.card();
        paymentCard.getChildren().addAll(new Label("Ödeme yöntemine göre · seçili tarih aralığı"), paymentBreakdown);
        TableView<ProductTotal> productTable = Ui.productTotals(java.util.List.of());
        VBox productCard = Ui.card(); productCard.getChildren().addAll(new Label("Ürün bazında satış"), productTable);
        TableView<Sale> salesTable = Ui.sales(java.util.List.of(), true);
        VBox salesCard = Ui.card(); salesCard.getChildren().addAll(new Label("Satış detayları · en fazla 100 kayıt"), salesTable);
        VBox monthly = Ui.card(); Label monthlyTitle = new Label("Aylık ciro ve kâr");
        monthly.getChildren().add(monthlyTitle);
        page.getChildren().addAll(metrics, paymentCard, productCard, salesCard, monthly);

        Runnable refresh = () -> {
            if (from.getValue() == null || to.getValue() == null || from.getValue().isAfter(to.getValue())) {
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING, "Tarih aralığını kontrol et.").showAndWait(); return;
            }
            String selectedPayment = paymentMethod.getValue();
            SalesSummary summary = reports.summary(from.getValue(), to.getValue(), selectedPayment);
            metrics.getChildren().setAll(
                    Ui.metric("Ciro", Ui.money(summary.revenue()), "₺", Ui.RED),
                    Ui.metric("Tahmini brüt kâr", Ui.money(summary.profit()), "↗", Ui.GREEN),
                    Ui.metric("Satılan miktar", String.format("%.2f", summary.quantity()), "adet", Ui.INK),
                    Ui.metric("İşlem", String.format("%.0f", (double) summary.transactionCount()), "satış", Ui.INK));
            paymentBreakdown.getChildren().setAll(java.util.List.of("Nakit", "Kart", "IBAN").stream().map(method -> {
                SalesSummary methodSummary = reports.summary(from.getValue(), to.getValue(), method);
                return Ui.metric(method, Ui.money(methodSummary.revenue()),
                        String.format("%.0f satış", (double) methodSummary.transactionCount()), Ui.RED);
            }).toList());
            productTable.setItems(FXCollections.observableArrayList(reports.productTotals(from.getValue(), to.getValue(), selectedPayment)));
            salesTable.setItems(FXCollections.observableArrayList(reports.sales(from.getValue(), to.getValue(), selectedPayment)));
            monthly.getChildren().setAll(monthlyTitle, monthlyChart(reports, selectedPayment));
        };
        apply.setOnAction(event -> refresh.run());
        paymentMethod.setOnAction(event -> refresh.run());
        refresh.run();
        return page;
    }

    private static LineChart<String, Number> monthlyChart(ReportService reports, String paymentMethod) {
        CategoryAxis x = new CategoryAxis(); NumberAxis y = new NumberAxis(); LineChart<String, Number> chart = new LineChart<>(x, y);
        chart.setTitle("Son 12 ay"); chart.setAnimated(false); chart.setPrefHeight(310);
        XYChart.Series<String, Number> revenue = new XYChart.Series<>(), profit = new XYChart.Series<>();
        revenue.setName("Ciro"); profit.setName("Kâr"); LocalDate current = LocalDate.now();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("MMM yy", new Locale("tr", "TR"));
        for (int i = 11; i >= 0; i--) {
            LocalDate month = current.minusMonths(i).withDayOfMonth(1);
            SalesSummary summary = reports.summary(month, month.withDayOfMonth(month.lengthOfMonth()), paymentMethod);
            String label = month.format(format); revenue.getData().add(new XYChart.Data<>(label, summary.revenue()));
            profit.getData().add(new XYChart.Data<>(label, summary.profit()));
        }
        chart.getData().addAll(revenue, profit); return chart;
    }
}
