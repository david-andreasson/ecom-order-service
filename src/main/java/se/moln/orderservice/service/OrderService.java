package se.moln.orderservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import se.moln.orderservice.dto.*;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderItem;
import se.moln.orderservice.model.OrderStatus;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final RestTemplate restTemplate;
    private final OrderRepository orderRepository;
    private final String userServiceUrl;
    private final String productUrl;
    private final JwtService jwtService;
    private final EntitlementClient entitlementClient;

    public OrderService(OrderRepository orderRepository,
                        @Value("${userservice.url}") String userServiceUrl,
                        @Value("${productservice.url}") String productUrl,
                        JwtService jwtService,
                        EntitlementClient entitlementClient) {
        this.restTemplate = new RestTemplate();
        this.orderRepository = orderRepository;
        this.userServiceUrl = userServiceUrl;
        this.productUrl = productUrl;
        this.jwtService = jwtService;
        this.entitlementClient = entitlementClient;
    }

    public PurchaseResponse purchaseProduct(PurchaseRequest request, String jwtToken) {
        if (jwtToken == null || jwtToken.isBlank()) {
            throw new IllegalArgumentException("Missing bearer token");
        }
        UUID userId = jwtService.extractUserId(jwtToken);
        String correlationId = UUID.randomUUID().toString();

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.CREATED);
        order.setOrderDate(OffsetDateTime.now());
        order.setOrderNumber(generateOrderNumber());
        
        // Link to payment if provided
        if (request.paymentId() != null && !request.paymentId().isBlank()) {
            order.setPaymentId(request.paymentId());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-Correlation-Id", correlationId);

        List<OrderItem> items = new ArrayList<>();
        for (var itemReq : request.items()) {
            // Get product info
            ResponseEntity<ProductResponse> prodResp;
            try {
                prodResp = restTemplate.exchange(
                        productUrl + "/api/products/{id}",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        ProductResponse.class,
                        itemReq.productId()
                );
            } catch (RestClientResponseException ex) {
                throw toUpstreamException("Product service error", ex, correlationId);
            }

            ProductResponse prod = prodResp.getBody();
            if (prod == null) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Product service returned empty body | cid=" + correlationId);
            }

            // Reserve inventory
            try {
                HttpHeaders postHeaders = new HttpHeaders();
                postHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
                postHeaders.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                postHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                postHeaders.set("X-Correlation-Id", correlationId);

                restTemplate.exchange(
                        productUrl + "/api/inventory/{id}/purchase",
                        HttpMethod.POST,
                        new HttpEntity<>(new InventoryPurchaseRequest(itemReq.quantity()), postHeaders),
                        Void.class,
                        itemReq.productId()
                );
            } catch (RestClientResponseException ex) {
                throw toUpstreamException("Insufficient stock or product service error", ex, correlationId);
            }

            OrderItem item = new OrderItem();
            item.setProductId(itemReq.productId());
            item.setQuantity(itemReq.quantity());
            item.setPriceAtPurchase(prod.price());
            item.setProductName(prod.name());
            item.setOrder(order);
            items.add(item);
        }

        order.setOrderItems(items);

        BigDecimal total = items.stream()
                .map(i -> i.getPriceAtPurchase().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);

        try {
            Order saved = orderRepository.save(order);
            
            // Grant entitlements for purchased products
            for (var itemReq : request.items()) {
                try {
                    // For now, hardcoded: HOROSCOPE_PDF gives 1 entitlement
                    // In production, this mapping should come from product metadata
                    entitlementClient.grantEntitlement("Bearer " + jwtToken, "HOROSCOPE_PDF", itemReq.quantity());
                } catch (Exception entErr) {
                    // Log but don't fail the order - entitlement can be granted manually
                    System.err.println("Failed to grant entitlement for order " + saved.getId() + ": " + entErr.getMessage());
                }
            }
            
            return new PurchaseResponse(saved.getId(), saved.getOrderNumber(), saved.getTotalAmount());
        } catch (Exception err) {
            // Rollback: return all reserved products (best-effort)
            for (OrderItem item : items) {
                try {
                    HttpHeaders postHeaders = new HttpHeaders();
                    postHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
                    postHeaders.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    postHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    postHeaders.set("X-Correlation-Id", correlationId);
                    restTemplate.exchange(
                            productUrl + "/api/inventory/{id}/return",
                            HttpMethod.POST,
                            new HttpEntity<>(new InventoryPurchaseRequest(item.getQuantity()), postHeaders),
                            Void.class,
                            item.getProductId()
                    );
                } catch (Exception ignore) {
                    // ignore rollback errors
                }
            }
            throw err;
        }
    }


    public List<OrderHistoryDto> getOrderHistory(String jwtToken, int page, int size) {
        if (jwtToken == null || jwtToken.isBlank()) {
            throw new IllegalArgumentException("Missing bearer token");
        }
        UUID userId = jwtService.extractUserId(jwtToken);
        var pageObj = orderRepository.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate")));
        return pageObj.getContent().stream().map(o -> new OrderHistoryDto(
                o.getId(),
                o.getOrderNumber(),
                o.getTotalAmount(),
                o.getStatus(),
                o.getOrderDate(),
                o.getOrderItems().stream().map(oi -> new OrderItemDto(
                        oi.getProductId(), oi.getProductName(), oi.getQuantity(), oi.getPriceAtPurchase()
                )).toList()
        )).toList();
    }


    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private ResponseStatusException toUpstreamException(String message, RestClientResponseException ex, String cid) {
        var status = org.springframework.http.HttpStatus.resolve(ex.getRawStatusCode());
        if (status == null) status = org.springframework.http.HttpStatus.BAD_GATEWAY;
        return new ResponseStatusException(status, message + " | " + ex.getResponseBodyAsString() + " | cid=" + cid);
    }
}