package com.dashboard.backend.security;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String userEmail;

        try {
            userEmail = jwtService.extractEmail(jwt);
        } catch (ExpiredJwtException e) {
            System.out.println("❌ Token expiré");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expiré");
            return;
        } catch (Exception e) {
            System.out.println("❌ Token invalide");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalide");
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var userOptional = userRepository.findByEmail(userEmail);

            if (userOptional.isPresent()) {
                User user = userOptional.get();

                // ✅ On attribue une autorité par défaut
                var authorities = List.of("ROLE_USER");

                UserPrincipal principal = new UserPrincipal(user);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                System.out.println("✅ Authentifié : " + user.getEmail());
            } else {
                System.out.println("❌ Utilisateur introuvable en BDD : " + userEmail);
            }
        }

        filterChain.doFilter(request, response);
    }
}
