package com.dashboard.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;


@Service
public class JwtService {


    private final JwtProperties jwtProperties;
    private SecretKey secretKey;
    private JwtParser parser;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
            this.parser = Jwts.parser().verifyWith(secretKey).build();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid JWT secret key format : " + jwtProperties.getSecret(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JWT service", e);
        }
    }

    /**
     * Génère un token JWT avec des claims personnalisés (fullName, userId)
     */
    public String generateToken(String email, String fullName, UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getExpirationTime());

        return Jwts.builder()
                .subject(email)
                .claim("fullName", fullName)
                .claim("userId", userId.toString()) // UUID converti en String
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extrait l'ID utilisateur du token JWT
     */
    public UUID extractUserId(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        String userIdStr = claims.get("userId", String.class);
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }

    // Autres méthodes inchangées...
    public String extractEmail(String token) {
        return parser.parseSignedClaims(token).getPayload().getSubject();
    }

    public String extractFullName(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        return claims.get("fullName", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parser.parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
