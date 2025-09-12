package se.moln.orderservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.issuer:user-service}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    public Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean isTokenValid(String token) {
        try {
            return token != null && !token.isBlank() && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract authenticated user UUID. Prefer custom 'uid' claim; fallback to standard 'sub'.
     */
    public UUID extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object uid = claims.get("uid");
        if (uid != null) {
            return UUID.fromString(uid.toString());
        }
        String sub = claims.getSubject();
        return UUID.fromString(sub);
    }
}