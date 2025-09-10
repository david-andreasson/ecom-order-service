package se.moln.orderservice.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import se.moln.orderservice.dto.OrderRequest;
import se.moln.orderservice.dto.ProductResponse;
import se.moln.orderservice.dto.AdjustStockRequest;
import se.moln.orderservice.dto.UserResponse;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderItem;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient webClient;

    @Value("${userservice.url}")
    private String userServiceUrl;

    @Value("${productservice.url}")
    private String productServiceUrl;

    public OrderService(OrderRepository orderRepository, WebClient webClient) {
        this.orderRepository = orderRepository;
        this.webClient = webClient;
    }

    @Transactional
    public Mono<Order> createOrder(OrderRequest orderRequest) {
        return webClient.get()
                .uri(userServiceUrl + "/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + orderRequest.jwtToken())
                .retrieve()
                .onStatus(httpStatus -> httpStatus.isError(), clientResponse ->
                        Mono.error(new IllegalStateException("Användaren är ogiltig eller kunde inte hittas. Ogiltig JWT-token.")))
                .bodyToMono(UserResponse.class)
                .flatMap(userResponse -> {
                    UUID userId = userResponse.userId();

                    Order order = new Order();
                    order.setUserId(userId);
                    order.setOrderDate(LocalDateTime.now());
                    order.setStatus("PROCESSING");
                    order.setTotalAmount(BigDecimal.ZERO);

                    System.out.println("här");
                    return Flux.fromIterable(orderRequest.items())
                            .flatMap(itemRequest ->
                                    webClient.post()
                                            .uri(productServiceUrl + "/api/inventory/{productId}/purchase", itemRequest.productId())
                                            .bodyValue(new AdjustStockRequest(itemRequest.quantity()))
                                            .retrieve()
                                            .onStatus(httpStatus -> httpStatus.isError(), clientResponse ->
                                                    Mono.error(new IllegalStateException("Kunde inte reservera lager för produkt " + itemRequest.productId())))
                                            .bodyToMono(ProductResponse.class)
                                            .map(productResponse -> {
                                                System.out.println("här inne i map");
                                                OrderItem orderItem = new OrderItem();
                                                orderItem.setProductId(itemRequest.productId());
                                                orderItem.setQuantity(itemRequest.quantity());
                                                orderItem.setPriceAtPurchase(productResponse.price());
                                                orderItem.setProductName(productResponse.name());
                                                orderItem.setOrder(order);
                                                return orderItem;
                                            })
                            )
                            .collectList()
                            .doOnNext(orderItems -> {
                                order.setOrderItems(orderItems);
                                BigDecimal totalAmount = orderItems.stream()
                                        .map(item -> item.getPriceAtPurchase().multiply(new BigDecimal(item.getQuantity())))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                order.setTotalAmount(totalAmount);
                                order.setStatus("COMPLETED");
                            })
                            .flatMap(orderItems -> Mono.fromCallable(() -> orderRepository.save(order)));
                });
    }
}