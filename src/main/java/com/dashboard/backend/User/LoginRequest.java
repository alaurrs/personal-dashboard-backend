package com.dashboard.backend.User;

public record LoginRequest(
    String email,
    String password
) {

}
