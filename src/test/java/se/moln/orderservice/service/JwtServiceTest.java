package se.moln.orderservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"; // 64 chars

    private String buildToken(Date exp, String subject, UUID uid) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
                .setSubject(subject)
                .setIssuer("user-service")
                .setExpiration(exp);
        if (uid != null) {
            builder.claim("uid", uid.toString());
        }
        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    @Test
    void isTokenValid_true_forFutureExpiration() {
        JwtService svc = new JwtService(SECRET, "user-service");
        String token = buildToken(new Date(System.currentTimeMillis() + 60_000), UUID.randomUUID().toString(), null);
        assertTrue(svc.isTokenValid(token));
    }

    @Test
    void isTokenValid_false_forExpired() {
        JwtService svc = new JwtService(SECRET, "user-service");
        String token = buildToken(new Date(System.currentTimeMillis() - 60_000), UUID.randomUUID().toString(), null);
        assertFalse(svc.isTokenValid(token));
    }

    @Test
    void extractUserId_prefersUidClaim_thenFallbackToSub() {
        JwtService svc = new JwtService(SECRET, "user-service");
        UUID uid = UUID.randomUUID();
        String tokenWithUid = buildToken(new Date(System.currentTimeMillis() + 60_000), UUID.randomUUID().toString(), uid);
        assertEquals(uid, svc.extractUserId(tokenWithUid));

        UUID sub = UUID.randomUUID();
        String tokenWithSub = buildToken(new Date(System.currentTimeMillis() + 60_000), sub.toString(), null);
        assertEquals(sub, svc.extractUserId(tokenWithSub));
    }
}
