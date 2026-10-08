package app.backend.repository;

import app.backend.model.Product;
import java.util.List;

public interface ProductRepository {
    List<Product> findActiveProducts();
    void save(Product product);
    boolean deactivate(int productId);
}
