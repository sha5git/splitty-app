package com.expensesplit.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;

    @Value("${firebase.mock:false}")
    private boolean mockMode;

    public FirebaseTokenFilter(@Autowired(required = false) FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                FirebaseUserPrincipal principal;

                if (mockMode) {
                    // For mock/test authentication
                    String uid = token;
                    String email = uid + "@example.com";
                    String name = "Mock " + uid;
                    String avatarUrl = "https://example.com/avatar/" + uid;
                    principal = new FirebaseUserPrincipal(uid, email, name, avatarUrl);
                } else {
                    if (firebaseAuth == null) {
                        throw new IllegalStateException("FirebaseAuth bean is null but firebase.mock is false");
                    }
                    FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token);
                    String uid = decodedToken.getUid();
                    String email = decodedToken.getEmail();
                    String name = (String) decodedToken.getClaims().get("name");
                    String avatarUrl = (String) decodedToken.getClaims().get("picture");
                    principal = new FirebaseUserPrincipal(uid, email, name, avatarUrl);
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (FirebaseAuthException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid Firebase token: " + e.getMessage());
                return;
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Authentication failed: " + e.getMessage());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
