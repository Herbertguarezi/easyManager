package com.guarezi.easymanager.application.ports.in;

import com.guarezi.easymanager.domain.classes.Product;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductUseCases {
    // Use case to create a product
    Product create(String name, int amount, MultipartFile file, HttpServletRequest request) throws IOException;

    // Use case to delete a product
    void delete(UUID id);

    // Use cases to get all products or a specific product by id
    List<Product> getProducts();
    Optional<Product> getProduct(UUID id);

    // Use case to update a product
    Product updateProduct(Product newProduct, UUID id);

    String saveImage(MultipartFile file) throws IOException;
}
