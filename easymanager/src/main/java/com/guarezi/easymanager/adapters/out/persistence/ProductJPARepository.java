package com.guarezi.easymanager.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductJPARepository extends JpaRepository<ProductEntity, UUID> {
}
