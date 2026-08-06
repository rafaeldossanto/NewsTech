package com.web.newstech.shared.config;

import com.web.newstech.authoring.MongoUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.requestMatchers("/escrever/**").hasRole("AUTHOR")
						.requestMatchers("/actuator/health", "/actuator/info").permitAll()
						.requestMatchers("/actuator/**").hasRole("ADMIN")
						.anyRequest().permitAll())
				.formLogin(form -> form
						.loginPage("/entrar")
						.loginProcessingUrl("/entrar")
						.defaultSuccessUrl("/", false)
						.permitAll())
				.logout(logout -> logout.logoutSuccessUrl("/"))
				// CSRF fica ligado: cadastro, publicacao e as acoes do admin mudam estado.
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService(MongoUserDetailsService mongoUserDetailsService) {
		return mongoUserDetailsService;
	}

}
