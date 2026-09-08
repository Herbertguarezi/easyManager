package com.guarezi.easymanager.products.infrastructure.persistence;

import com.guarezi.easymanager.products.application.ports.out.ProductRepository;
import com.guarezi.easymanager.products.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductRepositoryPersistenceAdapterTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savesRetrievesAndDeletesAProduct() {
        Product product = new Product(null, "Chair", 5, "http://example.com/photo.jpg", "999888777");

        Product saved = productRepository.saveProduct(product);
        assertThat(saved.getId()).isNotNull();

        Optional<Product> found = productRepository.getProduct(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Chair");
        assertThat(found.get().getAmount()).isEqualTo(5);
        assertThat(found.get().getBarcode()).isEqualTo("999888777");

        productRepository.deleteProduct(saved.getId());
        assertThat(productRepository.getProduct(saved.getId())).isEmpty();
    }
}
