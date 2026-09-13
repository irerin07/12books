package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.common.config.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthenticationEntryPoint authenticationEntryPoint;

	private final CorsProperties corsProperties;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationEntryPoint authenticationEntryPoint, CorsProperties corsProperties) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.corsProperties = corsProperties;
	}

	/**
	 * 토큰 기반 API라 세션·폼로그인이 필요 없다.
	 *
	 * <p>CSRF는 끈 채로 둔다. 인증은 전부 Authorization 헤더로 하므로 브라우저가 자동으로
	 * 실어 보내는 자격증명이 없다. 유일한 예외인 refresh 쿠키는 {@code SameSite=Strict} +
	 * POST 전용으로 막는다.
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.csrf(csrf -> csrf.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/v1/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
						// Phase 0~2를 눌러 확인하는 정적 검증 콘솔. 토큰을 받기 전에 열려야 하므로
						// 공개한다. 페이지 자체는 비밀을 담지 않고, 여기서 부르는 API는 아래
						// anyRequest().authenticated()가 그대로 지킨다.
						.requestMatchers(HttpMethod.GET, "/", "/index.html").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	/**
	 * 브라우저에서 다른 오리진의 프런트가 이 API를 부를 수 있게 한다.
	 *
	 * <p>허용 목록이 비어 있으면 <b>아무 오리진도 열지 않는다.</b> 설정을 깜빡한 것이
	 * "아무나 부를 수 있다"로 이어지면 안 되고, 개발 기본값을 넣어 두면 그 값이 그대로 배포로
	 * 따라간다.
	 *
	 * <p>{@code allowCredentials}가 켜져 있어야 refresh 쿠키가 오간다. 그래서 오리진에
	 * 와일드카드를 쓸 수 없다 — 브라우저가 그 조합을 거부한다.
	 */
	private CorsConfigurationSource corsConfigurationSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		if (corsProperties.allowedOrigins().isEmpty()) {
			return source;
		}
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(corsProperties.allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setAllowCredentials(true);
		config.setMaxAge(Duration.ofHours(1));
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
