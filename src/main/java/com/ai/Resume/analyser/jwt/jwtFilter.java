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

        try {
            // Skip OPTIONS preflight requests
            if (request.getMethod().equals("OPTIONS")) {
                filterChain.doFilter(request, response);
                return;
            }

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

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            System.out.println("JWT Filter Error: " + e.getMessage());
            filterChain.doFilter(request, response);
        }
    }
}