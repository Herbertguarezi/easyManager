package com.guarezi.easymanager.adapters.out.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserJPARepository extends JpaRepository<UserEntity, UUID> {
}
