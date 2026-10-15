package com.guarezi.easymanager.auth.presentation;

import com.guarezi.easymanager.auth.domain.SellerStatus;

import java.util.UUID;

public record RegisterResponse(UUID id, String email, SellerStatus status) {
}
