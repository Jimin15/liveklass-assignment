package com.example.liveklass.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserResponse {

    private final Long id;
    private final String name;

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName());
    }
}
