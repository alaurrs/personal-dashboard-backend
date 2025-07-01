package com.dashboard.backend.User;

public record RegisterRequest (
    String email,
    String password,
    String fullName
){
}
