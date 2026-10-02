package com.example.monitoring.order;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    public enum Status { NEW, PAID, PAYMENT_FAILED, SHIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String product;
    private int quantity;
    private BigDecimal amount;
    private String channel;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant createdAt;

    protected Order() {
    }

    public Order(String product, int quantity, BigDecimal amount, String channel) {
        this.product = product;
        this.quantity = quantity;
        this.amount = amount;
        this.channel = channel;
        this.status = Status.NEW;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public String getChannel() { return channel; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(Status status) { this.status = status; }
}
