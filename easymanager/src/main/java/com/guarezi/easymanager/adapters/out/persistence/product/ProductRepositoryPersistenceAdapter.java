package com.guarezi.easymanager.adapters.out.persistence.product;

import com.guarezi.easymanager.application.ports.out.ProductRepository;
import com.guarezi.easymanager.domain.classes.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductRepositoryPersistenceAdapter implements ProductRepository {
    @Autowired
    ProductJPARepository repository;

    @Override
    public List<Product> getProducts() {
        try{
            return repository.findAll().stream().map(this::toDomain).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get the products" + e);
        }
    }

    @Override
    public Optional<Product> getProduct(UUID id) {
        try{
            return repository.findById(id).map(this::toDomain);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get the product" + e);
        }

    }

    @Override
    public Product saveProduct(Product product) {
        try{
            product.setId(UUID.randomUUID());
            ProductEntity productEntity = toEntity(product);
            return toDomain(repository.save(productEntity));
        } catch (Exception e) {
            throw new RuntimeException("Couldn't save the product " + e);
        }

    }

    @Override
    public Product saveProduct(Product newProduct, UUID id) {
        Optional<Product> product = repository.findById(id).map(this::toDomain);
        try{
            product.get().setName(newProduct.getName());
            product.get().setAmount(newProduct.getAmount());

            return toDomain(repository.save(toEntity(product.get())));
        } catch (Exception e) {
            throw new RuntimeException("Couldn't update the product" + e);
        }
    }

    @Override
    public void deleteProduct(UUID id) {
        repository.deleteById(id);
    }



    private Product toDomain(ProductEntity entity){
        if(entity == null) return null;

        Product product = new Product();
        product.setId(entity.getId());
        product.setName(entity.getName());
        product.setAmount(entity.getAmount());
        product.setPhotoUrl(entity.getPhotoUrl());
        product.setBarcode(entity.getBarcode());

        return product;
    }

    private ProductEntity toEntity(Product product){
        if(product == null) return null;

        ProductEntity entity = new ProductEntity();
        entity.setId(product.getId());
        entity.setName(product.getName());
        entity.setAmount(product.getAmount());
        entity.setPhotoUrl(product.getPhotoUrl());
        entity.setBarcode(product.getBarcode());

        return entity;
    }
}
