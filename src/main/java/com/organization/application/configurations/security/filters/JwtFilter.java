package com.organization.application.configurations.security.filters;

import com.organization.application.configurations.exceptions.InvalidTokenException;
import com.organization.application.configurations.security.jwt.JwtUtil;
import com.organization.application.configurations.security.service.UserPrincipal;
import com.organization.application.messages.ResponseMessages;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private static final  String BEARER_PART = "Bearer ";

    private final JwtUtil jwtUtil;

    private final UserDetailsService userDetailsService;

    private final SecurityErrorWriter securityErrorWriter;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService,
            SecurityErrorWriter securityErrorWriter) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull  HttpServletRequest request,@NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = getToken(request);
        if (token != null){
            String username = null;
            try {
                username = jwtUtil.getUsername(token);
                authenticate(username, request);
            }catch (InvalidTokenException e){
                log.debug("Token rejected: {}", e.getMessage());
            }catch (UsernameNotFoundException e){
                log.debug("Token references a user that no longer exists: {}", username);
            }catch (AuthenticationServiceException | DataAccessException | TransactionException e){
                log.error("Auth infrastructure failure", e);
                writeServiceUnavailable(response);
                return;
            }
        }

        filterChain.doFilter(request,response);
    }

    private void writeServiceUnavailable(HttpServletResponse response) throws IOException {
        securityErrorWriter.writeError(response,
                HttpServletResponse.SC_SERVICE_UNAVAILABLE, ResponseMessages.ERROR);
    }

    private void authenticate(String username, HttpServletRequest request) {
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
