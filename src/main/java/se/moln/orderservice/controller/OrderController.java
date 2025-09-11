package se.moln.orderservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import se.moln.orderservice.dto.PurchaseRequest;
import se.moln.orderservice.dto.PurchaseResponse;
import se.moln.orderservice.service.OrderService;


@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }


    @PostMapping("/purchase")
    public Mono<ResponseEntity<PurchaseResponse>> purchaseProduct(
            @RequestBody PurchaseRequest purchaseRequest,
            @RequestHeader("Authorization") String authorizationHeader) {

        String token = authorizationHeader.replace("Bearer ", "");
        System.out.println("hello");

        return orderService.purchaseProduct(
                        purchaseRequest.productId(),
                        purchaseRequest.quantity(),
                        token
                )
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}