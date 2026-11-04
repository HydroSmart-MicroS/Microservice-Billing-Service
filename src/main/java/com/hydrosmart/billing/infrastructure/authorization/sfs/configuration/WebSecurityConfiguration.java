package com.hydrosmart.billing.infrastructure.authorization.sfs.configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class WebSecurityConfiguration {
    @org.springframework.beans.factory.annotation.Value("${INTERNAL_SERVICE_TOKEN}") private String token;
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/internal/v1/**").hasRole("SERVICE")
                .anyRequest().authenticated())
            .addFilterBefore(new com.hydrosmart.billing.infrastructure.authorization.sfs.pipeline.InternalServiceAuthenticationFilter(token),org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
            .addFilterBefore(new com.hydrosmart.billing.infrastructure.authorization.AccountLifecycle.Filter(),org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
            .addFilterBefore(new com.hydrosmart.billing.shared.infrastructure.messaging.RequestTrace.Filter(),org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
            .build();
    }
}
