package com.example.monitoring.payment;

import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/** Simulates a call to an external payment provider. */
@Service
public class PaymentService {

    /**
     * Declarative instrumentation: the {@code @Timed} aspect wraps this method in a Timer.
     * {@code histogram = true} publishes {@code payment_process_seconds_bucket} so percentiles
     * can be computed in PromQL. The {@code exception} tag tells successes and failures apart.
     */
    @Timed(value = "payment.process", description = "Time spent calling the payment provider", histogram = true)
    public void charge(String channel, BigDecimal amount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        sleep(20 + random.nextInt(130));
        if (random.nextDouble() < 0.03) {
            throw new PaymentDeclinedException("Payment declined by provider");
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class PaymentDeclinedException extends RuntimeException {
        public PaymentDeclinedException(String message) {
            super(message);
        }
    }
}
