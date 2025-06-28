package com.guarezi.easymanager.domain.classes;

import com.guarezi.easymanager.domain.enums.Role;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private UUID id;
    private String name;
    private String username;
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Role> roles;

}
