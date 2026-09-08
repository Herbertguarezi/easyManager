package com.guarezi.easymanager.products.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductJPARepository extends JpaRepository<ProductEntity, UUID> {
}
