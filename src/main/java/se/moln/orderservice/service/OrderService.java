package se.moln.orderservice.service;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import se.moln.orderservice.dto.AdjustStockRequest;
import se.moln.orderservice.dto.ProductResponse;
import se.moln.orderservice.dto.PurchaseResponse;

import java.util.UUID;

@Service
public class OrderService {


    private final GenericWebClient genericWebClient;
    private final JwtService jwtService;

    public OrderService(GenericWebClient genericWebClient, JwtService jwtService) {
        this.genericWebClient = genericWebClient;
        this.jwtService = jwtService;
    }


    public Mono<PurchaseResponse> purchaseProduct(UUID productId, int quantity, String token) {
        String email;
        try {
            if (!jwtService.isTokenValid(token)){
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ogiltig JWT-token."));
            }
            email = jwtService.extractSubject(token);
        }catch (JwtException e){
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT-token verifiering misslyckades: " + e.getMessage()));
        }
        System.out.println("Köp-begäran från: " + email);

        String getProductUri = String.format("/api/products/%s", productId);

        return genericWebClient.get(getProductUri, ProductResponse.class)
                .flatMap(product -> {
                    System.out.println("Aktuellt lagersaldo för produkten: " + product.stockQuantity());
                    if (product.stockQuantity() < quantity) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Otillräckligt lager för produkten."));
                    }

                    String postPurchaseUri = String.format("/api/inventory/%s/purchase", productId);
                    AdjustStockRequest request = new AdjustStockRequest(quantity);

                    return genericWebClient.post(postPurchaseUri, request, PurchaseResponse.class)
                            .doOnSuccess(purchaseResponse -> {
                                System.out.println("Köp genomfört för: " + email);
                            });
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkten hittades inte.")));
    }
}