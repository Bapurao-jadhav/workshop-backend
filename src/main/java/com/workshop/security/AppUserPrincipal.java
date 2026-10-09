package com.workshop.security;

import com.workshop.entity.Role;

/** Authenticated user placed in the SecurityContext by {@link JwtAuthenticationFilter}. */
public record AppUserPrincipal(Long id, String email, String name, Role role) {
}
