package com.omniorder.order.service;

import com.omniorder.order.domain.Order;
import com.omniorder.order.domain.OrderStatus;
import com.omniorder.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReconciliationJob {

    private static final long PENDING_TTL_SECONDS = 300;

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public ReconciliationJob(OrderService orderService,
                             OrderRepository orderRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelayString = "${app.reconciliation.poll-delay-ms:60000}")
    @Transactional
    public void reconcileStuckPendingOrders() {
        Instant cutoff = Instant.now().minusSeconds(PENDING_TTL_SECONDS);
        List<Order> stuck = orderRepository.findByStatusAndCreatedAtBefore(
            OrderStatus.PENDING, cutoff);
        for (Order order : stuck) {
            // rejectOrder is idempotent and publishes stock.release (compensation)
            orderService.rejectOrder(order.getId(), "reconciliation: pending TTL expired");
        }
    }
}
