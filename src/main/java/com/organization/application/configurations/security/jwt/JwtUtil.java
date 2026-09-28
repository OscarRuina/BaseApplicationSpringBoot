package com.organization.application.configurations.security.jwt;

import com.organization.application.configurations.exceptions.InvalidTokenException;
import com.organization.application.messages.ExceptionMessages;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class JwtUtil {

    private final Key signingKey;

    private final long timeExpiration;

    public JwtUtil(@Value("${jwt.token.secretKey}") String secretKey,
            @Value("${jwt.token.expiration}") long timeExpiration) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.timeExpiration = timeExpiration;
    }

    /** Necessary Methods to manage Tokens **/
    //1 - Create Token
    public String createToken(String username, Set<String> roles){

        Map<String,Object> claims = new HashMap<>();
        claims.put("roles",roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + timeExpiration))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    //2 - Get Username From Token
    public String getUsername(String token){
        try{
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        }catch (JwtException | IllegalArgumentException e){
            log.debug("Token rejected: {}", e.getMessage());
            throw new InvalidTokenException(ExceptionMessages.INVALIDATE_TOKEN,e);
        }
    }
}
