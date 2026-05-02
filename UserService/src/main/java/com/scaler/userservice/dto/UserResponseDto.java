package com.scaler.userservice.dto;

import com.scaler.userservice.model.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponseDto {
    private Long id;
    private String name;
    private String email;
    private Role role;
}
