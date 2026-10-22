package com.ledgerguard.ledger.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security. Two demo users are provisioned in memory: {@code alice} (ROLE_USER)
 * and {@code admin} (ROLE_ADMIN). Every endpoint except {@code /auth/token}, the Swagger UI, and
 * the health/metrics probes requires a valid bearer token.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(User.withUsername("alice")
                .password(encoder.encode("password123"))
                .roles("USER")
                .build());
        manager.createUser(User.withUsername("bob")
                .password(encoder.encode("password123"))
                .roles("USER")
                .build());
        manager.createUser(User.withUsername("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN")
                .build());
        return manager;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                              PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                            RestAuthenticationEntryPoint entryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/token").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
