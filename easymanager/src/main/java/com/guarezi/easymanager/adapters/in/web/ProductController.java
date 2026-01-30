package com.guarezi.easymanager.adapters.in.web;

import com.guarezi.easymanager.application.ports.in.ProductUseCases;
import com.guarezi.easymanager.domain.classes.Product;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@CrossOrigin("*")
public class ProductController {
    @Autowired
    ProductUseCases productUseCases;

    // Endpoint to list all products
    @GetMapping
    public List<Product> listProducts() {
        return productUseCases.getProducts();
    }

    // Endpoint to list a specific product
    @GetMapping("/{id}")
    public ResponseEntity<?> listProduct(@PathVariable UUID id){
        try{
            Optional<Product> product= productUseCases.getProduct(id);
            if (product.isPresent()) throw new Exception();
            return new ResponseEntity<>(product.get(), HttpStatus.OK);
        } catch (Exception e) {
            return  ResponseEntity.status(500).body("Can`t find the product " + e.getMessage());
        }
    }

    // Endpoint to create a new product
    @PostMapping
    public ResponseEntity<?> createProduct(
            @RequestParam("name") String name,
            @RequestParam("amount") int amount,
            @RequestParam("file") MultipartFile file,
            @RequestParam("barcode") String barcode,
            HttpServletRequest request
    ) {
        try {
            return new ResponseEntity<>(productUseCases.create(name, amount, file, barcode, request), HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Erro ao salvar produto: " + e.getMessage());
        }
    }

    // Endpoint to delete the product
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable UUID id){
        try{
            productUseCases.delete(id);
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>("Can`t delete product " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable UUID id, @RequestBody Product newProduct){
        try{
            return new ResponseEntity<>("Produto atualizado " + productUseCases.updateProduct(newProduct, id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Can`t update the product: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}

