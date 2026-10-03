package com.example.monitoring.order;

import com.example.monitoring.payment.PaymentService;
import com.example.monitoring.payment.PaymentService.PaymentDeclinedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Business logic plus programmatic (builder-style) Micrometer instrumentation.
 * Covers the four Prometheus metric types:
 *   Counter   - {@code orders_placed_total}, {@code orders_payments_total} - cumulative value over time (can only go up)
 *   Gauge     - {@code orders_queue_size} - current value at scrape time (changes up and down)
 *   Summary   - {@code orders_amount_euros} - quantiles computed inside the app (how many requests were above/below a certain value)
 *   Histogram - {@code orders_shipping_seconds_bucket} - quantiles computed by PromQL (how many requests were above/below a certain value)
 * Data model: time series <=> metric name + labels(key=value pairs) + samples(timestamp + value)
 */
@Service
public class OrderService {

    private static final Map<String, BigDecimal> PRICES = Map.of(
            "keyboard", new BigDecimal("49.90"),
            "mouse", new BigDecimal("19.90"),
            "monitor", new BigDecimal("229.00"),
            "headset", new BigDecimal("79.00"),
            "laptop", new BigDecimal("1199.00"));

    /** Paid orders waiting for the shipping worker - its size is the saturation signal. */
    private final Queue<Long> shippingQueue = new ConcurrentLinkedQueue<>();

    private final OrderRepository repository;
    private final PaymentService paymentService;
    /**
     * Micrometer export data through a registry. 
     * A MeterRegistry holds all meters and hands their current values to a backend. 
     * It renders the values as Prometheus text only when /actuator/prometheus is requested. 
    */
    private final MeterRegistry registry;
    // Summary metric
    private final DistributionSummary orderAmount;
    // Histogram metric
    private final Timer shippingTimer;

    public OrderService(OrderRepository repository, PaymentService paymentService, MeterRegistry registry) {
        this.repository = repository;
        this.paymentService = paymentService;
        this.registry = registry;

        // Gauge metric: Micrometer keeps a reference to the queue and calls size() on every scrape.
        registry.gaugeCollectionSize("orders.queue.size", Tags.empty(), shippingQueue);

        // Summary metric: quantiles are calculated in the JVM. Cheap to query, but they CANNOT be
        // aggregated across instances (an average of p95s is not a p95).
        this.orderAmount = DistributionSummary.builder("orders.amount")
                .baseUnit("euros")
                .description("Order value")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Histogram metric: only bucket counters are exported; histogram_quantile() computes percentiles
        // at query time, and buckets CAN be summed across instances.
        this.shippingTimer = Timer.builder("orders.shipping")
                .description("Time to ship one paid order")
                .publishPercentileHistogram()
                // Bound the bucket range; default 1ms..30s would export ~70 buckets.
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(1))
                .register(registry);
    }

    // Counter metric via builder with a bounded label: channel has 3 possible values -> 3 time series.
    // ANTI-PATTERN - never do this: one new time series per each order -> cardinality explosion.
    // example: counter("orderId", ...).increment(); - order ids, e-mails, raw URLs, timestamps, error messages 
    // (it has a big range of possible values, making new metric series for nearly every request, causing memory explosion).
    public Order create(CreateOrderRequest request) {
        BigDecimal unitPrice = PRICES.get(request.product());
        if (unitPrice == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product: " + request.product());
        }
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
        Order order = repository.save(new Order(request.product(), request.quantity(), amount, request.channel()));

        
        counter("orders.placed", "channel", request.channel()).increment();

        orderAmount.record(amount.doubleValue());

        try {
            paymentService.charge(request.channel(), amount);
            order.setStatus(Order.Status.PAID);
            counter("orders.payments", "result", "success").increment();
            shippingQueue.add(order.getId());
        } catch (PaymentDeclinedException e) {
            order.setStatus(Order.Status.PAYMENT_FAILED);
            counter("orders.payments", "result", "declined").increment();
        }
        return repository.save(order);
    }

    /** Shipping worker: handles at most 3 orders every 200 ms (~15/s). Faster input makes the queue grow. */
    @Scheduled(fixedDelay = 200)
    public void shipPending() {
        for (int i = 0; i < 3; i++) {
            Long id = shippingQueue.poll();
            if (id == null) {
                return;
            }
            shippingTimer.record(() -> repository.findById(id).ifPresent(order -> {
                order.setStatus(Order.Status.SHIPPED);
                repository.save(order);
            }));
        }
    }

    // Couner metric via builder.
    private Counter counter(String name, String tagKey, String tagValue) {
        // Builders are idempotent: the same name + tags returns the already registered counter.
        return Counter.builder(name).tag(tagKey, tagValue).register(registry);
    }
}
