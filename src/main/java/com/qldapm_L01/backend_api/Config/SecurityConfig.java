package com.qldapm_L01.backend_api.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // 1. Cấu hình cho Cơ chế CORS (Pre-flight requests)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 2. Nhóm API Xác thực, cấp mới Token
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/test").permitAll()

                        // 2.1 Nhóm API quản trị
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 3. Nhóm Public API Tài liệu (Khách vãng lai được mở xem danh sách và thông tin file)
                        .requestMatchers(HttpMethod.GET, "/api/documents").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/{id}/metadata").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/{id}/rating").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/{id}/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/{id}/pages/{pageNumber}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tags").permitAll()

                        // LƯU Ý QUAN TRỌNG: Quy tắc cho '/my' bắt buộc phải xếp TRƯỚC '/{id}' 
                        // để Spring Security không bắt nhầm chữ 'my' thành tham số 'id'
                        .requestMatchers(HttpMethod.GET, "/api/documents/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/documents/{id}").permitAll()

                        // 4. Nhóm API Tài liệu Yêu cầu Đăng nhập (POST, PUT, DELETE, download-url, v.v...)
                        .requestMatchers("/api/documents/**").authenticated()

                        // 5. Block mặc định cho tất cả các endpoint khác chưa khai báo
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*");
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
