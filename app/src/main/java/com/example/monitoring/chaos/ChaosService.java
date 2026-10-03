package com.example.monitoring.chaos;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ThreadLocalRandom;

/** 
 * ChaosService is used to simulate failures and latency for testing purposes.
 * 
 * Plus programmatic (builder-style) Micrometer instrumentation.
 * Covers the Prometheus metric type:
 *   Gauge     - {@code orders_queue_size} - current value at scrape time (changes up and down)
 * Data model: time series <=> metric name + labels(key=value pairs) + samples(timestamp + value)
 * 
 * We really slow down and break the app: apply() and /stats cause the real faults:
 *   - Thread.sleep(latencyMs) really blocks the request thread, so the response really arrives later.
 *   - throw new ResponseStatusException(500) really returns HTTP 500 to the client.
 *   - select 1 from pg_sleep(...) really holds a Postgres connection, so the Hikari pool really runs out.
 */
@Service
public class ChaosService {

    private static final Logger log = LoggerFactory.getLogger(ChaosService.class);

    private volatile ChaosSettings settings = ChaosSettings.NONE;

    /**  
     * Constructor only report the setting values as gague metric (changeable numbers up/down over time), but does not apply any chaos. 
     * Chaos only takes effect after PUT /api/chaos, and DELETE /api/chaos sets it back to 0.
     * The effect is measured independently, by the normal metrics that know nothing about chaos
    */
    public ChaosService(MeterRegistry registry) {
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
