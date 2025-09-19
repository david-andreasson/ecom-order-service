package se.moln.orderservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import se.moln.orderservice.dto.*;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderItem;
import se.moln.orderservice.model.OrderStatus;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final WebClient.Builder webClientBuilder;
    private final OrderRepository orderRepository;
    private final String userServiceUrl;
    private final String productServiceUrl;
    private final JwtService jwtService;

    public OrderService(WebClient.Builder webClientBuilder,
                        OrderRepository orderRepository,
                        @Value("${userservice.url}") String userServiceUrl,
                        @Value("${productservice.url}") String productServiceUrl,
                        JwtService jwtService) {
        this.webClientBuilder = webClientBuilder;
        this.orderRepository = orderRepository;
        this.userServiceUrl = userServiceUrl;
        this.productServiceUrl = productServiceUrl;
        this.jwtService = jwtService;
    }

    public Mono<PurchaseResponse> purchaseProduct(PurchaseRequest request, String jwtToken) {
        if (jwtToken == null || jwtToken.isBlank()) {
            return Mono.error(new IllegalArgumentException("Missing bearer token"));
        }
        UUID userId = jwtService.extractUserId(jwtToken);
        String correlationId = UUID.randomUUID().toString();
        WebClient webClient = webClientBuilder.build();

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.CREATED);
        order.setOrderDate(OffsetDateTime.now());
        order.setOrderNumber(generateOrderNumber());

        return Flux.fromIterable(request.items())
                .flatMap(itemReq ->
                        // hämta produktinfo
                        webClient.get()
                                .uri(productServiceUrl + "/api/products/{id}", itemReq.productId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                .header("X-Correlation-Id", correlationId)
                                .retrieve()
                                .bodyToMono(ProductResponse.class)
                                .flatMap(prod ->
                                        // reservera lagret
                                        webClient.post()
                                                .uri(productServiceUrl + "/api/inventory/{id}/purchase", itemReq.productId())
                                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                                                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                .header("X-Correlation-Id", correlationId)
                                                .bodyValue(new InventoryPurchaseRequest(itemReq.quantity()))
                                                .retrieve()
                                                .toBodilessEntity()
                                                .thenReturn(prod)
                                )
                                .map(prod -> {
                                    OrderItem item = new OrderItem();
                                    item.setProductId(itemReq.productId());
                                    item.setQuantity(itemReq.quantity());
                                    item.setPriceAtPurchase(prod.price());
                                    item.setProductName(prod.name());
                                    item.setOrder(order);
                                    return item;
                                })
                )
                .collectList()
                .flatMap(items -> {
                    order.setOrderItems(items);

                    BigDecimal total = items.stream()
                            .map(i -> i.getPriceAtPurchase().multiply(BigDecimal.valueOf(i.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    order.setTotalAmount(total);

                    return Mono.fromCallable(() -> orderRepository.save(order))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(saved -> new PurchaseResponse(saved.getId(), saved.getOrderNumber(), saved.getTotalAmount()))
                            .onErrorResume(err -> {
                                // Rollback: returnera alla reserverade produkter
                                return Flux.fromIterable(order.getOrderItems())
                                        .flatMap(item ->
                                                webClient.post()
                                                        .uri(productServiceUrl + "/api/inventory/{id}/return", item.getProductId())
                                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                                                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                                        .header("X-Correlation-Id", correlationId)
                                                        .bodyValue(new InventoryPurchaseRequest(item.getQuantity()))
                                                        .retrieve()
                                                        .toBodilessEntity()
                                                        .onErrorResume(refundErr -> Mono.empty())
                                        )
                                        .then(Mono.error(err));
                            });
                });
    }


    public Mono<List<OrderHistoryDto>> getOrderHistory(String jwtToken, int page, int size) {
        if (jwtToken == null || jwtToken.isBlank()) {
            return Mono.error(new IllegalArgumentException("Missing bearer token"));
        }
        UUID userId = jwtService.extractUserId(jwtToken);
        return Mono.fromCallable(() ->  orderRepository.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate")))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(pageObj -> pageObj.getContent().stream().map(o -> new OrderHistoryDto(
                        o.getId(),
                        o.getOrderNumber(),
                        o.getTotalAmount(),
                        o.getStatus(),
                        o.getOrderDate(),
                        o.getOrderItems().stream().map(oi -> new OrderItemDto(
                                oi.getProductId(), oi.getProductName(), oi.getQuantity(), oi.getPriceAtPurchase()
                        )).toList()
                )).toList());
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}