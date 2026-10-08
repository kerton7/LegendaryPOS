package app.frontend.server;

import app.backend.BackendServices;
import app.backend.model.Product;
import app.backend.model.OrderLine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Executors;

/** Serves the phone order page and its small LAN-only sales API. */
public final class LocalSalesServer implements AutoCloseable {
    private static final int PORT = 8765;
    private final BackendServices backend;
    private final Runnable onDataChanged;
    private final String accessCode;
    private HttpServer server;

    public LocalSalesServer(BackendServices backend, Runnable onDataChanged) {
        this.backend = backend;
        this.onDataChanged = onDataChanged;
        this.accessCode = loadOrCreateAccessCode(backend.databaseDirectory());
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/api/products", this::products);
        server.createContext("/api/orders", this::orders);
        server.createContext("/api/sales", this::sales);
        server.createContext("/", this::home);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "local-sales-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
    }

    public String accessCode() { return accessCode; }
    public String phoneUrl() { return "http://" + lanAddress() + ":" + PORT; }

    private void home(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "text/plain; charset=utf-8", "Yalnızca GET destekleniyor.");
            return;
        }
        try (var input = LocalSalesServer.class.getResourceAsStream("/mobile/index.html")) {
            if (input == null) {
                respond(exchange, 500, "text/plain; charset=utf-8", "Telefon ekranı bulunamadı.");
                return;
            }
            byte[] page = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, page.length);
            try (var output = exchange.getResponseBody()) { output.write(page); }
        }
    }

    private void products(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            respond(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"PIN yanlış.\"}");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Yalnızca GET destekleniyor.\"}");
            return;
        }
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Product product : backend.products().listActive()) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"id\":").append(product.id())
                    .append(",\"name\":\"").append(jsonEscape(product.name()))
                    .append("\",\"variant\":\"").append(jsonEscape(product.variant()))
                    .append("\",\"price\":").append(product.price()).append('}');
        }
        json.append(']');
        respond(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private void sales(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            respond(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"PIN yanlış.\"}");
            return;
        }
        if ("/api/sales/delete".equals(exchange.getRequestURI().getPath())) {
            deleteOneSale(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Yalnızca POST destekleniyor.\"}");
            return;
        }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = parseForm(body);
            int productId = Integer.parseInt(form.getOrDefault("productId", ""));
            String paymentMethod = form.get("paymentMethod");
            Product product = backend.products().listActive().stream()
                    .filter(item -> item.id() == productId).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı."));
            long id = backend.sales().record(product, LocalDate.now(), 1, product.price(), paymentMethod, "Telefon siparişi");
            respond(exchange, 201, "application/json; charset=utf-8", "{\"ok\":true,\"saleId\":" + id + "}");
            onDataChanged.run();
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"" + jsonEscape(exception.getMessage()) + "\"}");
        } catch (RuntimeException exception) {
            respond(exchange, 500, "application/json; charset=utf-8", "{\"error\":\"Satış kaydedilemedi.\"}");
        }
    }

    private void deleteOneSale(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Yalnızca POST destekleniyor.\"}");
            return;
        }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int productId = Integer.parseInt(parseForm(body).getOrDefault("productId", ""));
            Product product = backend.products().listActive().stream()
                    .filter(item -> item.id() == productId).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı."));
            var latestSale = backend.reports().sales(LocalDate.now(), LocalDate.now()).stream()
                    .filter(sale -> sale.productName().equals(product.name()) && sale.variant().equals(product.variant()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Bugün bu ürün için silinecek satış yok."));
            if (!backend.sales().deleteOneSaleUnit(latestSale.id()))
                throw new IllegalArgumentException("Satış zaten silinmiş.");
            respond(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"deletedSaleId\":" + latestSale.id() + "}");
            onDataChanged.run();
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"" + jsonEscape(exception.getMessage()) + "\"}");
        } catch (RuntimeException exception) {
            respond(exchange, 500, "application/json; charset=utf-8", "{\"error\":\"Satış silinemedi.\"}");
        }
    }

    private void orders(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            respond(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"PIN yanlış.\"}");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Yalnızca POST destekleniyor.\"}");
            return;
        }
        try {
            Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String paymentMethod = form.get("paymentMethod");
            Map<Integer, Product> active = new HashMap<>();
            for (Product product : backend.products().listActive()) active.put(product.id(), product);
            Map<Integer, Integer> quantities = new HashMap<>();
            String items = form.getOrDefault("items", "");
            for (String entry : items.split(",")) {
                if (entry.isBlank()) continue;
                String[] pair = entry.split(":", -1);
                if (pair.length != 2) throw new IllegalArgumentException("Sipariş ürünü okunamadı.");
                int productId = Integer.parseInt(pair[0]);
                int quantity = Integer.parseInt(pair[1]);
                if (quantity < 1 || quantity > 1000) throw new IllegalArgumentException("Ürün adedi 1 ile 1000 arasında olmalı.");
                if (!active.containsKey(productId)) throw new IllegalArgumentException("Ürün artık satışta değil.");
                quantities.merge(productId, quantity, Integer::sum);
            }
            List<OrderLine> lines = quantities.entrySet().stream()
                    .map(entry -> new OrderLine(active.get(entry.getKey()), entry.getValue()))
                    .toList();
            String orderId = backend.sales().saveOrder(lines, LocalDate.now(), paymentMethod);
            respond(exchange, 201, "application/json; charset=utf-8", "{\"ok\":true,\"orderId\":\"" + orderId + "\",\"lineCount\":" + lines.size() + "}");
            onDataChanged.run();
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"" + jsonEscape(exception.getMessage()) + "\"}");
        } catch (RuntimeException exception) {
            respond(exchange, 500, "application/json; charset=utf-8", "{\"error\":\"Sipariş kaydedilemedi.\"}");
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String supplied = exchange.getRequestHeaders().getFirst("X-Access-Code");
        return supplied != null && java.security.MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8), accessCode.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> fields = new HashMap<>();
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            if (separator >= 0) {
                fields.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
            }
        }
        return fields;
    }

    private static String loadOrCreateAccessCode(Path dataDirectory) {
        Path codeFile = dataDirectory.resolve("phone-access-code.txt");
        try {
            Files.createDirectories(dataDirectory);
            if (Files.exists(codeFile)) {
                String existing = Files.readString(codeFile, StandardCharsets.UTF_8).trim();
                if (existing.matches("[0-9]{6}")) return existing;
            }
            String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
            Files.writeString(codeFile, code, StandardCharsets.UTF_8);
            return code;
        } catch (IOException exception) {
            throw new IllegalStateException("Telefon erişim kodu oluşturulamadı.", exception);
        }
    }

    private static String lanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && address.isSiteLocalAddress())
                        return address.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return "BILGISAYAR-IP";
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    @Override public void close() {
        if (server != null) server.stop(1);
    }
}
