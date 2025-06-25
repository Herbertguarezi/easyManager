package com.guarezi.easymanager.adapters.in.web;

import com.guarezi.easymanager.application.ports.in.ProductUseCases;
import com.guarezi.easymanager.domain.Product;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/products")
@CrossOrigin("*")
public class ProductController {
    @Autowired
    ProductUseCases productUseCases;

    @GetMapping
    public List<Product> listProducts() {
        return productUseCases.getProducts();
    }

    @PostMapping
    public ResponseEntity<?> createProduct(
            @RequestParam("name") String name,
            @RequestParam("amount") int amount,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        try {
            return new ResponseEntity<>(productUseCases.create(name, amount, file, request), HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Erro ao salvar produto: " + e.getMessage());
        }
    }
}

