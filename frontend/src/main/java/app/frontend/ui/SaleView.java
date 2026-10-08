package app.frontend.ui;

import app.backend.BackendServices;
import app.backend.model.OrderLine;
import app.backend.model.Product;
import app.backend.model.Sale;
import app.backend.model.SalesSummary;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class SaleView {
    /** Keeps a desktop draft while navigating between screens in the same app session. */
    private static final Map<Integer, Integer> PENDING_ORDER = new LinkedHashMap<>();

    private SaleView() { }

    static VBox create(BackendServices backend) {
        VBox page = Ui.page("Hızlı Satış", "Ürünleri sipariş listesine ekle, kontrol edip tek seferde kaydet");
        HBox totals = new HBox(14);
        Label status = new Label("Yeni bir sipariş oluşturmaya başla.");
        status.setStyle("-fx-text-fill:" + Ui.MUTED + ";-fx-font-size:13px;");
        Button undo = new Button("↶  Son ürünü geri al");
        undo.setStyle("-fx-background-color:#FFF0EC;-fx-text-fill:" + Ui.RED + ";-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:10 16;");
        undo.setVisible(false); undo.setManaged(false);
        HBox feedback = new HBox(12, status, undo); feedback.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> paymentMethod = new ComboBox<>(FXCollections.observableArrayList("Nakit", "Kart", "IBAN"));
        paymentMethod.setValue("Nakit"); paymentMethod.setPrefWidth(150);
        AtomicLong lastSaleId = new AtomicLong();
        AtomicReference<TableView<Sale>> todaySales = new AtomicReference<>();
        AtomicReference<Runnable> refreshSalesRef = new AtomicReference<>(() -> { });
        Map<Integer, Label> soldCounts = new HashMap<>();
        Runnable refreshTotals = () -> {
            SalesSummary summary = backend.reports().summary(LocalDate.now(), LocalDate.now());
            totals.getChildren().setAll(
                    Ui.metric("Bugünkü ciro", Ui.money(summary.revenue()), "₺", Ui.RED),
                    Ui.metric("Bugünkü sipariş", String.format(Locale.forLanguageTag("tr-TR"), "%d", summary.transactionCount()), "işlem", Ui.GREEN));
        };

        VBox orderCard = Ui.card();
        Label orderHeading = new Label("Bekleyen sipariş");
        orderHeading.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:" + Ui.INK + ";");
        Label orderHint = new Label("Ürün ekleyip adetleri düzenle. Kaydetmeden önce hiçbir satış rapora yazılmaz.");
        orderHint.setWrapText(true); orderHint.setStyle("-fx-text-fill:" + Ui.MUTED + ";-fx-font-size:12px;");
        HBox paymentPicker = new HBox(10, Ui.label("Ödeme yöntemi"), paymentMethod);
        paymentPicker.setAlignment(Pos.CENTER_LEFT);
        VBox orderLines = new VBox(8);
        Label orderTotal = new Label("Toplam: 0,00 ₺ · 0 porsiyon");
        orderTotal.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:" + Ui.INK + ";");
        Button saveOrder = Ui.primary("Siparişi kaydet"); saveOrder.setDisable(true);
        saveOrder.setMaxWidth(Double.MAX_VALUE);
        orderCard.getChildren().addAll(orderHeading, orderHint, orderLines, orderTotal, paymentPicker, saveOrder);

        AtomicReference<Runnable> refreshOrderRef = new AtomicReference<>(() -> { });
        Runnable refreshOrder = () -> {
            orderLines.getChildren().clear();
            List<Product> activeProducts = backend.products().listActive();
            PENDING_ORDER.keySet().removeIf(id -> activeProducts.stream().noneMatch(product -> product.id() == id));
            double total = 0;
            int portionCount = 0;
            for (Product product : activeProducts) {
                int quantity = PENDING_ORDER.getOrDefault(product.id(), 0);
                Label soldCount = soldCounts.get(product.id());
                if (soldCount != null) soldCount.setText(savedCount(backend, product) + " satıldı · " + quantity + " bekliyor");
                if (quantity == 0) continue;
                total += quantity * product.price(); portionCount += quantity;
                Label itemName = new Label(product.name() + " · " + product.variant());
                itemName.setStyle("-fx-font-weight:bold;-fx-text-fill:" + Ui.INK + ";");
                Label amount = new Label(quantity + " × " + Ui.money(product.price()) + " = " + Ui.money(quantity * product.price()));
                amount.setStyle("-fx-font-size:12px;-fx-text-fill:" + Ui.MUTED + ";");
                VBox itemInfo = new VBox(3, itemName, amount); HBox.setHgrow(itemInfo, Priority.ALWAYS);
                Button minus = new Button("−"); minus.setTooltip(new Tooltip("Adedi azalt"));
                minus.setOnAction(event -> { updatePending(product, -1); refreshOrderRef.get().run(); });
                Label count = new Label(Integer.toString(quantity)); count.setMinWidth(24); count.setAlignment(Pos.CENTER);
                Button plus = new Button("+"); plus.setTooltip(new Tooltip("Adedi artır"));
                plus.setOnAction(event -> { updatePending(product, 1); refreshOrderRef.get().run(); });
                Button remove = new Button("🗑"); remove.setTooltip(new Tooltip("Bekleyen siparişten bu satırı çıkar"));
                remove.setStyle("-fx-text-fill:#B42318;-fx-background-color:#FFF0EC;-fx-background-radius:8;");
                remove.setOnAction(event -> { PENDING_ORDER.remove(product.id()); refreshOrderRef.get().run(); });
                HBox line = new HBox(8, itemInfo, minus, count, plus, remove); line.setAlignment(Pos.CENTER_LEFT);
                line.setStyle("-fx-padding:8 0 8 0;-fx-border-color:transparent transparent #E8E9EC transparent;");
                orderLines.getChildren().add(line);
            }
            orderTotal.setText("Toplam: " + Ui.money(total) + " · " + portionCount + " porsiyon");
            saveOrder.setDisable(portionCount == 0);
        };
        refreshOrderRef.set(refreshOrder);

        undo.setOnAction(event -> {
            try {
                if (lastSaleId.get() > 0 && backend.sales().deleteOneSaleUnit(lastSaleId.get())) {
                    lastSaleId.set(0); undo.setVisible(false); undo.setManaged(false);
                    status.setText("Son satış kalemi geri alındı."); refreshTotals.run(); refreshSalesRef.get().run();
                }
            } catch (Exception e) { status.setText("Geri alma başarısız: " + e.getMessage()); }
        });

        List<Product> products = backend.products().listActive();
        FlowPane productGrid = new FlowPane(14, 14);
        productGrid.setAlignment(Pos.TOP_LEFT); productGrid.setPrefWrapLength(920);
        for (Product product : products) {
            productGrid.getChildren().add(productCard(product, backend, status, refreshTotals, refreshSalesRef,
                    refreshOrderRef, soldCounts));
        }

        Label salesHeading = new Label("Bugünkü satışlar");
        salesHeading.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:" + Ui.INK + ";");
        TableView<Sale> salesTable = Ui.sales(backend.reports().sales(LocalDate.now(), LocalDate.now()), false,
                sale -> deleteSale(sale, backend, status, refreshTotals, refreshSalesRef, refreshOrderRef));
        todaySales.set(salesTable);
        refreshSalesRef.set(() -> {
            TableView<Sale> old = todaySales.get();
            List<Sale> rows = backend.reports().sales(LocalDate.now(), LocalDate.now());
            lastSaleId.set(rows.isEmpty() ? 0 : rows.getFirst().id());
            undo.setVisible(lastSaleId.get() != 0); undo.setManaged(lastSaleId.get() != 0);
            for (Product product : products) {
                Label count = soldCounts.get(product.id());
                if (count != null) count.setText(savedCount(backend, product) + " satıldı · " + PENDING_ORDER.getOrDefault(product.id(), 0) + " bekliyor");
            }
            TableView<Sale> updated = Ui.sales(rows, false,
                    sale -> deleteSale(sale, backend, status, refreshTotals, refreshSalesRef, refreshOrderRef));
            todaySales.set(updated);
            int index = page.getChildren().indexOf(old);
            if (index >= 0) page.getChildren().set(index, updated);
        });

        saveOrder.setOnAction(event -> {
            List<OrderLine> lines = new ArrayList<>();
            for (Product product : products) {
                int quantity = PENDING_ORDER.getOrDefault(product.id(), 0);
                if (quantity > 0) lines.add(new OrderLine(product, quantity));
            }
            if (lines.isEmpty()) return;
            try {
                backend.sales().saveOrder(lines, LocalDate.now(), paymentMethod.getValue());
                PENDING_ORDER.clear();
                status.setText("Sipariş kaydedildi · " + lines.stream().mapToInt(line -> (int) line.quantity()).sum() + " porsiyon.");
                lastSaleId.set(backend.reports().sales(LocalDate.now(), LocalDate.now()).getFirst().id());
                undo.setVisible(true); undo.setManaged(true);
                refreshOrderRef.get().run(); refreshTotals.run(); refreshSalesRef.get().run();
            } catch (Exception e) { status.setText("Sipariş kaydedilemedi: " + e.getMessage()); }
        });

        Label hintLabel = new Label("Sepetteki çöp kutusu kaydedilmemiş satırı kaldırır. Ürün kartındaki çöp kutusu, o üründen kaydedilmiş en son 1 porsiyonu siler.");
        hintLabel.setWrapText(true); hintLabel.setStyle("-fx-text-fill:" + Ui.MUTED + ";-fx-font-size:12px;");
        VBox hint = Ui.card(); hint.getChildren().add(hintLabel);
        page.getChildren().addAll(totals, feedback, orderCard, productGrid, hint, salesHeading, salesTable);
        refreshTotals.run(); refreshOrder.run(); refreshSalesRef.get().run(); return page;
    }

    private static VBox productCard(Product product, BackendServices backend, Label status,
                                    Runnable refreshTotals, AtomicReference<Runnable> refreshSalesRef,
                                    AtomicReference<Runnable> refreshOrderRef, Map<Integer, Label> soldCounts) {
        VBox card = new VBox(9); card.setAlignment(Pos.CENTER); card.setPrefSize(214, 246);
        card.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#E8E9EC;-fx-border-radius:14;-fx-padding:16;");
        Label icon = new Label(iconFor(product.name()));
        icon.setAlignment(Pos.CENTER); icon.setMinSize(78, 78); icon.setStyle("-fx-font-size:42px;-fx-background-color:" + colorFor(product.name()) + ";-fx-background-radius:39;");
        Label name = new Label(product.name()); name.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:" + Ui.INK + ";");
        Label variant = new Label(product.variant()); variant.setStyle("-fx-font-size:12px;-fx-text-fill:" + Ui.MUTED + ";");
        Label price = new Label(Ui.money(product.price())); price.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + Ui.RED + ";");
        Label soldCount = new Label("0 satıldı · 0 bekliyor"); soldCount.setStyle("-fx-font-size:11px;-fx-text-fill:" + Ui.MUTED + ";");
        soldCounts.put(product.id(), soldCount);
        Button add = Ui.primary(product.price() > 0 ? "+ Sepete" : "Fiyat girilmeli");
        add.setMaxWidth(Double.MAX_VALUE); add.setDisable(product.price() <= 0);
        add.setOnAction(event -> {
            updatePending(product, 1);
            status.setText(product.name() + " · " + product.variant() + " bekleyen siparişe eklendi.");
            refreshOrderRef.get().run();
        });
        Button remove = new Button("🗑");
        remove.setTooltip(new Tooltip("Kaydedilmiş bu üründen 1 porsiyon sil"));
        remove.setStyle("-fx-text-fill:#B42318;-fx-background-color:#FFF0EC;-fx-background-radius:8;-fx-padding:10 13;");
        remove.setOnAction(event -> deleteProductSale(product, backend, status, refreshTotals, refreshSalesRef, refreshOrderRef));
        HBox actions = new HBox(8, add, remove); actions.setAlignment(Pos.CENTER); HBox.setHgrow(add, Priority.ALWAYS);
        card.getChildren().addAll(icon, name, variant, price, soldCount, actions); return card;
    }

    private static void updatePending(Product product, int delta) {
        int next = PENDING_ORDER.getOrDefault(product.id(), 0) + delta;
        if (next <= 0) PENDING_ORDER.remove(product.id()); else PENDING_ORDER.put(product.id(), next);
    }

    private static int savedCount(BackendServices backend, Product product) {
        return (int) Math.round(backend.reports().sales(LocalDate.now(), LocalDate.now()).stream()
                .filter(sale -> sale.productName().equals(product.name()) && sale.variant().equals(product.variant()))
                .mapToDouble(Sale::quantity).sum());
    }

    private static void deleteProductSale(Product product, BackendServices backend, Label status,
                                          Runnable refreshTotals, AtomicReference<Runnable> refreshSalesRef,
                                          AtomicReference<Runnable> refreshOrderRef) {
        List<Sale> matching = backend.reports().sales(LocalDate.now(), LocalDate.now()).stream()
                .filter(sale -> sale.productName().equals(product.name()) && sale.variant().equals(product.variant()))
                .toList();
        if (matching.isEmpty()) { status.setText("Bugün bu ürün için silinecek satış yok."); return; }
        try {
            Sale latest = matching.getFirst();
            if (backend.sales().deleteOneSaleUnit(latest.id())) {
                status.setText(product.name() + " · " + product.variant() + " için 1 satış silindi.");
                refreshTotals.run(); refreshSalesRef.get().run(); refreshOrderRef.get().run();
            }
        } catch (Exception e) { status.setText("Satış silinemedi: " + e.getMessage()); }
    }

    private static void deleteSale(Sale sale, BackendServices backend, Label status,
                                   Runnable refreshTotals, AtomicReference<Runnable> refreshSalesRef,
                                   AtomicReference<Runnable> refreshOrderRef) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                sale.productName() + " · " + sale.variant() + " satışından 1 porsiyon silinsin mi?", ButtonType.CANCEL, ButtonType.OK);
        confirm.setHeaderText("Satış kaydını sil");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            if (backend.sales().deleteOneSaleUnit(sale.id())) {
                status.setText(sale.productName() + " satışından 1 porsiyon silindi.");
                refreshTotals.run(); refreshSalesRef.get().run(); refreshOrderRef.get().run();
            }
        } catch (Exception e) { status.setText("Satış kaydı silinemedi: " + e.getMessage()); }
    }

    private static String iconFor(String productName) {
        String name = productName.toLowerCase(Locale.forLanguageTag("tr-TR"));
        if (name.contains("kokoreç")) return "🍢";
        if (name.contains("sucuk")) return "🌭";
        if (name.contains("ciğer")) return "🍖";
        return "🍽️";
    }

    private static String colorFor(String productName) {
        String name = productName.toLowerCase(Locale.forLanguageTag("tr-TR"));
        if (name.contains("kokoreç")) return "#FFF0E8";
        if (name.contains("sucuk")) return "#FDEBEC";
        if (name.contains("ciğer")) return "#F2ECFA";
        return "#F1F2F4";
    }
}
