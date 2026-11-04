package com.hydrosmart.billing.infrastructure.authorization.sfs.pipeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] expected;
    public InternalServiceAuthenticationFilter(String token) {
        if (token==null || token.length()<32) throw new IllegalStateException("INTERNAL_SERVICE_TOKEN must contain at least 32 characters");
        expected=token.getBytes(StandardCharsets.UTF_8);
    }
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if (request.getRequestURI().startsWith("/internal/v1/")) {
            var supplied=request.getHeader("X-Service-Token");
            if (supplied==null || !MessageDigest.isEqual(expected,supplied.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(401); return;
            }
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("telemetry",null,java.util.List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
        }
        chain.doFilter(request,response);
    }
}
