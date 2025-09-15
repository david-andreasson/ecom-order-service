package se.moln.orderservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserIdOrderByOrderDateDesc(UUID userId);

    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByUserId(UUID userId, Pageable pageable);

    //används av analytics. Hämtar COMPLETED ordrar i intervallet och laddar orderItems
    @EntityGraph(attributePaths = {"orderItems"})
    List<Order> findByStatusAndOrderDateBetween(OrderStatus status, OffsetDateTime from, OffsetDateTime to);
}
