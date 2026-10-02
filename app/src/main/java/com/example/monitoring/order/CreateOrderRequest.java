package com.example.monitoring.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateOrderRequest(
        @NotBlank String product,
        @Min(1) @Max(100) int quantity,
        @NotBlank @Pattern(regexp = "web|mobile|partner") String channel) {
}
