package se.moln.orderservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.moln.orderservice.dto.HoroscopeRequest;
import se.moln.orderservice.service.EntitlementClient;
import se.moln.orderservice.service.HoroscopeService;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("/api/horoscope")
public class HoroscopeController {

    private final HoroscopeService service;
    private final EntitlementClient entitlementClient;

    public HoroscopeController(HoroscopeService service, EntitlementClient entitlementClient) {
        this.service = service;
        this.entitlementClient = entitlementClient;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                    @Valid @RequestBody HoroscopeRequest req) {
        // Require entitlement HOROSCOPE_PDF
        String sku = "HOROSCOPE_PDF";
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "NEEDS_PURCHASE", "sku", sku));
        }
        boolean has = false;
        try {
            has = entitlementClient.hasEntitlement(auth, sku);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "NEEDS_PURCHASE", "sku", sku));
        }
        if (!has) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "NEEDS_PURCHASE", "sku", sku));
        }
        boolean consumed = entitlementClient.consume(auth, sku, 1);
        if (!consumed) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "NEEDS_PURCHASE", "sku", sku));
        }

        String id = service.generateHoroscopePdf(req);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @GetMapping(value = "/{id}/download")
    public ResponseEntity<FileSystemResource> download(
            @PathVariable String id,
            @RequestParam(name = "inline", defaultValue = "false") boolean inline
    ) {
        File f = service.resolvePdf(id);
        if (f == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        FileSystemResource resource = new FileSystemResource(f);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        // Stöd för inline-visning i webbläsaren
        String dispType = inline ? "inline" : "attachment";
        headers.set(HttpHeaders.CONTENT_DISPOSITION, dispType + "; filename=horoscope-" + id + ".pdf");
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }
}
