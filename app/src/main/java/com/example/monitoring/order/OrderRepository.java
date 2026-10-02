package com.example.monitoring.order;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByOrderByIdDesc(Pageable pageable);

    @Query("select o.status as status, count(o) as count from Order o group by o.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        Order.Status getStatus();
        long getCount();
    }
}
