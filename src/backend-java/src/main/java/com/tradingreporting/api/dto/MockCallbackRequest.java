package com.tradingreporting.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MockCallbackRequest(@NotBlank @Size(min = 1, max = 100) String username) {
}
