package app.backend.service;

import app.backend.model.Product;
import app.backend.repository.ProductRepository;

import java.util.List;

public final class ProductService {
    private final ProductRepository products;
    public ProductService(ProductRepository products) { this.products = products; }

    public List<Product> listActive() { return products.findActiveProducts(); }

    public void save(Product product) {
        if (product == null) throw new IllegalArgumentException("Ürün bilgisi gerekli.");
        if (product.name() == null || product.name().isBlank()) throw new IllegalArgumentException("Ürün adı boş olamaz.");
        if (product.variant() == null || product.variant().isBlank()) throw new IllegalArgumentException("Porsiyon boş olamaz.");
        if (!Double.isFinite(product.price()) || product.price() < 0 || !Double.isFinite(product.cost()) || product.cost() < 0)
            throw new IllegalArgumentException("Fiyat ve maliyet geçerli, negatif olmayan sayılar olmalı.");
        products.save(product);
    }

    public void delete(int productId) {
        if (productId <= 0) throw new IllegalArgumentException("Geçersiz ürün numarası.");
        if (!products.deactivate(productId)) throw new IllegalStateException("Ürün bulunamadı veya zaten silinmiş.");
    }
}
