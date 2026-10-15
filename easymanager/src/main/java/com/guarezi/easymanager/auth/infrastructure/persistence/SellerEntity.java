package com.guarezi.easymanager.auth.infrastructure.persistence;

import com.guarezi.easymanager.auth.domain.Role;
import com.guarezi.easymanager.auth.domain.SellerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "sellers")
public class SellerEntity {

    // New tables (V2+) use CHAR(36) for UUID primary keys, per
    // docs/easy-manager-arquitetura-banco-de-dados.md sec. 1 — unlike
    // products.id (V1, native UUID column type, kept only to match the
    // legacy schema it bootstraps). JdbcTypeCode forces that mapping;
    // without it Hibernate defaults java.util.UUID to a native UUID column.
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    @Column(name = "email_confirmed_at")
    private Instant emailConfirmedAt;

    @Column(unique = true, columnDefinition = "CHAR(14)")
    private String cnpj;

    @Column(name = "cnpj_verified_at")
    private Instant cnpjVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "mfa_secret")
    private String mfaSecret;
}
