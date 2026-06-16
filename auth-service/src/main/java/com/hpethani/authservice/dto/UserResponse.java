package com.hpethani.authservice.dto;

import com.hpethani.authservice.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String email;
    private String password;
    private Role role; // Optional: defaults to USER if not provided

}
