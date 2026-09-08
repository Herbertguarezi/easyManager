package com.guarezi.easymanager.products.presentation;

import com.guarezi.easymanager.products.application.ports.in.ProductUseCases;
import com.guarezi.easymanager.products.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers only the endpoints with correct current behavior. GET /products/{id}
// and PUT /products/{id} have known bugs (see docs/easy-manager-software-
// engineering.md sec. 13 and PROGRESS.md Fase 3) that are fixed in a later
// phase, not characterized here.
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductUseCases productUseCases;

    @Test
    void listProductsReturnsAllProducts() throws Exception {
        UUID id = UUID.randomUUID();
        when(productUseCases.getProducts()).thenReturn(List.of(new Product(id, "Chair", 5, "url", "123")));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chair"));
    }

    @Test
    void deleteProductRemovesIt() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/products/" + id))
                .andExpect(status().isOk());

        verify(productUseCases).delete(id);
    }
}
