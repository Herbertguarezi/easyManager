package com.guarezi.easymanager.auth.application.ports.in;

import com.guarezi.easymanager.auth.domain.Seller;

public interface RegisterSellerUseCase {
    Seller register(String email, String rawPassword);
}
