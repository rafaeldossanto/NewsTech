package com.web.newstech.shared.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * O portal e publico; o admin nao.
 *
 * <p>Usuario unico em memoria: nao ha cadastro de usuarios no produto e nao faz sentido
 * criar um so para uma tela de operacao. A senha chega em texto por variavel de ambiente
 * e e codificada com BCrypt na subida - em nenhum momento e gravada.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final NewsTechProperties properties;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.requestMatchers("/actuator/health", "/actuator/info").permitAll()
						.requestMatchers("/actuator/**").hasRole("ADMIN")
						.anyRequest().permitAll())
				.formLogin(form -> form.defaultSuccessUrl("/admin/sources", true))
				.logout(logout -> logout.logoutSuccessUrl("/"))
				// CSRF fica ligado: o admin tem formularios que mudam estado
				// (ativar fonte, disparar coleta) e sao exatamente o alvo desse ataque.
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
		UserDetails admin = User.withUsername(properties.admin().username())
				.password(passwordEncoder.encode(properties.admin().password()))
				.roles("ADMIN")
				.build();
		return new InMemoryUserDetailsManager(admin);
	}

}
