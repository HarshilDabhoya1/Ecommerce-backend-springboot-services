package com.scaler.userservice.service;

import com.scaler.userservice.model.Session;
import com.scaler.userservice.model.User;

public interface UserService {

    User signUp(String name, String email, String password);

    /** Creates a user with ADMIN role. Caller must validate the admin secret before calling. */
    User signUpAdmin(String name, String email, String password);

    Session login(String email, String password);

    void logout(String token);

    User validateToken(String token);
}