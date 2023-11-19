package com.organization.application.configurations.security.jwt;

import com.organization.application.configurations.exceptions.InvalidTokenException;
import com.organization.application.messages.ExceptionMessages;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class JwtUtil {

    //encrypted key generator online
    @Value("${jwt.token.secretKey}")
    private String secretKey;

    @Value("${jwt.token.expiration}")
    private String timeExpiration;

    /** Necessary Methods to manage Tokens **/
    //1 - Create Token
    public String createToken(String username, Set<String> roles){

        Map<String,Object> claims = new HashMap<>();
        claims.put("roles",roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + Long.parseLong(timeExpiration)))
                .signWith(getSignatureKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    //2 - Get Signature Key
    public Key getSignatureKey(){
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    //3 - Validate Access Token
    public Boolean isTokenValid(String token){
        try{
            Jwts.parserBuilder()
                    .setSigningKey(getSignatureKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return true;
        }catch (Exception e){
            log.error("ERROR Invalid Token ".concat(e.getMessage()));
            throw new InvalidTokenException(ExceptionMessages.INVALIDATE_TOKEN,e);
        }

    }

    //4 - Get Claims From Token
    public Claims getAllClaims(String token){
        return Jwts.parserBuilder()
                .setSigningKey(getSignatureKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    //5 - Get One Claim From Token
    public <T> T getClaim(String token, Function<Claims, T> claimsTFunction){
        Claims claims = getAllClaims(token);
        return claimsTFunction.apply(claims);
    }

    //6 - Get Username From Token
    public String getUsername(String token){
        return getClaim(token, Claims::getSubject);
    }

    //7 - Get Expiration From Token
    public Date getExpiration(String token){
        return getClaim(token, Claims::getExpiration);
    }

    //8 - Validate Expiration
    public Boolean isTokenExpired(String token){
        return getExpiration(token).before(new Date());
    }

}
