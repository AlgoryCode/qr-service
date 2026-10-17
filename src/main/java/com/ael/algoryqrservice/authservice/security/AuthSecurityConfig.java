package com.ael.algoryqrservice.authservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class AuthSecurityConfig {

    private final AuthJwtAuthenticationFilter authJwtAuthenticationFilter;
    private final GoogleOidcAuthenticationSuccessHandler googleSuccessHandler;
    private final GoogleOidcAuthenticationFailureHandler googleFailureHandler;
    private final GoogleOAuthLoopbackHostNormalizerFilter googleOAuthLoopbackHostNormalizerFilter;

    @Bean
    @Order(0)
    public SecurityFilterChain authApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/api/v1/auth/**",
                        "/auth/**",
                        "/google-auth/**",
                        "/oauth2/**",
                        "/login/oauth2/**"
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"message\":\"Forbidden\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/staff/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/demo/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/demo/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/demo/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/tokens/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/tokens/expire").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/tokens/revoke").permitAll()
                        .requestMatchers("/api/v1/auth/google/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers("/auth/google/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/google/redeem").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .redirectionEndpoint(redir -> redir.baseUri(GoogleOAuthPaths.CALLBACK))
                        .successHandler(googleSuccessHandler)
                        .failureHandler(googleFailureHandler)
                )
                .addFilterBefore(
                        googleOAuthLoopbackHostNormalizerFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(authJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
