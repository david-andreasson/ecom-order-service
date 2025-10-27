package se.moln.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import se.moln.orderservice.dto.ProductResponse;
import se.moln.orderservice.dto.PurchaseRequest;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServicePurchaseTest {

    private OrderRepository orderRepository;
    private JwtService jwtService;
    private EntitlementClient entitlementClient;
    private RestTemplate restTemplate;
    private OrderService orderService;
    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        jwtService = mock(JwtService.class);
        entitlementClient = mock(EntitlementClient.class);

        orderService = new OrderService(orderRepository, "http://product.test", jwtService, entitlementClient);
        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(orderService, "restTemplate", restTemplate);

        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
        when(jwtService.extractUserId("token"))
                .thenReturn(userId);
    }

    @Test
    void purchaseProduct_success_reservesInventory_andGrantsEntitlement() {
        ProductResponse product = new ProductResponse(productId, "USB-C Hub", new BigDecimal("24.99"), 10);

        when(restTemplate.exchange(
                eq("http://product.test/api/products/{id}"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProductResponse.class),
                any(UUID.class)))
                .thenReturn(ResponseEntity.ok(product));

        when(restTemplate.exchange(
                eq("http://product.test/api/inventory/{id}/purchase"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class),
                any(UUID.class)))
                .thenReturn(ResponseEntity.ok().build());

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(inv -> {
                    Order o = inv.getArgument(0);
                    o.setId(UUID.randomUUID());
                    return o;
                });

        PurchaseRequest request = new PurchaseRequest(
                List.of(new PurchaseRequest.OrderItemRequest(productId, 2)),
                null);

        var response = orderService.purchaseProduct(request, "token");

        assertNotNull(response);
        assertNotNull(response.orderId());
        assertTrue(response.orderNumber().startsWith("ORD-"));
        assertEquals(new BigDecimal("49.98"), response.totalAmount());

        verify(restTemplate).exchange(
                eq("http://product.test/api/products/{id}"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProductResponse.class),
                eq(productId));

        verify(restTemplate).exchange(
                eq("http://product.test/api/inventory/{id}/purchase"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class),
                eq(productId));

        verify(orderRepository).save(any(Order.class));
        verify(entitlementClient).grantEntitlement("Bearer " + "token", "HOROSCOPE_PDF", 2);
    }

    @Test
    void purchaseProduct_productService404_translatesToResponseStatusException() {
        when(restTemplate.exchange(
                eq("http://product.test/api/products/{id}"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProductResponse.class),
                any(UUID.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "not found"));

        PurchaseRequest request = new PurchaseRequest(
                List.of(new PurchaseRequest.OrderItemRequest(productId, 1)),
                null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderService.purchaseProduct(request, "token"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(orderRepository, never()).save(any());
        verify(entitlementClient, never()).grantEntitlement(anyString(), anyString(), anyInt());
    }

    @Test
    void purchaseProduct_missingToken_throwsIllegalArgumentException() {
        PurchaseRequest request = new PurchaseRequest(
                List.of(new PurchaseRequest.OrderItemRequest(productId, 1)),
                null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.purchaseProduct(request, null));

        assertTrue(ex.getMessage().toLowerCase().contains("missing bearer token"));
        verifyNoInteractions(restTemplate, orderRepository, entitlementClient, jwtService);
    }

    @Test
    void purchaseProduct_inventoryReservationFailure_surfacesOriginalException() {
        ProductResponse product = new ProductResponse(productId, "USB-C Hub", new BigDecimal("24.99"), 10);

        when(restTemplate.exchange(
                eq("http://product.test/api/products/{id}"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProductResponse.class),
                any(UUID.class)))
                .thenReturn(ResponseEntity.ok(product));

        when(restTemplate.exchange(
                eq("http://product.test/api/inventory/{id}/purchase"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class),
                any(UUID.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "conflict", new byte[0], StandardCharsets.UTF_8));

        PurchaseRequest request = new PurchaseRequest(
                List.of(new PurchaseRequest.OrderItemRequest(productId, 2)),
                null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderService.purchaseProduct(request, "token"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(orderRepository, never()).save(any());
    }
}
