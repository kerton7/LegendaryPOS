package app.frontend.ui;

import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import app.backend.service.ReportService;
import app.backend.model.SalesSummary;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class DashboardView {
    private DashboardView() { }

    static VBox create(ReportService reports) {
        VBox page = Ui.page("Genel Bakış", "İşletmenin satış ve kârlılık özeti");
        LocalDate today = LocalDate.now(); SalesSummary summary = reports.summary(today, today);
        HBox metrics = new HBox(14,
                Ui.metric("Bugünkü ciro", Ui.money(summary.revenue()), "₺", Ui.RED),
                Ui.metric("Bugünkü kâr", Ui.money(summary.profit()), "↗", Ui.GREEN),
                Ui.metric("Satılan ürün", String.format("%.2f", summary.quantity()), "adet", Ui.INK),
                Ui.metric("Satış adedi", String.format("%.0f", (double) summary.transactionCount()), "işlem", Ui.INK));
        page.getChildren().add(metrics);

        HBox sections = new HBox(16);
        VBox chartCard = Ui.card(); chartCard.getChildren().addAll(new Label("Son 7 gün · Ciro"), weeklyChart(reports));
        HBox.setHgrow(chartCard, Priority.ALWAYS);
        VBox productsCard = Ui.card(); productsCard.getChildren().addAll(new Label("Bugünün ürünleri"), Ui.productTotals(reports.productTotals(today, today)));
        productsCard.setPrefWidth(390); sections.getChildren().addAll(chartCard, productsCard); page.getChildren().add(sections);

        VBox recent = Ui.card(); recent.getChildren().addAll(new Label("Son satışlar"), Ui.sales(reports.sales(today, today), false));
        page.getChildren().add(recent); return page;
    }

    private static BarChart<String, Number> weeklyChart(ReportService reports) {
        CategoryAxis x = new CategoryAxis(); NumberAxis y = new NumberAxis(); y.setLabel("₺");
        BarChart<String, Number> chart = new BarChart<>(x, y); chart.setLegendVisible(false); chart.setAnimated(false); chart.setPrefHeight(300);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd MMM", new Locale("tr", "TR"));
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            series.getData().add(new XYChart.Data<>(date.format(format), reports.summary(date, date).revenue()));
        }
        chart.getData().add(series); return chart;
    }
}
