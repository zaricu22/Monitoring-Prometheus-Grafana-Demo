package com.example.monitoring.order;

import com.example.monitoring.chaos.ChaosService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every call is recorded automatically by Spring Boot as {@code http_server_requests_seconds}
 * with the labels method, uri (the route template, e.g. /api/orders/{id}), status, outcome and exception.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository repository;
    private final ChaosService chaos;
    private final JdbcTemplate jdbc;

    public OrderController(OrderService orderService, OrderRepository repository, ChaosService chaos, JdbcTemplate jdbc) {
        this.orderService = orderService;
        this.repository = repository;
        this.chaos = chaos;
        this.jdbc = jdbc;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@Valid @RequestBody CreateOrderRequest request) {
        chaos.apply();
        return orderService.create(request);
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable long id) {
        chaos.apply();
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }

    @GetMapping
    public List<Order> latest(@RequestParam(defaultValue = "20") int limit) {
        chaos.apply();
        return repository.findAllByOrderByIdDesc(PageRequest.of(0, Math.min(limit, 100)));
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        chaos.apply();
        long dbLatencyMs = chaos.get().dbLatencyMs();
        if (dbLatencyMs > 0) {
            // Simulates a slow query: the connection is held for the whole sleep, so concurrent
            // requests queue up for the small Hikari pool (watch hikaricp_connections_pending).
            jdbc.queryForObject("select 1 from pg_sleep(?)", Integer.class, dbLatencyMs / 1000.0);
        }
        Map<String, Long> result = new TreeMap<>();
        repository.countByStatus().forEach(row -> result.put(row.getStatus().name(), row.getCount()));
        return result;
    }
}
