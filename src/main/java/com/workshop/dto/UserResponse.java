package com.workshop.dto;

import com.workshop.entity.Role;
import com.workshop.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Public profile of a user (never contains the password)")
public record UserResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Asha Patil") String name,
        @Schema(example = "asha@example.com") String email,
        @Schema(example = "+919876543210") String phone,
        @Schema(example = "USER") Role role,
        Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getCreatedAt());
    }
}
