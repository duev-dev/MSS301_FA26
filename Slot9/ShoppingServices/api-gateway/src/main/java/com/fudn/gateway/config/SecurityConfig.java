package com.fudn.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // TODO DOC-16a: tai nguyen Swagger mo tu do (Swagger UI goi /aggregate/** khong kem JWT)
    private final String[] freeResourceUrls = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/swagger-resources/**", "/aggregate/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // REST API dung Bearer token, khong dung cookie/session -> tat CSRF, stateless
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // TODO 4.10 + DOC-16b: quy tac phan quyen
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll() // health check mo
                        .requestMatchers(freeResourceUrls).permitAll()      // Swagger docs mo
                        .anyRequest().authenticated()                       // con lai phai co JWT
                )

                // TODO DOC-16b: bat CORS cho Swagger UI / browser client
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // TODO 4.10: Gateway la OAuth2 Resource Server, xac thuc bang JWT
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    // TODO DOC-16c: cau hinh CORS cho moi path cua Gateway
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
