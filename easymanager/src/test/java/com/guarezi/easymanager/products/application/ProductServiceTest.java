package com.guarezi.easymanager.products.application;

import com.guarezi.easymanager.products.application.ports.out.ProductRepository;
import com.guarezi.easymanager.products.domain.Product;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private HttpServletRequest request;

    @TempDir
    Path tempDir;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductService();
        ReflectionTestUtils.setField(productService, "productRepository", productRepository);
        ReflectionTestUtils.setField(productService, "uploadDir", tempDir.toString());
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
    }

    @Test
    void createSavesProductWithGeneratedPhotoUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "content".getBytes());
        when(productRepository.saveProduct(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product created = productService.create("Chair", 10, file, "123456", request);

        assertThat(created.getName()).isEqualTo("Chair");
        assertThat(created.getAmount()).isEqualTo(10);
        assertThat(created.getBarcode()).isEqualTo("123456");
        assertThat(created.getPhotoUrl()).startsWith("http://localhost:8080/images/");
        verify(productRepository).saveProduct(any(Product.class));
    }

    @Test
    void deleteDelegatesToRepository() {
        UUID id = UUID.randomUUID();

        productService.delete(id);

        verify(productRepository).deleteProduct(id);
    }

    @Test
    void getProductsReturnsAllFromRepository() {
        List<Product> products = List.of(new Product(UUID.randomUUID(), "Chair", 1, "url", "111"));
        when(productRepository.getProducts()).thenReturn(products);

        assertThat(productService.getProducts()).isEqualTo(products);
    }

    @Test
    void getProductReturnsFromRepositoryById() {
        UUID id = UUID.randomUUID();
        Product product = new Product(id, "Chair", 1, "url", "111");
        when(productRepository.getProduct(id)).thenReturn(Optional.of(product));

        assertThat(productService.getProduct(id)).contains(product);
    }
}
