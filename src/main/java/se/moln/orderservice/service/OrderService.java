package se.moln.orderservice.service;

import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import se.moln.orderservice.dto.AdjustStockRequest;
import se.moln.orderservice.dto.ProductResponse;
import se.moln.orderservice.dto.PurchaseResponse;
import se.moln.orderservice.dto.UserResponse;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderItem;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final WebClient.Builder webClientBuilder;
    private final JwtService jwtService;
    private final String userServiceUrl;
    private final String productServiceUrl;
    private final OrderRepository orderRepository;

    public OrderService(WebClient.Builder webClientBuilder,
                        JwtService jwtService,
                        OrderRepository orderRepository,
                        @Value("${userservice.url}") String userServiceUrl,
                        @Value("${productservice.url}") String productServiceUrl
    ) {
        this.webClientBuilder = webClientBuilder;
        this.jwtService = jwtService;
        this.orderRepository = orderRepository;
        this.userServiceUrl = userServiceUrl;
        this.productServiceUrl = productServiceUrl;
    }


    public Mono<PurchaseResponse> purchaseProduct(UUID productId, int quantity, String token) {
        validateTokenOrThrow(token);

        return fetchProduct(productId)
                .flatMap(product -> ensureInStock(product, quantity)
                        .then(purchaseInInventory(productId, quantity))
                        .zipWith(fetchCurrentUserId(token))
                        .flatMap(tuple -> {
                            PurchaseResponse purchaseResponse = tuple.getT1();
                            UUID userId = tuple.getT2();

                            Order order = buildOrder(userId, product, quantity);
                            return persistOrder(order).thenReturn(purchaseResponse);
                        })
                );
    }

    private void validateTokenOrThrow(String token) {
        try {
            if (!jwtService.isTokenValid(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ogiltig JWT-token.");
            }
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT-token verifiering misslyckades: " + e.getMessage());
        }
    }

    private Mono<ProductResponse> fetchProduct(UUID productId) {
        return webClientBuilder.build()
                .get()
                .uri(productServiceUrl + "/api/products/{id}", productId)
                .retrieve()
                .onStatus(s -> s.is4xxClientError(), r -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkten hittades inte.")))
                .onStatus(s -> s.is5xxServerError(), r -> Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Produktservice svarade med fel.")))
                .bodyToMono(ProductResponse.class);
    }

    private Mono<Void> ensureInStock(ProductResponse product, int quantity) {
        if (product.stockQuantity() < quantity) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Otillräckligt lager för produkten."));
        }
        return Mono.empty();
    }

    private Mono<PurchaseResponse> purchaseInInventory(UUID productId, int quantity) {
        return webClientBuilder.build()
                .post()
                .uri(productServiceUrl + "/api/inventory/{id}/purchase", productId)
                .bodyValue(new AdjustStockRequest(quantity))
                .retrieve()
                .onStatus(s -> s.is4xxClientError(), r -> Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Köp kunde inte genomföras (inventory).")))
                .onStatus(s -> s.is5xxServerError(), r -> Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Inventory svarade med fel.")))
                .bodyToMono(PurchaseResponse.class);
    }

    private Mono<UUID> fetchCurrentUserId(String token) {
        return webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .flatMap(user -> {
                    UUID uid = user.getId();
                    if (uid == null) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Kunde inte extrahera userId från /me-svaret."
                        ));
                    }
                    return Mono.just(uid);
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Kunde inte läsa inloggad användare (tomt svar från /me)."
                )));
    }


    private Order buildOrder(UUID userId, ProductResponse product, int quantity) {
        BigDecimal unitPrice = product.price();
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));

        OrderItem item = new OrderItem();
        item.setProductId(product.id());
        item.setQuantity(quantity);
        item.setPriceAtPurchase(unitPrice);
        item.setProductName(product.name());

        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus("COMPLETED");
        item.setOrder(order);
        order.setOrderItems(List.of(item));

        return order;
    }

    private Mono<Order> persistOrder(Order order) {
        return Mono.fromCallable(() -> orderRepository.save(order))
                .subscribeOn(Schedulers.boundedElastic());
    }
}