package com.ael.algoryqrservice.security;

import com.ael.algoryqrservice.print.security.PrintDeviceAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.savedrequest.NullRequestCache;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PrintDeviceAuthenticationFilter printDeviceAuthenticationFilter;
    private final ProductAccessGatewayFilter productAccessGatewayFilter;
    private final EmailVerificationGatewayFilter emailVerificationGatewayFilter;
    private final AuthRateLimitGatewayFilter authRateLimitGatewayFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final GoogleOidcAuthenticationSuccessHandler googleSuccessHandler;
    private final GoogleOidcAuthenticationFailureHandler googleFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/email-verification/resend",
                                "/auth/email-verification/verify"
                        ).permitAll()
                        .requestMatchers("/customer/auth/**").permitAll()
                        .requestMatchers("/waiter/auth/login", "/waiter/auth/refresh", "/waiter/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/auth/sessions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/auth/sessions/refresh").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/admin/auth/sessions").permitAll()
                        .requestMatchers("/google-auth/**").permitAll()
                        .requestMatchers(GoogleOAuthPaths.LEGACY_CALLBACK).permitAll()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/menu/public/**").permitAll()
                        .requestMatchers("/store/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/campaign/claim").permitAll()
                        .requestMatchers(HttpMethod.GET, "/menu/tags").permitAll()
                        .requestMatchers(HttpMethod.GET, "/menu/allergens").permitAll()
                        .requestMatchers(HttpMethod.GET, "/menu/chef-avatars").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/healthcheck").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/packages/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/analytics/menu/*/events").permitAll()
                        .requestMatchers(HttpMethod.POST, "/analytics/menu/*/visit").permitAll()
                        .requestMatchers(HttpMethod.POST, "/analytics/menu/*/product/*/visit").permitAll()
                        .requestMatchers(HttpMethod.POST, "/analytics/site/visit").permitAll()
                        .requestMatchers(HttpMethod.POST, "/integrations/ubereats/webhooks/orders").permitAll()
                        .requestMatchers("/integrations/odeal/test/**").permitAll()
                        .requestMatchers("/internal/integrations/**").permitAll()
                        .requestMatchers("/internal/menu-import/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/print-agent/devices/pair").permitAll()
                        .requestMatchers(HttpMethod.POST, "/print-agent/devices/connect").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/print-agent/jobs/**", "/print-agent/devices/heartbeat")
                            .hasRole("PRINT_AGENT")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri(GoogleOAuthPaths.CALLBACK)
                        )
                        .successHandler(googleSuccessHandler)
                        .failureHandler(googleFailureHandler)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(printDeviceAuthenticationFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(authRateLimitGatewayFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(productAccessGatewayFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(emailVerificationGatewayFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<ProductAccessGatewayFilter> productAccessGatewayFilterRegistration(
            ProductAccessGatewayFilter filter
    ) {
        FilterRegistrationBean<ProductAccessGatewayFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<EmailVerificationGatewayFilter> emailVerificationGatewayFilterRegistration(
            EmailVerificationGatewayFilter filter
    ) {
        FilterRegistrationBean<EmailVerificationGatewayFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitGatewayFilter> authRateLimitGatewayFilterRegistration(
            AuthRateLimitGatewayFilter filter
    ) {
        FilterRegistrationBean<AuthRateLimitGatewayFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    Map.of("message", "Bu işlem için ADMIN yetkisi gerekli")
            );
        };
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
