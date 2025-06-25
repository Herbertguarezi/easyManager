package com.guarezi.easymanager.application;

import com.guarezi.easymanager.application.ports.in.ProductUseCases;
import com.guarezi.easymanager.application.ports.out.ProductRepository;
import com.guarezi.easymanager.domain.Product;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProductService implements ProductUseCases {

    @Autowired
    ProductRepository productRepository;

    private static final String UPLOAD_FOLDER = "uploads/";

    @Override
    public Product create(Product product){
        return productRepository.saveProduct(product);
    }

    @Override
    public Product create(String name, int amount, MultipartFile file, HttpServletRequest request) throws IOException {
        // Save image
        String fileName = saveImage(file);

        // Generate image URL
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + ":" + request.getServerPort();
        String imageUrl = baseUrl + "/images/" + fileName;

        // Create and save product
        Product product = new Product();
        product.setName(name);
        product.setAmount(amount);
        product.setPhotoUrl(imageUrl);

        return productRepository.saveProduct(product);
    }

    @Override
    public void delete(UUID id) {
        productRepository.deleteProduct(id);
    }

    @Override
    public List<Product> getProducts() {
        return productRepository.getProducts();
    }

    @Override
    public Optional<Product> getProduct(UUID id) {
        return productRepository.getProduct(id);
    }

    @Override
    public Product updateProduct(Product newProduct, UUID id) {
        return productRepository.saveProduct(newProduct, id);
    }

    @Value("${upload.directory}")
    private String uploadDir;


    public String saveImage(MultipartFile file) throws IOException {
        Path directory = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(directory);

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = directory.resolve(fileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return fileName;
    }
}
