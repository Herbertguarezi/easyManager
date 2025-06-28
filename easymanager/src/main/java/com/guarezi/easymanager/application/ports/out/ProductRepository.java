package com.guarezi.easymanager.application.ports.out;

import com.guarezi.easymanager.domain.classes.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    List<Product> getProducts();
    Optional<Product> getProduct(UUID id);
    Product saveProduct(Product product);
    Product saveProduct(Product newProduct, UUID id);
    void deleteProduct(UUID id);
}