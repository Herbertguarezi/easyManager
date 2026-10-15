package com.guarezi.easymanager.auth.application.ports.out;

import com.guarezi.easymanager.auth.domain.Seller;

import java.util.Optional;
import java.util.UUID;

public interface SellerRepository {
    Seller save(Seller seller);

    Optional<Seller> findById(UUID id);

    Optional<Seller> findByEmail(String email);

    boolean existsByEmail(String email);
}
