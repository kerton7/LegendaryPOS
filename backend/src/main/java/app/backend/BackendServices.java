package app.backend;

import app.backend.persistence.SqliteDatabase;
import app.backend.service.ProductService;
import app.backend.service.ReportService;
import app.backend.service.SalesService;
import java.nio.file.Path;

/** Composition root for the local application backend. */
public final class BackendServices {
    private final ProductService products;
    private final SalesService sales;
    private final ReportService reports;
    private final SqliteDatabase database;

    private BackendServices(SqliteDatabase database) {
        this.database = database;
        products = new ProductService(database); sales = new SalesService(database); reports = new ReportService(database);
    }

    public static BackendServices open() {
        SqliteDatabase database = new SqliteDatabase(); database.initialize();
        return new BackendServices(database);
    }
    public ProductService products() { return products; }
    public SalesService sales() { return sales; }
    public ReportService reports() { return reports; }
    public Path databaseDirectory() { return database.dataDirectory(); }
}
