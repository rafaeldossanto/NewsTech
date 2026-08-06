package com.web.newstech.authoring;

import com.web.newstech.authoring.enums.Role;
import com.web.newstech.authoring.repository.UserRepository;
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Mantem a conta de admin em dia com as variaveis de ambiente.
 *
 * <p>Antes o admin vivia em memoria; agora e um usuario no banco. A senha continua vindo
 * de ADMIN_PASSWORD e e reaplicada a cada subida - assim trocar a variavel troca a senha,
 * que era o comportamento de quando ela era lida direto da configuracao.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements InitializingBean {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final NewsTechProperties properties;

	@Override
	public void afterPropertiesSet() {
		String username = properties.admin().username().toLowerCase(Locale.ROOT);
		Optional<User> existente = userRepository.findByUsername(username);

		User admin = existente.orElseGet(() -> User.builder()
				.username(username)
				.email(username + "@newstech.local")
				.createdAt(Instant.now())
				.build());

		admin.setPasswordHash(passwordEncoder.encode(properties.admin().password()));
		admin.setRoles(Set.of(Role.ADMIN, Role.AUTHOR));
		admin.setActive(true);
		userRepository.save(admin);

		if (existente.isEmpty()) {
			log.info("Conta de admin '{}' criada", username);
		}
	}

}
