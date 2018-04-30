package com.github.damiox.ecommerce.service;

import com.github.damiox.ecommerce.dao.UserRepository;
import com.github.damiox.ecommerce.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Date;
import javax.transaction.Transactional;

@Service
public class SecurityServiceImpl implements SecurityService, UserDetailsService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthenticationManager authenticationManager;

    @Transactional
    @Override
    public String authenticate(final String username, final String password) {
        final Authentication authentication =
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        final User user = this.loadUserByUsername(username);

        return JwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    @Transactional
    @Override
    public Authentication authenticate(final String token) {
        final Claims claims = JwtUtils.parseToken(token);

        User user = new User();
        user.setUsername(claims.getSubject());
        user.setPassword("");
        user.setId(Long.parseLong(claims.get(JwtUtils.TOKEN_CLAIM_SUB).toString()));
        user.setRole(claims.get(JwtUtils.TOKEN_CLAIM_ROLES).toString());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        // Setting up Authentication...
        SecurityContextHolder.getContext().setAuthentication(
            //new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
            authentication
        );

        return authentication;
    }

    @Transactional
    @Override
    public User loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password."));
    }

    private static class JwtUtils {
        private static final String TOKEN_SECRET_KEY = "ECommerceAppSecretKey";
        private static final String TOKEN_PREFIX = "Bearer";
        private static final String TOKEN_CLAIM_SUB = "sub";
        private static final String TOKEN_CLAIM_ROLES = "roles";
        private static final long TOKEN_EXPIRATION = 3_600_000; // 1 hour

        private static String generateToken(final Long userId, final String username, final String userRole) {
            final Date now = new Date();
            final Date exp = new Date(now.getTime() + TOKEN_EXPIRATION);

            String jwtToken =
                Jwts.builder()
                    .setSubject(username)
                    .claim(TOKEN_CLAIM_SUB, userId)
                    .claim(TOKEN_CLAIM_ROLES, userRole)
                    .setIssuedAt(now)
                    .setNotBefore(now)
                    .setExpiration(exp)
                    .signWith(SignatureAlgorithm.HS256, TOKEN_SECRET_KEY)
                    .compact();

            return TOKEN_PREFIX + " " + jwtToken;
        }

        private static Claims parseToken(String token) {
            try {
                return Jwts.parser()
                    .setSigningKey(TOKEN_SECRET_KEY)
                    .parseClaimsJws(token.replace(TOKEN_PREFIX, ""))
                    .getBody();
            } catch (Exception e) {
                throw new BadCredentialsException("Invalid token.");
            }
        }
    }

}
