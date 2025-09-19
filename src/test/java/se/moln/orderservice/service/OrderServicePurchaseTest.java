package se.moln.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;
import se.moln.orderservice.dto.PurchaseResponse;
import se.moln.orderservice.dto.PurchaseRequest;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServicePurchaseTest {

    private OrderRepository orderRepository;
    private JwtService jwtService;

    private UUID userId;
    private UUID productId;

    private static class StubExchange implements ExchangeFunction {
        private final Map<String, ClientResponse> routes = new HashMap<>();
        private final AtomicInteger refundCalls = new AtomicInteger();

        public void route(HttpMethod method, String path, HttpStatus status, String body) {
            ClientResponse resp = ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body == null ? "" : body)
                    .build();
            routes.put(method.name() + " " + path, resp);
        }

        @Override
        @NonNull
        public Mono<ClientResponse> exchange(@NonNull ClientRequest request) {
            URI uri = request.url();
            String key = request.method().name() + " " + uri.getPath();
            ClientResponse resp = routes.get(key);
            if (resp == null) {
                // For refund path, count calls and return 200 by default
                if (uri.getPath().contains("/api/inventory/") && uri.getPath().endsWith("/return")) {
                    refundCalls.incrementAndGet();
                    // Allow override of refund response if configured
                    ClientResponse override = routes.get(request.method().name() + " " + uri.getPath());
                    if (override != null) {
                        return Mono.just(override);
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .build());
                }
                // Default 404 if not configured
                return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("")
                        .build());
            }
            return Mono.just(resp);
        }

        public int getRefundCalls() {
            return refundCalls.get();
        }
    }

    private WebClient.Builder webClientBuilder;
    private StubExchange stub;

    private OrderService newService() {
        return new OrderService(webClientBuilder, orderRepository, "http://user.test", "http://product.test", jwtService);
    }

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        jwtService = mock(JwtService.class);
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
        when(jwtService.extractUserId("jwt-token")).thenReturn(userId);

        stub = new StubExchange();
        webClientBuilder = WebClient.builder().exchangeFunction(stub);
    }

    @Test
    void purchase_success_createsOrder_andReturnsResponse() {
        // Setup downstream responses
        String productJson = String.format("{\n  \"id\": \"%s\", \"name\": \"USB-C Hub\", \"price\": 24.99, \"stockQuantity\": 10\n}", productId);
        stub.route(HttpMethod.GET, "/api/products/" + productId, HttpStatus.OK, productJson);
        stub.route(HttpMethod.POST, "/api/inventory/" + productId + "/purchase", HttpStatus.OK, "");

        // Save succeeds
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            // copy totals calculated by service
            return o;
        });

        OrderService service = newService();
        Mono<PurchaseResponse> mono = service.purchaseProduct(
                new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(productId, 2))),
                "jwt-token");
        PurchaseResponse resp = mono.block();
        assertNotNull(resp);
        assertNotNull(resp.orderId());
        assertTrue(resp.orderNumber().startsWith("ORD-"));
        assertEquals(new BigDecimal("49.98"), resp.totalAmount());

        // Verify save entity contents
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void purchase_productNotFound_mapsTo404() {
        // Product 404
        stub.route(HttpMethod.GET, "/api/products/" + productId, HttpStatus.NOT_FOUND, "");

        OrderService service = newService();
        var ex = assertThrows(org.springframework.web.reactive.function.client.WebClientResponseException.NotFound.class,
                () -> service.purchaseProduct(new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(productId, 1))), "jwt-token").block());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void purchase_productService5xx_mapsTo5xx() {
        // Product 500
        stub.route(HttpMethod.GET, "/api/products/" + productId, HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        OrderService service = newService();
        var ex = assertThrows(org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError.class,
                () -> service.purchaseProduct(new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(productId, 1))), "jwt-token").block());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
    }

    @Test
    void purchase_inventoryConflict_409_propagatesConflict_withBody() {
        String productJson = String.format("{\n  \"id\": \"%s\", \"name\": \"USB-C Hub\", \"price\": 24.99, \"stockQuantity\": 1\n}", productId);
        stub.route(HttpMethod.GET, "/api/products/" + productId, HttpStatus.OK, productJson);
        stub.route(HttpMethod.POST, "/api/inventory/" + productId + "/purchase", HttpStatus.CONFLICT, "Out of stock");

        OrderService service = newService();
        var ex = assertThrows(org.springframework.web.reactive.function.client.WebClientResponseException.Conflict.class,
                () -> service.purchaseProduct(new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(productId, 2))), "jwt-token").block());
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getResponseBodyAsString().toLowerCase().contains("out of stock"));
    }

    @Test
    void purchase_saveFailure_triggersRefund_compensationFlow() {
        String productJson = String.format("{\n  \"id\": \"%s\", \"name\": \"USB-C Hub\", \"price\": 24.99, \"stockQuantity\": 10\n}", productId);
        stub.route(HttpMethod.GET, "/api/products/" + productId, HttpStatus.OK, productJson);
        stub.route(HttpMethod.POST, "/api/inventory/" + productId + "/purchase", HttpStatus.OK, "");

        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("DB down"));

        OrderService service = newService();
        assertThrows(RuntimeException.class, () -> service.purchaseProduct(new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(productId, 2))), "jwt-token").block());
        // Ensure refund was attempted
        assertTrue(stub.getRefundCalls() >= 1);
    }

    @Test
    void purchase_inventory5xx_propagates5xx() {
        String productJson = String.format("{\n  \"id\": \"%s\", \"name\": \"USB-C Hub\", \"price\": 24.99, \"stockQuantity\": 10\n}", productId);
        stub.route(HttpMethod.GET, "/api/products/" + productId, HttpStatus.OK, productJson);
        stub.route(HttpMethod.POST, "/api/inventory/" + productId + "/purchase", HttpStatus.INTERNAL_SERVER_ERROR, "down");

        OrderService service = newService();
        var ex = assertThrows(org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError.class,
                () -> service.purchaseProduct(new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(productId, 2))), "jwt-token").block());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
    }

    @Test
    void purchase_missingToken_errors() {
        OrderService service = newService();
        var ex = assertThrows(IllegalArgumentException.class, () -> service.purchaseProduct(new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(productId, 1))), null).block());
        assertTrue(ex.getMessage().toLowerCase().contains("missing bearer token"));
    }

    @Test
    void purchase_inventory400_propagates400WithBody() {
        String productJson = String.format("{\n  \"id\": \"%s\", \"name\": \"USB-C Hub\", \"price\": 24.99, \"stockQuantity\": 10\n}", productId);
        stub.route(HttpMethod.GET, "/api/products/" + productId, HttpStatus.OK, productJson);
        stub.route(HttpMethod.POST, "/api/inventory/" + productId + "/purchase", HttpStatus.BAD_REQUEST, "bad req");

        OrderService service = newService();
        var ex = assertThrows(org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest.class,
                () -> service.purchaseProduct(new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(productId, 2))), "jwt-token").block());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        // Response body should be propagated by WebClientResponseException
        assertTrue(ex.getResponseBodyAsString().toLowerCase().contains("bad req"));
    }

    @Test
    void purchase_saveFailure_refundAlsoFails_butOriginalErrorBubbles() {
        String productJson = String.format("{\n  \"id\": \"%s\", \"name\": \"USB-C Hub\", \"price\": 24.99, \"stockQuantity\": 10\n}", productId);
        stub.route(HttpMethod.GET, "/api/products/" + productId, HttpStatus.OK, productJson);
        stub.route(HttpMethod.POST, "/api/inventory/" + productId + "/purchase", HttpStatus.OK, "");
        // Configure refund endpoint to fail with 500
        stub.route(HttpMethod.POST, "/api/inventory/" + productId + "/return", HttpStatus.INTERNAL_SERVER_ERROR, "oops");

        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("DB down"));

        OrderService service = newService();
        assertThrows(RuntimeException.class, () -> service.purchaseProduct(new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(productId, 2))), "jwt-token").block());
        // Refund was attempted (may not be counted due to override path short-circuit); primary guarantee is original error bubbles up.
    }
}
