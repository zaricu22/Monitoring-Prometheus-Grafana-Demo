package com.example.monitoring.chaos;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class ChaosService {

    private static final Logger log = LoggerFactory.getLogger(ChaosService.class);

    private volatile ChaosSettings settings = ChaosSettings.NONE;

    public ChaosService(MeterRegistry registry) {
        // Gauges sample a value on every scrape. Exposing the injected faults lets Grafana show
        // "cause" (chaos settings) and "effect" (error rate / latency) on the same dashboard.
        Gauge.builder("chaos.error.ratio", this, s -> s.settings.errorRate())
                .description("Configured probability of an injected HTTP 500")
                .register(registry);
        Gauge.builder("chaos.latency", this, s -> s.settings.latencyMs() / 1000.0)
                .baseUnit("seconds")
                .description("Configured extra latency per order request")
                .register(registry);
        Gauge.builder("chaos.db.latency", this, s -> s.settings.dbLatencyMs() / 1000.0)
                .baseUnit("seconds")
                .description("Configured time the stats query holds a DB connection")
                .register(registry);
    }

    public ChaosSettings get() {
        return settings;
    }

    public void set(ChaosSettings newSettings) {
        log.warn("Chaos settings changed: {} -> {}", settings, newSettings);
        settings = newSettings;
    }

    /** Applies the configured extra latency and, with the configured probability, fails the request. */
    public void apply() {
        ChaosSettings current = settings;
        if (current.latencyMs() > 0) {
            sleep(current.latencyMs());
        }
        if (current.errorRate() > 0 && ThreadLocalRandom.current().nextDouble() < current.errorRate()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Injected failure (chaos)");
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
