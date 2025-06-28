package com.guarezi.easymanager.application.ports.in;

import com.guarezi.easymanager.domain.classes.User;

import java.util.UUID;

public interface UserUseCases {
    // Use case to create an user
    User create(User user);

    // Use case to delete an user
    void delete(UUID id);


}
