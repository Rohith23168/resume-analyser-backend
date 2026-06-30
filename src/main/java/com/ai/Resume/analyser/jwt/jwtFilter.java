package com.ai.Resume.analyser.jwt;

import com.ai.Resume.analyser.configuration.entryPointService;
import com.ai.Resume.analyser.model.usersTable;
import com.ai.Resume.analyser.repository.usersTableRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Service
public class jwtFilter extends OncePerRequestFilter {

    @Autowired
    private entryPointService entryService;

    @Autowired
    private usersTableRepo usersTableRepository;

    @Autowired
    private jwtService jwtservice;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Skip OPTIONS preflight requests
        if (request.getMethod().equals("OPTIONS")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only JWT/authentication resolution is wrapped in try/catch.
        // If anything here fails, we simply proceed unauthenticated rather than
        // breaking the request — we must NOT swallow exceptions thrown by the
        // actual controller/service further down the chain.
        try {
            String token = null;

            // Read JWT from cookie
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("entrypasstoken".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }

            // Validate token and set authentication context
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String email = jwtservice.getEmail(token);
                usersTable user = usersTableRepository.findById(email).orElse(null);

                if (user != null && jwtservice.validateToken(token, user.getEmail())) {
                    User userDetails = (User) entryService.loadUserByUsername(user.getEmail());

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception e) {
            // Auth resolution failed (bad/expired token, malformed JWT, etc).
            // Log it and continue unauthenticated — downstream security rules will
            // correctly reject the request with 401 if the endpoint requires auth.
            System.out.println("JWT Filter Error (auth resolution): " + e.getMessage());
            SecurityContextHolder.clearContext();
        }

        // The filter chain (and therefore the controller) must run EXACTLY ONCE.
        // Any exception thrown here is a real application error and must propagate
        // normally to Spring's exception handling — it must never be caught and
        // retried by re-calling filterChain.doFilter.
        filterChain.doFilter(request, response);
    }
}