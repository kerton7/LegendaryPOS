package app.frontend.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import app.backend.BackendServices;
import app.frontend.server.LocalSalesServer;

import java.util.List;

/** Application entry point and screen navigation. */
public class MainApp extends Application {
    private final BackendServices backend = BackendServices.open();
    private final BorderPane root = new BorderPane();
    private final VBox navigation = new VBox(7);
    private String activeScreen = "Genel Bakış";
    private LocalSalesServer localServer;
    private String serverStatus = "Telefon sunucusu başlatılıyor…";

    @Override public void start(Stage stage) {
        root.setStyle("-fx-background-color:#F4F5F7;");
        try {
            localServer = new LocalSalesServer(backend, () -> javafx.application.Platform.runLater(() -> {
                if (!"Ürünler".equals(activeScreen)) show(activeScreen);
            }));
            localServer.start();
            serverStatus = "Telefon: " + localServer.phoneUrl() + "\nPIN: " + localServer.accessCode();
        } catch (Exception exception) {
            serverStatus = "Telefon sunucusu açılamadı\n" + exception.getMessage();
        }
        buildNavigation(); show("Genel Bakış");
        Scene scene = new Scene(root, 1240, 790);
        stage.setTitle("Efsane Kokoreç · İşletme Yönetimi"); stage.setMinWidth(1000); stage.setMinHeight(680);
        stage.setScene(scene); stage.show();
    }

    @Override public void stop() {
        if (localServer != null) localServer.close();
    }

    private void buildNavigation() {
        navigation.setPadding(new Insets(24, 16, 18, 16)); navigation.setPrefWidth(224); navigation.setStyle("-fx-background-color:#202326;");
        Label brand = new Label("●  EFSANE\n     KOKOREÇ"); brand.setStyle("-fx-text-fill:white;-fx-font-size:19px;-fx-font-weight:900;-fx-line-spacing:3;");
        Label caption = new Label("İŞLETME PANELİ"); caption.setStyle("-fx-text-fill:#909499;-fx-font-size:10px;-fx-font-weight:bold;-fx-padding:18 0 9 10;");
        navigation.getChildren().addAll(brand, caption);
        for (String screen : List.of("Genel Bakış", "Satış Gir", "Raporlar", "Ürünler")) navigation.getChildren().add(navigationButton(screen));
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Label local = new Label("●  Yerel veritabanı\n     SQLite · Bu bilgisayar\n\n" + serverStatus);
        local.setWrapText(true); local.setMaxWidth(205);
        local.setStyle("-fx-text-fill:#AEB2B5;-fx-font-size:10px;-fx-padding:12 8 0 8;");
        navigation.getChildren().addAll(spacer, local); root.setLeft(navigation);
    }

    private Button navigationButton(String screen) {
        Button button = new Button(screen); button.setMaxWidth(Double.MAX_VALUE); button.setAlignment(Pos.CENTER_LEFT);
        button.setPadding(new Insets(12, 14, 12, 14)); button.setOnAction(event -> show(screen));
        styleNavigationButton(button); return button;
    }

    private void styleNavigationButton(Button button) {
        boolean selected = button.getText().equals(activeScreen);
        button.setStyle("-fx-background-color:" + (selected ? Ui.RED : "transparent") + ";-fx-text-fill:" +
                (selected ? "white" : "#D0D2D4") + ";-fx-font-size:13px;-fx-font-weight:" +
                (selected ? "bold" : "normal") + ";-fx-background-radius:9;");
    }

    private void show(String screen) {
        activeScreen = screen;
        for (Node node : navigation.getChildren()) if (node instanceof Button button) styleNavigationButton(button);
        VBox content = switch (screen) {
            case "Satış Gir" -> SaleView.create(backend);
            case "Raporlar" -> ReportsView.create(backend.reports());
            case "Ürünler" -> ProductsView.create(backend.products());
            default -> DashboardView.create(backend.reports());
        };
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;"); root.setCenter(scroll);
    }

    public static void main(String[] args) { launch(args); }
}
