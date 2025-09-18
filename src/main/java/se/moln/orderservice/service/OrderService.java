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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import se.moln.orderservice.dto.OrderHistoryDto;
import se.moln.orderservice.dto.OrderItemDto;
import se.moln.orderservice.dto.ProductResponse;
import se.moln.orderservice.dto.PurchaseResponse;
import se.moln.orderservice.dto.InventoryPurchaseRequest;
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

    public Mono<PurchaseResponse> purchaseProduct(UUID productId, int qty, String jwtToken) {
        if (jwtToken == null || jwtToken.isBlank()) {
            return Mono.error(new IllegalArgumentException("Missing bearer token"));
        }
        UUID userId = jwtService.extractUserId(jwtToken);
        String correlationId = UUID.randomUUID().toString();

        WebClient webClient = webClientBuilder.build();

        return webClient.get()
                .uri(productServiceUrl + "/api/products/{id}", productId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .onStatus(sc -> sc.is4xxClientError(), rsp ->
                        rsp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        body.isBlank() ? ("Product not found | cid=" + correlationId) : (body + " | cid=" + correlationId)
                                )))
                )
                .onStatus(sc -> sc.is5xxServerError(), rsp ->
                        rsp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        body.isBlank() ? ("Product service error | cid=" + correlationId) : (body + " | cid=" + correlationId)
                                )))
                )
                .bodyToMono(ProductResponse.class)
                .flatMap(prod -> webClient.post()
                        .uri(productServiceUrl + "/api/inventory/{id}/purchase", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .header("X-Correlation-Id", correlationId)
                        .bodyValue(new InventoryPurchaseRequest(qty))
                        .retrieve()
                        .onStatus(sc -> sc.isError(), rsp ->
                                rsp.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .flatMap(body -> {
                                            HttpStatus status = HttpStatus.valueOf(rsp.statusCode().value());
                                            HttpStatus mapped = status.is5xxServerError() ? HttpStatus.BAD_GATEWAY : status;
                                            String baseMsg = (status.value() == 409)
                                                    ? "Insufficient stock"
                                                    : ("Inventory purchase failed: " + status);
                                            String msg = (body.isBlank() ? baseMsg : (baseMsg + " | " + body)) + " | cid=" + correlationId;
                                            return Mono.error(new ResponseStatusException(mapped, msg));
                                        })
                        )
                        .toBodilessEntity()
                        .thenReturn(prod))
                .flatMap(prod -> {
                    Order order = new Order();
                    order.setUserId(userId);
                    order.setStatus(OrderStatus.CREATED);
                    order.setOrderDate(OffsetDateTime.now());
                    order.setOrderNumber(generateOrderNumber());

                    OrderItem item = new OrderItem();
                    item.setProductId(productId);
                    item.setQuantity(qty);
                    item.setPriceAtPurchase(prod.price());
                    item.setProductName(prod.name());
                    item.setOrder(order);
                    order.setOrderItems(List.of(item));

                    BigDecimal total = prod.price().multiply(BigDecimal.valueOf(qty));
                    order.setTotalAmount(total);

                    return Mono.fromCallable(() -> orderRepository.save(order))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(saved -> new PurchaseResponse(saved.getId(), saved.getOrderNumber(), saved.getTotalAmount()))
                            .onErrorResume(err ->
                                    // Compensation: refund inventory if order save fails
                                    webClient.post()
                                            .uri(productServiceUrl + "/api/inventory/{id}/return", productId)
                                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                                            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                            .header("X-Correlation-Id", correlationId)
                                            .bodyValue(new InventoryPurchaseRequest(qty))
                                            .retrieve()
                                            .toBodilessEntity()
                                            .onErrorResume(refundErr -> Mono.empty()) // swallow refund errors
                                            .then(Mono.error(err))
                            );
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