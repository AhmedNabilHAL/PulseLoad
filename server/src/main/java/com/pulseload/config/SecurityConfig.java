package com.pulseload.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Workshop security baseline (Topic 2). Authentication is session-cookie based:
 * a client proves
 * its identity once with HTTP Basic credentials, Spring establishes a session,
 * and the browser
 * carries the session cookie thereafter. Because the session is cookie-backed,
 * CSRF protection is
 * enabled using a cookie-stored token the SPA echoes back in a header.
 *
 * <p>
 * Authorization is role-based: everyone authenticated may read, only
 * OPERATOR/ADMIN may mutate
 * tests or control runs, and destructive deletes are gated further still by
 * method security
 * ({@code @PreAuthorize} on the controller).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        @Value("${app.cors.allowed-origins}")
        private List<String> allowedOrigins;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(Customizer.withDefaults())
                                // Token stored in a non-HttpOnly cookie (XSRF-TOKEN) so the SPA can read it and
                                // echo it back in the X-XSRF-TOKEN header on mutating requests. The plain
                                // request
                                // handler validates the raw cookie token (the default XOR handler expects an
                                // encoded token the SPA never receives).
                                .csrf(csrf -> csrf
                                                .csrfTokenRepository(cookieCsrfTokenRepository())
                                                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                                                // Mutations to tests and run control require an elevated role.
                                                .requestMatchers(HttpMethod.POST, "/tests/**")
                                                .hasAnyRole("OPERATOR", "ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/tests/**")
                                                .hasAnyRole("OPERATOR", "ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/tests/**")
                                                .hasAnyRole("OPERATOR", "ADMIN")
                                                .requestMatchers(HttpMethod.POST, "/runs/**")
                                                .hasAnyRole("OPERATOR", "ADMIN")
                                                // Reads are available to any authenticated user.
                                                .anyRequest().authenticated())
                                // Persist the Basic-authenticated context into the session so the client can
                                // authenticate later requests with the session cookie alone (HTTP Basic is
                                // otherwise per-request and would not establish a cookie-based session).
                                .httpBasic(basic -> basic
                                                .securityContextRepository(new HttpSessionSecurityContextRepository()));

                return http.build();
        }

        /**
         * Cookie path defaults to the servlet context path ({@code /api}), which
         * would hide the
         * cookie from {@code document.cookie} on the SPA's pages (served from
         * {@code /}, a
         * different path). Pinning it to {@code /} keeps the token readable by the
         * frontend
         * regardless of the API's context path.
         */
        private CookieCsrfTokenRepository cookieCsrfTokenRepository() {
                CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
                repository.setCookiePath("/");
                return repository;
        }

        @Bean
        public InMemoryUserDetailsManager userDetailsManager(PasswordEncoder passwordEncoder) {
                UserDetails viewer = User.withUsername("viewer")
                                .password(passwordEncoder.encode("viewer"))
                                .roles("VIEWER")
                                .build();
                UserDetails operator = User.withUsername("operator")
                                .password(passwordEncoder.encode("operator"))
                                .roles("OPERATOR")
                                .build();
                UserDetails admin = User.withUsername("admin")
                                .password(passwordEncoder.encode("admin"))
                                .roles("ADMIN")
                                .build();
                return new InMemoryUserDetailsManager(viewer, operator, admin);
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(allowedOrigins);
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(
                                List.of("Authorization", "Content-Type", "Accept", "Origin", "X-XSRF-TOKEN"));
                // Required so the browser sends the session and CSRF cookies on cross-origin
                // API calls.
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}