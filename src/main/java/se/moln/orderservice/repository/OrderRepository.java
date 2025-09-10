package se.moln.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.moln.orderservice.model.Order;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserIdOrderByOrderDateDesc(UUID userId);
}