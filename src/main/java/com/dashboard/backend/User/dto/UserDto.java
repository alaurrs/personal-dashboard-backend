package com.dashboard.backend.User.dto;

import com.dashboard.backend.User.model.User;

public record UserDto(String id, String email, String fullName) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName()
        );
    }

}
