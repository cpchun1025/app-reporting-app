package com.tradingreporting.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank @Size(min = 1, max = 100) String username,
        @NotBlank @Size(min = 1, max = 256) String password) {
}
