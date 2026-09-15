package com.ecommerce.auth.entity;

import com.ecommerce.common.enums.Role;
import com.ecommerce.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String name;
    private final Role role;
    private final String password;

    private CustomUserDetails(Long id, String email, String name, Role role, String password) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
        this.password = password;
    }

    public static CustomUserDetails fromUser(User user) {
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getPassword()
        );
    }

    public static CustomUserDetails fromClaims(Long id, String email, String name, Role role) {
        return new CustomUserDetails(id, email, name, role, null);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }
}
