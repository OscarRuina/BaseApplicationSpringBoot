package com.organization.application.configurations.security.filters;

import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.services.implementations.UserDetailsServiceImpl;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final static String BEARER_PART = "Bearer ";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    private String username = null;

    private Claims claims = null;

    /** Filter before accessing endpoint **/

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getServletPath().matches("auth/login|auth/register")){
            filterChain.doFilter(request,response);
        }else {
            String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            String token = null;

            if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PART)){
                token = authorizationHeader.substring(7);
                username = jwtUtil.getUsername(token);
                claims = jwtUtil.getAllClaims(token);
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null){
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtUtil.isTokenValid(token,userDetails)){
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails,
                                    null, userDetails.getAuthorities());
                    new WebAuthenticationDetailsSource().buildDetails(request);
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                }

            }

            filterChain.doFilter(request,response);

        }

    }

}
