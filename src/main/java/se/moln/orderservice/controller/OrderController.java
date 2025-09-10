package se.moln.orderservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import se.moln.orderservice.dto.OrderRequest;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Mono<ResponseEntity<Order>> createOrder(@RequestBody OrderRequest req) {
        return orderService.createOrder(req)
                .map(newOrder -> ResponseEntity.status(HttpStatus.CREATED).body(newOrder));
    }
}