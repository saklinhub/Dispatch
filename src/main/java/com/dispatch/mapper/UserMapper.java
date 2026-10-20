package com.dispatch.mapper;

import com.dispatch.dto.response.UserResponse;
import com.dispatch.entity.User;
import org.springframework.stereotype.Component;

/**
 * Mapper for User entity and DTO conversions.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
