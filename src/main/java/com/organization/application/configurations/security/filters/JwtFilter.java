package com.organization.application.configurations.security.filters;

import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.configurations.security.service.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private static final  String BEARER_PART = "Bearer ";

    private final JwtUtil jwtUtil;

    private final UserDetailsService userDetailsService;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull  HttpServletRequest request,@NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = getToken(request);
        if (token != null){
            try {
                if (jwtUtil.isTokenValid(token) && Boolean.FALSE.equals(jwtUtil.isTokenExpired(token))){
                    authenticate(token, request);
                }
            }catch (RuntimeException e){
                log.debug("Token rejected: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request,response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        String username = jwtUtil.getUsername(token);

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UserPrincipal principal = (UserPrincipal) userDetails;

        if (!principal.getEntity().isActive()){
            log.debug("Token of an inactive user rejected. Username: {}", username);
            return;
        }

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken
                        (userDetails, null, userDetails.getAuthorities());
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        log.debug("Token found and valid. Username: {}", username);
    }

    private String getToken(HttpServletRequest httpServletRequest){
        String authHeader = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PART)){
            return authHeader.substring(BEARER_PART.length());
        }
        return null;
    }
}
