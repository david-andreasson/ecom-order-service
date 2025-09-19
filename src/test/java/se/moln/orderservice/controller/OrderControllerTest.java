 package se.moln.orderservice.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import se.moln.orderservice.dto.OrderHistoryDto;
import se.moln.orderservice.dto.PurchaseRequest;
import se.moln.orderservice.dto.PurchaseResponse;
import se.moln.orderservice.model.OrderStatus;
import se.moln.orderservice.service.OrderService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderControllerTest {

    @Test
    void purchase_passesTokenAndBodyToService_andReturnsOkResponse() {
        OrderService svc = mock(OrderService.class);
        OrderController ctrl = new OrderController(svc);

        UUID pid = UUID.randomUUID();
        PurchaseRequest req = new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(pid, 3)));
        PurchaseResponse expected = new PurchaseResponse(UUID.randomUUID(), "ORD-ABC12345", new BigDecimal("123.45"));

        when(svc.purchaseProduct(any(PurchaseRequest.class), any())).thenReturn(Mono.just(expected));

        var respEntity = ctrl.purchase("Bearer my.jwt.token", req).block();
        assertNotNull(respEntity);
        assertEquals(200, respEntity.getStatusCode().value());
        assertEquals(expected, respEntity.getBody());

        ArgumentCaptor<PurchaseRequest> reqCap = ArgumentCaptor.forClass(PurchaseRequest.class);
        ArgumentCaptor<String> tokCap = ArgumentCaptor.forClass(String.class);
        verify(svc).purchaseProduct(reqCap.capture(), tokCap.capture());
        PurchaseRequest captured = reqCap.getValue();
        assertNotNull(captured);
        assertEquals(1, captured.items().size());
        assertEquals(pid, captured.items().get(0).productId());
        assertEquals(3, captured.items().get(0).quantity());
        assertEquals("my.jwt.token", tokCap.getValue());
    }

    @Test
    void history_passesTokenAndPaging_andReturnsOkResponse() {
        OrderService svc = mock(OrderService.class);
        OrderController ctrl = new OrderController(svc);
        List<OrderHistoryDto> data = List.of(new OrderHistoryDto(
                UUID.randomUUID(), "ORD-1", new BigDecimal("10.00"), OrderStatus.CREATED,
                OffsetDateTime.now(), List.of()
        ));
        when(svc.getOrderHistory("tkn", 1, 5)).thenReturn(Mono.just(data));

        var respEntity = ctrl.history("Bearer tkn", 1, 5).block();
        assertNotNull(respEntity);
        assertEquals(200, respEntity.getStatusCode().value());
        assertEquals(data, respEntity.getBody());
    }

    @Test
    void history_missingBearerYieldsServiceError() {
        OrderService svc = mock(OrderService.class);
        OrderController ctrl = new OrderController(svc);
        when(svc.getOrderHistory(null, 0, 10)).thenReturn(Mono.error(new IllegalArgumentException("Missing bearer token")));

        assertThrows(IllegalArgumentException.class, () -> ctrl.history(null, 0, 10).block());
    }

    @Test
    void purchase_withoutBearerPrefix_passesNullToken_andBubblesServiceError() {
        OrderService svc = mock(OrderService.class);
        OrderController ctrl = new OrderController(svc);
        UUID pid = UUID.randomUUID();
        PurchaseRequest req = new PurchaseRequest(List.of(new PurchaseRequest.OrderItemRequest(pid, 1)));
        // When token is null, service is expected to error
        when(svc.purchaseProduct(any(PurchaseRequest.class), isNull()))
                .thenReturn(Mono.error(new IllegalArgumentException("Missing bearer token")));

        assertThrows(IllegalArgumentException.class, () -> ctrl.purchase("notbearer token", req).block());
        ArgumentCaptor<PurchaseRequest> reqCap2 = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(svc).purchaseProduct(reqCap2.capture(), isNull());
        assertEquals(1, reqCap2.getValue().items().size());
        assertEquals(pid, reqCap2.getValue().items().get(0).productId());
        assertEquals(1, reqCap2.getValue().items().get(0).quantity());
    }
}
