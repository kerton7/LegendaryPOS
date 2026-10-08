package app.backend.persistence;

import app.backend.model.Product;
import app.backend.model.OrderLine;
import app.backend.model.ProductTotal;
import app.backend.model.Sale;
import app.backend.model.SalesSummary;
import app.backend.repository.ProductRepository;
import app.backend.repository.ReportRepository;
import app.backend.repository.SalesRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** SQLite persistence and query layer. UI code must not contain SQL. */
public final class SqliteDatabase implements ProductRepository, SalesRepository, ReportRepository {
    private static final String DB_NAME = "efsane-kokorec.sqlite";

    private Path databaseFile() {
        String custom = System.getProperty("efsane.data.dir");
        Path directory;
        if (custom != null && !custom.isBlank()) {
            directory = Path.of(custom);
        } else {
            Path base = Path.of(System.getProperty("user.dir"));
            try {
                Path command = Path.of(ProcessHandle.current().info().command().orElse("")).toAbsolutePath();
                String commandPath = command.toString();
                int bundleMarker = commandPath.indexOf(".app/Contents/MacOS/");
                if (bundleMarker >= 0) {
                    Path appBundle = Path.of(commandPath.substring(0, bundleMarker + ".app".length()));
                    base = appBundle.getParent();
                } else if (command.getFileName() != null && command.getFileName().toString().startsWith("EfsaneKokorec")) {
                    base = command.getParent();
                }
            } catch (Exception ignored) { }
            directory = base.resolve("data");
        }
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            if (custom != null && !custom.isBlank()) throw new IllegalStateException("Veri klasörü oluşturulamadı: " + directory, e);
            directory = userDataDirectory();
            try { Files.createDirectories(directory); }
            catch (Exception fallbackError) { throw new IllegalStateException("Veri klasörü oluşturulamadı: " + directory, fallbackError); }
        }
        return directory.resolve(DB_NAME);
    }

    /** Directory that contains the single local database and device pairing code. */
    public Path dataDirectory() {
        return databaseFile().getParent();
    }

    private Path userDataDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("mac")) return home.resolve("Library/Application Support/Efsane Kokorec");
        if (os.contains("win")) return Path.of(System.getenv().getOrDefault("LOCALAPPDATA", home.resolve("AppData/Local").toString())).resolve("EfsaneKokorec");
        return home.resolve(".local/share/efsane-kokorec");
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA journal_mode=WAL");
        }
        return connection;
    }

    public void initialize() {
        String products = "CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY, name TEXT NOT NULL, variant TEXT NOT NULL, price REAL NOT NULL, cost REAL NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1, UNIQUE(name,variant))";
        String sales = "CREATE TABLE IF NOT EXISTS sales (id INTEGER PRIMARY KEY, sold_at TEXT NOT NULL, sold_time TEXT NOT NULL DEFAULT '', product_id INTEGER NOT NULL, product_name TEXT NOT NULL, variant TEXT NOT NULL, qty REAL NOT NULL, unit_price REAL NOT NULL, unit_cost REAL NOT NULL, payment_method TEXT NOT NULL DEFAULT 'Belirtilmedi', note TEXT DEFAULT '', order_id TEXT, FOREIGN KEY(product_id) REFERENCES products(id))";
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute(products);
            statement.execute(sales);
            if (!hasColumn(connection, "sales", "payment_method")) {
                statement.execute("ALTER TABLE sales ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'Belirtilmedi'");
            }
            if (!hasColumn(connection, "sales", "order_id")) {
                statement.execute("ALTER TABLE sales ADD COLUMN order_id TEXT");
            }
            if (!hasColumn(connection, "sales", "sold_time")) {
                statement.execute("ALTER TABLE sales ADD COLUMN sold_time TEXT NOT NULL DEFAULT ''");
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM products")) {
                if (result.next() && result.getInt(1) == 0) seedProducts(connection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Veritabanı başlatılamadı: " + e.getMessage(), e);
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) if (column.equals(result.getString("name"))) return true;
            return false;
        }
    }

    private void seedProducts(Connection connection) throws SQLException {
        String[][] rows = {
                {"Kuzu Kokoreç", "Çeyrek", "200"}, {"Kuzu Kokoreç", "Yarım", "300"},
                {"Kuzu Kokoreç", "3 Çeyrek", "450"}, {"Kuzu Kokoreç", "Tam", "600"},
                {"Kuzu Kokoreç", "Uykuluk-Badem", "350"}, {"Kasap Sucuk", "Yarım", "200"},
                {"Kasap Sucuk", "3 Çeyrek", "300"}, {"Kasap Sucuk", "Tam", "400"},
                {"Yaprak Ciğer", "Yarım", "0"}, {"Yaprak Ciğer", "3 Çeyrek", "0"},
                {"Yaprak Ciğer", "Tam", "0"}, {"Kuzu Kokoreç", "Gram", "600"}
        };
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO products(name,variant,price,cost) VALUES(?,?,?,0)")) {
            for (String[] row : rows) {
                statement.setString(1, row[0]); statement.setString(2, row[1]);
                statement.setDouble(3, Double.parseDouble(row[2])); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override public List<Product> findActiveProducts() {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT id,name,variant,price,cost FROM products WHERE active=1 ORDER BY name,id";
        try (Connection c = open(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) products.add(new Product(r.getInt(1), r.getString(2), r.getString(3), r.getDouble(4), r.getDouble(5)));
            return products;
        } catch (SQLException e) { throw new IllegalStateException("Ürünler okunamadı", e); }
    }

    @Override public void save(Product product) {
        String sql = product.id() == 0
                ? "INSERT INTO products(name,variant,price,cost) VALUES(?,?,?,?) ON CONFLICT(name,variant) DO UPDATE SET price=excluded.price,cost=excluded.cost,active=1 WHERE products.active=0"
                : "UPDATE products SET name=?,variant=?,price=?,cost=? WHERE id=?";
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, product.name()); s.setString(2, product.variant());
            s.setDouble(3, product.price()); s.setDouble(4, product.cost());
            if (product.id() != 0) s.setInt(5, product.id());
            int changed = s.executeUpdate();
            if (changed == 0) throw new SQLException("Bu ürün ve porsiyon zaten kayıtlı.");
        } catch (SQLException e) { throw new IllegalStateException("Ürün kaydedilemedi: " + e.getMessage(), e); }
    }

    @Override public boolean deactivate(int productId) {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement("UPDATE products SET active=0 WHERE id=? AND active=1")) {
            s.setInt(1, productId); return s.executeUpdate() == 1;
        } catch (SQLException e) { throw new IllegalStateException("Ürün silinemedi: " + e.getMessage(), e); }
    }

    @Override public long insertSale(Product product, LocalDate date, double quantity, double unitPrice, String paymentMethod, String note) {
        String sql = "INSERT INTO sales(sold_at,sold_time,product_id,product_name,variant,qty,unit_price,unit_cost,payment_method,note) VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, date.toString()); s.setString(2, currentTime()); s.setInt(3, product.id()); s.setString(4, product.name());
            s.setString(5, product.variant()); s.setDouble(6, quantity); s.setDouble(7, unitPrice);
            s.setDouble(8, product.cost()); s.setString(9, paymentMethod); s.setString(10, note); s.executeUpdate();
            try (Statement idQuery = c.createStatement(); ResultSet result = idQuery.executeQuery("SELECT last_insert_rowid()")) {
                if (result.next()) return result.getLong(1);
                throw new SQLException("Satış numarası alınamadı.");
            }
        } catch (SQLException e) { throw new IllegalStateException("Satış kaydedilemedi: " + e.getMessage(), e); }
    }

    @Override public boolean deleteSale(long saleId) {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement("DELETE FROM sales WHERE id=?")) {
            s.setLong(1, saleId); return s.executeUpdate() == 1;
        } catch (SQLException e) { throw new IllegalStateException("Son satış geri alınamadı: " + e.getMessage(), e); }
    }

    @Override public String insertOrder(List<OrderLine> lines, LocalDate date, String paymentMethod) {
        String orderId = UUID.randomUUID().toString();
        String orderTime = currentTime();
        String sql = "INSERT INTO sales(sold_at,sold_time,product_id,product_name,variant,qty,unit_price,unit_cost,payment_method,note,order_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            try {
                for (OrderLine line : lines) {
                    Product product = line.product();
                    s.setString(1, date.toString()); s.setString(2, orderTime); s.setInt(3, product.id()); s.setString(4, product.name());
                    s.setString(5, product.variant()); s.setDouble(6, line.quantity()); s.setDouble(7, product.price());
                    s.setDouble(8, product.cost()); s.setString(9, paymentMethod); s.setString(10, "Toplu sipariş");
                    s.setString(11, orderId); s.addBatch();
                }
                s.executeBatch();
                c.commit();
                return orderId;
            } catch (SQLException exception) {
                c.rollback();
                throw exception;
            }
        } catch (SQLException e) { throw new IllegalStateException("Sipariş kaydedilemedi: " + e.getMessage(), e); }
    }

    @Override public boolean deleteOneSaleUnit(long saleId) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (PreparedStatement decrement = c.prepareStatement("UPDATE sales SET qty=qty-1 WHERE id=? AND qty>1")) {
                decrement.setLong(1, saleId);
                if (decrement.executeUpdate() == 1) { c.commit(); return true; }
            }
            try (PreparedStatement delete = c.prepareStatement("DELETE FROM sales WHERE id=?")) {
                delete.setLong(1, saleId);
                boolean deleted = delete.executeUpdate() == 1;
                c.commit();
                return deleted;
            } catch (SQLException exception) {
                c.rollback();
                throw exception;
            }
        } catch (SQLException e) { throw new IllegalStateException("Satış silinemedi: " + e.getMessage(), e); }
    }

    @Override public SalesSummary summarize(LocalDate from, LocalDate to) {
        return summarize(from, to, null);
    }

    @Override public SalesSummary summarize(LocalDate from, LocalDate to, String paymentMethod) {
        String sql = "SELECT COALESCE(SUM(qty*unit_price),0),COALESCE(SUM(qty*(unit_price-unit_cost)),0),COALESCE(SUM(qty),0),COUNT(DISTINCT COALESCE(order_id,CAST(id AS TEXT))) FROM sales WHERE sold_at BETWEEN ? AND ?" + (paymentMethod == null ? "" : " AND payment_method=?");
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, from.toString()); s.setString(2, to.toString());
            if (paymentMethod != null) s.setString(3, paymentMethod);
            try (ResultSet r = s.executeQuery()) {
                r.next(); return new SalesSummary(r.getDouble(1), r.getDouble(2), r.getDouble(3), r.getLong(4));
            }
        } catch (SQLException e) { throw new IllegalStateException("Özet rapor okunamadı", e); }
    }

    @Override public List<ProductTotal> findProductTotals(LocalDate from, LocalDate to) {
        return findProductTotals(from, to, null);
    }

    @Override public List<ProductTotal> findProductTotals(LocalDate from, LocalDate to, String paymentMethod) {
        String sql = "SELECT product_name,variant,SUM(qty),SUM(qty*unit_price),SUM(qty*(unit_price-unit_cost)) FROM sales WHERE sold_at BETWEEN ? AND ?" + (paymentMethod == null ? "" : " AND payment_method=?") + " GROUP BY product_name,variant ORDER BY SUM(qty) DESC";
        List<ProductTotal> rows = new ArrayList<>();
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, from.toString()); s.setString(2, to.toString());
            if (paymentMethod != null) s.setString(3, paymentMethod);
            try (ResultSet r = s.executeQuery()) { while (r.next()) rows.add(new ProductTotal(r.getString(1), r.getString(2), r.getDouble(3), r.getDouble(4), r.getDouble(5))); }
            return rows;
        } catch (SQLException e) { throw new IllegalStateException("Ürün raporu okunamadı", e); }
    }

    @Override public List<Sale> findSales(LocalDate from, LocalDate to) {
        return findSales(from, to, null);
    }

    @Override public List<Sale> findSales(LocalDate from, LocalDate to, String paymentMethod) {
        String sql = "SELECT id,sold_at,sold_time,product_name,variant,qty,qty*unit_price,qty*(unit_price-unit_cost),payment_method,note FROM sales WHERE sold_at BETWEEN ? AND ?" + (paymentMethod == null ? "" : " AND payment_method=?") + " ORDER BY sold_at DESC,sold_time DESC,id DESC LIMIT 100";
        List<Sale> rows = new ArrayList<>();
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, from.toString()); s.setString(2, to.toString());
            if (paymentMethod != null) s.setString(3, paymentMethod);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    String timeValue = r.getString(3);
                    LocalTime time = timeValue == null || timeValue.isBlank() ? null : LocalTime.parse(timeValue);
                    rows.add(new Sale(r.getLong(1), LocalDate.parse(r.getString(2)), time, r.getString(4), r.getString(5),
                            r.getDouble(6), r.getDouble(7), r.getDouble(8), r.getString(9), r.getString(10)));
                }
            }
            return rows;
        } catch (SQLException e) { throw new IllegalStateException("Satış kayıtları okunamadı", e); }
    }

    private String currentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}
