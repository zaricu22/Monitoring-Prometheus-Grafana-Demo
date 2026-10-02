package com.example.monitoring.chaos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Fault injection knobs, changed at runtime through {@code /api/chaos} to make dashboards and alerts react.
 *
 * @param errorRate   probability (0..1) that an order endpoint answers with HTTP 500
 * @param latencyMs   extra latency added to every order endpoint call
 * @param dbLatencyMs time the stats query holds a DB connection (via pg_sleep) - exhausts the Hikari pool
 */
public record ChaosSettings(
        @DecimalMin("0.0") @DecimalMax("1.0") double errorRate,
        @Min(0) @Max(10_000) long latencyMs,
        @Min(0) @Max(10_000) long dbLatencyMs) {

    public static final ChaosSettings NONE = new ChaosSettings(0, 0, 0);
}
