package se.moln.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import se.moln.orderservice.dto.OrderHistoryDto;
import se.moln.orderservice.dto.PurchaseRequest;
import se.moln.orderservice.dto.PurchaseResponse;
import se.moln.orderservice.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping(path = "/purchase", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Genomför köp",
            description = "Validerar produkt, reserverar/uppdaterar lager via Inventory och skapar order vid lyckad reservation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Köp genomfört",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PurchaseResponse.class),
                            examples = @ExampleObject(name = "PurchaseResponse",
                                    value = "{\n  \"orderId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\n  \"orderNumber\": \"ORD-ABC12345\",\n  \"totalAmount\": 12999\n}"))),
            @ApiResponse(responseCode = "400", description = "Ogiltig förfrågan",
                    content = @Content(mediaType = "application/problem+json",
                            examples = @ExampleObject(name = "BadRequest",
                                    value = "{\n  \"type\": \"about:blank\",\n  \"title\": \"Bad Request\",\n  \"status\": 400,\n  \"detail\": \"Missing bearer token\"\n}"))),
            @ApiResponse(responseCode = "401", description = "Otillåten (saknar eller ogiltig token)",
                    content = @Content(mediaType = "application/problem+json",
                            examples = @ExampleObject(name = "Unauthorized",
                                    value = "{\n  \"type\": \"about:blank\",\n  \"title\": \"Unauthorized\",\n  \"status\": 401,\n  \"detail\": \"Invalid or expired token\"\n}"))),
            @ApiResponse(responseCode = "404", description = "Produkten hittades inte",
                    content = @Content(mediaType = "application/problem+json",
                            examples = @ExampleObject(name = "NotFound",
                                    value = "{\n  \"type\": \"about:blank\",\n  \"title\": \"Not Found\",\n  \"status\": 404,\n  \"detail\": \"Product not found\"\n}"))),
            @ApiResponse(responseCode = "409", description = "Otillräckligt lager",
                    content = @Content(mediaType = "application/problem+json",
                            examples = @ExampleObject(name = "ConflictInsufficientStock",
                                    value = "{\n  \"type\": \"about:blank\",\n  \"title\": \"Conflict\",\n  \"status\": 409,\n  \"detail\": \"Insufficient stock | <downstream-body> | cid=<correlation-id>\"\n}"))),
            @ApiResponse(responseCode = "502", description = "Fel i bakomliggande tjänst (Product/Inventory)",
                    content = @Content(mediaType = "application/problem+json",
                            examples = @ExampleObject(name = "BadGateway",
                                    value = "{\n  \"type\": \"about:blank\",\n  \"title\": \"Bad Gateway\",\n  \"status\": 502,\n  \"detail\": \"Product service error | cid=<correlation-id>\"\n}")))
    })
    public Mono<ResponseEntity<PurchaseResponse>> purchase(
            @Parameter(description = "Bearer-token i formatet 'Bearer <JWT>'")
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody PurchaseRequest purchaseRequest) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        return orderService.purchaseProduct(purchaseRequest, token)
                .map(ResponseEntity::ok);
    }

    @GetMapping(path = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Hämta orderhistorik",
            description = "Returnerar paginerad lista av användarens ordrar med orderrader. Kräver autentisering."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrderHistoryDto.class),
                            examples = @ExampleObject(name = "OrderHistory",
                                    value = "[{\n  \"id\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\n  \"orderNumber\": \"ORD-ABC12345\",\n  \"totalAmount\": 25998,\n  \"status\": \"CREATED\",\n  \"orderDate\": \"2025-09-12T10:15:30Z\",\n  \"items\": [{\n    \"productId\": \"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3\",\n    \"productName\": \"iPhone 16 Pro\",\n    \"quantity\": 2,\n    \"priceAtPurchase\": 12999\n  }]\n}]"))),
            @ApiResponse(responseCode = "401", description = "Otillåten (saknar eller ogiltig token)", content = @Content)
    })
    public Mono<ResponseEntity<List<OrderHistoryDto>>> history(
            @Parameter(description = "Bearer-token i formatet 'Bearer <JWT>'")
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Parameter(description = "Sida (0-baserad)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Radsstorlek") @RequestParam(defaultValue = "200") int size) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        return orderService.getOrderHistory(token, page, size)
                .map(ResponseEntity::ok);
    }
}