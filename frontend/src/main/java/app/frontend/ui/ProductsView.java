package app.frontend.ui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import app.backend.model.Product;
import app.backend.service.ProductService;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

final class ProductsView {
    private ProductsView() { }

    static VBox create(ProductService products) {
        VBox page = Ui.page("Ürünler", "Menü fiyatı ve birim maliyetini düzenle");
        AtomicReference<TableView<Product>> tableRef = new AtomicReference<>();
        TableView<Product> table = Ui.products(products.listActive(), product ->
                        editProduct(product, products).ifPresent(saved -> tableRefresh(products, tableRef.get())),
                product -> confirmDelete(product, products, tableRef));
        tableRef.set(table);
        Button add = Ui.primary("+ Ürün Ekle");
        add.setOnAction(event -> editProduct(null, products).ifPresent(saved -> tableRefresh(products, table)));
        page.getChildren().addAll(add, table); return page;
    }

    private static void tableRefresh(ProductService products, TableView<Product> table) {
        table.getItems().setAll(products.listActive());
    }

    private static void confirmDelete(Product product, ProductService products, AtomicReference<TableView<Product>> tableRef) {
        ButtonType delete = new ButtonType("Ürünü Sil", ButtonBar.ButtonData.OK_DONE);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                product.name() + " · " + product.variant() + " ürününü menüden silmek istiyor musun?\nGeçmiş satış kayıtları korunur.",
                delete, ButtonType.CANCEL);
        confirmation.setTitle("Ürünü sil"); confirmation.setHeaderText(null);
        confirmation.showAndWait().filter(delete::equals).ifPresent(button -> {
            try {
                products.delete(product.id());
                tableRefresh(products, tableRef.get());
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
            }
        });
    }

    private static Optional<Product> editProduct(Product original, ProductService products) {
        Dialog<Product> dialog = new Dialog<>(); dialog.setTitle(original == null ? "Ürün ekle" : "Ürünü düzenle");
        ButtonType save = new ButtonType("Kaydet", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        TextField name = new TextField(original == null ? "" : original.name());
        TextField variant = new TextField(original == null ? "" : original.variant());
        TextField price = new TextField(original == null ? "" : String.valueOf(original.price()));
        TextField cost = new TextField(original == null ? "" : String.valueOf(original.cost()));
        GridPane fields = new GridPane(); fields.setHgap(10); fields.setVgap(10);
        fields.addRow(0, new Label("Ürün adı"), name); fields.addRow(1, new Label("Porsiyon"), variant);
        fields.addRow(2, new Label("Satış fiyatı ₺"), price); fields.addRow(3, new Label("Birim maliyet ₺"), cost);
        dialog.getDialogPane().setContent(fields);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                Product updated = new Product(original == null ? 0 : original.id(), name.getText().trim(), variant.getText().trim(),
                        parse(price.getText()), parse(cost.getText()));
                if (updated.name().isBlank() || updated.variant().isBlank() || updated.price() < 0 || updated.cost() < 0)
                    throw new IllegalArgumentException();
                products.save(updated); return updated;
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Ürün bilgilerini kontrol et; tüm alanlar geçerli olmalı.").showAndWait();
                return null;
            }
        });
        return dialog.showAndWait();
    }

    private static double parse(String value) { return Double.parseDouble(value.trim().replace(',', '.')); }
}
