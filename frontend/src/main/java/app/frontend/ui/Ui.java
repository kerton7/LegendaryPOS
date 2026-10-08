package app.frontend.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import app.backend.model.Product;
import app.backend.model.ProductTotal;
import app.backend.model.Sale;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Consumer;

final class Ui {
    static final String RED = "#C7422D", INK = "#202326", MUTED = "#81858B", GREEN = "#16835D";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private Ui() { }

    static VBox page(String title, String subtitle) {
        VBox content = new VBox(18); content.setPadding(new Insets(30, 34, 30, 34));
        Label heading = new Label(title); heading.setStyle("-fx-font-size:26px;-fx-font-weight:bold;-fx-text-fill:" + INK + ";");
        Label description = new Label(subtitle); description.setStyle("-fx-font-size:13px;-fx-text-fill:" + MUTED + ";");
        content.getChildren().add(new VBox(5, heading, description)); return content;
    }
    static VBox card() { VBox box = new VBox(12); box.setPadding(new Insets(18)); box.setStyle("-fx-background-color:white;-fx-background-radius:13;-fx-border-color:#E8E9EC;-fx-border-radius:13;"); return box; }
    static Label label(String text) { Label l = new Label(text); l.setStyle("-fx-font-size:12px;-fx-text-fill:" + MUTED + ";"); return l; }
    static String money(double n) { return String.format(new Locale("tr", "TR"), "%,.2f ₺", n); }
    static Button primary(String text) { Button b = new Button(text); b.setStyle("-fx-background-color:" + RED + ";-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:10 16;"); return b; }

    static VBox metric(String title, String amount, String badge, String color) {
        VBox box = card(); HBox row = new HBox(); Label titleLabel = label(title);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label tag = new Label(badge); tag.setStyle("-fx-text-fill:" + color + ";-fx-font-size:11px;-fx-font-weight:bold;"); row.getChildren().addAll(titleLabel, spacer, tag);
        Label value = new Label(amount); value.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:" + INK + ";");
        box.getChildren().addAll(row, value); HBox.setHgrow(box, Priority.ALWAYS); box.setMinWidth(170); return box;
    }

    static TableView<Product> products(List<Product> rows, Consumer<Product> edit, Consumer<Product> delete) {
        TableView<Product> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.getColumns().add(productColumn("Ürün", Product::name, 180));
        table.getColumns().add(productColumn("Porsiyon", Product::variant, 120));
        table.getColumns().add(productColumn("Satış fiyatı", p -> money(p.price()), 130));
        table.getColumns().add(productColumn("Birim maliyet", p -> money(p.cost()), 130));
        TableColumn<Product, Void> action = new TableColumn<>("İşlem");
        action.setCellFactory(col -> new TableCell<>() {
            private final Button button = new Button("Düzenle");
            private final Button deleteButton = new Button("🗑");
            private final HBox actions = new HBox(7, button, deleteButton);
            {
                deleteButton.setTooltip(new Tooltip("Ürünü sil"));
                deleteButton.setStyle("-fx-text-fill:#B42318;-fx-background-color:#FFF0EC;-fx-background-radius:7;");
                button.setOnAction(e -> edit.accept(getTableView().getItems().get(getIndex())));
                deleteButton.setOnAction(e -> delete.accept(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : actions); }
        });
        action.setPrefWidth(170);
        table.getColumns().add(action); return table;
    }

    static TableView<ProductTotal> productTotals(List<ProductTotal> rows) {
        TableView<ProductTotal> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.getColumns().add(totalColumn("Ürün", ProductTotal::name, 170));
        table.getColumns().add(totalColumn("Porsiyon", ProductTotal::variant, 95));
        table.getColumns().add(totalColumn("Miktar", p -> String.format("%.2f", p.quantity()), 75));
        table.getColumns().add(totalColumn("Ciro", p -> money(p.revenue()), 110));
        table.getColumns().add(totalColumn("Kâr", p -> money(p.profit()), 110));
        table.setPrefHeight(Math.min(360, 85 + rows.size() * 42)); return table;
    }

    static TableView<Sale> sales(List<Sale> rows, boolean showNote) {
        return sales(rows, showNote, null);
    }

    static TableView<Sale> sales(List<Sale> rows, boolean showNote, Consumer<Sale> delete) {
        TableView<Sale> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.getColumns().add(saleColumn("Tarih", s -> s.date().format(DATE), 95));
        table.getColumns().add(saleColumn("Saat", s -> s.time() == null ? "—" : s.time().format(TIME), 75));
        table.getColumns().add(saleColumn("Ürün", s -> s.productName() + " · " + s.variant(), 180));
        table.getColumns().add(saleColumn("Ödeme", Sale::paymentMethod, 90));
        table.getColumns().add(saleColumn("Miktar", s -> String.format("%.2f", s.quantity()), 80));
        table.getColumns().add(saleColumn("Ciro", s -> money(s.revenue()), 110));
        table.getColumns().add(saleColumn("Kâr", s -> money(s.profit()), 110));
        if (showNote) table.getColumns().add(saleColumn("Not", Sale::note, 140));
        if (delete != null) {
            TableColumn<Sale, Void> action = new TableColumn<>("İşlem");
            action.setPrefWidth(76);
            action.setCellFactory(col -> new TableCell<>() {
                private final Button button = new Button("🗑");
                { button.setTooltip(new Tooltip("Bu satış kaydını sil")); button.setStyle("-fx-text-fill:#B42318;-fx-background-color:#FFF0EC;-fx-background-radius:7;"); button.setOnAction(e -> delete.accept(getTableView().getItems().get(getIndex()))); }
                @Override protected void updateItem(Void item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : button); }
            });
            table.getColumns().add(action);
        }
        table.setPrefHeight(Math.min(380, 85 + rows.size() * 42)); return table;
    }

    private static TableColumn<Product, String> productColumn(String title, Function<Product, String> value, int width) {
        return column(title, value, width);
    }
    private static TableColumn<ProductTotal, String> totalColumn(String title, Function<ProductTotal, String> value, int width) {
        return column(title, value, width);
    }
    private static TableColumn<Sale, String> saleColumn(String title, Function<Sale, String> value, int width) {
        return column(title, value, width);
    }
    private static <T> TableColumn<T, String> column(String title, Function<T, String> value, int width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width); return column;
    }
}
