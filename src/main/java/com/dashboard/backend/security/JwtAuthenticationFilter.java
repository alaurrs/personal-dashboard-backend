package com.dashboard.backend.security;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@AllArgsConstructor
@Slf4j
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
            log.warn("❌ Token JWT expiré : {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expiré");
            return;
        } catch (Exception e) {
            log.error("❌ Erreur lors de la validation du token JWT", e);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalide");
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            userRepository.findByEmail(userEmail).ifPresent(user -> {
                // Créer l'objet UserPrincipal pour obtenir les autorités, mais nous n'en aurons plus besoin ensuite.
                UserPrincipal userPrincipal = new UserPrincipal(user);

                // --- MODIFICATION CLÉ ---
                // Le premier argument est le "principal". Nous y mettons directement l'entité User.
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        user, // <-- C'EST LE CHANGEMENT QUI RÈGLE TOUT
                        null,
                        userPrincipal.getAuthorities() // On peut toujours utiliser le principal pour les autorités
                );
                // --- FIN DE LA MODIFICATION ---

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.info("✅ Utilisateur authentifié via JWT : {}", user.getEmail());
            });
        }

        filterChain.doFilter(request, response);
    }
}
